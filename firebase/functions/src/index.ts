/**
 * OPTIONAL Cloud Functions for the Agios Nektarios village app.
 *
 * The app is designed to run with none of this, on the free Spark plan: the
 * client maintains its own counters inside transactions, sweeps its own
 * subcollections, and administers roles through Firestore documents that the
 * security rules gate.
 *
 * The single thing a client genuinely cannot do is send a push notification,
 * because that needs an FCM credential and anything shipped inside an APK is
 * public. That is all these functions are for. None of them writes a counter —
 * doing so would double-count against the client and produce a worse bug than
 * the missing notifications they fix.
 *
 * Deploying them requires the Blaze plan. Nothing breaks if you never do.
 */

export {
  onVoteWritten,
  onCommentCreated,
  onIssueStatusChanged,
} from "./issues";

export { onMessageCreated } from "./chats";

export { onAnnouncementCreated } from "./announcements";

// Deleting a Firebase Auth account belonging to someone else is the one
// administrative act with no client-side equivalent, so it stays here as an
// optional upgrade. Roles, suspension, renaming and password resets are all
// plain Firestore writes now — see AdminRepository.
export { adminDeleteAuthAccount } from "./admin";
