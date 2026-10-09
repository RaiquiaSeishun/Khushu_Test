package com.kaizen.khushu

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaizen.khushu.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeNavigationInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun withHome(source: String, official: Boolean = false, check: () -> Unit) = runBlocking {
        prepareActivityTestPermissions()
        val repository = SettingsRepository(context)
        val original = repository.settingsFlow.first()
        val cacheBackups = mutableMapOf<File, ByteArray?>()
        try {
            // Exercise a startup value persisted by the removed Guided Prayer tab.
            repository.updateStartupTab("salah")
            repository.setOnboardingCompleted(true)
            repository.setDeveloperWelcomeDismissed(true)
            repository.updateLastSeenAppVersionCode(BuildConfig.VERSION_CODE)
            repository.updateUseGpsLocation(false)
            repository.updatePrayerSourceType(source)
            if (official) {
                // UI-only fixture, valid for the current and following Malaysia months.
                val month = YearMonth.now(JakimTimetableRepository.timeZone)
                for (m in listOf(month, month.plusMonths(1))) {
                    val file = File(context.filesDir, "jakim-timetables/WLY01-$m.json")
                    cacheBackups[file] = file.takeIf { it.exists() }?.readBytes()
                    val rows = JSONArray()
                    for (day in 1..m.lengthOfMonth()) rows.put(JSONObject().apply {
                        put("date", m.atDay(day).format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)))
                        for ((key, time) in mapOf("imsak" to "05:40:00", "fajr" to "05:50:00", "syuruk" to "07:00:00",
                            "dhuhr" to "13:10:00", "asr" to "16:30:00", "maghrib" to "19:00:00", "isha" to "20:10:00")) put(key, time)
                    })
                    val response = JSONObject().put("status", "OK!").put("zone", "WLY01").put("prayerTime", rows)
                    file.parentFile!!.mkdirs()
                    file.writeText(JSONObject().put("fetchedAt", System.currentTimeMillis()).put("response", response.toString()).toString())
                }
            }
            repository.updateJakimZone(if (official) "WLY01" else "")
            repository.updateShowUpcomingEventsOnHome(true)
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithTag("home-prayer-card").fetchSemanticsNodes().isNotEmpty()
                }
                check()
            }
        } finally {
            for ((file, bytes) in cacheBackups) if (bytes == null) file.delete() else file.writeBytes(bytes)
            repository.updateStartupTab(original.startupTab)
            repository.setOnboardingCompleted(original.onboardingCompleted)
            repository.setDeveloperWelcomeDismissed(original.developerWelcomeDismissed)
            repository.updateLastSeenAppVersionCode(original.lastSeenAppVersionCode)
            repository.updateShowUpcomingEventsOnHome(original.showUpcomingEventsOnHome)
            repository.updatePrayerSourceType("JAKIM")
            repository.updateJakimZone(original.jakimZone)
            repository.updateAutomaticJakimZone(original.automaticJakimZone)
            if (original.automaticJakimZone) repository.applyAutomaticJakimZone(original.lastLocationFixEpochMs,
                original.locationLat, original.locationLng, JakimZoneLocator.Result(
                    original.jakimZone.takeIf { original.automaticJakimZoneConfirmed }, original.automaticJakimZoneNotice))
            repository.updatePrayerSourceType(original.prayerSourceType)
            repository.updateUseGpsLocation(original.useGpsLocation)
        }
    }

    private fun assertInitialShortcutsVisible() {
        compose.onNodeWithTag("home-prayer-card").assertIsDisplayed()
        compose.onNodeWithTag("home-quick-actions").assertIsDisplayed()
        compose.onNodeWithText("Qibla").assertIsDisplayed()
        compose.onNodeWithText("Mosques").assertDoesNotExist()
        compose.onNodeWithText("Events").assertDoesNotExist()
        val shortcuts = compose.onNodeWithTag("home-quick-actions").fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithTag("main-navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Shortcuts extend behind bottom navigation: $shortcuts / $navigation", shortcuts.bottom <= navigation.top)
        val isha = compose.onNode(hasText("Isha") and hasAnyAncestor(hasTestTag("home-prayer-list")))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Explore must remain below the prayer list", isha.bottom <= shortcuts.top)
        compose.onNodeWithText("Guided prayer").assertDoesNotExist()
        compose.onNodeWithText("Pray").assertDoesNotExist()
    }

    @Test fun legacyStartupOpensHomeWithVisibleQiblaAndNoGuidedPrayerCustomization() = withHome("LOCAL") {
        assertInitialShortcutsVisible()
        compose.onNodeWithText("Qibla").performClick()
        compose.onNodeWithText("Qibla Direction").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Customize").performClick()
        compose.onNodeWithText("Tasbih Screen").assertIsDisplayed()
        compose.onNodeWithText("Pray Screen").assertDoesNotExist()
    }

    @Test fun sourceNoticeDoesNotHideQiblaAndCompassStillOpens() = withHome("JAKIM") {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Approximate prayer times").fetchSemanticsNodes().isNotEmpty()
        }
        assertInitialShortcutsVisible()
        compose.onNodeWithText("Qibla").performClick()
        compose.onNodeWithText("Qibla Direction").assertIsDisplayed()
    }
    @Test fun officialNoticeStaysCompactAndDetailsOpenWithoutChangingHomeLayout() = withHome("JAKIM", official = true) {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Official JAKIM times · WLY01").fetchSemanticsNodes().isNotEmpty()
        }
        assertInitialShortcutsVisible()
        val source = compose.onNodeWithTag("home-prayer-source").fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue("Official source row is too tall: $source", source.height <= 56 * density)
        compose.onNodeWithTag("home-prayer-source").performClick()
        compose.onNodeWithText("Night fractions are derived", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        assertInitialShortcutsVisible()
    }

}
