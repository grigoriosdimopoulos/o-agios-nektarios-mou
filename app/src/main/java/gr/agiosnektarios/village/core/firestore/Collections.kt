package gr.agiosnektarios.village.core.firestore

/** Every Firestore path in the app, in one place. */
object Collections {
    const val USERS = "users"
    const val ISSUES = "issues"
    const val COMMENTS = "comments"
    const val VOTES = "votes"
    const val PHOTOS = "photos"
    const val NOTIFICATIONS = "notifications"
    const val ADMIN_CLAIMS = "adminClaims"
    const val CONFIG = "config"
    const val ANNOUNCEMENTS = "announcements"
    const val CHATS = "chats"
    const val MESSAGES = "messages"
    const val STREET_NAMES = "streetNames"
    const val EVENTS = "events"
    const val CONTACTS = "contacts"
}

/** FCM topics every signed-in device subscribes to. */
object Topics {
    const val ANNOUNCEMENTS = "announcements"
}
