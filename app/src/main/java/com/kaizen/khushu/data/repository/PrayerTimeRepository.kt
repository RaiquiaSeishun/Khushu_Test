@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.kaizen.khushu.data.repository

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.CalculationParameters
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.SunnahTimes
import com.batoulapps.adhan2.data.DateComponents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

fun Instant.toDate(): Date = Date(this.toEpochMilliseconds())

@Serializable
data class AlAdhanResponse(val code: Int, val data: AlAdhanData)

@Serializable
data class AlAdhanData(val timings: Map<String, String>)

internal fun nextPrayerDate(date: Date): Date =
    Calendar.getInstance().apply { time = date; add(Calendar.DAY_OF_MONTH, 1) }.time

fun nextPrayerDate(date: Date, settings: UserSettings): Date =
    if (settings.prayerSourceType == "JAKIM") Date.from(JakimTimetableRepository.localDate(date).plusDays(1)
        .atStartOfDay(JakimTimetableRepository.timeZone).toInstant()) else nextPrayerDate(date)

class PrayerTimeRepository(
    private val cacheDirectory: java.io.File?,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build(),
    private val apiBaseUrl: String = "https://api.aladhan.com/v1",
    private val jakim: JakimTimetableRepository = JakimTimetableRepository(cacheDirectory?.let { java.io.File(it, "jakim") }, client),
) {
    constructor(settingsRepository: SettingsRepository) : this(
        settingsRepository.prayerCacheDirectory,
        jakim = JakimTimetableRepository(settingsRepository.jakimCacheDirectory, OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()),
    )
    var lastApiError: String? = null
        private set

    companion object {
        private val apiCache = PrayerApiCache()
    }
    private val json = Json { ignoreUnknownKeys = true }

    fun usesApiSource(settings: UserSettings): Boolean {
        return settings.prayerSourceType in setOf("API", "JAKIM")
    }

    fun supportsLocalCalculationMethod(methodStr: String): Boolean =
        PrayerCalculationPolicy.supportsLocalMethod(methodStr)

    fun getLocalPrayerTimes(
        date: Date,
        lat: Double,
        lng: Double,
        methodStr: String,
        madhabStr: String
    ): PrayerTimes {
        val coordinates = Coordinates(lat, lng)
        val calendar = Calendar.getInstance().apply { time = date }
        val dateComponents = DateComponents(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
        
        val localMethod = if (supportsLocalCalculationMethod(methodStr)) methodStr else "MUSLIM_WORLD_LEAGUE"
        val parameters = getCalculationParameters(localMethod).copy(
            madhab = if (madhabStr == "HANAFI") Madhab.HANAFI else Madhab.SHAFI
        )

        return PrayerTimes(coordinates, dateComponents, parameters)
    }

    fun getSunnahTimes(prayerTimes: PrayerTimes): SunnahTimes {
        return SunnahTimes(prayerTimes)
    }

    suspend fun getEffectivePrayerDateTimes(
        date: Date,
        settings: UserSettings
    ): Map<String, Date> {
        if (settings.prayerSourceType == "JAKIM") {
            val official = jakim.get(date, settings.jakimZone).times
            if (official != null) {
                val offsets = mapOf("Fajr" to settings.fajrOffsetMinutes, "Dhuhr" to settings.dhuhrOffsetMinutes,
                    "Asr" to settings.asrOffsetMinutes, "Maghrib" to settings.maghribOffsetMinutes, "Isha" to settings.ishaOffsetMinutes)
                return offsets.mapValues { (name, offset) -> Date(official.getValue(name).time + offset * 60_000L) }
            }
        }
        val isApiSource = settings.prayerSourceType == "API"
        val localPrayerTimes = getLocalPrayerTimes(
            date = date,
            lat = settings.locationLat.toDouble(),
            lng = settings.locationLng.toDouble(),
            methodStr = settings.prayerCalculationMethod,
            madhabStr = settings.prayerMadhab
        )
        val apiTimings = if (isApiSource) {
            getFallbackPrayerTimes(
                date = date,
                lat = settings.locationLat.toDouble(),
                lng = settings.locationLng.toDouble(),
                methodStr = settings.prayerCalculationMethod,
                madhabStr = settings.prayerMadhab
            )
        } else {
            null
        }

        fun parseApiTime(key: String, fallback: Date): Date {
            val timeStr = apiTimings?.get(key) ?: return fallback
            return try {
                val normalizedTime = timeStr.take(5)
                val parsed = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).parse(normalizedTime)
                    ?: return fallback
                val targetCal = Calendar.getInstance().apply { time = date }
                val parsedCal = Calendar.getInstance().apply { time = parsed }
                targetCal.set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
                targetCal.set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
                targetCal.set(Calendar.SECOND, 0)
                targetCal.set(Calendar.MILLISECOND, 0)
                targetCal.time
            } catch (e: Exception) {
                fallback
            }
        }

        fun applyOffset(time: Date, minutes: Int): Date = Date(time.time + minutes * 60_000L)

        val fajr = if (isApiSource) parseApiTime("Fajr", localPrayerTimes.fajr.toDate()) else localPrayerTimes.fajr.toDate()
        val dhuhr = if (isApiSource) parseApiTime("Dhuhr", localPrayerTimes.dhuhr.toDate()) else localPrayerTimes.dhuhr.toDate()
        val asr = if (isApiSource) parseApiTime("Asr", localPrayerTimes.asr.toDate()) else localPrayerTimes.asr.toDate()
        val maghrib = if (isApiSource) parseApiTime("Maghrib", localPrayerTimes.maghrib.toDate()) else localPrayerTimes.maghrib.toDate()
        val isha = if (isApiSource) parseApiTime("Isha", localPrayerTimes.isha.toDate()) else localPrayerTimes.isha.toDate()

        return mapOf(
            "Fajr" to applyOffset(fajr, settings.fajrOffsetMinutes),
            "Dhuhr" to applyOffset(dhuhr, settings.dhuhrOffsetMinutes),
            "Asr" to applyOffset(asr, settings.asrOffsetMinutes),
            "Maghrib" to applyOffset(maghrib, settings.maghribOffsetMinutes),
            "Isha" to applyOffset(isha, settings.ishaOffsetMinutes)
        )
    }

    suspend fun getExtraPrayerDateTimes(
        date: Date,
        settings: UserSettings
    ): Map<String, Date> {
        if (settings.prayerSourceType == "JAKIM") {
            val official = jakim.get(date, settings.jakimZone).times
            if (official != null) {
                val nextDate = Date.from(JakimTimetableRepository.localDate(date).plusDays(1)
                    .atStartOfDay(JakimTimetableRepository.timeZone).toInstant())
                val result = mutableMapOf("IMSAK" to official.getValue("Imsak"), "SUNRISE" to official.getValue("Sunrise"),
                    "SUNSET" to official.getValue("Maghrib"))
                val nextFajr = jakim.get(nextDate, settings.jakimZone).times?.get("Fajr")
                if (nextFajr != null) {
                    val sunset = official.getValue("Maghrib")
                    val night = nextFajr.time - sunset.time
                    result += mapOf("FIRST_THIRD" to Date(sunset.time + night / 3),
                        "MIDNIGHT" to Date(sunset.time + night / 2), "LAST_THIRD" to Date(sunset.time + night * 2 / 3))
                }
                return result
            }
        }
        val isApiSource = settings.prayerSourceType == "API"
        val localPrayerTimes = getLocalPrayerTimes(
            date = date,
            lat = settings.locationLat.toDouble(),
            lng = settings.locationLng.toDouble(),
            methodStr = settings.prayerCalculationMethod,
            madhabStr = settings.prayerMadhab
        )
        val nextDayPrayerTimes = getLocalPrayerTimes(
            date = nextPrayerDate(date),
            lat = settings.locationLat.toDouble(),
            lng = settings.locationLng.toDouble(),
            methodStr = settings.prayerCalculationMethod,
            madhabStr = settings.prayerMadhab
        )
        val apiTimings = if (isApiSource) {
            getFallbackPrayerTimes(
                date = date,
                lat = settings.locationLat.toDouble(),
                lng = settings.locationLng.toDouble(),
                methodStr = settings.prayerCalculationMethod,
                madhabStr = settings.prayerMadhab
            )
        } else {
            null
        }

        fun parseApiTime(key: String, fallback: Date): Date {
            val timeStr = apiTimings?.get(key) ?: return fallback
            return try {
                val normalizedTime = timeStr.take(5)
                val parsed = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).parse(normalizedTime)
                    ?: return fallback
                val targetCal = Calendar.getInstance().apply { time = date }
                val parsedCal = Calendar.getInstance().apply { time = parsed }
                targetCal.set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
                targetCal.set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
                targetCal.set(Calendar.SECOND, 0)
                targetCal.set(Calendar.MILLISECOND, 0)
                targetCal.time
            } catch (e: Exception) {
                fallback
            }
        }

        val localSunrise = localPrayerTimes.sunrise.toDate()
        val localSunset = localPrayerTimes.maghrib.toDate()
        val localImsak = Date(localPrayerTimes.fajr.toDate().time - TimeUnit.MINUTES.toMillis(10))
        val sunnahTimes = getSunnahTimes(localPrayerTimes)
        val localMidnight = sunnahTimes.middleOfTheNight.toDate()
        val localLastThird = sunnahTimes.lastThirdOfTheNight.toDate()
        val firstThirdMillis = localPrayerTimes.maghrib.toDate().time +
            ((nextDayPrayerTimes.fajr.toDate().time - localPrayerTimes.maghrib.toDate().time) / 3L)
        val localFirstThird = Date(firstThirdMillis)

        return mapOf(
            "IMSAK" to if (isApiSource) parseApiTime("Imsak", localImsak) else localImsak,
            "SUNRISE" to if (isApiSource) parseApiTime("Sunrise", localSunrise) else localSunrise,
            "SUNSET" to if (isApiSource) parseApiTime("Sunset", localSunset) else localSunset,
            "FIRST_THIRD" to if (isApiSource) parseApiTime("Firstthird", localFirstThird) else localFirstThird,
            "MIDNIGHT" to if (isApiSource) parseApiTime("Midnight", localMidnight) else localMidnight,
            "LAST_THIRD" to if (isApiSource) parseApiTime("Lastthird", localLastThird) else localLastThird,
        )
    }

    suspend fun hasOfficialTimetable(date: Date, settings: UserSettings): Boolean =
        settings.prayerSourceType != "JAKIM" || jakim.get(date, settings.jakimZone).times != null

    suspend fun refreshOfficialTimetable(date: Date, settings: UserSettings) {
        if (settings.prayerSourceType == "JAKIM") jakim.get(date, settings.jakimZone, forceRefresh = true)
    }

    suspend fun getPrayerDataNotice(date: Date, settings: UserSettings): String? {
        if (settings.prayerSourceType != "JAKIM") return if (settings.prayerSourceType == "API") lastApiError
            else if (!supportsLocalCalculationMethod(settings.prayerCalculationMethod))
                "Selected convention needs internet. Using approximate Muslim World League times offline."
            else null
        val result = jakim.get(date, settings.jakimZone)
        val adjusted = listOf(settings.fajrOffsetMinutes, settings.dhuhrOffsetMinutes, settings.asrOffsetMinutes,
            settings.maghribOffsetMinutes, settings.ishaOffsetMinutes).any { it != 0 }
        return result.notice + if (adjusted && result.times != null) " Manual offsets are active; displayed times differ from the official entries." else ""
    }

    suspend fun getFallbackPrayerTimes(
        date: Date,
        lat: Double,
        lng: Double,
        methodStr: String,
        madhabStr: String
    ): Map<String, String>? = withContext(Dispatchers.IO) {
        try {
            val calendar = Calendar.getInstance().apply { time = date }
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val month = calendar.get(Calendar.MONTH) + 1
            val year = calendar.get(Calendar.YEAR)

            val methodId = getApiMethodId(methodStr)
            val school = if (madhabStr == "HANAFI") 1 else 0
            val timeZoneString = URLEncoder.encode(calendar.timeZone.id, Charsets.UTF_8.name())

            val url = buildString {
                append("$apiBaseUrl/timings/")
                append("$day-$month-$year")
                append("?latitude=$lat")
                append("&longitude=$lng")
                append("&method=$methodId")
                if (methodId == 99) {
                    val params = getCalculationParameters(methodStr)
                    append("&methodSettings=${params.fajrAngle},null,${params.ishaAngle}")
                }
                append("&school=$school")
                append("&timezonestring=$timeZoneString")
                if (methodStr == "MOON_SIGHTING_COMMITTEE") {
                    append("&shafaq=general")
                }
            }
            lastApiError = null
            val result = apiCache.get(url, cacheDirectory) {
                coroutineContext.ensureActive()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    coroutineContext.ensureActive()
                    if (!response.isSuccessful) {
                        lastApiError = "Prayer service returned HTTP ${response.code}"
                        null
                    } else {
                        val body = response.body?.string()
                        val parsed = body?.let { json.decodeFromString<AlAdhanResponse>(it) }
                        val timings = parsed?.data?.timings
                        val required = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
                        val validTime = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9](?: .*|)$")
                        if (parsed?.code == 200 && timings != null &&
                            required.all { validTime.matches(timings[it].orEmpty()) }) timings
                        else {
                            lastApiError = "Prayer service returned incomplete or invalid timings"
                            null
                        }
                    }
                }
            }
            if (result == null && lastApiError == null) lastApiError = "Prayer service unavailable"
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            lastApiError = "Prayer service unavailable; using an approximate local fallback"
            null
        }
    }

    private fun getCalculationParameters(methodStr: String): CalculationParameters {
        return when (methodStr) {
            "SHIA_ITHNA_ASHARI" -> CalculationParameters(fajrAngle = 16.0, ishaAngle = 14.0)
            "MUSLIM_WORLD_LEAGUE" -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
            "EGYPTIAN" -> CalculationMethod.EGYPTIAN.parameters
            "KARACHI" -> CalculationMethod.KARACHI.parameters
            "UMM_AL_QURA" -> CalculationMethod.UMM_AL_QURA.parameters
            "DUBAI" -> CalculationMethod.DUBAI.parameters
            "MOON_SIGHTING_COMMITTEE" -> CalculationMethod.MOON_SIGHTING_COMMITTEE.parameters
            "NORTH_AMERICA" -> CalculationMethod.NORTH_AMERICA.parameters
            "KUWAIT" -> CalculationMethod.KUWAIT.parameters
            "QATAR" -> CalculationMethod.QATAR.parameters
            "SINGAPORE" -> CalculationMethod.SINGAPORE.parameters
            "ALGERIA" -> CalculationParameters(fajrAngle = 18.0, ishaAngle = 17.0)
            "TUNISIA" -> CalculationParameters(fajrAngle = 18.0, ishaAngle = 18.0)
            "FRANCE_UOIF" -> CalculationParameters(fajrAngle = 12.0, ishaAngle = 12.0)
            "FRANCE_15" -> CalculationParameters(fajrAngle = 15.0, ishaAngle = 15.0)
            "FRANCE_18" -> CalculationParameters(fajrAngle = 18.0, ishaAngle = 17.0)
            "TEHRAN" -> CalculationMethod.OTHER.parameters
            "TURKEY" -> CalculationMethod.OTHER.parameters
            "RUSSIA" -> CalculationParameters(fajrAngle = 16.0, ishaAngle = 15.0)
            "MALAYSIA" -> CalculationParameters(fajrAngle = 18.0, ishaAngle = 18.0)
            "INDONESIA" -> CalculationParameters(fajrAngle = 20.0, ishaAngle = 18.0)
            "MOROCCO" -> CalculationParameters(fajrAngle = 19.0, ishaAngle = 17.0)
            "JORDAN" -> CalculationParameters(fajrAngle = 18.0, ishaAngle = 18.0)
            "GULF_REGION" -> CalculationParameters(fajrAngle = 19.5, ishaAngle = 90.0)
            "PORTUGAL" -> CalculationParameters(fajrAngle = 18.0, ishaAngle = 15.0)
            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
        }
    }

    private fun getApiMethodId(methodStr: String): Int {
        return when (methodStr) {
            "SHIA_ITHNA_ASHARI" -> 0
            "KARACHI" -> 1
            "NORTH_AMERICA" -> 2
            "MUSLIM_WORLD_LEAGUE" -> 3
            "UMM_AL_QURA" -> 4
            "EGYPTIAN" -> 5
            "TEHRAN" -> 7
            "GULF_REGION" -> 8
            "KUWAIT" -> 9
            "QATAR" -> 10
            "SINGAPORE" -> 11
            "FRANCE_UOIF" -> 12
            "TURKEY" -> 13
            "RUSSIA" -> 14
            "MOON_SIGHTING_COMMITTEE" -> 15
            "DUBAI" -> 16
            "MALAYSIA" -> 17
            "TUNISIA" -> 18
            "ALGERIA" -> 19
            "INDONESIA" -> 20
            "MOROCCO" -> 21
            "PORTUGAL" -> 22
            "JORDAN" -> 23
            else -> 99 // Fallback to custom for others or default
        }
    }
}
