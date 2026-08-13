/**
 * Cloud Functions for the Agios Nektarios village app.
 *
 * Three jobs live here, and nothing else should:
 *
 * 1. **Counters** — vote tallies, comment totals, unread badges. Clients are
 *    forbidden from writing these by the security rules, so the numbers on
 *    screen cannot be forged.
 * 2. **Notifications** — everything that has to reach a resident who is not
 *    looking at the app.
 * 3. **Privileged operations** — deleting accounts, setting roles, cascading
 *    deletes. These need the Admin SDK and an authorisation check a client
 *    cannot skip.
 */

export {
  onIssueCreated,
  onIssueDeleted,
  onVoteWritten,
  onCommentCreated,
  onCommentDeleted,
  onIssueStatusChanged,
} from "./issues";

export { onMessageCreated } from "./chats";

export { onAnnouncementCreated } from "./announcements";

export {
  adminSetRole,
  adminSetDisabled,
  adminRenameUser,
  adminSendPasswordReset,
  adminDeleteUser,
  deleteMyAccount,
  bootstrapFirstAdmin,
} from "./admin";
