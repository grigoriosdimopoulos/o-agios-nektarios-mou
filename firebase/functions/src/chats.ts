import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { FieldValue, db, preview, sendToUser } from "./common";

const DEEP_LINK = "agiosnektarios://open";

/**
 * Fans a new message out to the conversation.
 *
 * Everything here is server-owned for a reason: the sender's client could
 * otherwise write other members' unread counters, and the conversation preview
 * would be whatever the sender claimed rather than what was actually sent.
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

    const updates: Record<string, unknown> = {
      lastMessage: previewText,
      lastMessageSenderId: message.senderId ?? "",
      lastMessageAt: message.createdAt ?? FieldValue.serverTimestamp(),
    };
    // The sender's own counter is untouched: they have obviously read it.
    others.forEach((id) => {
      updates[`unreadCounts.${id}`] = FieldValue.increment(1);
    });
    await chatRef.update(updates);

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
