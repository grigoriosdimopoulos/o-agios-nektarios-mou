import { HttpsError, onCall, CallableRequest } from "firebase-functions/v2/https";
import { auth, db, logger } from "./common";

/**
 * The one administrative act with no client-side equivalent.
 *
 * Everything else an administrator does — changing a role, suspending an
 * account, correcting a name, triggering a password reset — is now an ordinary
 * Firestore write gated by the security rules, and needs no server. Deleting
 * *somebody else's* Firebase Auth account is the exception: only the Admin SDK
 * can do it.
 *
 * Without this deployed, `AdminRepository.deleteUser` still removes the
 * resident's profile and everything they wrote; their login simply survives,
 * and using it again lands on the "complete your profile" screen as a new
 * resident. This function closes that last gap for anyone on the Blaze plan.
 */
export const adminDeleteAuthAccount = onCall(async (request: CallableRequest) => {
  const callerUid = request.auth?.uid;
  if (!callerUid) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }

  // The caller's role is read from Firestore rather than a custom claim,
  // because that is where the app keeps it now.
  const caller = await db.collection("users").doc(callerUid).get();
  if (caller.data()?.role !== "ADMIN" || caller.data()?.disabled === true) {
    throw new HttpsError("permission-denied", "Administrators only.");
  }

  const userId = request.data?.userId;
  if (typeof userId !== "string" || userId.trim().length === 0) {
    throw new HttpsError("invalid-argument", "userId is required.");
  }
  if (userId === callerUid) {
    throw new HttpsError(
      "failed-precondition",
      "Use account deletion in Settings to close your own account."
    );
  }

  await auth.deleteUser(userId).catch((error) => {
    logger.warn(`Auth record for ${userId} already gone`, error);
  });
  logger.info(`${callerUid} deleted the login for ${userId}`);
  return { ok: true };
});
