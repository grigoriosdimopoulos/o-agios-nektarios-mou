package gr.agiosnektarios.village.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import gr.agiosnektarios.village.core.di.ApplicationScope
import gr.agiosnektarios.village.core.firestore.Topics
import gr.agiosnektarios.village.data.session.SessionRepository
import gr.agiosnektarios.village.data.session.SessionState
import gr.agiosnektarios.village.data.settings.SettingsRepository
import gr.agiosnektarios.village.data.user.UserRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Keeps the device's FCM registration attached to whoever is signed in.
 *
 * Two things have to stay in sync: the token list on the user document (used
 * for targeted pushes) and the `announcements` topic subscription (used for
 * the village-wide broadcast, which is one send instead of N).
 */
@Singleton
class PushTokenSynchronizer @Inject constructor(
    private val messaging: FirebaseMessaging,
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var lastSyncedUserId: String? = null

    fun start() {
        scope.launch {
            combine(
                sessionRepository.state,
                settingsRepository.settings,
            ) { session, settings -> session to settings.notifyAnnouncements }
                .distinctUntilChanged()
                .collect { (session, wantsAnnouncements) ->
                    when (session) {
                        is SessionState.SignedIn -> {
                            syncToken(session.profile.id)
                            setAnnouncementTopic(wantsAnnouncements)
                        }
                        SessionState.SignedOut -> detach()
                        else -> Unit
                    }
                }
        }
    }

    private suspend fun syncToken(userId: String) {
        if (lastSyncedUserId == userId) return
        runCatching {
            val token = messaging.token.await()
            userRepository.addFcmToken(userId, token).getOrThrow()
            lastSyncedUserId = userId
        }.onFailure { Log.w(TAG, "Could not register push token", it) }
    }

    private suspend fun setAnnouncementTopic(subscribe: Boolean) {
        runCatching {
            if (subscribe) {
                messaging.subscribeToTopic(Topics.ANNOUNCEMENTS).await()
            } else {
                messaging.unsubscribeFromTopic(Topics.ANNOUNCEMENTS).await()
            }
        }.onFailure { Log.w(TAG, "Topic subscription failed", it) }
    }

    /**
     * Detaches this device on sign-out so the next person to use the phone does
     * not receive the previous resident's notifications.
     */
    private suspend fun detach() {
        val userId = lastSyncedUserId ?: return
        lastSyncedUserId = null
        runCatching {
            val token = messaging.token.await()
            userRepository.removeFcmToken(userId, token)
            messaging.unsubscribeFromTopic(Topics.ANNOUNCEMENTS).await()
        }.onFailure { Log.w(TAG, "Could not detach push token", it) }
    }

    private companion object {
        const val TAG = "PushTokenSync"
    }
}
