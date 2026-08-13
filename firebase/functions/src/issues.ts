import {
  onDocumentCreated,
  onDocumentDeleted,
  onDocumentUpdated,
  onDocumentWritten,
} from "firebase-functions/v2/firestore";
import {
  FieldValue,
  db,
  deleteQueryBatched,
  logger,
  preview,
  sendToUser,
} from "./common";

const DEEP_LINK = "agiosnektarios://open";

/** Keeps the author's lifetime report counter in step. */
export const onIssueCreated = onDocumentCreated("issues/{issueId}", async (event) => {
  const issue = event.data?.data();
  if (!issue?.authorId) return;

  await db
    .collection("users")
    .doc(issue.authorId)
    .update({ issueCount: FieldValue.increment(1) })
    .catch((error) => logger.warn("Could not bump issueCount", error));
});

/**
 * Cascades the delete.
 *
 * A client can only remove the issue document itself, so its votes and
 * comments would otherwise survive as orphan subcollections that still cost
 * storage and can never be read.
 */
export const onIssueDeleted = onDocumentDeleted("issues/{issueId}", async (event) => {
  const issueId = event.params.issueId;
  const issue = event.data?.data();

  const [votes, comments] = await Promise.all([
    deleteQueryBatched(db.collection("issues").doc(issueId).collection("votes")),
    deleteQueryBatched(db.collection("issues").doc(issueId).collection("comments")),
  ]);
  logger.info(`Cleaned up issue ${issueId}: ${votes} votes, ${comments} comments`);

  if (issue?.authorId) {
    await db
      .collection("users")
      .doc(issue.authorId)
      .update({ issueCount: FieldValue.increment(-1) })
      .catch(() => undefined);
  }
});

/**
 * Recomputes vote tallies from the votes subcollection.
 *
 * Counting the documents rather than incrementing on each write is what makes
 * the totals self-healing: a lost trigger or a manual edit is corrected by the
 * next vote instead of leaving a permanently wrong number on screen.
 */
export const onVoteWritten = onDocumentWritten(
  "issues/{issueId}/votes/{voterId}",
  async (event) => {
    const issueId = event.params.issueId;
    const issueRef = db.collection("issues").doc(issueId);

    const votes = await issueRef.collection("votes").get();
    let upvotes = 0;
    let downvotes = 0;
    votes.forEach((doc) => {
      const value = doc.data().value;
      if (value === 1) upvotes += 1;
      else if (value === -1) downvotes += 1;
    });

    await issueRef.update({
      upvotes,
      downvotes,
      score: upvotes - downvotes,
    });

    const before = event.data?.before.data();
    const after = event.data?.after.data();
    const becameUpvote = after?.value === 1 && before?.value !== 1;
    if (!becameUpvote) return;

    const issue = (await issueRef.get()).data();
    if (!issue?.authorId) return;

    // Author's own vote on their own report is not news to them.
    if (issue.authorId === event.params.voterId) return;

    await Promise.all([
      db
        .collection("users")
        .doc(issue.authorId)
        .update({ upvotesReceived: FieldValue.increment(1) })
        .catch(() => undefined),
      sendToUser(issue.authorId, {
        type: "VOTE",
        title: issue.title ?? "",
        body: String(upvotes),
        bodyKey: "notif_upvotes",
        deepLink: `${DEEP_LINK}/issue/${issueId}`,
        // One collapse key per issue: ten upvotes produce one notification
        // that updates, not ten separate ones.
        collapseKey: `vote-${issueId}`,
      }),
    ]);
  }
);

export const onCommentCreated = onDocumentCreated(
  "issues/{issueId}/comments/{commentId}",
  async (event) => {
    const issueId = event.params.issueId;
    const comment = event.data?.data();
    if (!comment) return;

    const issueRef = db.collection("issues").doc(issueId);
    await issueRef.update({ commentCount: FieldValue.increment(1) });

    const issue = (await issueRef.get()).data();
    if (!issue?.authorId || issue.authorId === comment.authorId) return;

    await sendToUser(issue.authorId, {
      type: "COMMENT",
      title: issue.title ?? "",
      body: `${comment.authorName ?? ""}: ${preview(comment.text ?? "")}`,
      deepLink: `${DEEP_LINK}/issue/${issueId}`,
      collapseKey: `comment-${issueId}`,
    });
  }
);

export const onCommentDeleted = onDocumentDeleted(
  "issues/{issueId}/comments/{commentId}",
  async (event) => {
    await db
      .collection("issues")
      .doc(event.params.issueId)
      .update({ commentCount: FieldValue.increment(-1) })
      .catch(() => undefined);
  }
);

/**
 * Notifies the author and everyone who upvoted when a report's status changes.
 *
 * Upvoters are told because upvoting is how a resident says "this affects me
 * too" — they are exactly the people who want to know it was fixed.
 */
export const onIssueStatusChanged = onDocumentUpdated(
  "issues/{issueId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;
    if (before.statusId === after.statusId) return;

    const issueId = event.params.issueId;
    const isResolved = after.statusId === "RESOLVED";

    if (after.authorId) {
      const wasTerminal = before.statusId === "RESOLVED" || before.statusId === "WONT_DO";
      const isTerminal = after.statusId === "RESOLVED" || after.statusId === "WONT_DO";
      if (!wasTerminal && isTerminal) {
        await db
          .collection("users")
          .doc(after.authorId)
          .update({ resolvedCount: FieldValue.increment(1) })
          .catch(() => undefined);
      } else if (wasTerminal && !isTerminal) {
        await db
          .collection("users")
          .doc(after.authorId)
          .update({ resolvedCount: FieldValue.increment(-1) })
          .catch(() => undefined);
      }
    }

    const votes = await db
      .collection("issues")
      .doc(issueId)
      .collection("votes")
      .where("value", "==", 1)
      .get();

    const recipients = new Set<string>();
    if (after.authorId) recipients.add(after.authorId);
    votes.forEach((doc) => recipients.add(doc.id));
    // Whoever made the change already knows about it.
    if (after.resolvedById) recipients.delete(after.resolvedById);

    const bodyKey = isResolved
      ? "notif_status_resolved"
      : after.statusId === "WONT_DO"
        ? "notif_status_wont_do"
        : "notif_status_changed";

    await Promise.all(
      [...recipients].map((userId) =>
        sendToUser(userId, {
          type: "STATUS",
          title: after.title ?? "",
          body: "",
          bodyKey,
          deepLink: `${DEEP_LINK}/issue/${issueId}`,
          collapseKey: `status-${issueId}`,
        })
      )
    );
  }
);
