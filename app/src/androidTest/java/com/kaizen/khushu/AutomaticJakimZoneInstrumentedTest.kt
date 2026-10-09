package com.kaizen.khushu

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaizen.khushu.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticJakimZoneInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun restore(repository: SettingsRepository, original: UserSettings) {
        repository.updatePrayerSourceType("JAKIM")
        repository.updateJakimZone(original.jakimZone)
        repository.updateLocationFix(original.locationLat, original.locationLng, original.lastLocationFixEpochMs,
            original.locationAccuracyMeters)
        repository.updateAutomaticJakimZone(original.automaticJakimZone)
        if (original.automaticJakimZone) repository.applyAutomaticJakimZone(original.lastLocationFixEpochMs,
            original.locationLat, original.locationLng, JakimZoneLocator.Result(
                original.jakimZone.takeIf { original.automaticJakimZoneConfirmed }, original.automaticJakimZoneNotice))
        repository.updatePrayerSourceType(original.prayerSourceType)
        repository.updateUseGpsLocation(original.useGpsLocation)
    }

    @Test fun detectionPersistsAndANewFixInvalidatesTheOldZone() = runBlocking {
        val repository = SettingsRepository(context)
        val original = repository.settingsFlow.first()
        try {
            repository.updatePrayerSourceType("JAKIM")
            repository.updateJakimZone("WLY01")
            repository.updateAutomaticJakimZone(true)
            repository.updateLocationFix(5.364f, 100.561f, 100L, 20f)
            repository.applyAutomaticJakimZone(100L, 5.364f, 100.561f, JakimZoneLocator.Result("KDH05", "GPS detected"))
            val restored = SettingsRepository(context).settingsFlow.first()
            assertTrue(restored.automaticJakimZone)
            assertTrue(restored.useGpsLocation)
            assertEquals("KDH05", restored.effectiveJakimZone)
            assertEquals("GPS detected", restored.automaticJakimZoneNotice)
            repository.updateLocationFix(3.1579f, 101.7116f, 200L, 20f)
            assertEquals("", repository.settingsFlow.first().effectiveJakimZone)
            repository.applyAutomaticJakimZone(100L, 5.364f, 100.561f, JakimZoneLocator.Result("KDH05", "Old response"))
            assertFalse(repository.settingsFlow.first().automaticJakimZoneConfirmed)
            repository.applyAutomaticJakimZone(200L, 3.1579f, 101.7116f, JakimZoneLocator.Result("WLY01", "New response"))
            assertEquals("WLY01", repository.settingsFlow.first().effectiveJakimZone)
        } finally { restore(repository, original) }
    }

    @Test fun manualSelectionAndGpsDisablePreventAnInFlightLookupFromOverwritingTheChoice() = runBlocking {
        val repository = SettingsRepository(context)
        val original = repository.settingsFlow.first()
        try {
            repository.updatePrayerSourceType("JAKIM")
            repository.updateAutomaticJakimZone(true)
            repository.updateLocationFix(5.364f, 100.561f, 100L, 20f)
            repository.updateJakimZone("PNG01")
            repository.applyAutomaticJakimZone(100L, 5.364f, 100.561f, JakimZoneLocator.Result("KDH05", "Late response"))
            assertEquals("PNG01", repository.settingsFlow.first().effectiveJakimZone)
            assertFalse(repository.settingsFlow.first().automaticJakimZone)
            repository.updateAutomaticJakimZone(true)
            repository.updateUseGpsLocation(false)
            repository.applyAutomaticJakimZone(100L, 5.364f, 100.561f, JakimZoneLocator.Result("KDH05", "Late response"))
            assertEquals("PNG01", repository.settingsFlow.first().jakimZone)
            assertFalse(repository.settingsFlow.first().automaticJakimZone)
            repository.updateAutomaticJakimZone(true)
            repository.updatePrayerSourceType("LOCAL")
            repository.applyAutomaticJakimZone(100L, 5.364f, 100.561f, JakimZoneLocator.Result("KDH05", "Wrong source"))
            assertFalse(repository.settingsFlow.first().automaticJakimZoneConfirmed)
        } finally { restore(repository, original) }
    }
}
