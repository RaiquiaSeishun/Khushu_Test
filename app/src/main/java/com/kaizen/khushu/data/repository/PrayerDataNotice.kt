package com.kaizen.khushu.data.repository

/** Source information is distinct from availability of an official timetable. */
data class PrayerDataNotice(
    val title: String,
    val summary: String,
    val details: String,
    val approximate: Boolean,
    val manualAdjustments: Boolean = false,
) {
    companion object {
        fun fromJakim(result: JakimTimetableRepository.Result, settings: UserSettings): PrayerDataNotice {
            val unconfirmedGps = settings.automaticJakimZone && !settings.automaticJakimZoneConfirmed
            val adjusted = listOf(settings.fajrOffsetMinutes, settings.dhuhrOffsetMinutes,
                settings.asrOffsetMinutes, settings.maghribOffsetMinutes, settings.ishaOffsetMinutes).any { it != 0 }
            val zoneNotice = if (settings.automaticJakimZone) settings.automaticJakimZoneNotice
                else "Zone selected manually; GPS does not change it."
            val details = if (unconfirmedGps) zoneNotice
                else result.notice + " " + zoneNotice
            return when {
                result.times == null -> PrayerDataNotice(
                    if (unconfirmedGps) "GPS prayer zone" else "Approximate prayer times",
                    if (unconfirmedGps) "Times are approximate until your zone is confirmed."
                    else if (settings.effectiveJakimZone.isBlank()) "Choose your prayer zone in Settings to use official times."
                    else "Official timetable unavailable. Using local calculations.",
                    details,
                    true,
                )
                else -> PrayerDataNotice(
                    "Official JAKIM times · ${settings.effectiveJakimZone}",
                    if (adjusted) "Manual time adjustments are active." else "Using the e-Solat timetable.",
                    details + if (adjusted) " Manual offsets are active; displayed times differ from the official entries." else "",
                    false,
                    manualAdjustments = adjusted,
                )
            }
        }
    }
}
