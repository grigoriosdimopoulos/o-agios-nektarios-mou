package gr.agiosnektarios.village.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import gr.agiosnektarios.village.MainActivity
import gr.agiosnektarios.village.R
import gr.agiosnektarios.village.core.model.VillageEvent
import gr.agiosnektarios.village.data.auth.AuthRepository
import gr.agiosnektarios.village.data.event.EventRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Tomorrow's calendar, the evening before.
 *
 * The calendar was a notebook: somebody put "καθαρισμός Σάββατο" on it and it
 * reached whoever happened to open the tab. An entry nobody is reminded of is
 * an entry that competes with a telephone call and loses.
 *
 * One notification a day at most, and only when there is something on it. That
 * restraint is the whole design — a village app that speaks every day is a
 * village app people silence, and then the fire notice goes quiet too.
 *
 * No push involved. This reads the calendar the phone already has cached and
 * raises the notification locally, so it works without a server and, thanks to
 * Firestore's offline cache, mostly without a signal.
 */
@HiltWorker
class EventReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val eventRepository: EventRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        authRepository.currentUserId ?: return Result.success()

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return Result.success()

        val events = runCatching { eventRepository.observeUpcoming().first() }
            .getOrElse { return Result.retry() }

        val tomorrow = events.filter { it.isTomorrow() }
        if (tomorrow.isEmpty()) return Result.success()

        val title = context.getString(R.string.reminder_title)
        val body = tomorrow.take(3).joinToString("\n") { event ->
            if (event.allDay) {
                event.title
            } else {
                context.getString(R.string.reminder_line, clock(event.start), event.title)
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.ANNOUNCEMENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(tomorrow.first().title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(TAG, ID, notification) }
        return Result.success()
    }

    private fun clock(millis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        return "%02d:%02d".format(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
        )
    }

    /** Tomorrow in the device's own zone, not "within twenty-four hours". */
    private fun VillageEvent.isTomorrow(): Boolean {
        val target = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val event = Calendar.getInstance().apply { timeInMillis = start }
        return target.get(Calendar.YEAR) == event.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == event.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        private const val TAG = "village-calendar"
        private const val ID = 4711
        private const val REQUEST = 4711
        private const val WORK = "event-reminders"

        /**
         * Runs once a day, first fire aimed at the early evening.
         *
         * Android will not honour an exact hour for periodic work and should
         * not be asked to — an alarm that wakes a phone precisely at seven for
         * a village calendar is not worth the battery. The initial delay simply
         * aims the daily cycle at a civilised time; where it lands after that
         * is the system's business.
         */
        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val evening = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 19)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val request = PeriodicWorkRequestBuilder<EventReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(evening.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
