package gr.agiosnektarios.village.core.firestore

/** Every Firestore path in the app, in one place. */
object Collections {
    const val USERS = "users"
    const val ISSUES = "issues"
    const val COMMENTS = "comments"
    const val VOTES = "votes"
    const val PHOTOS = "photos"
    const val ANNOUNCEMENTS = "announcements"
    const val CHATS = "chats"
    const val MESSAGES = "messages"
}

/** FCM topics every signed-in device subscribes to. */
object Topics {
    const val ANNOUNCEMENTS = "announcements"
}
