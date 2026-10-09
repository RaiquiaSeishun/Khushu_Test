package com.kaizen.khushu.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.SystemClock
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kaizen.khushu.data.model.CustomBeadStyle
import com.kaizen.khushu.data.model.DEFAULT_CUSTOM_BEAD_STYLE_ID
import com.kaizen.khushu.data.model.defaultCustomBeadStyle
import com.kaizen.khushu.data.repository.QuranScriptFontRepository
import com.kaizen.khushu.data.repository.SettingsRepository
import com.kaizen.khushu.data.repository.UserSettings
import com.kaizen.khushu.data.repository.JakimZoneLocator
import com.kaizen.khushu.util.AppIconManager
import com.kaizen.khushu.widget.PrayerWidgetProvider
import com.kaizen.khushu.logic.DeviceLocation
import com.kaizen.khushu.logic.LocationFixPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.CountDownLatch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val appContext: Context,
) : ViewModel() {
    private var foregroundJob: Job? = null
    private var locationJob: Job? = null
    private var zoneJob: Job? = null
    private val zoneLocator = JakimZoneLocator(java.io.File(appContext.filesDir, "gps-prayer-zone.json"))
    private data class ZoneFix(val enabled: Boolean, val epoch: Long, val lat: Float, val lng: Float, val accuracy: Float)
    private var lastAutomaticAttemptMs = 0L
    val locationError = MutableStateFlow<String?>(null)
    val locationRefreshing = MutableStateFlow(false)
    val availableQuranScripts = MutableStateFlow(QuranScriptFontRepository.availableScripts(appContext))
    val downloadingQuranScript = MutableStateFlow<String?>(null)
    val quranScriptDownloadProgress = MutableStateFlow(0f)

    val settings: StateFlow<UserSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserSettings(
            hapticsEnabled = true,
            dynamicColor = true,
            pureBlack = false,
            keepScreenAwake = true,
            volumeCounting = false,
            themeMode = "System",
            showStepTimer = true,
            fluidTransitions = true,
            vibrationOnCount = true,
            showLapCounter = true,
            showExitButton = true,
            showCompletionText = true,
            completionText = "الحمد لله",
            colorSeed = "default",
            tasbeehListMode = true,
            startupTab = "home",
            tasbihBeadStyle = "CLASSIC_AMBER",
            showTajweed = false,
            customBeadStyles = listOf(defaultCustomBeadStyle()),
            activeBeadStyleId = DEFAULT_CUSTOM_BEAD_STYLE_ID,
            tasbihSoundEnabled = true,
            tasbihSoundId = "1",
            lastSeenAppVersionCode = 0,
        )
    )

    val isSettingsLoaded: StateFlow<Boolean> = repository.settingsFlow
        .map { true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    init {
        viewModelScope.launch {
            settings.map { ZoneFix(it.automaticJakimZone && it.useGpsLocation && it.prayerSourceType == "JAKIM",
                it.lastLocationFixEpochMs, it.locationLat, it.locationLng, it.locationAccuracyMeters) }
                .distinctUntilChanged().collect {
                    zoneJob?.cancel()
                    if (foregroundJob?.isActive == true) resolveAutomaticZone(repository.settingsFlow.first())
                }
        }
        viewModelScope.launch {
            settings.map { it.useGpsLocation }.distinctUntilChanged().collect { enabled ->
                if (!enabled) locationJob?.cancel()
                else if (foregroundJob?.isActive == true && LocationFixPolicy.needsRefresh(
                        settings.value.lastLocationFixEpochMs, System.currentTimeMillis(),
                        settings.value.locationRefreshIntervalMinutes,
                    ) && LocationFixPolicy.canRetryAutomatically(lastAutomaticAttemptMs, SystemClock.elapsedRealtime())) {
                    lastAutomaticAttemptMs = SystemClock.elapsedRealtime()
                    refreshLocation()
                }
            }
        }

        viewModelScope.launch {
            settings.map { it.locationLat to it.locationLng }
                .distinctUntilChanged()
                .collect { (lat, lng) ->
                    resolveAndSaveLocationLabel(lat, lng)
                }
        }
    }

    private fun resolveAndSaveLocationLabel(lat: Float, lng: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val label = runCatching {
                val geocoder = Geocoder(appContext, Locale.getDefault())
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val results = mutableListOf<android.location.Address>()
                    val latch = CountDownLatch(1)
                    geocoder.getFromLocation(lat.toDouble(), lng.toDouble(), 1) { found ->
                        results += found
                        latch.countDown()
                    }
                    latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                    results
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat.toDouble(), lng.toDouble(), 1).orEmpty()
                }

                val best = addresses.firstOrNull()
                listOfNotNull(
                    best?.locality?.takeIf { it.isNotBlank() },
                    best?.subAdminArea?.takeIf { it.isNotBlank() },
                    best?.adminArea?.takeIf { it.isNotBlank() }
                ).firstOrNull()
            }.getOrNull() ?: "Your area"

            repository.updateLocationLabel(label)
        }
    }

    fun downloadQuranScript(script: String) {
        if (downloadingQuranScript.value != null) return
        if (QuranScriptFontRepository.isDownloaded(appContext, script)) {
            availableQuranScripts.value = QuranScriptFontRepository.availableScripts(appContext)
            return
        }
        if (script != QuranScriptFontRepository.UTHMANIC_HAFS) return

        viewModelScope.launch {
            downloadingQuranScript.value = script
            quranScriptDownloadProgress.value = 0f
            val downloaded = runCatching {
                QuranScriptFontRepository.downloadUthmanicHafs(appContext) { progress ->
                    quranScriptDownloadProgress.value = progress
                }
            }.getOrDefault(false)
            if (downloaded) {
                availableQuranScripts.value = QuranScriptFontRepository.availableScripts(appContext)
            }
            downloadingQuranScript.value = null
            quranScriptDownloadProgress.value = 0f
        }
    }

    fun toggleHaptics(enabled: Boolean) {
        viewModelScope.launch { repository.updateHaptics(enabled) }
    }

    fun toggleDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.updateDynamicColor(enabled) }
    }

    fun togglePureBlack(enabled: Boolean) {
        viewModelScope.launch { repository.updatePureBlack(enabled) }
    }

    fun toggleKeepScreenAwake(enabled: Boolean) {
        viewModelScope.launch { repository.updateKeepScreenAwake(enabled) }
    }

    fun toggleVolumeCounting(enabled: Boolean) {
        viewModelScope.launch { repository.updateVolumeCounting(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { repository.updateThemeMode(mode) }
    }

    fun toggleShowStepTimer(show: Boolean) {
        viewModelScope.launch { repository.updateShowStepTimer(show) }
    }

    fun toggleFluidTransitions(enabled: Boolean) {
        viewModelScope.launch { repository.updateFluidTransitions(enabled) }
    }

    fun toggleVibrationOnCount(enabled: Boolean) {
        viewModelScope.launch { repository.updateVibrationOnCount(enabled) }
    }

    fun toggleShowLapCounter(show: Boolean) {
        viewModelScope.launch { repository.updateShowLapCounter(show) }
    }

    fun updateShowExitButton(show: Boolean) {
        viewModelScope.launch { repository.updateShowExitButton(show) }
    }

    fun updateShowCompletionText(show: Boolean) {
        viewModelScope.launch { repository.updateShowCompletionText(show) }
    }

    fun updateCompletionText(text: String) {
        viewModelScope.launch { repository.updateCompletionText(text) }
    }

    fun setColorSeed(seed: String) {
        viewModelScope.launch { repository.updateColorSeed(seed) }
    }

    fun toggleTasbeehListMode(isList: Boolean) {
        viewModelScope.launch { repository.updateTasbeehListMode(isList) }
    }

    fun toggleTasbeehDynamicColors(enabled: Boolean) {
        viewModelScope.launch { repository.updateTasbeehDynamicColors(enabled) }
    }

    fun toggleTasbeehStealthModeAllowed(enabled: Boolean) {
        viewModelScope.launch { repository.updateTasbeehStealthModeAllowed(enabled) }
    }

    fun toggleTasbeehVolumeEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setTasbeehVolumeEnabled(enabled) }
    }

    fun toggleTasbeehVolumeAnimation(enabled: Boolean) {
        viewModelScope.launch { repository.setTasbeehVolumeAnimation(enabled) }
    }

    fun toggleTasbihSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setTasbihSoundEnabled(enabled) }
    }

    fun setTasbihSoundId(id: String) {
        viewModelScope.launch { repository.setTasbihSoundId(id) }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch { repository.setOnboardingCompleted(completed) }
    }

    fun setDeveloperWelcomeDismissed(dismissed: Boolean) {
        viewModelScope.launch { repository.setDeveloperWelcomeDismissed(dismissed) }
    }

    fun setStudyNoteDismissed(dismissed: Boolean) {
        viewModelScope.launch { repository.setStudyNoteDismissed(dismissed) }
    }

    fun markReleaseNotesSeen(versionCode: Int) {
        viewModelScope.launch { repository.updateLastSeenAppVersionCode(versionCode) }
    }


    fun setStringElasticity(value: Float) {
        viewModelScope.launch { repository.updateStringElasticity(value) }
    }

    fun setWobbleStiffness(value: Float) {
        viewModelScope.launch { repository.updateWobbleStiffness(value) }
    }

    fun setWobbleDampingRatio(value: Float) {
        viewModelScope.launch { repository.updateWobbleDampingRatio(value) }
    }

    fun setBeadMicroScale(value: Float) {
        viewModelScope.launch { repository.updateBeadMicroScale(value) }
    }

    fun saveCustomBeadStyle(style: CustomBeadStyle) {
        val currentList = settings.value.customBeadStyles.toMutableList()
        val index = currentList.indexOfFirst { it.id == style.id }
        if (index != -1) {
            currentList[index] = style
        } else {
            currentList.add(style)
        }
        viewModelScope.launch { repository.updateCustomBeadStyles(currentList) }
    }

    fun deleteCustomBeadStyle(id: String) {
        val newList = settings.value.customBeadStyles.filter { it.id != id }
        viewModelScope.launch { repository.updateCustomBeadStyles(newList) }
    }

    fun setActiveBeadStyleId(id: String) {
        viewModelScope.launch { repository.updateActiveBeadStyleId(id) }
    }

    fun setStartupTab(route: String) {
        viewModelScope.launch { repository.updateStartupTab(route) }
    }

    fun setTasbihBeadStyle(style: String) {
        viewModelScope.launch { repository.updateTasbihBeadStyle(style) }
    }

    fun setLogoStyle(style: String) {
        viewModelScope.launch {
            val previous = repository.settingsFlow.first().logoStyle
            try {
                AppIconManager.apply(appContext, style)
                repository.updateLogoStyle(style)
                Toast.makeText(appContext, "App icon updated. Your launcher may take a moment to refresh.", Toast.LENGTH_SHORT).show()
            } catch (_: IllegalArgumentException) {
                Toast.makeText(appContext, "Unable to change the app icon.", Toast.LENGTH_SHORT).show()
            } catch (_: SecurityException) {
                Toast.makeText(appContext, "Android did not allow the icon change.", Toast.LENGTH_SHORT).show()
            } catch (_: java.io.IOException) {
                runCatching { AppIconManager.apply(appContext, previous) }
                Toast.makeText(appContext, "Unable to save the app icon selection.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setReadingTheme(theme: String) {
        viewModelScope.launch { repository.updateReadingTheme(theme) }
    }

    fun setArabicSizeSp(size: Float) {
        viewModelScope.launch { repository.updateArabicSizeSp(size) }
    }

    fun setTranslationSizeSp(size: Float) {
        viewModelScope.launch { repository.updateTranslationSizeSp(size) }
    }

    fun toggleShowTranslation(show: Boolean) {
        viewModelScope.launch { repository.updateShowTranslation(show) }
    }

    fun toggleShowTransliteration(show: Boolean) {
        viewModelScope.launch { repository.updateShowTransliteration(show) }
    }

    fun toggleShowWordByWord(show: Boolean) {
        viewModelScope.launch { repository.updateShowWordByWord(show) }
    }

    fun toggleReadingKeepScreenOn(keep: Boolean) {
        viewModelScope.launch { repository.updateReadingKeepScreenOn(keep) }
    }

    fun toggleShowContinueReading(show: Boolean) {
        viewModelScope.launch { repository.updateShowContinueReading(show) }
    }

    fun toggleShowTajweed(show: Boolean) {
        viewModelScope.launch { repository.updateShowTajweed(show) }
    }

    fun setSelectedReciterId(id: String) {
        viewModelScope.launch { repository.updateSelectedReciterId(id) }
    }

    fun setSelectedScript(script: String) {
        viewModelScope.launch { repository.updateSelectedScript(script) }
    }

    fun setSelectedTranslationLang(lang: String) {
        viewModelScope.launch { repository.updateSelectedTranslationLang(lang) }
    }

    fun setSelectedTranslationSource(source: String) {
        viewModelScope.launch { repository.setSelectedTranslationSource(source) }
    }

    fun setSelectedTafsir(id: String, source: String) {
        viewModelScope.launch { repository.setSelectedTafsir(id, source) }
    }

    fun setShowTafsir(show: Boolean) {
        viewModelScope.launch { repository.setShowTafsir(show) }
    }

    fun setSelectedAudioSource(source: String) {
        viewModelScope.launch { repository.setSelectedAudioSource(source) }
    }

    fun updateLastReadTopicId(id: String) {
        viewModelScope.launch { repository.updateLastReadTopicId(id) }
    }

    fun clearLastReadTopicId() {
        viewModelScope.launch { repository.updateLastReadTopicId("") }
    }

    fun toggleBookmark(topicId: String, ayahIndex: Int = 0) {
        viewModelScope.launch {
            val key = "$topicId:$ayahIndex"
            val current = settings.value.bookmarkedAyahs
            val updated = if (current.contains(key)) current - key else current + key
            repository.updateBookmarkedTopicIds(updated)
        }
    }

    fun toggleMastered(topicId: String) {
        viewModelScope.launch {
            val current = settings.value.masteredTopicIds
            val updated = if (current.contains(topicId)) current - topicId else current + topicId
            repository.updateMasteredTopicIds(updated)
        }
    }

    fun setPrayerCalculationMethod(method: String) {
        viewModelScope.launch { repository.updatePrayerCalculationMethod(method) }
    }

    fun setPrayerMadhab(madhab: String) {
        viewModelScope.launch { repository.updatePrayerMadhab(madhab) }
    }

    fun setLocation(lat: Float, lng: Float) {
        viewModelScope.launch { repository.updateLocation(lat, lng) }
    }

    fun toggleUseGpsLocation(enabled: Boolean) {
        viewModelScope.launch { repository.updateUseGpsLocation(enabled) }
    }

    fun setJakimZone(zone: String) {
        viewModelScope.launch { repository.updateJakimZone(zone) }
    }

    fun setAutomaticJakimZone(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutomaticJakimZone(enabled)
            if (enabled) refreshLocation()
        }
    }

    private fun resolveAutomaticZone(current: UserSettings) {
        val hasPermission = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED }
        if (zoneJob?.isActive == true || !current.automaticJakimZone || !current.useGpsLocation ||
            !hasPermission || current.prayerSourceType != "JAKIM" || current.lastLocationFixEpochMs <= 0 ||
            System.currentTimeMillis() - current.lastLocationFixEpochMs !in 0..LocationFixPolicy.MAX_AGE_MS) return
        zoneJob = viewModelScope.launch {
            val result = zoneLocator.locate(current.locationLat.toDouble(), current.locationLng.toDouble(), current.locationAccuracyMeters)
            repository.applyAutomaticJakimZone(current.lastLocationFixEpochMs, current.locationLat, current.locationLng, result)
            PrayerWidgetProvider.forceWidgetRefresh(appContext)
        }
    }

    fun setPrayerSourceType(source: String) {
        viewModelScope.launch { repository.updatePrayerSourceType(source) }
    }

    fun setPrayerOffset(prayerName: String, minutes: Int) {
        viewModelScope.launch { repository.updatePrayerOffset(prayerName, minutes.coerceIn(-15, 15)) }
    }

    fun setPrayerNotificationEnabled(prayerName: String, enabled: Boolean) {
        viewModelScope.launch { repository.updatePrayerNotificationEnabled(prayerName, enabled) }
    }

    fun setPrePrayerNotificationEnabled(prayerName: String, enabled: Boolean) {
        viewModelScope.launch { repository.updatePrePrayerNotificationEnabled(prayerName, enabled) }
    }

    fun setPrePrayerMinutes(prayerName: String, minutes: Int) {
        viewModelScope.launch { repository.updatePrePrayerMinutes(prayerName, minutes.coerceIn(1, 60)) }
    }

    fun setPrayerNotificationAlertStyle(style: String) {
        viewModelScope.launch { repository.updatePrayerNotificationAlertStyle(style) }
    }

    fun setPrayerNotificationCustomSoundUri(uri: String) {
        viewModelScope.launch { repository.updatePrayerNotificationCustomSoundUri(uri) }
    }

    fun toggleExtraPrayerTiming(id: String, enabled: Boolean) {
        val updated = settings.value.selectedExtraPrayerTimings.toMutableSet().apply {
            if (enabled) add(id) else remove(id)
        }
        viewModelScope.launch { repository.updateSelectedExtraPrayerTimings(updated) }
    }

    fun toggleExtraPrayerNotification(id: String, enabled: Boolean) {
        val updated = settings.value.extraPrayerNotifications.toMutableSet().apply {
            if (enabled) add(id) else remove(id)
        }
        viewModelScope.launch { repository.updateExtraPrayerNotifications(updated) }
    }

    fun toggleShowExtraPrayerTimingsOnHome(enabled: Boolean) {
        viewModelScope.launch { repository.updateShowExtraPrayerTimingsOnHome(enabled) }
    }

    fun toggleShowUpcomingEventsOnHome(enabled: Boolean) {
        viewModelScope.launch { repository.updateShowUpcomingEventsOnHome(enabled) }
    }

    fun setIslamicEventPerspective(perspective: String) {
        viewModelScope.launch { repository.updateIslamicEventPerspective(perspective) }
    }

    fun updateWidgetCustomization(
        backgroundColor: String,
        backgroundOpacity: Float,
        panelColor: String,
        panelOpacity: Float,
        fontColor: String? = null
    ) {
        viewModelScope.launch {
            repository.updateWidgetCustomization(
                backgroundColor,
                backgroundOpacity,
                panelColor,
                panelOpacity
            )
            fontColor?.let { repository.updateWidgetFontColor(it) }
            PrayerWidgetProvider.forceWidgetRefresh(appContext)
        }
    }

    fun onForeground() {
        if (foregroundJob?.isActive == true) return
        foregroundJob = viewModelScope.launch {
            val initial = repository.settingsFlow.first()
            if (initial.automaticJakimZone && !initial.automaticJakimZoneConfirmed) resolveAutomaticZone(initial)
            while (isActive) {
                val current = repository.settingsFlow.first()
                if (current.useGpsLocation && (LocationFixPolicy.needsRefresh(
                        current.lastLocationFixEpochMs, System.currentTimeMillis(),
                        current.locationRefreshIntervalMinutes,
                    ) || current.automaticJakimZone && !current.automaticJakimZoneConfirmed) &&
                    LocationFixPolicy.canRetryAutomatically(lastAutomaticAttemptMs, SystemClock.elapsedRealtime())) {
                    lastAutomaticAttemptMs = SystemClock.elapsedRealtime()
                    refreshLocation()
                }
                delay(60_000L)
            }
        }
    }

    fun onBackground() {
        foregroundJob?.cancel()
        locationJob?.cancel()
        zoneJob?.cancel()
    }

    fun setLocationRefreshInterval(minutes: Int) {
        viewModelScope.launch { repository.updateLocationRefreshInterval(minutes) }
    }

    fun refreshLocation() {
        if (locationJob?.isActive == true) return
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            locationError.value = "Location permission is required. Use GPS Access to grant it."
            return
        }
        locationJob = viewModelScope.launch {
            locationRefreshing.value = true
            locationError.value = null
            try {
                val fix = DeviceLocation.findFix(appContext, fine)
                if (fix == null) {
                    locationError.value = "No recent, accurate location found. Check device location settings and retry. Previous coordinates are retained."
                } else if (repository.settingsFlow.first().useGpsLocation) {
                    if (fix.time < repository.settingsFlow.first().lastLocationFixEpochMs) {
                        locationError.value = "No newer location fix found. Previous coordinates are retained."
                        return@launch
                    }
                    repository.updateLocationFix(
                        fix.latitude.toFloat(), fix.longitude.toFloat(), fix.time, fix.accuracy,
                    )
                }
            } finally {
                locationRefreshing.value = false
            }
        }
    }

    companion object {
        fun factory(repository: SettingsRepository, appContext: Context) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return SettingsViewModel(repository, appContext) as T
                }
            }
    }
}
