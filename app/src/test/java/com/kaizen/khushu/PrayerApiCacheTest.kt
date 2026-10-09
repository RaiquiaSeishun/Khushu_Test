package com.kaizen.khushu

import com.kaizen.khushu.data.repository.PrayerApiCache
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PrayerApiCacheTest {
    private val timings = mapOf("Fajr" to "05:50", "Dhuhr" to "13:10")

    @Test fun concurrentRequestsShareOneFetch() = runBlocking {
        val cache = PrayerApiCache()
        var requests = 0
        val results = (1..10).map {
            async { cache.get("same-request", null) { requests++; delay(10); timings } }
        }.awaitAll()
        assertEquals(1, requests)
        assertTrue(results.all { it == timings })
    }

    @Test fun concurrentFailuresShareOneFetchWithoutPreventingRetry() = runBlocking {
        val cache = PrayerApiCache()
        var requests = 0
        val results = (1..10).map {
            async { cache.get("same-request", null) { requests++; delay(20); null } }
        }.awaitAll()
        assertEquals(1, requests)
        assertTrue(results.all { it == null })
        assertEquals(timings, cache.get("same-request", null) { timings })
    }

    @Test fun cancellationClearsPendingRequest() = runBlocking {
        val cache = PrayerApiCache()
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            cache.get("key", null) { entered.complete(Unit); delay(Long.MAX_VALUE); timings }
        }
        entered.await()
        job.cancelAndJoin()
        assertEquals(timings, cache.get("key", null) { timings })
    }

    @Test fun changedInputsRequireNewFetch() = runBlocking {
        val cache = PrayerApiCache()
        var requests = 0
        val base = "date=2026-10-09&lat=3.139&lng=101.687&method=17&school=0&timezone=Asia/Kuala_Lumpur"
        val urls = listOf(base, base.replace("09", "10"), base.replace("3.139", "3.140"),
            base.replace("101.687", "102.0"), base.replace("method=17", "method=3"),
            base.replace("school=0", "school=1"), base.replace("Asia/Kuala_Lumpur", "UTC"))
        urls.forEach { cache.get(it, null) { requests++; timings } }
        urls.forEach { cache.get(it, null) { requests++; timings } }
        assertEquals(urls.size, requests)
    }

    @Test fun expiresEntriesAndDoesNotCacheFailures() = runBlocking {
        var now = 1_000L
        val cache = PrayerApiCache { now }
        assertNull(cache.get("key", null) { null })
        assertEquals(timings, cache.get("key", null) { timings })
        now += 24 * 60 * 60_000L
        var requests = 0
        assertEquals(timings, cache.get("key", null) { requests++; timings })
        assertEquals(1, requests)
        now = 0
        assertNull(cache.get("key", null) { null })
    }

    @Test fun diskCacheWorksOfflineAcrossInstancesAndSurvivesCorruption() = runBlocking {
        val directory = Files.createTempDirectory("prayer-cache-test").toFile()
        try {
            assertEquals(timings, PrayerApiCache().get("key", directory) { timings })
            assertEquals(timings, PrayerApiCache().get("key", directory) { fail("Network used for cached request"); null })
            directory.listFiles()!!.forEach { it.writeText("broken cache") }
            assertEquals(timings, PrayerApiCache().get("key", directory) { timings })
        } finally { directory.deleteRecursively() }
    }
}
