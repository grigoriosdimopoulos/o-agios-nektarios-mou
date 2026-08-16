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
import gr.agiosnektarios.village.data.notification.AppNotification
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * Turns a notice into a notification on this device.
 *
 * Shared by the two things that can decide to raise one: the live listener that
 * runs while the app is open, and the scheduled worker that runs when it is
 * not. Keeping the rendering in one place is what stops the same notice looking
 * like two different notifications depending on which half noticed it.
 */
@Singleton
class NotificationPresenter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun show(notice: AppNotification) {
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
     * An explicit map, for the reason [VillageMessagingService] gives: whoever
     * wrote the notice must not be able to name an arbitrary resource, and an
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
}
