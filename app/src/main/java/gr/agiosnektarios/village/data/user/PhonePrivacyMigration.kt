package gr.agiosnektarios.village.data.user

import gr.agiosnektarios.village.core.di.ApplicationScope
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.session.SessionState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Takes each resident's telephone number off the readable directory, once.
 *
 * There is no server on this plan, so there is nobody to run a migration but
 * the phones themselves — and a resident's own device is the only thing
 * allowed to write their profile anyway, which makes it the right place rather
 * than merely the available one.
 *
 * Runs when somebody signs in and does nothing at all if the profile has no
 * number left on it, so the second and every later launch costs one read of a
 * document the app has already loaded. A failure is silent and simply tried
 * again next time: the old field is not a fault, it is a number in a place it
 * should no longer be, and nothing a resident is doing at sign-in should stop
 * for it.
 */
@Singleton
class PhonePrivacyMigration @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            sessionRepository.state
                .map { (it as? SessionState.SignedIn)?.profile?.id }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId != null) userRepository.migratePhoneToPrivate(userId)
                }
        }
    }
}
