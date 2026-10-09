package com.kaizen.khushu

import com.kaizen.khushu.data.repository.*
import com.kaizen.khushu.notifications.toPrayerNotificationScheduleConfig
import java.nio.file.Files
import java.time.Instant
import java.util.Date
import java.util.TimeZone
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class JakimTimetableRepositoryTest {
    private val date = Date.from(Instant.parse("2026-10-08T16:30:00Z")) // 9 Oct in Malaysia
    private fun fixture(name: String = "WLY01-2026-10") = javaClass.getResource("/jakim/$name.json")!!.readText()
    private fun settings() = UserSettings(
        hapticsEnabled = true, dynamicColor = true, pureBlack = false, keepScreenAwake = true,
        volumeCounting = false, themeMode = "System", showStepTimer = true, fluidTransitions = true,
        vibrationOnCount = true, showLapCounter = true, showExitButton = true, showCompletionText = true,
        completionText = "", colorSeed = "default", tasbeehListMode = true, startupTab = "home",
        tasbihBeadStyle = "CLASSIC_AMBER", prayerSourceType = "JAKIM", jakimZone = "WLY01",
    )

    @Test fun officialEntriesUseMalaysiaDateAndDoNotSendCoordinates() = runBlocking {
        val server = MockWebServer(); server.start()
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            server.enqueue(MockResponse().setBody(fixture()))
            val repository = JakimTimetableRepository(null, OkHttpClient(), server.url("/index.php").toString())
            val result = repository.get(date, "WLY01")
            assertEquals(Instant.parse("2026-10-08T21:51:00Z").toEpochMilli(), result.times!!.getValue("Fajr").time)
            assertEquals(Instant.parse("2026-10-09T11:04:00Z").toEpochMilli(), result.times.getValue("Maghrib").time)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("WLY01", request.requestUrl!!.queryParameter("zone"))
            assertEquals("duration", request.requestUrl!!.queryParameter("period"))
            assertNull(request.requestUrl!!.queryParameter("latitude"))
            assertNull(request.requestUrl!!.queryParameter("longitude"))
            assertEquals("datestart=2026-10-01&dateend=2026-10-31", request.body.readUtf8())
            assertTrue(result.notice.startsWith("Official JAKIM"))
        } finally { TimeZone.setDefault(original); server.shutdown() }
    }

    @Test fun monthPersistsOfflineAndConcurrentDaysShareOneFetch() = runBlocking {
        val directory = Files.createTempDirectory("jakim-offline").toFile()
        val server = MockWebServer(); server.start()
        var clock = 1_791_504_000_000L
        try {
            server.enqueue(MockResponse().setBody(fixture()))
            val endpoint = server.url("/index.php").toString()
            val repository = JakimTimetableRepository(directory, OkHttpClient(), endpoint) { clock }
            (1..8).map { day -> async { repository.get(Date.from(Instant.parse("2026-10-${day.toString().padStart(2, '0')}T04:00:00Z")), "WLY01") } }.awaitAll()
            assertEquals(1, server.requestCount)
            clock += 8 * 86_400_000L
            server.enqueue(MockResponse().setResponseCode(503))
            val reopened = JakimTimetableRepository(directory, OkHttpClient(), endpoint) { clock }
            val saved = reopened.get(date, "WLY01")
            assertEquals(Instant.parse("2026-10-08T21:51:00Z").toEpochMilli(), saved.times!!.getValue("Fajr").time)
            assertTrue(saved.notice.startsWith("Cached official"))
            assertTrue(saved.notice.contains("Revision check unavailable"))
            reopened.get(date, "WLY01")
            assertEquals(2, server.requestCount) // failed checks are throttled
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    @Test fun zonesAndMonthsNeverReuseAnotherTimetable() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            for (name in listOf("WLY01-2026-10", "JHR02-2026-10", "WLY01-2026-01"))
                server.enqueue(MockResponse().setBody(fixture(name)))
            val repository = JakimTimetableRepository(null, OkHttpClient(), server.url("/index.php").toString())
            val kl = repository.get(date, "WLY01").times!!.getValue("Fajr")
            val johor = repository.get(date, "JHR02").times!!.getValue("Fajr")
            assertEquals(9 * 60_000L, kl.time - johor.time)
            val january = repository.get(Date.from(Instant.parse("2026-01-09T04:00:00Z")), "WLY01")
            assertEquals(Instant.parse("2026-01-08T22:10:00Z").toEpochMilli(), january.times!!.getValue("Fajr").time)
            assertEquals(3, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun rejectsWrongZoneDateMissingDaysInvalidTimeAndCorruptDisk() = runBlocking {
        val invalid = listOf(
            fixture().replace("WLY01", "JHR02"), fixture().replace("01-Okt-2026", "01-Okt-2025"),
            fixture().replace("05:53:00", "99:99:00"), "{\"status\":\"OK!\",\"zone\":\"WLY01\",\"prayerTime\":[]}", "not JSON",
        )
        for (body in invalid) {
            val directory = Files.createTempDirectory("jakim-invalid").toFile()
            directory.resolve("WLY01-2026-10.json").writeText("corrupt")
            val server = MockWebServer(); server.start()
            try {
                server.enqueue(MockResponse().setBody(body))
                val repository = JakimTimetableRepository(directory, OkHttpClient(), server.url("/index.php").toString())
                val result = repository.get(date, "WLY01")
                assertNull(result.times)
                assertTrue(result.notice.contains("approximate local fallback"))
                assertEquals("corrupt", directory.resolve("WLY01-2026-10.json").readText())
            } finally { server.shutdown(); directory.deleteRecursively() }
        }
    }

    @Test fun explicitSelectionOffsetsFallbackAndReminderAvailability() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody(fixture()))
            val jakim = JakimTimetableRepository(null, OkHttpClient(), server.url("/official").toString())
            val repository = PrayerTimeRepository(null, OkHttpClient(), server.url("/aladhan").toString(), jakim)
            val unselected = settings().copy(jakimZone = "")
            assertFalse(repository.hasOfficialTimetable(date, unselected))
            assertTrue(repository.getPrayerDataNotice(date, unselected)!!.summary.contains("Choose your prayer zone"))
            assertEquals(0, server.requestCount)
            val times = repository.getEffectivePrayerDateTimes(date, settings())
            assertEquals(5, times.size)
            assertEquals(Instant.parse("2026-10-08T21:51:00Z").toEpochMilli(), times.getValue("Fajr").time)
            assertTrue(repository.hasOfficialTimetable(date, settings()))
            val adjusted = settings().copy(fajrOffsetMinutes = 3)
            assertEquals(times.getValue("Fajr").time + 180_000, repository.getEffectivePrayerDateTimes(date, adjusted).getValue("Fajr").time)
            assertTrue(repository.getPrayerDataNotice(date, adjusted)!!.details.contains("Manual offsets"))
            assertEquals(1, server.requestCount)
            assertNotEquals(settings().toPrayerNotificationScheduleConfig(), settings().copy(jakimZone = "JHR02").toPrayerNotificationScheduleConfig())
            assertNotEquals(settings().toPrayerNotificationScheduleConfig(), settings().copy(lastPrayerRefreshEpochMs = 42).toPrayerNotificationScheduleConfig())
            val fallback = repository.getEffectivePrayerDateTimes(date, unselected)
            val local = repository.getEffectivePrayerDateTimes(date, unselected.copy(prayerSourceType = "LOCAL"))
            assertEquals(local, fallback)
        } finally { server.shutdown() }
    }

    @Test fun manualRefreshChecksRevisionsAndMalaysiaDayAdvancementIsStable() = runBlocking {
        val server = MockWebServer(); server.start()
        val original = TimeZone.getDefault()
        try {
            server.enqueue(MockResponse().setBody(fixture()))
            server.enqueue(MockResponse().setBody(fixture()))
            val jakim = JakimTimetableRepository(null, OkHttpClient(), server.url("/official").toString())
            val repository = PrayerTimeRepository(null, OkHttpClient(), server.url("/aladhan").toString(), jakim)
            repository.getEffectivePrayerDateTimes(date, settings())
            repository.refreshOfficialTimetable(date, settings())
            assertEquals(2, server.requestCount)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals("2026-10-10", JakimTimetableRepository.localDate(nextPrayerDate(date, settings())).toString())
        } finally { TimeZone.setDefault(original); server.shutdown() }
    }

    @Test fun unconfirmedAutomaticZoneCannotReusePriorOfficialTimesOrReminders() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val jakim = JakimTimetableRepository(null, OkHttpClient(), server.url("/official").toString())
            val repository = PrayerTimeRepository(null, OkHttpClient(), server.url("/aladhan").toString(), jakim)
            val pending = settings().copy(automaticJakimZone = true, automaticJakimZoneConfirmed = false,
                automaticJakimZoneNotice = "Checking the prayer zone for this GPS fix…")
            assertEquals("", pending.effectiveJakimZone)
            assertFalse(repository.hasOfficialTimetable(date, pending))
            assertEquals(repository.getEffectivePrayerDateTimes(date, pending.copy(prayerSourceType = "LOCAL")),
                repository.getEffectivePrayerDateTimes(date, pending))
            assertTrue(repository.getPrayerDataNotice(date, pending)!!.details.contains("Checking the prayer zone"))
            assertEquals(0, server.requestCount)
            val confirmed = pending.copy(automaticJakimZoneConfirmed = true)
            assertNotEquals(pending.toPrayerNotificationScheduleConfig(), confirmed.toPrayerNotificationScheduleConfig())
            server.enqueue(MockResponse().setBody(fixture()))
            assertTrue(repository.hasOfficialTimetable(date, confirmed))
            assertEquals("WLY01", server.takeRequest().requestUrl!!.queryParameter("zone"))
            assertEquals("WLY01", pending.copy(automaticJakimZone = false).effectiveJakimZone)
        } finally { server.shutdown() }
    }
    @Test fun sourceCardsDistinguishOfficialPendingAndUnavailableTimes() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody(fixture()))
            server.enqueue(MockResponse().setResponseCode(503))
            val jakim = JakimTimetableRepository(null, OkHttpClient(), server.url("/official").toString())
            val repository = PrayerTimeRepository(null, OkHttpClient(), server.url("/aladhan").toString(), jakim)
            val official = repository.getPrayerDataNotice(date, settings())!!
            assertFalse(official.approximate)
            assertEquals("Official JAKIM times · WLY01", official.title)
            val cached = repository.getPrayerDataNotice(date, settings())!!
            assertFalse(cached.approximate)
            assertTrue(cached.details.contains("Cached official"))
            val adjusted = repository.getPrayerDataNotice(date, settings().copy(fajrOffsetMinutes = 3))!!
            assertTrue(adjusted.summary.contains("adjustments"))
            val pending = repository.getPrayerDataNotice(date, settings().copy(
                automaticJakimZone = true, automaticJakimZoneConfirmed = false,
                automaticJakimZoneNotice = "Checking the prayer zone for this GPS fix…"))!!
            assertTrue(pending.approximate)
            assertTrue(pending.summary.contains("approximate"))
            assertFalse(pending.details.contains("Choose an official"))
            assertTrue(pending.details.contains("Checking"))
            val unavailable = repository.getPrayerDataNotice(date, settings().copy(jakimZone = "JHR02"))!!
            assertTrue(unavailable.approximate)
            assertTrue(unavailable.summary.contains("unavailable"))
            assertTrue(unavailable.details.contains("JHR02"))
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }

}
