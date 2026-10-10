package com.kaizen.khushu

import com.kaizen.khushu.logic.QiblaDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class QiblaDirectionTest {
    @Test fun bearingsMatchIndependentSphericalVectorReferenceAcrossHemispheres() {
        // Reference values obtained by projecting the Kaaba's 3D unit vector onto the
        // observer's local north/east tangent vectors, rather than the production formula.
        for ((location, expected) in listOf(
            (3.139 to 101.6869) to 292.537708,
            (6.1248 to 100.3678) to 291.194879,
            (51.5074 to -0.1278) to 118.987219,
            (40.7128 to -74.0060) to 58.481701,
            (-33.8688 to 151.2093) to 277.499589,
        )) assertEquals(expected, QiblaDirection.bearingDegrees(location.first, location.second), 0.00001)
    }

    @Test fun sameMeridianPointsNorthOrSouth() {
        assertEquals(0.0, QiblaDirection.bearingDegrees(0.0, 39.8262), 0.00001)
        assertEquals(180.0, QiblaDirection.bearingDegrees(40.0, 39.8262), 0.00001)
    }

    @Test fun magneticDeclinationIsAddedWithCorrectSignAndWraparound() {
        assertEquals(15.0, QiblaDirection.trueHeadingDegrees(10.0, 5.0), 0.0)
        assertEquals(5.0, QiblaDirection.trueHeadingDegrees(10.0, -5.0), 0.0)
        assertEquals(2.0, QiblaDirection.trueHeadingDegrees(358.0, 4.0), 0.0)
        assertEquals(358.0, QiblaDirection.trueHeadingDegrees(2.0, -4.0), 0.0)
    }

    @Test fun northMovesOppositePhoneRotationAndQiblaSharesThatFrame() {
        assertEquals(0.0, QiblaDirection.relativeDegrees(0.0, 0.0), 0.0)
        assertEquals(270.0, QiblaDirection.relativeDegrees(0.0, 90.0), 0.0)
        assertEquals(180.0, QiblaDirection.relativeDegrees(0.0, 180.0), 0.0)
        assertEquals(90.0, QiblaDirection.relativeDegrees(0.0, 270.0), 0.0)
        assertEquals(0.0, QiblaDirection.relativeDegrees(292.54, 292.54), 0.00001)
        assertEquals(90.0, QiblaDirection.relativeDegrees(292.54, 202.54), 0.00001)
        assertEquals(2.0, QiblaDirection.relativeDegrees(1.0, 359.0), 0.0)
    }
}
