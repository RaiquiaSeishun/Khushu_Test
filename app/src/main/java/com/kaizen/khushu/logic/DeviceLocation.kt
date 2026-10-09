package com.kaizen.khushu.logic

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One bounded request per enabled provider; never requests background location. */
object DeviceLocation {
    @SuppressLint("MissingPermission") // Caller checks permissions; revocation is handled below.
    suspend fun findFix(context: Context, precisePermission: Boolean): Location? = coroutineScope {
        val manager = context.getSystemService(LocationManager::class.java) ?: return@coroutineScope null
        val providers = buildList {
            add(LocationManager.NETWORK_PROVIDER)
            if (precisePermission) add(LocationManager.GPS_PROVIDER)
        }.filter { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }

        fun usable(location: Location): Boolean = location.hasAccuracy() &&
            location.elapsedRealtimeNanos > 0 && LocationFixPolicy.isUsable(
                location.latitude, location.longitude,
                (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000,
                location.accuracy, precisePermission,
            )

        val fresh = providers.map { provider ->
            async {
                withTimeoutOrNull(20_000L) {
                    suspendCancellableCoroutine<Location?> { continuation ->
                        val signal = CancellationSignal()
                        continuation.invokeOnCancellation { signal.cancel() }
                        try {
                            manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                                if (continuation.isActive) continuation.resume(location)
                            }
                        } catch (_: SecurityException) {
                            if (continuation.isActive) continuation.resume(null)
                        } catch (_: IllegalArgumentException) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                }
            }
        }.awaitAll().filterNotNull().filter(::usable)
        // Fresh fixes are preferred. Cached fixes must meet the same age/accuracy limits.
        val candidates = fresh.ifEmpty {
            providers.mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }.filter(::usable)
        }
        // Provider names do not guarantee accuracy. Within the freshness window, prefer
        // the smallest reported horizontal uncertainty, then the most recent fix.
        candidates.minWithOrNull(compareBy<Location> { it.accuracy }.thenByDescending { it.elapsedRealtimeNanos })
    }
}
