package gr.agiosnektarios.village.core.crash

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the stack trace of the last crash so it can be read off the device.
 *
 * There is no Crashlytics here (it wants the Blaze plan for its backend
 * integrations, and this app is deliberately serverless), and the people
 * testing this have a phone and no computer — no `adb logcat`, no Play Console.
 * Without something like this a crash report is "it crashed", which is not
 * enough to fix anything.
 *
 * The handler chains to whatever was installed before it, so the process still
 * dies exactly as it would have; this only records the reason on the way out.
 */
@Singleton
class CrashReporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file: File get() = File(context.filesDir, FILE_NAME)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last crash, or null. Read once on launch. */
    fun lastCrash(): String? =
        runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }

    private fun write(thread: Thread, error: Throwable) {
        val when_ = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            appendLine("Agios Nektarios — crash report")
            appendLine(when_)
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("thread: ${thread.name}")
            appendLine()
            appendLine(error.stackTraceToString())
            // The root cause is usually the interesting line and is often
            // buried under several wrapper frames.
            var cause = error.cause
            while (cause != null) {
                appendLine("--- caused by ---")
                appendLine(cause.stackTraceToString())
                cause = cause.cause
            }
        }
        file.writeText(report)
    }

    private companion object {
        const val FILE_NAME = "last-crash.txt"
    }
}
