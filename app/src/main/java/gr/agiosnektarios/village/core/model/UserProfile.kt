package gr.agiosnektarios.village.core.model

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A resident of the village.
 *
 * Stored at `users/{uid}` where the document id is always the Firebase Auth uid,
 * which is what the security rules key ownership off.
 */
data class UserProfile(
    @DocumentId val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    /**
     * The resident's own picture, as JPEG bytes.
     *
     * Held here and nowhere else. Earlier versions copied an avatar URL onto
     * every report, comment and message the person wrote; with the image itself
     * in the document that would mean carrying a copy of their face on every
     * line of every conversation. Places that show an author without loading
     * their profile draw the initials monogram instead.
     */
    val avatar: Blob? = null,
    /** Lower-cased "first last", kept in sync on write to support prefix search. */
    val nameLower: String = "",
    val role: String = Role.USER.id,
    val disabled: Boolean = false,
    /** Registration tokens for every device this resident is signed in on. */
    val fcmTokens: List<String> = emptyList(),
    val notificationPrefs: NotificationPrefs = NotificationPrefs(),
    @ServerTimestamp val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    /** Falls back to the email local part so a half-filled profile still renders. */
    val displayName: String
        get() = fullName.ifBlank { email.substringBefore('@') }.ifBlank { "—" }

    val initials: String
        get() {
            val letters = listOfNotNull(
                firstName.firstOrNull(),
                lastName.firstOrNull(),
            ).ifEmpty { listOfNotNull(displayName.firstOrNull()) }
            return letters.joinToString("").uppercase()
        }

    val avatarBytes: ByteArray? get() = avatar?.toBytes()

    val roleType: Role get() = Role.fromId(role)

    val isAdmin: Boolean get() = roleType == Role.ADMIN

    /** Moderators get every issue-level privilege an admin has, but no user management. */
    val canModerate: Boolean get() = roleType == Role.ADMIN || roleType == Role.MODERATOR
}

enum class Role(val id: String) {
    USER("USER"),
    MODERATOR("MODERATOR"),
    ADMIN("ADMIN"),
    ;

    companion object {
        fun fromId(id: String?): Role = entries.firstOrNull { it.id == id } ?: USER
    }
}

data class NotificationPrefs(
    val comments: Boolean = true,
    val statusChanges: Boolean = true,
    val votes: Boolean = true,
    val announcements: Boolean = true,
    val chat: Boolean = true,
)
