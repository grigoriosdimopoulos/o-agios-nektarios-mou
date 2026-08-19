package gr.agiosnektarios.village

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import gr.agiosnektarios.village.core.crash.CrashReporter
import gr.agiosnektarios.village.notifications.NotificationChannels
import gr.agiosnektarios.village.notifications.NotificationDispatcher
import gr.agiosnektarios.village.notifications.EventReminderWorker
import gr.agiosnektarios.village.notifications.NotificationSyncWorker
import gr.agiosnektarios.village.notifications.PushTokenSynchronizer
import javax.inject.Inject

@HiltAndroidApp
class VillageApplication : Application(), Configuration.Provider {

    @Inject lateinit var pushTokenSynchronizer: PushTokenSynchronizer

    @Inject
    lateinit var phonePrivacyMigration: gr.agiosnektarios.village.data.user.PhonePrivacyMigration

    @Inject lateinit var crashReporter: CrashReporter

    @Inject lateinit var notificationDispatcher: NotificationDispatcher

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** Lets WorkManager construct workers that have dependencies injected. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // First, so anything that fails during the rest of startup is recorded.
        crashReporter.install()
        NotificationChannels.register(this)
        // Starts a long-lived collector: the device's push registration follows
        // whoever is signed in, for as long as the process lives.
        pushTokenSynchronizer.start()
        // Moves this resident's telephone number off the directory every other
        // resident can read. Once per account, then a no-op.
        phonePrivacyMigration.start()
        // Watches the signed-in resident's inbox and raises a notification for
        // anything new. This is what stands in for server-sent push on the free
        // plan — see NotificationDispatcher for what that does and does not do.
        notificationDispatcher.start()
        // The half that survives this process: WorkManager keeps the schedule
        // across app death and reboot, so notices still arrive when the app is
        // closed — just not instantly. See NotificationSyncWorker.
        NotificationSyncWorker.schedule(this)
        EventReminderWorker.schedule(this)
    }
}
