package gr.agiosnektarios.village.data.weather

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.model.WeatherSnapshot
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What the app currently believes about the weather.
 *
 * [snapshot] survives a failed refresh on purpose. Up here the mobile signal
 * comes and goes, and a strip that empties itself whenever a request times out
 * would be blank most of the time it is most wanted. A reading with an age on
 * it is worth more than no reading, so the age is shown and the value stays.
 */
data class WeatherState(
    val snapshot: WeatherSnapshot? = null,
    val loading: Boolean = false,
    /** True once a fetch has failed and nothing newer has arrived since. */
    val stale: Boolean = false,
)

/**
 * The village's weather, fetched rarely and kept on disk.
 *
 * Three decisions worth stating:
 *
 *  * **No HTTP library.** The app has no other network call of its own — every
 *    other byte goes through the Firebase SDK — and pulling in Retrofit to make
 *    one GET an hour buys nothing. `HttpURLConnection` is in the platform.
 *
 *    The *parsing* is a different judgement and worth being straight about:
 *    `org.json` is in the platform too, and using it would have cost no
 *    dependency at all. It is stubbed to throw in JVM unit tests, though, so
 *    the parser could then only be exercised on a device — and this project has
 *    no device. kotlinx-serialization is here to buy a tested parser, not to
 *    save space, and pretending otherwise while adding a library would be the
 *    kind of comment this codebase keeps having to correct.
 *  * **Cached to a file, not to Firestore.** Weather is identical for every
 *    resident, so mirroring it through Firestore would be tidier; it would also
 *    mean every phone paying document reads for something it can fetch itself,
 *    on a free plan with a daily quota that the map and the reports already
 *    share.
 *  * **Served stale first.** The cached reading is emitted before the network
 *    is touched, so the strip is populated on the frame the map appears rather
 *    than a second later.
 */
@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var loadedFromDisk = false
    private var lastForcedAt = 0L

    private val cacheFile: File get() = File(context.filesDir, CACHE_FILE)

    /**
     * Brings the reading up to date, doing nothing if it already is.
     *
     * Safe to call from every screen entry and every resume: [MAX_AGE_MS] is
     * what decides whether anything actually happens, not the caller.
     */
    suspend fun refresh(force: Boolean = false) {
        // Outside the lock, so a second caller's spinner appears while the
        // first caller is still holding a socket open. With this inside the
        // critical section the Refresh button looked dead for as long as the
        // in-flight request took to time out.
        _state.update { it.copy(loading = true) }
        mutex.withLock {
            if (!loadedFromDisk) {
                loadedFromDisk = true
                readCache()?.let { cached ->
                    _state.update { it.copy(snapshot = cached, stale = cached.isOlderThan(MAX_AGE_MS)) }
                }
            }
            val current = _state.value.snapshot
            val recentlyForced = System.currentTimeMillis() - lastForcedAt < FORCE_FLOOR_MS
            if (!force && current != null && !current.isOlderThan(MAX_AGE_MS)) {
                _state.update { it.copy(loading = false) }
                return
            }
            // A floor under the Refresh button. Without it a resident holding
            // it down makes one request per tap, which is neither polite to a
            // free service nor any faster.
            if (force && recentlyForced) {
                _state.update { it.copy(loading = false) }
                return
            }
            if (force) lastForcedAt = System.currentTimeMillis()

            val fetched = withContext(io) { fetch() }
            _state.update {
                when (fetched) {
                    null -> it.copy(loading = false, stale = true)
                    else -> WeatherState(snapshot = fetched, loading = false, stale = false)
                }
            }
        }
    }

    private fun fetch(): WeatherSnapshot? {
        val body = runCatching {
            val connection = (URL(OpenMeteo.url()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    Log.w(TAG, "Weather request returned ${connection.responseCode}")
                    return@runCatching null
                }
                // Bounded read. A provider that starts answering with something
                // enormous should cost a wasted fetch, not the heap.
                connection.inputStream.bufferedReader().use { reader ->
                    val text = reader.readText()
                    if (text.length > MAX_BODY_CHARS) null else text
                }
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.w(TAG, "Weather request failed", it) }.getOrNull() ?: return null

        val snapshot = OpenMeteo.parse(body, System.currentTimeMillis()) ?: run {
            Log.w(TAG, "Weather response could not be read")
            return null
        }
        runCatching { cacheFile.writeText(body) }
            .onFailure { Log.w(TAG, "Could not cache the weather", it) }
        return snapshot
    }

    private suspend fun readCache(): WeatherSnapshot? = withContext(io) {
        runCatching {
            val file = cacheFile
            if (!file.exists()) return@runCatching null
            // Parsed with the file's own modification time as the fetch time:
            // storing the timestamp inside the payload would mean writing a
            // wrapper format around someone else's JSON for no gain.
            OpenMeteo.parse(file.readText(), file.lastModified())
        }.getOrNull()
    }

    private companion object {
        const val TAG = "Weather"
        const val CACHE_FILE = "weather.json"

        /**
         * How old a reading may be before a refresh is worth the radio.
         *
         * The provider updates its current block every fifteen minutes, but a
         * village checking whether it is windy does not need the difference
         * between 08:15 and 08:30, and waking the modem four times an hour on
         * a phone that spends the day in a pocket is a real battery cost for
         * none.
         */
        const val MAX_AGE_MS = 30 * 60 * 1000L

        /** The shortest gap between two hand-pressed refreshes. */
        const val FORCE_FLOOR_MS = 60 * 1000L
        const val TIMEOUT_MS = 12_000
        const val MAX_BODY_CHARS = 512 * 1024
    }
}

/** Age measured from when this device fetched it, not from the provider's clock. */
fun WeatherSnapshot.isOlderThan(millis: Long): Boolean =
    System.currentTimeMillis() - fetchedAt > millis

/** How long ago the reading was taken, for the "as of" line. */
fun WeatherSnapshot.ageMillis(now: Long = System.currentTimeMillis()): Long = now - fetchedAt
