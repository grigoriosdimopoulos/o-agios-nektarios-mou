package gr.agiosnektarios.village.data.issue

import gr.agiosnektarios.village.core.firestore.orEmptyOnError
import gr.agiosnektarios.village.core.firestore.orNullOnError
import gr.agiosnektarios.village.core.geo.GeoPoint
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.runCatchingUnit
import gr.agiosnektarios.village.core.firestore.Collections
import gr.agiosnektarios.village.core.firestore.asFlow
import gr.agiosnektarios.village.core.firestore.toObjectSafe
import gr.agiosnektarios.village.core.firestore.toObjectsSafe
import gr.agiosnektarios.village.core.geo.IssueClustering
import gr.agiosnektarios.village.core.geo.distanceMeters
import gr.agiosnektarios.village.core.geo.geohash
import gr.agiosnektarios.village.core.model.Comment
import gr.agiosnektarios.village.core.model.Issue
import gr.agiosnektarios.village.core.model.IssuePhoto
import gr.agiosnektarios.village.core.model.IssueCategory
import gr.agiosnektarios.village.core.model.IssueStatus
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.core.model.Vote
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** How the issue list is ordered. */
enum class IssueSort { NEWEST, TOP, MOST_DISCUSSED }

/**
 * Draft of a report, shared by the create and edit screens.
 *
 * [photos] are already-encoded JPEG bytes, sized by
 * [gr.agiosnektarios.village.data.media.ImageCodec] before they get here, and
 * [thumbnail] is the postage stamp cut from the first of them.
 */
data class IssueDraft(
    val title: String,
    val description: String,
    val category: IssueCategory,
    val position: GeoPoint,
    val blockId: String,
    val photos: List<ByteArray> = emptyList(),
    val thumbnail: ByteArray? = null,
)

