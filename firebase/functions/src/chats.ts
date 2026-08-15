import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { db, preview, sendToUser } from "./common";

const DEEP_LINK = "agiosnektarios://open";

/**
 * Sends the push for a new message. Notification-only.
 *
 * The preview line and the unread badges are written by the sender's client in
 * the same batch as the message — see ChatRepository.sendMessage. Incrementing
 * them here as well would double every badge.
 */
export const onMessageCreated = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const chatId = event.params.chatId;
    const message = event.data?.data();
    if (!message) return;

    const chatRef = db.collection("chats").doc(chatId);
    const chat = (await chatRef.get()).data();
    if (!chat) return;

    const memberIds: string[] = Array.isArray(chat.memberIds) ? chat.memberIds : [];
    const others = memberIds.filter((id) => id !== message.senderId);

    const previewText = message.systemEvent
      ? ""
      : message.text
        ? preview(message.text, 80)
        : "📷";

    if (message.systemEvent) return;

    const isGroup = chat.type === "GROUP";
    const title = isGroup
      ? `${chat.title ?? ""} · ${message.senderName ?? ""}`
      : (message.senderName ?? "");

    await Promise.all(
      others.map((userId) =>
        sendToUser(userId, {
          type: "CHAT",
          title,
          body: previewText,
          deepLink: `${DEEP_LINK}/chat/${chatId}`,
          collapseKey: `chat-${chatId}`,
        })
      )
    );
  }
);
