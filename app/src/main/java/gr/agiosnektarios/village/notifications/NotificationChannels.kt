package gr.agiosnektarios.village.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import gr.agiosnektarios.village.R

/**
 * Separate channels so a resident can silence chat without losing the
 * "dangerous situation on your street" notice — Android's own settings then do
 * the fine-grained work instead of an in-app switch nobody finds.
 */
object NotificationChannels {
    const val GENERAL = "general"
    const val ISSUES = "issues"
    const val CHAT = "chat"
    const val ANNOUNCEMENTS = "announcements"

    fun register(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channels = listOf(
            NotificationChannel(
                GENERAL,
                context.getString(R.string.notification_channel_general),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            NotificationChannel(
                ISSUES,
                context.getString(R.string.notification_channel_issues),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            NotificationChannel(
                CHAT,
                context.getString(R.string.notification_channel_chat),
                NotificationManager.IMPORTANCE_HIGH,
            ),
            NotificationChannel(
                ANNOUNCEMENTS,
                context.getString(R.string.notification_channel_announcements),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        manager.createNotificationChannels(channels)
    }

    fun channelFor(type: String?): String = when (type) {
        "COMMENT", "STATUS", "VOTE" -> ISSUES
        "CHAT" -> CHAT
        "ANNOUNCEMENT" -> ANNOUNCEMENTS
        else -> GENERAL
    }
}
