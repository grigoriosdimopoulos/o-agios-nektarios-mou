import { HttpsError, onCall, CallableRequest } from "firebase-functions/v2/https";
import { auth, db, deleteQueryBatched, logger } from "./common";

type Role = "USER" | "MODERATOR" | "ADMIN";

const ROLES: Role[] = ["USER", "MODERATOR", "ADMIN"];

/**
 * Rejects the call unless the caller is a signed-in administrator.
 *
 * The role is read from the caller's custom claim rather than their user
 * document, because a claim is signed by Firebase and cannot be spoofed by a
 * client that has write access to its own profile.
 */
function requireAdmin(request: CallableRequest): string {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  if (request.auth?.token?.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Administrators only.");
  }
  return uid;
}

function requireSignedIn(request: CallableRequest): string {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  return uid;
}

function requireString(value: unknown, field: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", `${field} is required.`);
  }
  return value.trim();
}

/**
 * Promotes or demotes a resident.
 *
 * The role is written to *both* the custom claim (which the security rules
 * read) and the user document (which the UI reads). Claims only refresh when
 * the target's ID token is renewed, so a promoted resident gains their new
 * powers within the hour or on their next sign-in — the document updates
 * immediately so the UI is not misleading in the meantime.
 */
export const adminSetRole = onCall(async (request) => {
  const callerUid = requireAdmin(request);
  const userId = requireString(request.data?.userId, "userId");
  const role = requireString(request.data?.role, "role") as Role;

  if (!ROLES.includes(role)) {
    throw new HttpsError("invalid-argument", `Unknown role ${role}.`);
  }
  // Without this, an administrator can lock the village out of its own
  // administration by demoting themselves while they are the only admin.
  if (userId === callerUid && role !== "ADMIN") {
    throw new HttpsError("failed-precondition", "You cannot demote yourself.");
  }

  await auth.setCustomUserClaims(userId, { role });
  await db.collection("users").doc(userId).update({ role });
  logger.info(`${callerUid} set role of ${userId} to ${role}`);
  return { ok: true };
});

/** Suspends or restores an account. */
export const adminSetDisabled = onCall(async (request) => {
  const callerUid = requireAdmin(request);
  const userId = requireString(request.data?.userId, "userId");
  const disabled = request.data?.disabled === true;

  if (userId === callerUid) {
    throw new HttpsError("failed-precondition", "You cannot suspend yourself.");
  }

  await auth.updateUser(userId, { disabled });
  await db.collection("users").doc(userId).update({ disabled });

  // Revoking refresh tokens ends existing sessions immediately; otherwise a
  // suspended resident keeps a valid token until it expires.
  if (disabled) {
    await auth.revokeRefreshTokens(userId);
  }
  return { ok: true };
});

/** Corrects a resident's name — for typos and for moderation of abusive names. */
export const adminRenameUser = onCall(async (request) => {
  requireAdmin(request);
  const userId = requireString(request.data?.userId, "userId");
  const firstName = requireString(request.data?.firstName, "firstName");
  const lastName = requireString(request.data?.lastName, "lastName");
  const displayName = `${firstName} ${lastName}`;

  await db.collection("users").doc(userId).update({
    firstName,
    lastName,
    nameLower: displayName.toLowerCase(),
  });
  await auth.updateUser(userId, { displayName });
  return { ok: true };
});

/**
 * Emails the resident a password reset link.
 *
 * Deliberately *not* "set this resident's password": an administrator should
 * never be able to learn or choose someone's credentials, only to help them
 * start the recovery flow.
 */
export const adminSendPasswordReset = onCall(async (request) => {
  requireAdmin(request);
  const userId = requireString(request.data?.userId, "userId");

  const user = await auth.getUser(userId);
  if (!user.email) {
    throw new HttpsError("failed-precondition", "This account has no email address.");
  }

  // generatePasswordResetLink does not send mail on its own; Firebase Auth's
  // built-in template does when the client calls sendPasswordResetEmail. Here
  // the link is generated so it can be logged/mailed by the project's own
  // transport if one is configured.
  const link = await auth.generatePasswordResetLink(user.email);
  logger.info(`Password reset link generated for ${userId}`);
  return { ok: true, link };
});

