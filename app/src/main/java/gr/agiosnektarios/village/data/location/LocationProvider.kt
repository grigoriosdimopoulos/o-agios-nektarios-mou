package gr.agiosnektarios.village.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import gr.agiosnektarios.village.core.VillageConfig
import gr.agiosnektarios.village.core.di.IoDispatcher
import gr.agiosnektarios.village.core.geo.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where the phone is, for reports filed by someone standing at the problem.
 *
 * Deliberately the platform's own [LocationManager] rather than Play Services'
 * fused provider. Two reasons, both specific to this village: the fused
 * provider leans on wifi and cell positioning, which on the skirts of
 * Kithairon are sparse to absent, while raw GPS works anywhere with sky; and
 * it would add a dependency to an app whose whole architecture is built around
 * not needing a paid tier.
 *
 * Everything here degrades rather than fails. No permission, no provider, no
 * fix in time — the caller gets null and falls back to putting a pin on the
 * map by hand, which is what the app did before this existed.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val manager: LocationManager?
        get() = ContextCompat.getSystemService(context, LocationManager::class.java)

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    /** Whether location is switched on at all, which is a different question. */
    val isEnabled: Boolean
        get() = manager?.let { LocationManagerCompat.isLocationEnabled(it) } == true

    /**
     * A position for right now, or null.
     *
     * A recent cached fix is used immediately when there is one — asking GPS
     * for a fresh fix costs seconds, and a report is being written by someone
     * who has not moved since they took the photo. Only when the cache is
     * stale or absent does this wait for the hardware.
     */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 8_000): GeoPoint? = withContext(io) {
        if (!hasPermission || !isEnabled) return@withContext null
        val lm = manager ?: return@withContext null

        lastKnownFresh(lm)?.let { return@withContext it.toGeoPoint() }

        val fix = runCatching {
            withTimeoutOrNull(timeoutMs) { awaitSingleFix(lm) }
        }.getOrNull()

        // A fix from outside the village is a fix from somewhere else — a
        // resident in Athens must not file a report onto their own street.
        fix?.toGeoPoint()?.takeIf { it in VillageConfig.BOUNDS }
            ?: lastKnown(lm)?.toGeoPoint()?.takeIf { it in VillageConfig.BOUNDS }
    }

    private fun lastKnownFresh(lm: LocationManager): Location? =
        lastKnown(lm)?.takeIf { System.currentTimeMillis() - it.time < FRESH_MS }

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? = runCatching {
        listOfNotNull(
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER),
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER),
        ).maxByOrNull { it.time }
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun awaitSingleFix(lm: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = android.os.CancellationSignal()
                runCatching {
                    lm.getCurrentLocation(
                        provider,
                        signal,
                        context.mainExecutor,
                    ) { location -> if (continuation.isActive) continuation.resume(location) }
                }.onFailure { if (continuation.isActive) continuation.resume(null) }
                continuation.invokeOnCancellation { runCatching { signal.cancel() } }
            } else {
                // Pre-R has no single-shot API, so this listens once and
                // unregisters itself. Without the removal it would keep the
                // GPS awake for the life of the process.
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { lm.removeUpdates(this) }
                        if (continuation.isActive) continuation.resume(location)
                    }

                    @Deprecated("Required by the pre-R interface")
                    override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) = Unit

                    override fun onProviderDisabled(p: String) {
                        runCatching { lm.removeUpdates(this) }
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                runCatching {
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, context.mainLooper)
                }.onFailure { if (continuation.isActive) continuation.resume(null) }
                continuation.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            }
        }

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)

    private companion object {
        /** Older than this and it is worth waiting for the hardware. */
        const val FRESH_MS = 90_000L
    }
}
