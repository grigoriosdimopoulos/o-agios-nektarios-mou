package gr.agiosnektarios.village.core.model

import com.google.firebase.firestore.Blob
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
    /**
     * An optional picture, as JPEG bytes.
     *
     * Unlike a report's photo this one rides inside the document: announcements
     * are rare and the list is capped, so the read stays small.
     */
    val image: Blob? = null,
    val authorId: String = "",
    val authorName: String = "",
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Date? = null,
    val updatedAt: Date? = null,
)
