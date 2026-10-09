package com.kaizen.khushu

import com.kaizen.khushu.logic.LocationFixPolicy
import org.junit.Assert.*
import org.junit.Test

class LocationFixPolicyTest {
    private fun usable(age: Long = 0, accuracy: Float = 20f, precise: Boolean = true) =
        LocationFixPolicy.isUsable(3.139, 101.687, age, accuracy, precise)

    @Test fun throttlesRetriesUsingElapsedTimeRatherThanWallClock() {
        assertTrue(LocationFixPolicy.canRetryAutomatically(0, 1_000))
        assertFalse(LocationFixPolicy.canRetryAutomatically(1_000, 900_999))
        assertTrue(LocationFixPolicy.canRetryAutomatically(1_000, 901_000))
        assertTrue(LocationFixPolicy.canRetryAutomatically(1_000, 0))
    }

    @Test fun rejectsOldAndFutureFixes() {
        assertTrue(usable(age = LocationFixPolicy.MAX_AGE_MS))
        assertFalse(usable(age = LocationFixPolicy.MAX_AGE_MS + 1))
        assertFalse(usable(age = -1))
    }

    @Test fun requiresValidAccuracyAndHonorsApproximatePermission() {
        assertFalse(usable(accuracy = Float.NaN))
        assertFalse(usable(accuracy = Float.POSITIVE_INFINITY))
        assertFalse(usable(accuracy = 0f))
        assertFalse(usable(accuracy = -1f))
        assertFalse(usable(accuracy = 2_000f))
        assertTrue(usable(accuracy = 2_000f, precise = false))
        assertFalse(usable(accuracy = 10_001f, precise = false))
    }

    @Test fun rejectsInvalidCoordinates() {
        assertFalse(LocationFixPolicy.isUsable(Double.NaN, 100.0, 0, 20f, true))
        assertFalse(LocationFixPolicy.isUsable(91.0, 100.0, 0, 20f, true))
        assertFalse(LocationFixPolicy.isUsable(3.0, -181.0, 0, 20f, true))
    }

    @Test fun refreshesMissingStaleAndClockSkewedLocations() {
        assertTrue(LocationFixPolicy.needsRefresh(0, 1_000, 60))
        assertTrue(LocationFixPolicy.needsRefresh(1_000, 0, 60))
        assertFalse(LocationFixPolicy.needsRefresh(1_000, 3_600_999, 60))
        assertTrue(LocationFixPolicy.needsRefresh(1_000, 3_601_000, 60))
        assertTrue(LocationFixPolicy.needsRefresh(1_000, 901_000, 15))
    }
}
