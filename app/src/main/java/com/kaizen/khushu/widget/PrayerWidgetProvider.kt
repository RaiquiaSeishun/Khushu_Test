package com.kaizen.khushu.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Geocoder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import com.kaizen.khushu.MainActivity
import com.kaizen.khushu.R
import com.kaizen.khushu.data.repository.PrayerTimeRepository
import com.kaizen.khushu.data.repository.SettingsRepository
import com.kaizen.khushu.data.repository.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch

class PrayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PrayerWidgetProvider"

        suspend fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_prayer_times)

            // 0. OPEN APP ON CLICK
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            val settingsRepository = SettingsRepository(context)
            val prayerTimeRepository = PrayerTimeRepository(settingsRepository)

            try {
                    val settings = settingsRepository.settingsFlow.first()
                    val nowMs = System.currentTimeMillis()
                    val nowDate = Date(nowMs)
                    val cal = Calendar.getInstance().apply { time = nowDate }
                    val isFriday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY

                    // 1. DATES
                    val gregorianDate = SimpleDateFormat("EEE, d MMMM", Locale.getDefault()).format(nowDate)
                    val hijriDate = runCatching {
                        val todayHijri = java.time.chrono.HijrahDate.now()
                        val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
                        todayHijri.format(formatter)
                    }.getOrDefault("Hijri Date")
                    
                    views.setTextViewText(R.id.text_gregorian_date, gregorianDate)
                    views.setTextViewText(R.id.text_hijri_date, hijriDate)

                    // 2. LOCATION
                    val locationName = settings.locationLabel.ifBlank { resolveLocationLabel(context, settings) }
                    views.setTextViewText(R.id.text_location, if (settings.prayerSourceType == "JAKIM") {
                        if (prayerTimeRepository.hasOfficialTimetable(nowDate, settings)) "JAKIM ${settings.jakimZone}"
                        else "Approximate fallback · JAKIM unavailable"
                    } else locationName)

                    // 3. PRAYER TIMES
                    val effectiveTimes = prayerTimeRepository.getEffectivePrayerDateTimes(nowDate, settings)
                    val extraTimes = prayerTimeRepository.getExtraPrayerDateTimes(nowDate, settings)
                    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

                    val prayers = listOf("Fajr", "Shuruq", "Dhuhr", "Asr", "Maghrib", "Isha")
                    val prayerIds = mapOf(
                        "Fajr" to R.id.time_fajr,
                        "Shuruq" to R.id.time_shuruq,
                        "Dhuhr" to R.id.time_dhuhr,
                        "Asr" to R.id.time_asr,
                        "Maghrib" to R.id.time_maghrib,
                        "Isha" to R.id.time_isha
                    )

                    prayers.forEach { name ->
                        val time = if (name == "Shuruq") extraTimes["SUNRISE"] else effectiveTimes[name]
                        views.setTextViewText(prayerIds[name]!!, time?.let { timeFormatter.format(it) } ?: "--:--")
                        
                        // Rename Dhuhr to Jumaah on Fridays
                        if (name == "Dhuhr") {
                            views.setTextViewText(R.id.label_dhuhr, if (isFriday) "Jumaah" else "Dhuhr")
                        }
                    }

                    // 4. ACTIVE PRAYER & TICKING COUNTDOWN
                    val orderedTimes = prayers.mapNotNull { name ->
                        val time = if (name == "Shuruq") extraTimes["SUNRISE"] else effectiveTimes[name]
                        time?.let { name to it.time }
                    }
                    
                    val currentPrayer = orderedTimes.lastOrNull { it.second <= nowMs }?.first ?: "Isha"
                    
                    val nextToday = orderedTimes.firstOrNull { it.second > nowMs }
                    val nextPrayerName: String
                    val nextPrayerTimeMs: Long

                    if (nextToday != null) {
                        nextPrayerName = if (nextToday.first == "Dhuhr" && isFriday) "Jumaah" else nextToday.first
                        nextPrayerTimeMs = nextToday.second
                    } else {
                        nextPrayerName = "Fajr"
                        val tomorrow = com.kaizen.khushu.data.repository.nextPrayerDate(nowDate, settings)
                        val tomorrowTimes = prayerTimeRepository.getEffectivePrayerDateTimes(tomorrow, settings)
                        nextPrayerTimeMs = tomorrowTimes["Fajr"]?.time ?: 0L
                    }

                    // Setup Chronometer for ticking countdown
                    if (nextPrayerTimeMs > nowMs) {
                        val base = SystemClock.elapsedRealtime() + (nextPrayerTimeMs - nowMs)
                        views.setChronometer(R.id.text_countdown, base, "$nextPrayerName in %s", true)
                        views.setChronometerCountDown(R.id.text_countdown, true)
                    } else {
                        views.setChronometer(R.id.text_countdown, 0L, "$nextPrayerName in --:--", false)
                    }

                    // 5. CUSTOMIZATION (Colors & Transparency)
                    applyCustomization(views, settings)

                    resetPrayerStyles(views, settings)
                    highlightActivePrayer(views, currentPrayer, settings)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating app widget", e)
                }
        }

        private fun applyCustomization(views: RemoteViews, settings: UserSettings) {
            // Layer 1: Total background
            val bgColor = Color.parseColor(settings.widgetBackgroundColor)
            views.setInt(R.id.img_widget_bg, "setColorFilter", bgColor)
            views.setInt(R.id.img_widget_bg, "setImageAlpha", (settings.widgetBackgroundOpacity * 255).toInt())

            // Layer 2: Inner panel background
            val panelColor = Color.parseColor(settings.widgetPanelColor)
            views.setInt(R.id.img_panel_bg, "setColorFilter", panelColor)
            views.setInt(R.id.img_panel_bg, "setImageAlpha", (settings.widgetPanelOpacity * 255).toInt())
            
            // Apply font color to static texts and icons
            val fontColor = Color.parseColor(settings.widgetFontColor)
            val subFontColor = (0x99 shl 24) or (fontColor and 0x00FFFFFF)
            
            views.setTextColor(R.id.text_gregorian_date, fontColor)
            views.setTextColor(R.id.text_hijri_date, fontColor)
            views.setTextColor(R.id.text_location, subFontColor)
            views.setTextColor(R.id.text_countdown, subFontColor)
            
            // Tint Icons
            views.setInt(R.id.icon_star, "setColorFilter", fontColor)
            views.setInt(R.id.icon_location, "setColorFilter", subFontColor)
        }

        private fun resetPrayerStyles(views: RemoteViews, settings: UserSettings) {
            val times = arrayOf(R.id.time_fajr, R.id.time_shuruq, R.id.time_dhuhr, R.id.time_asr, R.id.time_maghrib, R.id.time_isha)
            val labels = arrayOf(R.id.label_fajr, R.id.label_shuruq, R.id.label_dhuhr, R.id.label_asr, R.id.label_maghrib, R.id.label_isha)

            val fontColor = Color.parseColor(settings.widgetFontColor)
            val semiFontColor = (0xB3 shl 24) or (fontColor and 0x00FFFFFF)

            for (timeId in times) {
                views.setInt(timeId, "setBackgroundResource", 0)
                views.setTextColor(timeId, semiFontColor)
            }
            for (labelId in labels) {
                views.setTextColor(labelId, semiFontColor)
            }
        }

        private fun highlightActivePrayer(views: RemoteViews, prayer: String, settings: UserSettings) {
            val labelId = when (prayer) {
                "Fajr" -> R.id.label_fajr
                "Shuruq" -> R.id.label_shuruq
                "Dhuhr" -> R.id.label_dhuhr
                "Asr" -> R.id.label_asr
                "Maghrib" -> R.id.label_maghrib
                "Isha" -> R.id.label_isha
                else -> null
            }
            val timeId = when (prayer) {
                "Fajr" -> R.id.time_fajr
                "Shuruq" -> R.id.time_shuruq
                "Dhuhr" -> R.id.time_dhuhr
                "Asr" -> R.id.time_asr
                "Maghrib" -> R.id.time_maghrib
                "Isha" -> R.id.time_isha
                else -> null
            }

            if (labelId != null && timeId != null) {
                val fontColor = Color.parseColor(settings.widgetFontColor)
                views.setTextColor(labelId, fontColor)
                views.setInt(timeId, "setBackgroundResource", R.drawable.widget_active_pill)
                views.setTextColor(timeId, Color.BLACK)
            }
        }

        @Suppress("DEPRECATION")
        private fun resolveLocationLabel(context: Context, settings: UserSettings): String {
            return runCatching {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val results = mutableListOf<android.location.Address>()
                    val latch = CountDownLatch(1)
                    geocoder.getFromLocation(
                        settings.locationLat.toDouble(),
                        settings.locationLng.toDouble(),
                        1
                    ) { found ->
                        results += found
                        latch.countDown()
                    }
                    latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                    results
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(
                        settings.locationLat.toDouble(),
                        settings.locationLng.toDouble(),
                        1
                    ).orEmpty()
                }

                val best = addresses.firstOrNull()
                listOfNotNull(
                    best?.locality?.takeIf { it.isNotBlank() },
                    best?.subAdminArea?.takeIf { it.isNotBlank() },
                    best?.adminArea?.takeIf { it.isNotBlank() }
                ).firstOrNull()
            }.getOrNull() ?: "Your Area"
        }

        fun forceWidgetRefresh(context: Context) {
            val intent = Intent(context, PrayerWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                    ComponentName(context, PrayerWidgetProvider::class.java)
                )
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
