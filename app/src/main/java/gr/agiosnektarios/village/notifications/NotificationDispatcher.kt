package gr.agiosnektarios.village.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import gr.agiosnektarios.village.MainActivity
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.di.ApplicationScope
import gr.agiosnektarios.village.core.model.UserProfile
import gr.agiosnektarios.village.data.notification.AppNotification
import gr.agiosnektarios.village.data.notification.NotificationRepository
import gr.agiosnektarios.village.data.notification.NotificationType
import gr.agiosnektarios.village.data.session.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
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
 * **What that does not do.** Firebase Cloud Messaging can wake a stopped app.
 * A Firestore listener cannot: it lives in this process, so notices arrive
 * while the app is open or still resident in memory after being backgrounded,
 * and stop arriving once Android reclaims the process. They are not lost —
 * they are in the inbox and appear the moment the app is opened again — but a
 * phone with the app closed for a day will hear nothing until it is opened.
 * Real push remains available by deploying `firebase/functions` on Blaze;
 * [VillageMessagingService] already handles the delivery side of that.
 */
@Singleton
class NotificationDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val notificationRepository: NotificationRepository,
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
                        .filter { it.actorId != profile.id }
                        .filter { it.allowedBy(profile.notificationPrefs) }
                        .filterNot { it.isForTheConversationOnScreen() }
                        .forEach { show(it) }
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

    private fun show(notice: AppNotification) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val tag = notice.collapseKey.ifBlank { notice.id }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (notice.deepLink.isNotBlank()) {
                action = Intent.ACTION_VIEW
                data = Uri.parse(notice.deepLink)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = localizedBody(notice)
        val notification = NotificationCompat.Builder(
            context,
            NotificationChannels.channelFor(notice.type),
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(notice.title.ifBlank { context.getString(R.string.app_name) })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context)
            .notify(tag, tag.hashCode().absoluteValue, notification)
    }

    /**
     * Resolves the sender's key against this device's own strings.
     *
     * The same explicit map [VillageMessagingService] uses, and for the same
     * reason: the writer must not be able to name an arbitrary resource, and an
     * unknown key degrades to the literal text rather than crashing. It also
     * means a notice written by a Greek phone reads in English on an English
     * one.
     */
    private fun localizedBody(notice: AppNotification): String = when (notice.bodyKey) {
        "notif_upvotes" -> context.getString(R.string.notif_upvotes, notice.bodyArg)
        "notif_status_resolved" -> context.getString(R.string.notif_status_resolved)
        "notif_status_wont_do" -> context.getString(R.string.notif_status_wont_do)
        "notif_status_changed" -> context.getString(R.string.notif_status_changed)
        "notif_new_announcement" -> context.getString(R.string.notif_new_announcement)
        "notif_new_comment" -> context.getString(R.string.notif_new_comment)
        "notif_new_message" -> context.getString(R.string.notif_new_message, notice.bodyArg)
        else -> notice.body
    }

    private companion object {
        /** Enough to cover a long session without growing without bound. */
        const val SEEN_MEMORY = 400
    }
}
