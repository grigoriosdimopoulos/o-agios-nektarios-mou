package gr.agiosnektarios.village.notifications

import gr.agiosnektarios.village.core.di.ApplicationScope
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.data.notification.AppNotification
import gr.agiosnektarios.village.data.notification.NotificationRepository
import gr.agiosnektarios.village.data.notification.NotificationType
import gr.agiosnektarios.village.data.session.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Raises a device notification for each notice that arrives in the signed-in
 * resident's inbox.
 *
 * This is the receiving half of the free-plan substitute for push. Sending a
 * real push needs a server credential, and a server needs the paid plan; what
 * happens instead is that the acting client writes a document into the
 * recipient's inbox, and this listener turns it into a notification.
 *
 * This half is the fast one and only runs while the process is alive: a
 * Firestore listener cannot wake a stopped app the way Cloud Messaging can.
 * [NotificationSyncWorker] covers the app being closed, on a fifteen-minute
 * schedule. Between them, a notice is immediate while the app is open and at
 * worst a quarter of an hour late when it is not.
 *
 * They share the `seen` flag rather than each keeping their own memory, so
 * whichever announces a notice first stops the other repeating it.
 */
@Singleton
class NotificationDispatcher @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val notificationRepository: NotificationRepository,
    private val presenter: NotificationPresenter,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /**
     * Notices already raised on this device, by document id.
     *
     * A watermark on `createdAt` would be neater but cannot be trusted: the
     * field is a server timestamp, so a document arrives from the local cache
     * with `createdAt` null before the server fills it in, and a watermark
     * would either skip it or replay it. Ids are exact.
     */
    private val alreadyShown = LinkedHashSet<String>()

    /** True until the first snapshot has been seen for the current resident. */
    private var priming = true

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            sessionRepository.profile
                .flatMapLatest { profile: UserProfile? ->
                    priming = true
                    alreadyShown.clear()
                    if (profile == null) {
                        flowOf(emptyList<AppNotification>() to null)
                    } else {
                        notificationRepository.observe(profile.id).map { it to profile }
                    }
                }
                .collect { (notices, profile) ->
                    if (profile == null) return@collect
                    // The first snapshot is the backlog, not news. Raising a
                    // notification for every unread notice each time the app
                    // starts would be a burst of duplicates.
                    if (priming) {
                        priming = false
                        notices.forEach { alreadyShown += it.id }
                        return@collect
                    }
                    notices
                        .asReversed()
                        .filter { it.id.isNotBlank() && alreadyShown.add(it.id) }
                        .filter { !it.seen }
                        .filter { it.actorId != profile.id }
                        .filter { it.allowedBy(profile.notificationPrefs) }
                        .filterNot { it.isForTheConversationOnScreen() }
                        .forEach { notice ->
                            presenter.show(notice)
                            // Marks the account, not just this device: the
                            // scheduled worker reads the same flag, so a notice
                            // announced here is not announced again in fifteen
                            // minutes' time.
                            notificationRepository.markSeen(profile.id, notice.id)
                        }
                    trimMemory()
                }
        }
    }

    /** No point buzzing about a message in the thread already being read. */
    private fun AppNotification.isForTheConversationOnScreen(): Boolean =
        notificationType == NotificationType.CHAT &&
            ActiveChatTracker.activeChatId != null &&
            deepLink.endsWith(ActiveChatTracker.activeChatId.orEmpty())

    private fun trimMemory() {
        while (alreadyShown.size > SEEN_MEMORY) {
            alreadyShown.remove(alreadyShown.first())
        }
    }



    private companion object {
        /** Enough to cover a long session without growing without bound. */
        const val SEEN_MEMORY = 400
    }
}
