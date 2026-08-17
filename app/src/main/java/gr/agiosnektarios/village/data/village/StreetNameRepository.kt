package gr.agiosnektarios.village.data.village

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.firestore.Collections
import gr.agiosnektarios.village.core.firestore.asFlow
import gr.agiosnektarios.village.core.firestore.orEmptyOnError
import gr.agiosnektarios.village.core.firestore.toObjectsSafe
import gr.agiosnektarios.village.core.model.StreetName
import gr.agiosnektarios.village.core.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * The street names the village supplies for itself.
 *
 * Small enough to observe whole: this settlement has 82 ways, so there is no
 * pagination and no query beyond "all of them". The map merges the result into
 * the bundled geometry by way id.
 */
@Singleton
class StreetNameRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val streets get() = firestore.collection(Collections.STREET_NAMES)

    fun observeStreetNames(): Flow<List<StreetName>> =
        streets.asFlow()
            .map { it.toObjectsSafe<StreetName>() }
            .orEmptyOnError("streetNames")

    /** Way id to name, for merging into the map's road source. */
    fun observeNamesByWay(): Flow<Map<String, String>> =
        observeStreetNames().map { list ->
            list.filter { it.name.isNotBlank() }.associate { it.wayId to it.name }
        }

    /**
     * Names a street, or corrects a name already there.
     *
     * Proposing resets the confirmations to just the proposer: the people who
     * agreed to "Ελατιάς" did not agree to whatever it was renamed to, and
     * carrying their agreement across would launder one person's edit into a
     * consensus.
     */
    suspend fun propose(wayId: String, name: String, author: UserProfile): Result<Unit> =
        withContext(io) {
            val trimmed = name.trim().take(StreetName.MAX_LENGTH)
            if (trimmed.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Empty street name"))
            }
            runCatching {
                streets.document(wayId).set(
                    mapOf(
                        "name" to trimmed,
                        "proposedById" to author.id,
                        "proposedByName" to author.displayName,
                        "confirmedBy" to listOf(author.id),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
                Unit
            }
        }

    /**
     * Agrees, or withdraws agreement, with the name already proposed.
     *
     * Array union and removal rather than a read-modify-write, so two residents
     * confirming at the same moment cannot overwrite each other.
     */
    suspend fun setConfirmed(wayId: String, userId: String, confirmed: Boolean): Result<Unit> =
        withContext(io) {
            runCatching {
                streets.document(wayId).update(
                    mapOf(
                        "confirmedBy" to if (confirmed) {
                            FieldValue.arrayUnion(userId)
                        } else {
                            FieldValue.arrayRemove(userId)
                        },
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
                Unit
            }
        }

    /** Removing a name is a moderator action; the rules are what enforce that. */
    suspend fun clear(wayId: String): Result<Unit> = withContext(io) {
        runCatching {
            streets.document(wayId).delete().await()
            Unit
        }
    }
}
