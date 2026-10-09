package com.kaizen.khushu.logic

/** Conservative limits for prayer-location fixes, including approximate permission. */
object LocationFixPolicy {
    const val MAX_AGE_MS = 10 * 60_000L

    fun isUsable(
        latitude: Double,
        longitude: Double,
        ageMs: Long,
        accuracyMeters: Float,
        precisePermission: Boolean,
    ): Boolean = latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 &&
        ageMs in 0..MAX_AGE_MS && accuracyMeters.isFinite() &&
        accuracyMeters > 0f && accuracyMeters <= if (precisePermission) 1_000f else 10_000f

    fun canRetryAutomatically(lastElapsedMs: Long, nowElapsedMs: Long): Boolean =
        lastElapsedMs <= 0 || nowElapsedMs < lastElapsedMs || nowElapsedMs - lastElapsedMs >= 15 * 60_000L

    fun needsRefresh(fixEpochMs: Long, nowEpochMs: Long, intervalMinutes: Int): Boolean =
        fixEpochMs <= 0 || nowEpochMs < fixEpochMs ||
            nowEpochMs - fixEpochMs >= intervalMinutes.coerceIn(15, 360) * 60_000L
}
