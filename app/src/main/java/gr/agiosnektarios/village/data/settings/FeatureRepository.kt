package gr.agiosnektarios.village.data.settings

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import gr.agiosnektarios.village.core.di.ApplicationScope
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.firestore.Collections
import gr.agiosnektarios.village.core.firestore.SERVER_ACK_MS
import gr.agiosnektarios.village.core.firestore.asFlow
import gr.agiosnektarios.village.core.firestore.toObjectSafe
import gr.agiosnektarios.village.core.model.Feature
import gr.agiosnektarios.village.core.model.FeatureFlags
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import gr.agiosnektarios.village.data.auth.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * What this village has switched on.
 *
 * One document that every phone reads and only an administrator writes. It is
 * shared eagerly because almost every screen asks: a flag that arrives after
 * the screen it governs would show a feature for a moment and then take it
 * away, which is worse than either answer.
 *
 * A failure to read it — no signal on a cold start, or rules that have not
 * been deployed — yields the defaults rather than an error. The alternative is
 * a village whose app is empty because one document could not be fetched, and
 * the defaults are the same thing every village gets before anybody has
 * decided anything.
 */
@Singleton
class FeatureRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope scope: CoroutineScope,
) {
    private val document get() = firestore
        .collection(Collections.FEATURE_FLAGS)
        .document(FeatureFlags.DOCUMENT)

    @OptIn(ExperimentalCoroutinesApi::class)
    val flags: StateFlow<FeatureFlags> = authRepository.authState
        // Re-subscribed when somebody signs in, not merely caught.
        //
        // The rules refuse this document to a caller who is not signed in, and
        // `catch` *completes* the flow it catches — so a single failure on the
        // sign-in screen would have pinned every flag at its default for the
        // life of the process, and the first thing a resident saw after
        // signing in would be an app that had ignored everything the village
        // had decided.
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(FeatureFlags())
            } else {
                document.asFlow()
                    .map { it.toObjectSafe<FeatureFlags>() ?: FeatureFlags() }
                    .catch { emit(FeatureFlags()) }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, FeatureFlags())

    /** Whether one feature is on right now, for the places that cannot collect. */
    fun isOn(feature: Feature): Boolean = flags.value.isOn(feature)

    /**
     * Switches one feature on or off for the whole village.
     *
     * Writes only that feature's entry. An administrator changing the calendar
     * should not silently freeze every other flag at whatever this device last
     * saw, which is what writing the whole map would do.
     */
    suspend fun set(feature: Feature, on: Boolean): Result<Unit> = withContext(io) {
        runCatching {
            document.set(
                mapOf(
                    "enabled" to mapOf(feature.id to on),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            ).let { task -> withTimeoutOrNull(SERVER_ACK_MS) { task.await() } }
            Unit
        }
    }
}
