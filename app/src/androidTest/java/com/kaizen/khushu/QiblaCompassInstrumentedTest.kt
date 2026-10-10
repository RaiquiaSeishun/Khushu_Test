package com.kaizen.khushu

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaizen.khushu.ui.screens.home.QiblaCompassDial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QiblaCompassInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun northMarkerTracksHeadingInRenderedDialAndHidesWithoutHeading() {
        val heading = mutableStateOf<Float?>(0f)
        compose.setContent {
            MaterialTheme { QiblaCompassDial(bearingDegrees = 292.537708, trueHeading = heading.value) }
        }
        for (degrees in listOf(0f, 90f, 180f, 270f)) {
            compose.runOnIdle { heading.value = degrees }
            val dial = compose.onNodeWithTag("qibla-compass-dial").fetchSemanticsNode().boundsInRoot
            val north = compose.onNodeWithTag("qibla-north-marker").fetchSemanticsNode().boundsInRoot
            when (degrees) {
                0f -> { assertTrue(north.center.y < dial.center.y); assertEquals(dial.center.x, north.center.x, 1f) }
                90f -> { assertTrue(north.center.x < dial.center.x); assertEquals(dial.center.y, north.center.y, 1f) }
                180f -> { assertTrue(north.center.y > dial.center.y); assertEquals(dial.center.x, north.center.x, 1f) }
                270f -> { assertTrue(north.center.x > dial.center.x); assertEquals(dial.center.y, north.center.y, 1f) }
            }
        }
        compose.runOnIdle { heading.value = null }
        compose.onNodeWithTag("qibla-north-marker").assertDoesNotExist()
    }
}
