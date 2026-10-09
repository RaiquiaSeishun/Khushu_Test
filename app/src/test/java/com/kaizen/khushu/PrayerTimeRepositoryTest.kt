package com.kaizen.khushu

import com.kaizen.khushu.data.repository.PrayerTimeRepository
import com.kaizen.khushu.data.repository.UserSettings
import com.kaizen.khushu.data.repository.nextPrayerDate
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class PrayerTimeRepositoryTest {
    private val response = """{"code":200,"data":{"timings":{"Fajr":"05:50","Dhuhr":"13:10","Asr":"16:20","Maghrib":"19:00","Isha":"20:10"}}}"""

    @Test fun cachesRealHttpResponsesAndSeparatesMethods() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody(response))
            server.enqueue(MockResponse().setBody(response))
            val repository = PrayerTimeRepository(null, OkHttpClient(), server.url("/v1").toString())
            val date = Date(1_791_504_000_000L)
            assertEquals("05:50", repository.getFallbackPrayerTimes(date, 3.139, 101.687, "MALAYSIA", "SHAFI")?.get("Fajr"))
            repository.getFallbackPrayerTimes(date, 3.139, 101.687, "MALAYSIA", "SHAFI")
            repository.getFallbackPrayerTimes(date, 3.139, 101.687, "KARACHI", "HANAFI")
            assertEquals(2, server.requestCount)
            val first = server.takeRequest().requestUrl!!
            assertEquals("17", first.queryParameter("method"))
            assertEquals("0", first.queryParameter("school"))
            assertEquals("3.139", first.queryParameter("latitude"))
            assertEquals(TimeZone.getDefault().id, first.queryParameter("timezonestring"))
            val second = server.takeRequest().requestUrl!!
            assertEquals("1", second.queryParameter("method"))
            assertEquals("1", second.queryParameter("school"))
        } finally { server.shutdown() }
    }

    @Test fun rejectsHttpErrorsAndInvalidResponsesThenAllowsRetry() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody("""{"code":200,"data":{"timings":{"Fajr":"99:99"}}}"""))
            server.enqueue(MockResponse().setBody("not JSON"))
            server.enqueue(MockResponse().setBody(response))
            val repository = PrayerTimeRepository(null, OkHttpClient(), server.url("/v1").toString())
            repeat(3) {
                assertNull(repository.getFallbackPrayerTimes(Date(0), 3.139, 101.687, "MALAYSIA", "SHAFI"))
                assertNotNull(repository.lastApiError)
            }
            assertNotNull(repository.getFallbackPrayerTimes(Date(0), 3.139, 101.687, "MALAYSIA", "SHAFI"))
            assertNull(repository.lastApiError)
            assertEquals(4, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun unsupportedLocalConventionNeverContactsServer() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val repository = PrayerTimeRepository(null, OkHttpClient(), server.url("/v1").toString())
            val settings = UserSettings(
                hapticsEnabled = true, dynamicColor = true, pureBlack = false,
                keepScreenAwake = true, volumeCounting = false, themeMode = "System",
                showStepTimer = true, fluidTransitions = true, vibrationOnCount = true,
                showLapCounter = true, showExitButton = true, showCompletionText = true,
                completionText = "", colorSeed = "default", tasbeehListMode = true,
                startupTab = "home", tasbihBeadStyle = "CLASSIC_AMBER",
                prayerSourceType = "LOCAL", prayerCalculationMethod = "MALAYSIA",
            )
            assertFalse(repository.usesApiSource(settings))
            val local = repository.getEffectivePrayerDateTimes(Date(0), settings)
            val expected = repository.getEffectivePrayerDateTimes(Date(0), settings.copy(prayerCalculationMethod = "MUSLIM_WORLD_LEAGUE"))
            assertEquals(expected, local)
            assertEquals(5, local.size)
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun calendarDayAdvancesAcrossDaylightSavingAndYearBoundary() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            for ((year, month, day, hours) in listOf(
                listOf(2026, Calendar.MARCH, 7, 23),
                listOf(2026, Calendar.OCTOBER, 31, 25),
                listOf(2026, Calendar.DECEMBER, 31, 24),
            )) {
                val start = Calendar.getInstance().apply { clear(); set(year, month, day, 12, 0) }
                val next = nextPrayerDate(start.time)
                assertEquals(hours * 3_600_000L, next.time - start.timeInMillis)
                assertEquals(12, Calendar.getInstance().apply { time = next }.get(Calendar.HOUR_OF_DAY))
            }
        } finally { TimeZone.setDefault(original) }
    }
}
