package com.kaizen.khushu

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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

    private fun withHome(source: String, check: () -> Unit) = runBlocking {
        prepareActivityTestPermissions()
        val repository = SettingsRepository(context)
        val original = repository.settingsFlow.first()
        try {
            // Exercise a startup value persisted by the removed Guided Prayer tab.
            repository.updateStartupTab("salah")
            repository.setOnboardingCompleted(true)
            repository.setDeveloperWelcomeDismissed(true)
            repository.updateLastSeenAppVersionCode(BuildConfig.VERSION_CODE)
            repository.updateUseGpsLocation(false)
            repository.updatePrayerSourceType(source)
            repository.updateJakimZone("")
            repository.updateShowUpcomingEventsOnHome(true)
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithTag("home-prayer-card").fetchSemanticsNodes().isNotEmpty()
                }
                check()
            }
        } finally {
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
        for (label in listOf("Qibla", "Mosques", "Events")) compose.onNodeWithText(label).assertIsDisplayed()
        val shortcuts = compose.onNodeWithTag("home-quick-actions").fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithTag("main-navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Shortcuts extend behind bottom navigation", shortcuts.bottom <= navigation.top)
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

    @Test fun sourceNoticeDoesNotHideShortcutsAndEventsJumpStillWorks() = withHome("JAKIM") {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Approximate prayer times").fetchSemanticsNodes().isNotEmpty()
        }
        assertInitialShortcutsVisible()
        compose.onNodeWithText("Events").performClick()
        compose.onNodeWithTag("home-events").assertIsDisplayed()
    }
}
