package gr.agiosnektarios.village.data.user

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.firestore.Collections
import gr.agiosnektarios.village.core.firestore.SERVER_ACK_MS
import gr.agiosnektarios.village.core.firestore.asFlow
import gr.agiosnektarios.village.core.firestore.orEmptyOnError
import gr.agiosnektarios.village.core.firestore.toObjectsSafe
import gr.agiosnektarios.village.core.model.EmergencyContact
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The telephone numbers whose owners have agreed they may be texted.
 *
 * ## Why this is a collection of its own
 *
 * The emergency screen can open the phone's messaging app with every
 * resident's number in it, which is the only thing in this app that reaches
 * somebody whose phone is in a drawer. It needs every number on the sending
 * device, and there is no server to do it instead.
 *
 * Until now those numbers came off the resident directory, which meant every
 * phone in the village could read every number, permanently, whether or not
 * anybody ever pressed the button — a directory of forty-six mobile numbers,
 * harvestable by anyone who could sign in, for a feature most days nobody
 * uses. Firestore's rules grant a document at a time and the profile has to be
 * readable, so no rule could have narrowed that while the number lived there.
 *
 * So a number is here only if its owner put it here, and it is readable only
 * while the village has the feature switched on. Both are enforced in the
 * rules. Withdrawing consent deletes the document, and works even after an
 * administrator has switched the feature off — taking something back must
 * never depend on a setting somebody else controls.
 */
@Singleton
class EmergencyContactRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val contacts get() = firestore.collection(Collections.EMERGENCY_CONTACTS)

    /**
     * Everyone who has agreed, or nothing at all.
     *
     * Empty rather than an error when the feature is off: the rules refuse the
     * read, and "nobody has agreed" and "the village has this switched off"
     * are both answered by the screen, not by a crash.
     */
    fun observeAll(): Flow<List<EmergencyContact>> = contacts.asFlow()
        .map { it.toObjectsSafe<EmergencyContact>() }
        .orEmptyOnError("emergencyContacts")

    fun observeMine(userId: String): Flow<Boolean> = contacts.document(userId).asFlow()
        .map { it?.exists() == true }
        .catch { emit(false) }

    /** Agrees to be textable, with the number as it stands now. */
    suspend fun share(userId: String, name: String, phone: String): Result<Unit> =
        withContext(io) {
            runCatching {
                contacts.document(userId).set(
                    mapOf(
                        "name" to name.take(EmergencyContact.MAX_NAME),
                        "phone" to phone.take(EmergencyContact.MAX_PHONE),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).let { task -> withTimeoutOrNull(SERVER_ACK_MS) { task.await() } }
                Unit
            }
        }

    /** Takes it back. Always permitted, whatever the village has switched on. */
    suspend fun withdraw(userId: String): Result<Unit> = withContext(io) {
        runCatching {
            contacts.document(userId).delete()
                .let { task -> withTimeoutOrNull(SERVER_ACK_MS) { task.await() } }
            Unit
        }
    }
}