@Singleton
class IssueRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val issues get() = firestore.collection(Collections.ISSUES)

    private fun issueDoc(issueId: String) = issues.document(issueId)

    // ---------------------------------------------------------------- reads

    /**
     * Every report in the village, newest first.
     *
     * A village generates a few thousand reports at most, and the map needs all
     * of them at once to draw block counters, so this is a single capped live
     * query rather than a paged one. [limit] is the guard rail.
     */
    fun observeIssues(limit: Long = 1_000): Flow<List<Issue>> =
        issues.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .asFlow()
            .map { it.toObjectsSafe<Issue>() }
            .orEmptyOnError("all issues")

    fun observeIssue(issueId: String): Flow<Issue?> =
        issueDoc(issueId).asFlow().map { it.toObjectSafe<Issue>() }
            .orNullOnError("issue $issueId")

    /**
     * One resident's reports, newest first.
     *
     * Ordered locally for the same reason as the chat list: an equality filter
     * plus an `orderBy` on a different field needs a hand-created composite
     * index, and nobody's own report list is long enough for that to be worth
     * a setup step that can be forgotten.
     */
    fun observeIssuesByAuthor(authorId: String): Flow<List<Issue>> =
        issues.whereEqualTo("authorId", authorId)
            .limit(500)
            .asFlow()
            .map { snapshot ->
                snapshot.toObjectsSafe<Issue>()
                    .sortedByDescending { it.createdAt?.time ?: 0L }
            }
            .orEmptyOnError("issues by $authorId")

    /** One-shot read for the edit screen, which needs the photos it already has. */
    suspend fun getPhotos(issueId: String): Result<List<IssuePhoto>> = withContext(io) {
        runCatching {
            issueDoc(issueId).collection(Collections.PHOTOS)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get().await().toObjectsSafe<IssuePhoto>()
        }
    }

    suspend fun getIssue(issueId: String): Result<Issue?> = withContext(io) {
        runCatching { issueDoc(issueId).get().await().toObjectSafe<Issue>() }
    }

    /**
     * Reports of the same category close enough to [center] to be the same
     * real-world problem — shown while composing so a resident sees "three
     * neighbours already reported this" before filing a duplicate.
     *
     * Firestore cannot do radius queries, so this narrows by geohash prefix
     * (a ~1.2 km cell at precision 5) and applies the exact distance locally.
     */
    suspend fun findSimilarNearby(
        center: GeoPoint,
        category: IssueCategory,
        radiusMeters: Double = IssueClustering.SAME_PROBLEM_METERS,
    ): Result<List<Issue>> = withContext(io) {
        runCatching {
            val prefix = geohash(center.lat, center.lng, precision = 5)
            // The category is matched locally, not in the query: adding an
            // equality filter alongside the geohash range would need a
            // composite index. One precision-5 cell comfortably contains the
            // whole village, so this reads the village's reports once and
            // narrows them here \u2014 where the exact distance has to be applied
            // anyway, since Firestore cannot do radius queries.
            issues.orderBy("geohash")
                .startAt(prefix)
                .endAt(prefix + '\uf8ff')
                .limit(400)
                .get()
                .await()
                .toObjectsSafe<Issue>()
                .filter { it.categoryId == category.id }
                .filter { distanceMeters(center, GeoPoint(it.lat, it.lng)) <= radiusMeters }
                .sortedBy { distanceMeters(center, GeoPoint(it.lat, it.lng)) }
        }
    }

    // --------------------------------------------------------------- writes

    suspend fun createIssue(draft: IssueDraft, author: UserProfile): Result<String> =
        withContext(io) {
            runCatching {
                val doc = issues.document()
                doc.set(
                    mapOf(
                        "title" to draft.title.trim(),
                        "description" to draft.description.trim(),
                        "categoryId" to draft.category.id,
                        "statusId" to IssueStatus.OPEN.id,
                        "lat" to draft.position.lat,
                        "lng" to draft.position.lng,
                        "geohash" to geohash(draft.position.lat, draft.position.lng),
                        "blockId" to draft.blockId,
                        "photoCount" to draft.photos.size,
                        "thumbnail" to draft.thumbnail?.let(Blob::fromBytes),
                        "authorId" to author.id,
                        "authorName" to author.displayName,
                        "upvotes" to 0,
                        "downvotes" to 0,
                        "score" to 0,
                        "commentCount" to 0,
                        "resolutionNote" to "",
                        "resolvedById" to "",
                        "resolvedByName" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
                writePhotos(doc.id, draft.photos, author.id)
                doc.id
            }
        }

    /**
     * Writes each photo as its own document under the report.
     *
     * One batch: a report that shows "3 photos" and then cannot produce the
     * third is worse than one that failed outright.
     */
    private suspend fun writePhotos(issueId: String, photos: List<ByteArray>, authorId: String) {
        if (photos.isEmpty()) return
        val batch = firestore.batch()
        val collection = issueDoc(issueId).collection(Collections.PHOTOS)
        photos.forEach { bytes ->
            batch.set(
                collection.document(),
                mapOf(
                    "data" to Blob.fromBytes(bytes),
                    "authorId" to authorId,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
        }
        batch.commit().await()
    }

    /** The full photos of one report, read only when someone opens it. */
    fun observePhotos(issueId: String): Flow<List<IssuePhoto>> =
        issueDoc(issueId).collection(Collections.PHOTOS)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .asFlow()
            .map { it.toObjectsSafe<IssuePhoto>() }
            .orEmptyOnError("photos of $issueId")

    /**
     * Applies an edit, including whichever photos were added or taken away.
     *
     * Photos are passed as a delta rather than a whole new set: re-uploading
     * the pictures a resident did not touch would cost them their data
     * allowance to change a typo in the title.
     */
    suspend fun updateIssue(
        issueId: String,
        draft: IssueDraft,
        authorId: String,
        keptPhotoCount: Int = 0,
        removedPhotoIds: List<String> = emptyList(),
    ): Result<Unit> = withContext(io) {
        runCatchingUnit {
            issueDoc(issueId).update(
                mapOf(
                    "title" to draft.title.trim(),
                    "description" to draft.description.trim(),
                    "categoryId" to draft.category.id,
                    "lat" to draft.position.lat,
                    "lng" to draft.position.lng,
                    "geohash" to geohash(draft.position.lat, draft.position.lng),
                    "blockId" to draft.blockId,
                    "photoCount" to keptPhotoCount + draft.photos.size,
                    "thumbnail" to draft.thumbnail?.let(Blob::fromBytes),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()

            if (removedPhotoIds.isNotEmpty()) {
                val batch = firestore.batch()
                val photos = issueDoc(issueId).collection(Collections.PHOTOS)
                removedPhotoIds.forEach { batch.delete(photos.document(it)) }
                batch.commit().await()
            }
            writePhotos(issueId, draft.photos, authorId)
        }
    }

    /**
     * Removes the report and everything under it.
     *
     * Firestore does not cascade, so the subcollections are swept first and the
     * parent deleted last. That order matters: if this dies half way, what is
     * left is a live report missing some votes — recoverable and visible —
     * rather than orphaned documents under a path nothing can reach.
     */
    suspend fun deleteIssue(issueId: String): Result<Unit> = withContext(io) {
        runCatchingUnit {
            val doc = issueDoc(issueId)
            deleteAll(doc.collection(Collections.VOTES))
            deleteAll(doc.collection(Collections.COMMENTS))
            deleteAll(doc.collection(Collections.PHOTOS))
            doc.delete().await()
        }
    }

    /** Deletes a whole (small) subcollection in batches of [BATCH_LIMIT]. */
    private suspend fun deleteAll(collection: CollectionReference) {
        while (true) {
            val page = collection.limit(BATCH_LIMIT).get().await()
            if (page.isEmpty) return
            val batch = firestore.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            if (page.size() < BATCH_LIMIT) return
        }
    }

    suspend fun setStatus(
        issueId: String,
        status: IssueStatus,
        actor: UserProfile,
        note: String = "",
    ): Result<Unit> = withContext(io) {
        runCatchingUnit {
            val terminal = status.isTerminal
            issueDoc(issueId).update(
                mapOf(
                    "statusId" to status.id,
                    "resolutionNote" to note.trim(),
                    "resolvedById" to if (terminal) actor.id else "",
                    "resolvedByName" to if (terminal) actor.displayName else "",
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
    }

    // ---------------------------------------------------------------- votes

    fun observeMyVote(issueId: String, userId: String): Flow<Int> =
        issueDoc(issueId).collection(Collections.VOTES).document(userId)
            .asFlow()
            .map { it.toObjectSafe<Vote>()?.value ?: 0 }
            .catch { emit(0) }

    /**
     * Casts, flips or clears a vote.
     *
     * The vote document and the report's tally move together in one
     * transaction, so two neighbours voting at the same instant cannot lose
     * each other's increment — the transaction re-runs on contention.
     *
     * The delta is derived from the *previous* vote read inside the
     * transaction rather than from the UI's idea of it, which is what makes
     * flipping an upvote to a downvote a single -1/+1 pair instead of a guess.
     */
    suspend fun castVote(issueId: String, userId: String, value: Int): Result<Unit> =
        withContext(io) {
            runCatchingUnit {
                require(value in -1..1) { "Vote must be -1, 0 or 1" }
                val issueRef = issueDoc(issueId)
                val voteRef = issueRef.collection(Collections.VOTES).document(userId)

                firestore.runTransaction { transaction ->
                    val issueSnapshot = transaction.get(issueRef)
                    if (!issueSnapshot.exists()) return@runTransaction null

                    val previous = transaction.get(voteRef).let { snapshot ->
                        if (snapshot.exists()) snapshot.getLong("value")?.toInt() ?: 0 else 0
                    }
                    if (previous == value) return@runTransaction null

                    val upvotes = (issueSnapshot.getLong("upvotes") ?: 0L).toInt()
                    val downvotes = (issueSnapshot.getLong("downvotes") ?: 0L).toInt()
                    // Each vote contributes to exactly one counter, so removing
                    // the old contribution and adding the new one keeps both
                    // within the ±1 step the security rules allow.
                    val newUpvotes = upvotes - (if (previous == 1) 1 else 0) + (if (value == 1) 1 else 0)
                    val newDownvotes =
                        downvotes - (if (previous == -1) 1 else 0) + (if (value == -1) 1 else 0)

                    if (value == 0) {
                        transaction.delete(voteRef)
                    } else {
                        transaction.set(
                            voteRef,
                            mapOf(
                                "userId" to userId,
                                "value" to value,
                                "createdAt" to FieldValue.serverTimestamp(),
                            ),
                        )
                    }
                    transaction.update(
                        issueRef,
                        mapOf(
                            "upvotes" to newUpvotes.coerceAtLeast(0),
                            "downvotes" to newDownvotes.coerceAtLeast(0),
                            "score" to newUpvotes.coerceAtLeast(0) - newDownvotes.coerceAtLeast(0),
                        ),
                    )
                    null
                }.await()
            }
        }

    // ------------------------------------------------------------- comments

    fun observeComments(issueId: String): Flow<List<Comment>> =
        issueDoc(issueId).collection(Collections.COMMENTS)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .asFlow()
            .map { it.toObjectsSafe<Comment>() }
            .orEmptyOnError("comments of $issueId")

    suspend fun addComment(issueId: String, author: UserProfile, text: String): Result<Unit> =
        withContext(io) {
            runCatchingUnit { adjustCommentCount(issueId, delta = 1) { transaction, commentRef ->
                transaction.set(
                    commentRef,
                    mapOf(
                        "issueId" to issueId,
                        "authorId" to author.id,
                        "authorName" to author.displayName,
                        "text" to text.trim(),
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
            } }
        }

    suspend fun deleteComment(issueId: String, commentId: String): Result<Unit> = withContext(io) {
        runCatchingUnit {
            adjustCommentCount(issueId, delta = -1, commentId = commentId) { transaction, ref ->
                transaction.delete(ref)
            }
        }
    }

    /**
     * Writes a comment and the report's `commentCount` in one transaction.
     *
     * The count exists so the issue list can sort by "most discussed" without
     * reading every subcollection; keeping it in the same transaction as the
     * comment is what stops the two drifting apart.
     */
    private suspend fun adjustCommentCount(
        issueId: String,
        delta: Int,
        commentId: String? = null,
        write: (com.google.firebase.firestore.Transaction, com.google.firebase.firestore.DocumentReference) -> Unit,
    ) {
        val issueRef = issueDoc(issueId)
        val comments = issueRef.collection(Collections.COMMENTS)
        val commentRef = commentId?.let { comments.document(it) } ?: comments.document()

        firestore.runTransaction { transaction ->
            val issueSnapshot = transaction.get(issueRef)
            if (!issueSnapshot.exists()) return@runTransaction null
            val current = (issueSnapshot.getLong("commentCount") ?: 0L).toInt()
            write(transaction, commentRef)
            transaction.update(issueRef, "commentCount", (current + delta).coerceAtLeast(0))
            null
        }.await()
    }

    private companion object {
        const val BATCH_LIMIT = 300L
    }
}

/** Client-side ordering, so switching sort never costs a round trip. */
fun List<Issue>.sortedForDisplay(sort: IssueSort): List<Issue> = when (sort) {
    IssueSort.NEWEST -> sortedByDescending { it.createdAt?.time ?: 0L }
    IssueSort.TOP -> sortedWith(
        compareByDescending<Issue> { it.score }.thenByDescending { it.createdAt?.time ?: 0L },
    )
    IssueSort.MOST_DISCUSSED -> sortedWith(
        compareByDescending<Issue> { it.commentCount }.thenByDescending { it.createdAt?.time ?: 0L },
    )
}
