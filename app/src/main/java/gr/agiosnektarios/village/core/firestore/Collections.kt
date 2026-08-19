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
    const val ALERTS = "alerts"

    /** Per-resident documents nobody else may read. See [Collections.HOME]. */
    const val PRIVATE = "private"
    const val HOME = "home"

    /** The resident's own telephone number, readable by them and an admin. */
    const val CONTACT = "contact"

    /** What the village has switched on. One document, [FeatureFlags.DOCUMENT]. */
    const val FEATURE_FLAGS = "featureFlags"

    /**
     * Numbers whose owners have agreed they may be texted in an emergency.
     *
     * A separate collection precisely so its readability can be governed on
     * its own: a field on the profile could not be, because Firestore's rules
     * grant a document at a time and the profile has to be readable.
     */
    const val EMERGENCY_CONTACTS = "emergencyContacts"
}

/** FCM topics every signed-in device subscribes to. */
object Topics {
    const val ANNOUNCEMENTS = "announcements"
}
