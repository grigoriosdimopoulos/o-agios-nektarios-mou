package gr.agiosnektarios.village

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import gr.agiosnektarios.village.core.crash.CrashReporter
import gr.agiosnektarios.village.notifications.NotificationChannels
import gr.agiosnektarios.village.notifications.PushTokenSynchronizer
import javax.inject.Inject

@HiltAndroidApp
class VillageApplication : Application() {

    @Inject lateinit var pushTokenSynchronizer: PushTokenSynchronizer

    @Inject lateinit var crashReporter: CrashReporter

    override fun onCreate() {
        super.onCreate()
        // First, so anything that fails during the rest of startup is recorded.
        crashReporter.install()
        NotificationChannels.register(this)
        // Starts a long-lived collector: the device's push registration follows
        // whoever is signed in, for as long as the process lives.
        pushTokenSynchronizer.start()
    }
}
