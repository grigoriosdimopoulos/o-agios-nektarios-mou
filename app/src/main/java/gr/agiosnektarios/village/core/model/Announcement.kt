package gr.agiosnektarios.village.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Village-wide notice at `announcements/{id}`. Everyone reads them; only
 * administrators write them, which is enforced in the security rules rather
 * than only in the UI.
 */
data class Announcement(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val imageUrl: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Date? = null,
    val updatedAt: Date? = null,
)