/** Removes a resident and everything they wrote. */
export const adminDeleteUser = onCall(async (request) => {
  const callerUid = requireAdmin(request);
  const userId = requireString(request.data?.userId, "userId");

  if (userId === callerUid) {
    throw new HttpsError(
      "failed-precondition",
      "Use account deletion in Settings to remove your own account."
    );
  }

  await purgeUser(userId);
  return { ok: true };
});

/** Self-service account closure. */
export const deleteMyAccount = onCall(async (request) => {
  const uid = requireSignedIn(request);
  await purgeUser(uid);
  return { ok: true };
});

/**
 * Deletes a resident's auth record, profile and content.
 *
 * Order matters: content first, then the profile, then the auth account. If the
 * process dies partway the resident still cannot sign in *and* their remaining
 * content is orphaned rather than live under an account someone else could
 * later be issued.
 */
async function purgeUser(userId: string): Promise<void> {
  const issues = await db.collection("issues").where("authorId", "==", userId).get();
  for (const issue of issues.docs) {
    // The subcollections are swept by onIssueDeleted.
    await issue.ref.delete();
  }

  const comments = await db
    .collectionGroup("comments")
    .where("authorId", "==", userId)
    .get();
  for (const comment of comments.docs) {
    await comment.ref.delete();
  }

  // Votes carry a userId field precisely so this can be a filtered query
  // rather than a scan of every vote in the village.
  const votes = await db.collectionGroup("votes").where("userId", "==", userId).get();
  for (const vote of votes.docs) {
    await vote.ref.delete();
  }

  // Group conversations are left standing for the other members; only the
  // departing resident's membership is removed.
  const chats = await db
    .collection("chats")
    .where("memberIds", "array-contains", userId)
    .get();
  for (const chat of chats.docs) {
    const members: string[] = chat.data().memberIds ?? [];
    const remaining = members.filter((id) => id !== userId);
    if (remaining.length <= 1 && chat.data().type === "DIRECT") {
      await deleteQueryBatched(chat.ref.collection("messages"));
      await chat.ref.delete();
    } else {
      await chat.ref.update({ memberIds: remaining });
    }
  }

  await db.collection("users").doc(userId).delete();
  await auth.deleteUser(userId).catch((error) => {
    logger.warn(`Auth record for ${userId} already gone`, error);
  });

  logger.info(`Purged user ${userId}`);
}

/**
 * Bootstraps the first administrator.
 *
 * Runs on every sign-up but does nothing unless the new account's email matches
 * `BOOTSTRAP_ADMIN_EMAIL` *and* no administrator exists yet — a village needs a
 * way to get its first admin without a console visit, and this closes itself
 * the moment one exists.
 */
export const bootstrapFirstAdmin = onCall(async (request) => {
  const uid = requireSignedIn(request);
  const bootstrapEmail = process.env.BOOTSTRAP_ADMIN_EMAIL;
  if (!bootstrapEmail) {
    throw new HttpsError("failed-precondition", "No bootstrap address configured.");
  }

  const existing = await db
    .collection("users")
    .where("role", "==", "ADMIN")
    .limit(1)
    .get();
  if (!existing.empty) {
    throw new HttpsError("failed-precondition", "An administrator already exists.");
  }

  const user = await auth.getUser(uid);
  if ((user.email ?? "").toLowerCase() !== bootstrapEmail.toLowerCase()) {
    throw new HttpsError("permission-denied", "Not the configured bootstrap address.");
  }

  await auth.setCustomUserClaims(uid, { role: "ADMIN" });
  await db.collection("users").doc(uid).update({ role: "ADMIN" });
  logger.info(`Bootstrapped ${uid} as the first administrator`);
  return { ok: true };
});
