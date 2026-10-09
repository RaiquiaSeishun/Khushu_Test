package com.kaizen.khushu.data.repository

object PrayerCalculationPolicy {
    fun supportsLocalMethod(methodStr: String): Boolean {
        return when (methodStr) {
            "MUSLIM_WORLD_LEAGUE",
            "EGYPTIAN",
            "KARACHI",
            "UMM_AL_QURA",
            "DUBAI",
            "MOON_SIGHTING_COMMITTEE",
            "NORTH_AMERICA",
            "KUWAIT",
            "QATAR",
            "SINGAPORE",
            "ALGERIA",
            "TUNISIA",
            "FRANCE_UOIF",
            "FRANCE_15",
            "FRANCE_18" -> true
            "TEHRAN",
            "TURKEY" -> false
            else -> false
        }
    }

}
