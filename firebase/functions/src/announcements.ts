import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { logger, messaging, preview } from "./common";

const DEEP_LINK = "agiosnektarios://open";

/**
 * Broadcasts a new announcement to the whole village.
 *
 * This is the one place that uses an FCM *topic* rather than per-user sends:
 * the message is identical for everyone, so one publish beats N multicasts.
 * The trade-off is that the topic cannot consult each resident's notification
 * preference — the app handles that by unsubscribing the device from the topic
 * when the resident turns announcements off.
 */
export const onAnnouncementCreated = onDocumentCreated(
  "announcements/{announcementId}",
  async (event) => {
    const announcement = event.data?.data();
    if (!announcement) return;

    await messaging
      .send({
        topic: "announcements",
        data: {
          type: "ANNOUNCEMENT",
          title: announcement.title ?? "",
          body: preview(announcement.body ?? "", 160),
          bodyKey: "",
          deepLink: `${DEEP_LINK}/announcements`,
          collapseKey: `announcement-${event.params.announcementId}`,
        },
        android: { priority: "high" },
      })
      .catch((error) => logger.error("Announcement broadcast failed", error));
  }
);
