package com.kaizen.khushu

import com.kaizen.khushu.data.repository.JakimZoneLocator
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class JakimZoneLocatorTest {
    // Recorded live response for a public Kulim coordinate, 2026-10-09.
    private val kulim = """{"zone":"KDH05","state":"KDH","district":"Kulim"}"""
    private fun enqueueFive(server: MockWebServer) = repeat(5) { server.enqueue(MockResponse().setBody(kulim)) }

    @Test fun validLocationChecksUncertaintyAndPersistsOnlyItsOwnZone() = runBlocking {
        val server = MockWebServer(); server.start()
        val directory = Files.createTempDirectory("gps-zone-cache").toFile()
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            enqueueFive(server)
            val file = java.io.File(directory, "zone.json")
            val locator = JakimZoneLocator(file, OkHttpClient(), server.url("/zones/").toString()) { 100_000L }
            val result = locator.locate(5.364, 100.561, 20f)
            assertEquals("KDH05", result.zone)
            assertEquals(5, server.requestCount)
            val first = server.takeRequest()
            assertEquals("/zones/5.364000/100.561000", first.path)
            assertEquals("GET", first.method)
            assertEquals(0, first.body.size)
            assertNull(first.requestUrl!!.query)
            val surrounding = (1..4).map { server.takeRequest().path!! }.toSet()
            assertEquals(4, surrounding.size)
            assertFalse(first.path in surrounding)
            val offline = JakimZoneLocator(file, OkHttpClient(), server.url("/zones/").toString()) { 200_000L }
            assertEquals("KDH05", offline.locate(5.364, 100.561, 20f).zone)
            assertEquals(5, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(500))
            assertNull(offline.locate(3.15, 101.71, 20f).zone) // another location cannot reuse Kulim
        } finally { Locale.setDefault(locale); server.shutdown(); directory.deleteRecursively() }
    }

    @Test fun aZoneBoundaryDoesNotSilentlyChooseEitherTimetable() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody(kulim))
            repeat(4) { server.enqueue(MockResponse().setBody(if (it == 0)
                """{"zone":"PNG01","state":"PNG","district":"Seberang Perai"}""" else kulim)) }
            val result = JakimZoneLocator(null, OkHttpClient(), server.url("/zones/").toString()).locate(5.364, 100.561, 400f)
            assertNull(result.zone)
            assertTrue(result.notice.contains("more than one"))
        } finally { server.shutdown() }
    }

    @Test fun specialAreasNeedManualSelectionInsteadOfDistrictAssumptions() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            for ((zone, district) in listOf("KDH02" to "Yan", "PHG04" to "Bentong",
                "PRK06" to "Larut, Matang dan Selama", "SBH07" to "Ranau", "PHG02" to "Rompin")) {
                server.enqueue(MockResponse().setBody("""{"zone":"$zone","state":"${zone.take(3)}","district":"$district"}"""))
                val result = JakimZoneLocator(null, OkHttpClient(), server.url("/zones/").toString()).locate(5.364, 100.561, 20f)
                assertNull(result.zone)
                assertTrue(result.notice.contains("special"))
            }
            assertEquals(5, server.requestCount) // no false confidence from neighbourhood samples
        } finally { server.shutdown() }
    }

    @Test fun invalidFixesDoNotSendCoordinates() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val locator = JakimZoneLocator(null, OkHttpClient(), server.url("/zones/").toString())
            for ((lat, accuracy) in listOf(Double.NaN to 10f, 91.0 to 10f, 5.364 to 0f, 5.364 to 1_001f, 5.364 to Float.NaN)) {
                assertNull(locator.locate(lat, 100.561, accuracy).zone)
            }
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun invalidResponsesAndExpiredCacheCannotBecomeAConfirmedZone() = runBlocking {
        val server = MockWebServer(); server.start()
        val directory = Files.createTempDirectory("gps-zone-invalid").toFile()
        try {
            val file = java.io.File(directory, "zone.json")
            for (body in listOf("[]", "not json", """{"zone":"BAD01","state":"BAD","district":"Fake"}""",
                """{"zone":"KDH05","state":"PNG","district":"Kulim"}""")) {
                server.enqueue(MockResponse().setBody(body))
                assertNull(JakimZoneLocator(file, OkHttpClient(), server.url("/zones/").toString()).locate(5.364, 100.561, 20f).zone)
                assertFalse(file.exists())
            }
            var time = 100_000L
            val locator = JakimZoneLocator(file, OkHttpClient(), server.url("/zones/").toString()) { time }
            enqueueFive(server)
            assertEquals("KDH05", locator.locate(5.364, 100.561, 20f).zone)
            time += 86_400_001L
            server.enqueue(MockResponse().setResponseCode(503))
            assertNull(locator.locate(5.364, 100.561, 20f).zone)
            file.writeText("broken")
            server.enqueue(MockResponse().setResponseCode(503))
            assertNull(JakimZoneLocator(file, OkHttpClient(), server.url("/zones/").toString()).locate(5.364, 100.561, 20f).zone)
        } finally { server.shutdown(); directory.deleteRecursively() }
    }

    @Test fun cancelledLookupStopsTheNetworkRequest() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job = launch { JakimZoneLocator(null, OkHttpClient(), server.url("/zones/").toString()).locate(5.364, 100.561, 20f) }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            withTimeout(2_000L) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        } finally { server.shutdown() }
    }

    @Test fun providerStateAliasesMatchOfficialFederalAndNegeriSembilanCodes() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            for ((zone, state, district) in listOf(Triple("WLY01", "KUL", "W.P. Kuala Lumpur"),
                Triple("WLY01", "PJY", "W.P. Putrajaya"), Triple("WLY02", "LBN", "Labuan"),
                Triple("NGS03", "NSN", "Seremban"))) {
                repeat(5) { server.enqueue(MockResponse().setBody("""{"zone":"$zone","state":"$state","district":"$district"}""")) }
                assertEquals(zone, JakimZoneLocator(null, OkHttpClient(), server.url("/zones/").toString()).locate(3.15, 101.71, 20f).zone)
            }
        } finally { server.shutdown() }
    }
}
