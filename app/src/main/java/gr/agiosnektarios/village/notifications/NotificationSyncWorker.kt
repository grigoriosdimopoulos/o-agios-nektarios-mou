package gr.agiosnektarios.village.notifications

import android.content.Context
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
import gr.agiosnektarios.village.data.auth.AuthRepository
import gr.agiosnektarios.village.data.notification.NotificationRepository
import gr.agiosnektarios.village.data.user.UserRepository
import java.util.concurrent.TimeUnit

/**
 * Checks the inbox on a schedule, so notices arrive with the app closed.
 *
 * [NotificationDispatcher] only works while the app's process is alive. This is
 * the part that survives it: WorkManager owns the schedule, keeps it across
 * process death and reboot, and hands back a coroutine to run in.
 *
 * **Fifteen minutes is Android's floor, not a choice.** `PeriodicWorkRequest`
 * refuses a shorter interval, and in Doze even that stretches. A notice can
 * therefore be a quarter of an hour late — fine for a fallen tree, poor for a
 * conversation. Genuinely instant delivery needs either a foreground service
 * with its permanent notification, or real push from a server on the paid
 * plan; both remain open, and neither is free of a cost worth deciding on
 * deliberately.
 *
 * The two halves cooperate rather than duplicate: whichever sees a notice first
 * marks it seen, and the other skips what is already marked.
 */
@HiltWorker
class NotificationSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val presenter: NotificationPresenter,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userId = authRepository.currentUserId ?: return Result.success()
        val profile = userRepository.getProfile(userId).getOrNull() ?: return Result.success()

        val unseen = notificationRepository.unseen(userId).getOrElse {
            // A failed poll is worth retrying; the schedule would otherwise
            // skip an interval on one bad network moment.
            return Result.retry()
        }

        unseen
            .filter { it.actorId != profile.id }
            .filter { it.allowedBy(profile.notificationPrefs) }
            .forEach { notice ->
                presenter.show(notice)
                // Marked here rather than when the resident reads it: this is
                // the record of "already announced on this account", and it is
                // what stops the foreground listener announcing it twice.
                notificationRepository.markSeen(userId, notice.id)
            }

        return Result.success()
    }

    companion object {
        private const val NAME = "notification-sync"

        /**
         * Idempotent: KEEP means re-registering on every launch leaves an
         * existing schedule — and its next run — undisturbed.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationSyncWorker>(
                15,
                TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
