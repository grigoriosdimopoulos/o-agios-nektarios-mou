import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { setGlobalOptions } from "firebase-functions/v2";
import { logger } from "firebase-functions";

initializeApp();

/**
 * europe-west1 keeps functions next to the Firestore instance the app is
 * expected to run in; the client pins the same region when calling callables,
 * so the two must be changed together.
 */
export const REGION = "europe-west1";

setGlobalOptions({ region: REGION, maxInstances: 10 });

export const db = getFirestore();
export const auth = getAuth();
export const messaging = getMessaging();
export { FieldValue, logger };

export type NotificationType =
  | "COMMENT"
  | "STATUS"
  | "VOTE"
  | "CHAT"
  | "ANNOUNCEMENT";

/** Maps a notification type onto the user-document preference that gates it. */
const PREF_KEY: Record<NotificationType, string> = {
  COMMENT: "comments",
  STATUS: "statusChanges",
  VOTE: "votes",
  CHAT: "chat",
  ANNOUNCEMENT: "announcements",
};

export interface PushPayload {
  type: NotificationType;
  title: string;
  /**
   * Literal body text — user-authored content such as a comment or a chat
   * message, which is already in whatever language its author wrote it in.
   */
  body: string;
  /**
   * Key for a body the *app* words itself ("Marked as resolved").
   *
   * The server has no reliable way to know which language a resident reads —
   * they can switch it in Settings independently of their device locale — so
   * anything the product says rather than quotes is sent as a key and resolved
   * against the app's own string resources. [body] becomes the format argument.
   */
  bodyKey?: string;
  /** Deep link the notification opens; must match a pattern in the nav graph. */
  deepLink: string;
  /** Notifications sharing a key replace each other instead of stacking. */
  collapseKey: string;
}

/**
 * Sends a data-only push to every device a resident is signed in on.
 *
 * Data-only (no `notification` block) so the Android app always gets
 * `onMessageReceived` and can suppress or route the message itself — a
 * notification block would be rendered by the system even for the conversation
 * the user is currently reading.
 *
 * Returns silently when the resident has switched this category off or has no
 * registered devices; callers treat notification delivery as best-effort and
 * never fail their trigger because of it.
 */
export async function sendToUser(
  userId: string,
  payload: PushPayload
): Promise<void> {
  const snapshot = await db.collection("users").doc(userId).get();
  if (!snapshot.exists) return;

  const data = snapshot.data() ?? {};
  if (data.disabled === true) return;

  const prefs = data.notificationPrefs ?? {};
  if (prefs[PREF_KEY[payload.type]] === false) return;

  const tokens: string[] = Array.isArray(data.fcmTokens) ? data.fcmTokens : [];
  if (tokens.length === 0) return;

  const response = await messaging.sendEachForMulticast({
    tokens,
    data: {
      type: payload.type,
      title: payload.title,
      body: payload.body,
      bodyKey: payload.bodyKey ?? "",
      deepLink: payload.deepLink,
      collapseKey: payload.collapseKey,
    },
    android: { priority: "high" },
  });

  await pruneStaleTokens(userId, tokens, response.responses);
}

/**
 * Drops tokens FCM reports as dead.
 *
 * Without this, a user who reinstalls the app accumulates registrations
 * forever and every send burns quota on devices that no longer exist.
 */
async function pruneStaleTokens(
  userId: string,
  tokens: string[],
  responses: { success: boolean; error?: { code: string } }[]
): Promise<void> {
  const stale = tokens.filter((_, index) => {
    const result = responses[index];
    if (result?.success) return false;
    const code = result?.error?.code ?? "";
    return (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token" ||
      code === "messaging/invalid-argument"
    );
  });

  if (stale.length === 0) return;

  logger.info(`Pruning ${stale.length} stale token(s) for ${userId}`);
  await db
    .collection("users")
    .doc(userId)
    .update({ fcmTokens: FieldValue.arrayRemove(...stale) });
}

/** Truncates a body to something that fits in a notification shade. */
export function preview(text: string, max = 120): string {
  const trimmed = text.trim().replace(/\s+/g, " ");
  return trimmed.length <= max ? trimmed : `${trimmed.slice(0, max - 1)}…`;
}

/**
 * Deletes every document in a query, in batches.
 *
 * Used by the cascading deletes. Firestore caps a batch at 500 writes, so this
 * loops until the query comes back empty rather than assuming one pass is
 * enough.
 */
export async function deleteQueryBatched(
  query: FirebaseFirestore.Query,
  batchSize = 300
): Promise<number> {
  let deleted = 0;
  for (;;) {
    const snapshot = await query.limit(batchSize).get();
    if (snapshot.empty) return deleted;

    const batch = db.batch();
    snapshot.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    deleted += snapshot.size;

    if (snapshot.size < batchSize) return deleted;
  }
}
