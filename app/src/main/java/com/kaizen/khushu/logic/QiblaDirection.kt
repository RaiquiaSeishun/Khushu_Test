package com.kaizen.khushu.logic

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Bearings are clockwise from true north; dial angles are clockwise from the phone's top. */
object QiblaDirection {
    private const val KAABA_LATITUDE = 21.4225
    private const val KAABA_LONGITUDE = 39.8262

    fun normalize(degrees: Double): Double = (degrees % 360.0 + 360.0) % 360.0

    /** Initial great-circle bearing to the Kaaba, using the same spherical model for both axes. */
    fun bearingDegrees(latitude: Double, longitude: Double): Double {
        val latitudeRadians = Math.toRadians(latitude)
        val kaabaLatitudeRadians = Math.toRadians(KAABA_LATITUDE)
        val longitudeDifference = Math.toRadians(KAABA_LONGITUDE - longitude)
        val east = sin(longitudeDifference) * cos(kaabaLatitudeRadians)
        val north = cos(latitudeRadians) * sin(kaabaLatitudeRadians) -
            sin(latitudeRadians) * cos(kaabaLatitudeRadians) * cos(longitudeDifference)
        return normalize(Math.toDegrees(atan2(east, north)))
    }

    /** Android declination is positive east: true heading = magnetic heading + declination. */
    fun trueHeadingDegrees(magneticHeading: Double, declination: Double): Double =
        normalize(magneticHeading + declination)

    fun relativeDegrees(trueBearing: Double, trueHeading: Double): Double =
        normalize(trueBearing - trueHeading)
}
