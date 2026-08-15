import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentWritten,
} from "firebase-functions/v2/firestore";
import { db, preview, sendToUser } from "./common";

const DEEP_LINK = "agiosnektarios://open";

/**
 * These functions are OPTIONAL and notification-only.
 *
 * The app runs on the Spark plan, where there are no functions at all, so the
 * client maintains every counter inside a transaction and sweeps its own
 * subcollections. Nothing here writes a tally — if it did, deploying these
 * would double-count against the client's own increment, which is a far nastier
 * bug than simply having no push notifications.
 *
 * Deploy them only to add push (which genuinely needs a server, because the FCM
 * credential cannot ship inside the app). Everything else keeps working either
 * way.
 */

export const onVoteWritten = onDocumentWritten(
  "issues/{issueId}/votes/{voterId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    const becameUpvote = after?.value === 1 && before?.value !== 1;
    if (!becameUpvote) return;

    const issueId = event.params.issueId;
    const issue = (await db.collection("issues").doc(issueId).get()).data();
    if (!issue?.authorId) return;
    // Your own vote on your own report is not news to you.
    if (issue.authorId === event.params.voterId) return;

    await sendToUser(issue.authorId, {
      type: "VOTE",
      title: issue.title ?? "",
      body: String(issue.upvotes ?? 0),
      bodyKey: "notif_upvotes",
      deepLink: `${DEEP_LINK}/issue/${issueId}`,
      // One collapse key per report: ten upvotes update one notification
      // rather than stacking ten.
      collapseKey: `vote-${issueId}`,
    });
  }
);

export const onCommentCreated = onDocumentCreated(
  "issues/{issueId}/comments/{commentId}",
  async (event) => {
    const issueId = event.params.issueId;
    const comment = event.data?.data();
    if (!comment) return;

    const issue = (await db.collection("issues").doc(issueId).get()).data();
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

/**
 * Tells the author and everyone who upvoted when a report's status changes.
 *
 * Upvoters are included because upvoting is how a resident says "this affects
 * me too" — they are exactly the people who want to know it was fixed.
 */
export const onIssueStatusChanged = onDocumentUpdated(
  "issues/{issueId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;
    if (before.statusId === after.statusId) return;

    const issueId = event.params.issueId;
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

    const bodyKey =
      after.statusId === "RESOLVED"
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
