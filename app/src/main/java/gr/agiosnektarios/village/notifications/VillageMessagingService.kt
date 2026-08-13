package gr.agiosnektarios.village.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import gr.agiosnektarios.village.MainActivity
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.di.ApplicationScope
import gr.agiosnektarios.village.data.auth.AuthRepository
import gr.agiosnektarios.village.data.user.UserRepository
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Receives pushes sent by the Cloud Functions.
 *
 * Messages are data-only so this class always runs, even in the foreground —
 * that lets notifications for a conversation the user is already looking at be
 * suppressed, and lets each notification carry a deep link.
 */
@AndroidEntryPoint
class VillageMessagingService : FirebaseMessagingService() {

    @Inject lateinit var userRepository: UserRepository

    @Inject lateinit var authRepository: AuthRepository

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onNewToken(token: String) {
        // Tokens rotate; the user document keeps the full set so a resident
        // signed in on a phone and a tablet gets both.
        val userId = authRepository.currentUserId ?: return
        scope.launch { userRepository.addFcmToken(userId, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"]
        val title = data["title"].orEmpty().ifBlank { getString(R.string.app_name) }
        val deepLink = data["deepLink"].orEmpty()
        val body = localizedBody(data["bodyKey"], data["body"].orEmpty())

        if (type == "CHAT" && data["chatId"] == ActiveChatTracker.activeChatId) return

        showNotification(
            channelId = NotificationChannels.channelFor(type),
            title = title,
            body = body,
            deepLink = deepLink,
            // Grouping key: replacing an earlier notice about the same report
            // beats stacking five near-identical ones.
            tag = data["collapseKey"] ?: deepLink.ifBlank { title },
        )
    }

    /**
     * Resolves a server-sent body key against the app's own strings.
     *
     * An explicit map rather than `getIdentifier`: the server must not be able
     * to name an arbitrary resource, and an unknown key degrades to the literal
     * text instead of crashing.
     */
    private fun localizedBody(bodyKey: String?, literal: String): String = when (bodyKey) {
        "notif_upvotes" -> getString(R.string.notif_upvotes, literal)
        "notif_status_resolved" -> getString(R.string.notif_status_resolved)
        "notif_status_wont_do" -> getString(R.string.notif_status_wont_do)
        "notif_status_changed" -> getString(R.string.notif_status_changed)
        "notif_new_announcement" -> getString(R.string.notif_new_announcement)
        else -> literal
    }

    private fun showNotification(
        channelId: String,
        title: String,
        body: String,
        deepLink: String,
        tag: String,
    ) {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (deepLink.isNotBlank()) {
                action = Intent.ACTION_VIEW
                data = Uri.parse(deepLink)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(tag, tag.hashCode().absoluteValue, notification)
    }
}

/**
 * Which conversation is on screen, so its own pushes stay silent.
 *
 * A plain static field rather than injected state: [VillageMessagingService] is
 * constructed by the system on a background thread and may outlive the UI
 * process's view models entirely.
 */
object ActiveChatTracker {
    @Volatile
    var activeChatId: String? = null
}
