package gr.agiosnektarios.village.data.announcement

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.firestore.orEmptyOnError
import gr.agiosnektarios.village.core.firestore.orNullOnError
import gr.agiosnektarios.village.core.runCatchingUnit
import gr.agiosnektarios.village.core.firestore.Collections
import gr.agiosnektarios.village.core.firestore.asFlow
import gr.agiosnektarios.village.core.firestore.toObjectSafe
import gr.agiosnektarios.village.core.firestore.toObjectsSafe
import gr.agiosnektarios.village.core.model.Announcement
import gr.agiosnektarios.village.core.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Singleton
class AnnouncementRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val announcements get() = firestore.collection(Collections.ANNOUNCEMENTS)

    /**
     * Pinned notices first, then newest.
     *
     * Firestore cannot order across a boolean and a timestamp without a
     * composite index that also forces `pinned` into every query, so the
     * two-key ordering is applied locally over a capped, already-sorted page.
     */
    fun observeAnnouncements(limit: Long = 100): Flow<List<Announcement>> =
        announcements.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .asFlow()
            .map { snapshot ->
                snapshot.toObjectsSafe<Announcement>()
                    .sortedWith(
                        compareByDescending<Announcement> { it.pinned }
                            .thenByDescending { it.createdAt?.time ?: 0L },
                    )
            }
            .orEmptyOnError("announcements")

    fun observeAnnouncement(id: String): Flow<Announcement?> =
        announcements.document(id).asFlow().map { it.toObjectSafe<Announcement>() }
            .orNullOnError("announcement $id")

    suspend fun publish(
        author: UserProfile,
        title: String,
        body: String,
        image: ByteArray?,
        pinned: Boolean,
    ): Result<String> = withContext(io) {
        runCatching {
            val doc = announcements.document()
            doc.set(
                mapOf(
                    "title" to title.trim(),
                    "body" to body.trim(),
                    "image" to image?.let(Blob::fromBytes),
                    "authorId" to author.id,
                    "authorName" to author.displayName,
                    "pinned" to pinned,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            doc.id
        }
    }

    suspend fun update(
        id: String,
        title: String,
        body: String,
        image: ByteArray?,
        pinned: Boolean,
    ): Result<Unit> = withContext(io) {
        runCatchingUnit {
            announcements.document(id).update(
                mapOf(
                    "title" to title.trim(),
                    "body" to body.trim(),
                    "image" to image?.let(Blob::fromBytes),
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
    }

    suspend fun delete(id: String): Result<Unit> = withContext(io) {
        runCatchingUnit { announcements.document(id).delete().await() }
    }
}
