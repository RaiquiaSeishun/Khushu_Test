package com.kaizen.khushu

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaizen.khushu.data.repository.*
import com.kaizen.khushu.logic.PrayerManager
import com.kaizen.khushu.ui.screens.home.CalculationSource
import com.kaizen.khushu.ui.screens.home.HomeViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class HomeRefreshInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private class TimetableSource : Interceptor {
        val requests = AtomicInteger()
        val block = AtomicBoolean()
        val fail = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        override fun intercept(chain: Interceptor.Chain): Response {
            requests.incrementAndGet()
            if (block.get()) {
                entered.countDown()
                check(release.await(15, TimeUnit.SECONDS)) { "Test failed to release request" }
            }
            val request = chain.request()
            val body = request.body as FormBody
            val month = YearMonth.from(LocalDate.parse(body.value(0)))
            val rows = JSONArray()
            for (day in 1..month.lengthOfMonth()) rows.put(JSONObject().apply {
                put("date", month.atDay(day).format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)))
                for ((key, time) in mapOf("imsak" to "05:40:00", "fajr" to "05:50:00", "syuruk" to "07:00:00",
                    "dhuhr" to "13:10:00", "asr" to "16:30:00", "maghrib" to "19:00:00", "isha" to "20:10:00")) put(key, time)
            })
            val json = JSONObject().put("status", "OK!").put("zone", "WLY01").put("prayerTime", rows).toString()
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (fail.get()) 503 else 200).message("test response")
                .body(json.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private fun withHome(check: (HomeViewModel, TimetableSource, SettingsRepository) -> Unit) {
        val settings = SettingsRepository(context)
        val original = runBlocking { settings.settingsFlow.first() }
        val source = TimetableSource()
        val client = OkHttpClient.Builder().addInterceptor(source).build()
        val jakim = JakimTimetableRepository(null, client)
        val prayer = PrayerTimeRepository(null, client, jakim = jakim)
        val store = ViewModelStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var home: HomeViewModel
        try {
            runBlocking {
                settings.updateAutomaticJakimZone(false)
                settings.updateJakimZone("WLY01")
                settings.updatePrayerSourceType("JAKIM")
                // Seed current and next day so reactive UI reads never make network requests.
                val saved = settings.settingsFlow.first()
                prayer.hasOfficialTimetable(Date(), saved)
                prayer.hasOfficialTimetable(nextPrayerDate(Date(), saved), saved)
            }
            compose.runOnIdle {
                home = HomeViewModel(settings, prayer, IslamicEventsRepository(context), PrayerManager(settings, prayer, scope))
                store.put("home", home)
                scope.launch { home.uiState.collect {} }
            }
            compose.waitUntil(15_000) { home.uiState.value.calculationSource == CalculationSource.JAKIM && home.uiState.value.prayers.isNotEmpty() }
            check(home, source, settings)
        } finally {
            source.release.countDown()
            compose.runOnIdle { store.clear(); scope.cancel() }
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            runBlocking {
                settings.updateJakimZone(original.jakimZone)
                settings.updateAutomaticJakimZone(original.automaticJakimZone)
                if (original.automaticJakimZone) settings.applyAutomaticJakimZone(original.lastLocationFixEpochMs,
                    original.locationLat, original.locationLng, JakimZoneLocator.Result(
                        original.jakimZone.takeIf { original.automaticJakimZoneConfirmed }, original.automaticJakimZoneNotice))
                settings.updatePrayerSourceType(original.prayerSourceType)
                settings.updateLastPrayerRefresh(original.lastPrayerRefreshEpochMs)
            }
        }
    }

    @Test fun successfulOfficialRefreshStopsAndRepeatedPullsShareOneRequest() = withHome { home, source, settings ->
        val baseline = source.requests.get()
        source.block.set(true)
        compose.runOnIdle { home.refreshPrayerData(); assertTrue(home.isRefreshing.value); home.refreshPrayerData() }
        assertTrue(source.entered.await(10, TimeUnit.SECONDS))
        compose.runOnIdle { home.refreshPrayerData() }
        assertEquals(baseline + 1, source.requests.get())
        source.release.countDown()
        compose.waitUntil(15_000) { !home.isRefreshing.value && !home.uiState.value.isRefreshing }
        assertEquals(baseline + 1, source.requests.get())
        assertTrue(runBlocking { settings.settingsFlow.first().lastPrayerRefreshEpochMs } > 0)
        // Once finished, another pull must be permitted.
        compose.runOnIdle { home.refreshPrayerData(); assertTrue(home.isRefreshing.value) }
        compose.waitUntil(15_000) { !home.isRefreshing.value }
        assertEquals(baseline + 2, source.requests.get())
    }

    @Test fun failedOfficialRefreshStopsAndRetainsTheCachedTimetable() = withHome { home, source, _ ->
        val previous = home.uiState.value.prayers.map { it.name to it.time }
        source.fail.set(true)
        compose.runOnIdle { home.refreshPrayerData(); assertTrue(home.isRefreshing.value) }
        compose.waitUntil(15_000) { !home.isRefreshing.value && !home.uiState.value.isRefreshing }
        compose.runOnIdle { assertEquals(previous, home.uiState.value.prayers.map { it.name to it.time }) }
    }
}
