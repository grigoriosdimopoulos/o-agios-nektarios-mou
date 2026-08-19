package gr.agiosnektarios.village.data.contact

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.firestore.Collections
import gr.agiosnektarios.village.core.firestore.asFlow
import gr.agiosnektarios.village.core.firestore.orEmptyOnError
import gr.agiosnektarios.village.core.firestore.toObjectsSafe
import gr.agiosnektarios.village.core.model.ContactKind
import gr.agiosnektarios.village.core.model.VillageContact
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * The numbers the village keeps for itself.
 *
 * Deliberately small: a settlement of forty-six people is not going to have
 * more than a dozen of these, so the whole collection is observed at once with
 * no query and no paging. Sorting is done here rather than in Firestore, which
 * would need a composite index for a list this size.
 */
@Singleton
class ContactRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val contacts get() = firestore.collection(Collections.CONTACTS)

    fun observeContacts(): Flow<List<VillageContact>> =
        contacts.asFlow()
            .map { snapshot ->
                snapshot.toObjectsSafe<VillageContact>()
                    .filter { it.name.isNotBlank() && it.number.isNotBlank() }
                    .sortedWith(compareBy({ it.contactKind.ordinal }, { it.order }, { it.name }))
            }
            .orEmptyOnError("contacts")

    suspend fun save(
        id: String?,
        name: String,
        number: String,
        note: String,
        kind: ContactKind,
        authorId: String,
    ): Result<Unit> = withContext(io) {
        val cleanName = name.trim().take(VillageContact.MAX_NAME)
        val cleanNumber = number.trim().take(VillageContact.MAX_NUMBER)
        if (cleanName.isBlank() || !VillageContact.isDialable(cleanNumber)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid contact"))
        }
        runCatching {
            val document = if (id.isNullOrBlank()) contacts.document() else contacts.document(id)
            val fields = mutableMapOf<String, Any>(
                "name" to cleanName,
                "number" to cleanNumber,
                "note" to note.trim().take(VillageContact.MAX_NOTE),
                "kind" to kind.name,
                "order" to 0,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (id.isNullOrBlank()) {
                fields["createdById"] = authorId
                fields["createdAt"] = FieldValue.serverTimestamp()
                document.set(fields).await()
            } else {
                document.update(fields).await()
            }
            Unit
        }
    }

    suspend fun delete(id: String): Result<Unit> = withContext(io) {
        runCatching {
            contacts.document(id).delete().await()
            Unit
        }
    }
}
