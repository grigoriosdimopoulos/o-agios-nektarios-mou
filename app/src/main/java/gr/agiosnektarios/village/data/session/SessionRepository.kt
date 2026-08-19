package gr.agiosnektarios.village.data.session

import gr.agiosnektarios.village.core.di.ApplicationScope
import gr.agiosnektarios.village.core.model.HomePin
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.data.auth.AuthRepository
import gr.agiosnektarios.village.data.user.UserRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Who is using the app right now.
 *
 * Auth state and the profile document are two separate sources — you can be
 * authenticated a fraction of a second before your profile exists, and a Google
 * sign-in can leave you authenticated with no profile at all until the extra
 * details are collected. [SessionState] makes that difference explicit instead
 * of letting every screen invent its own null checks.
 */
sealed interface SessionState {
    /** Still resolving the persisted session; show the splash, not the sign-in screen. */
    data object Loading : SessionState

    data object SignedOut : SessionState

    /** Authenticated, but `users/{uid}` is missing — finish registration. */
    data class ProfileIncomplete(val userId: String, val email: String, val displayName: String) :
        SessionState

    data class SignedIn(val profile: UserProfile) : SessionState
}

@Singleton
class SessionRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SessionState> = authRepository.authState
        .flatMapLatest { firebaseUser ->
            if (firebaseUser == null) {
                flowOf(SessionState.SignedOut)
            } else {
                userRepository.observeProfile(firebaseUser.uid)
                    .map { profile ->
                        when {
                            profile == null -> SessionState.ProfileIncomplete(
                                userId = firebaseUser.uid,
                                email = firebaseUser.email.orEmpty(),
                                displayName = firebaseUser.displayName.orEmpty(),
                            )
                            // A suspended account is signed out on the spot; the
                            // security rules reject its writes regardless.
                            profile.disabled -> SessionState.SignedOut
                            else -> SessionState.SignedIn(profile)
                        }
                    }
                    // A permission error here means the account no longer exists
                    // server-side, so treat it as signed out rather than crashing.
                    .catch { emit(SessionState.SignedOut) }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    /**
     * The profile, or null when nobody is fully signed in.
     *
     * Distinct, because `NotificationDispatcher` flatMapLatests over this and
     * every write to your own document re-emitted it: an upvote on your report
     * bumps `upvotesReceived`, which tore down the inbox listener and re-primed
     * it, and priming marks whatever is in the first snapshot as already shown.
     * The notice about that very upvote is written at the same moment — so it
     * was swallowed, and only reappeared when the background sync ran up to
     * fifteen minutes later.
     */
    val profile: Flow<UserProfile?> = state
        .map { (it as? SessionState.SignedIn)?.profile }
        .distinctUntilChanged { old, new -> old?.id == new?.id }

    /**
     * The signed-in resident's own house pin.
     *
     * A second document and therefore a second listener, which is the price of
     * it being readable by nobody else. Shared here so the map, the alarm and
     * the pin screen do not each open their own.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val home: StateFlow<HomePin?> = state
        .flatMapLatest { current ->
            when (current) {
                is SessionState.SignedIn -> userRepository.observeHome(current.profile.id)
                    .catch { emit(null) }
                else -> flowOf(null)
            }
        }
        // Eagerly, like [state], so the listener is already running by the time
        // any screen asks. That is not the same as it having *answered*: on a
        // cold start `home.value` is null until Firestore delivers, so callers
        // collect this rather than reading it once. AlertViewModel.useHome()
        // learned that the hard way.
        .stateIn(scope, SharingStarted.Eagerly, null)

    val currentUserId: String? get() = authRepository.currentUserId

    val currentProfile: UserProfile? get() = (state.value as? SessionState.SignedIn)?.profile
}
