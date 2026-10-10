@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.kaizen.khushu.ui.screens.home

import android.content.Context
import android.content.Intent
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaizen.khushu.R
import com.kaizen.khushu.logic.QiblaDirection
import com.kaizen.khushu.ui.components.KhushuAppBar
import com.kaizen.khushu.ui.theme.BeVietnamPro
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun findNextPrayer(prayers: List<PrayerInfo>, now: Long): PrayerInfo? {
    if (prayers.isEmpty()) return null

    prayers.firstOrNull { it.rawTime.toEpochMilliseconds() > now }?.let {
        return it
    }

    val fajr = prayers.first()
    return fajr.copy(rawTime = kotlinx.datetime.Instant.fromEpochMilliseconds(fajr.rawTime.toEpochMilliseconds() + 86400000L))
}

private fun findCurrentPrayer(prayers: List<PrayerInfo>, now: Long): PrayerInfo? {
    if (prayers.isEmpty()) return null
    return prayers.lastOrNull { it.rawTime.toEpochMilliseconds() <= now } ?: prayers.last()
}

private fun homeDayStamp(epochMillis: Long): String {
    val effectiveMillis = if (epochMillis > 0L) epochMillis else System.currentTimeMillis()
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(effectiveMillis))
}

private fun emptyPrayerDoneStates(prayers: List<PrayerInfo>): Map<String, Boolean> {
    val names =
            prayers.filterNot { it.isExtra }.map { it.name }.ifEmpty {
                listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
            }
    return names.associateWith { false }
}

private fun compassPointLabel(bearing: Double): String {
    val points = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    val index = (((bearing + 11.25) % 360) / 22.5).toInt()
    return points[index]
}

@Composable
private fun rememberDeviceHeading(latitude: Float, longitude: Float): Float? {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val headingState = remember { mutableStateOf<Float?>(null) }
    val declination = remember(latitude, longitude) {
        // Location settings store no elevation; sea-level altitude is sufficient for this compass.
        GeomagneticField(latitude, longitude, 0f, System.currentTimeMillis()).declination
    }

    DisposableEffect(sensorManager) {
        val manager = sensorManager
        val rotationSensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (manager == null || rotationSensor == null) {
            onDispose { }
        } else {
            val rotationMatrix = FloatArray(9)
            val displayMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val (xAxis, yAxis) = when (context.display?.rotation) {
                        android.view.Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                        android.view.Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                        android.view.Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                    }
                    SensorManager.remapCoordinateSystem(rotationMatrix, xAxis, yAxis, displayMatrix)
                    SensorManager.getOrientation(displayMatrix, orientation)
                    val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    headingState.value = (azimuthDeg + 360f) % 360f
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            manager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { manager.unregisterListener(listener) }
        }
    }

    return headingState.value?.let {
        QiblaDirection.trueHeadingDegrees(it.toDouble(), declination.toDouble()).toFloat()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QiblaCompassDialog(
    bearingDegrees: Double,
    locationLabel: String,
    latitude: Float,
    longitude: Float,
    onDismiss: () -> Unit,
) {
    val heading = rememberDeviceHeading(latitude, longitude)
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Qibla Direction",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = BeVietnamPro),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = locationLabel.ifBlank { "Current location" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = BeVietnamPro),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
                QiblaCompassDial(bearingDegrees = bearingDegrees, trueHeading = heading)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "${bearingDegrees.roundToInt()}° ${compassPointLabel(bearingDegrees)} to Makkah",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = BeVietnamPro),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = heading?.let { "Facing ${it.roundToInt() % 360}° from true north" }
                        ?: "Compass heading unavailable. Use the bearing with a trusted compass.",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = BeVietnamPro),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    text = "Coordinates: ${"%.4f".format(Locale.US, latitude)}, ${"%.4f".format(Locale.US, longitude)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = BeVietnamPro),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

private data class HomeHijriBadgeParts(
        val dayMonth: String,
        val year: String,
)

private fun splitHijriBadgeParts(raw: String): HomeHijriBadgeParts {
    val compact = raw.trim().replace(Regex("\\s+"), " ")
    if (compact.isBlank()) {
        return HomeHijriBadgeParts(dayMonth = "Hijri Date", year = "")
    }
    val pieces = compact.split(" ")
    return if (pieces.size >= 2) {
        HomeHijriBadgeParts(dayMonth = pieces.dropLast(1).joinToString(" "), year = pieces.last())
    } else {
        HomeHijriBadgeParts(dayMonth = compact, year = "")
    }
}

@Composable
private fun HomeHijriBadge(
        badge: HomeHijriBadgeParts,
        modifier: Modifier = Modifier,
) {
    val baseColor = MaterialTheme.colorScheme.onSurface
    val label = remember(badge, baseColor) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = baseColor)) { append(badge.dayMonth) }
            if (badge.year.isNotBlank()) {
                append("  ")
                withStyle(SpanStyle(color = baseColor.copy(alpha = 0.4f))) { append(badge.year) }
            }
        }
    }

    Surface(
            modifier = modifier,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            ) {
        Text(
                text = label,
                style =
                        MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = BeVietnamPro,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                        ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun KhushuPullRefreshIndicator(
        progress: Float,
        isRefreshing: Boolean,
        darkTheme: Boolean,
        modifier: Modifier = Modifier,
) {
    val logoRes = R.drawable.ic_sukun_logo
    val targetProgress = if (isRefreshing) 1f else progress.coerceIn(0f, 1f)
    val visibleProgress by
            animateFloatAsState(
                    targetValue = targetProgress,
                    animationSpec = tween(durationMillis = if (isRefreshing) 240 else 120),
                    label = "home_refresh_indicator_progress"
            )
    val indicatorAlpha by
            animateFloatAsState(
                    targetValue = if (isRefreshing || progress > 0f) 1f else 0f,
                    animationSpec = tween(durationMillis = 180),
                    label = "home_refresh_indicator_alpha"
            )
    val pulseScaleBase =
            rememberInfiniteTransition(label = "home_refresh_logo_pulse")
                    .animateFloat(
                            initialValue = 1f,
                            targetValue = 1.10f,
                            animationSpec =
                                    infiniteRepeatable(
                                            animation = tween(
                                                durationMillis = 900,
                                                easing = FastOutSlowInEasing
                                            ),
                                            repeatMode = RepeatMode.Reverse
                                    ),
                            label = "home_refresh_logo_scale"
                    )
                    .value
    val pulseScale = if (isRefreshing) pulseScaleBase else 1f

    Box(
            modifier =
                    modifier.graphicsLayer {
                        alpha = indicatorAlpha
                        scaleX = pulseScale
                        scaleY = pulseScale
                    },
            contentAlignment = Alignment.Center
    ) {
        Icon(
                painter = painterResource(id = logoRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                modifier = Modifier.fillMaxSize()
        )
        Icon(
                painter = painterResource(id = logoRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier =
                        Modifier.fillMaxSize().drawWithContent {
                            val clipTop = size.height * (1f - visibleProgress)
                            clipRect(top = clipTop) { this@drawWithContent.drawContent() }
                        }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
        hazeState: HazeState,
        contentPadding: PaddingValues,
        onSettingsClick: () -> Unit,
        viewModel: HomeViewModel,
        modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    var currentInstant by remember { mutableStateOf(Instant.fromEpochMilliseconds(System.currentTimeMillis())) }
    LaunchedEffect(Unit) {
        while (true) {
            currentInstant = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            kotlinx.coroutines.delay(1000L)
        }
    }

    val currentTimeMillis = uiState.previewTime?.toEpochMilliseconds() ?: currentInstant.toEpochMilliseconds()
    val refreshThresholdPx = with(density) { 118.dp.toPx() }
    val refreshHoldPx = with(density) { 74.dp.toPx() }
    val maxPullPx = with(density) { 164.dp.toPx() }
    val currentDayStamp = homeDayStamp(currentTimeMillis)
    var cachedPrayers by remember { mutableStateOf<List<PrayerInfo>>(emptyList()) }
    var cachedExtraTimings by remember { mutableStateOf<List<PrayerInfo>>(emptyList()) }
    var cachedEvents by remember { mutableStateOf<List<IslamicEvent>>(emptyList()) }
    var cachedCalendarEvents by remember { mutableStateOf<List<IslamicEvent>>(emptyList()) }
    var cachedHijriDate by remember { mutableStateOf("") }
    var cachedEventsHeader by remember { mutableStateOf("") }

    val basePrayerList = (uiState.prayers.ifEmpty { cachedPrayers }).filterNot { it.isExtra }
    var doneStates by
            rememberSaveable(currentDayStamp) {
                mutableStateOf(emptyPrayerDoneStates(basePrayerList))
            }
    var showTimeOverrideDialog by remember { mutableStateOf(false) }
    var selectedQuickAction by remember { mutableStateOf<HomeQuickAction?>(null) }
    var previewHourText by remember { mutableStateOf("") }
    var previewMinuteText by remember { mutableStateOf("") }
    var pullOffsetPx by remember { mutableFloatStateOf(0f) }
    var thresholdReached by remember { mutableStateOf(false) }

    LaunchedEffect(
            uiState.prayers,
            uiState.extraTimings,
            uiState.events,
            uiState.calendarEvents,
            uiState.hijriDate,
            uiState.eventsHeader
    ) {
        if (uiState.prayers.isNotEmpty()) {
            cachedPrayers = uiState.prayers
        }
        if (uiState.extraTimings.isNotEmpty() || cachedExtraTimings.isEmpty()) {
            cachedExtraTimings = uiState.extraTimings
        }
        if (uiState.events.isNotEmpty() || cachedEvents.isEmpty()) {
            cachedEvents = uiState.events
        }
        if (uiState.calendarEvents.isNotEmpty() || cachedCalendarEvents.isEmpty()) {
            cachedCalendarEvents = uiState.calendarEvents
        }
        if (uiState.hijriDate.isNotBlank()) {
            cachedHijriDate = uiState.hijriDate
        }
        if (uiState.eventsHeader.isNotBlank()) {
            cachedEventsHeader = uiState.eventsHeader
        }
    }

    LaunchedEffect(basePrayerList) {
        val expected = emptyPrayerDoneStates(basePrayerList)
        if (expected.keys != doneStates.keys) {
            doneStates = expected
        }
    }

    val displayPrayers = uiState.prayers.ifEmpty { cachedPrayers }
    val displayExtraTimings = uiState.extraTimings.ifEmpty { cachedExtraTimings }
    val displayEvents = uiState.events.ifEmpty { cachedEvents }
    val displayCalendarEvents = uiState.calendarEvents.ifEmpty { cachedCalendarEvents }
    val displayHijriDate = uiState.hijriDate.ifBlank { cachedHijriDate }
    val displayEventsHeader = uiState.eventsHeader.ifBlank { cachedEventsHeader }
    val hijriBadge = remember(displayHijriDate) { splitHijriBadgeParts(displayHijriDate) }
    val qiblaBearing = remember(uiState.locationLat, uiState.locationLng) {
        QiblaDirection.bearingDegrees(uiState.locationLat.toDouble(), uiState.locationLng.toDouble())
    }

    val currentPrayer = findCurrentPrayer(displayPrayers, currentTimeMillis)
    val homeVisibleTimings =
            if (uiState.showExtraPrayerTimingsOnHome) {
                (displayPrayers + displayExtraTimings).sortedBy { it.rawTime.toEpochMilliseconds() }
            } else {
                displayPrayers
            }
    val nextPrayer = findNextPrayer(homeVisibleTimings, currentTimeMillis)

    val sunArcT = run {
        val fajrMs = displayPrayers.firstOrNull { it.name == "Fajr" }?.rawTime?.toEpochMilliseconds() ?: 0L
        val ishaMs = displayPrayers.firstOrNull { it.name == "Isha" }?.rawTime?.toEpochMilliseconds() ?: 1L
        val total = (ishaMs - fajrMs).toFloat()
        if (total <= 0) 0.5f else {
            val ratio = (currentTimeMillis - fajrMs).toFloat() / total
            (0.08f + ratio * (0.93f - 0.08f)).coerceIn(-0.1f, 1.1f)
        }
    }

    val locationLabel = uiState.locationLabel.ifBlank { "Your area" }
    val pullProgress = (pullOffsetPx / refreshThresholdPx).coerceIn(0f, 1f)

    val animatedPullOffsetPx by
            animateFloatAsState(
                    targetValue =
                            when {
                                isRefreshing -> refreshHoldPx
                                else -> pullOffsetPx
                            },
                    animationSpec = spring(stiffness = 420f, dampingRatio = 0.88f),
                    label = "home_pull_offset"
            )

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullOffsetPx = 0f
            thresholdReached = false
        }
    }

    val pullRefreshConnection =
            remember(listState, isRefreshing) {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                            available: androidx.compose.ui.geometry.Offset,
                            source: NestedScrollSource
                    ): androidx.compose.ui.geometry.Offset {
                        if (source != NestedScrollSource.UserInput)
                                return androidx.compose.ui.geometry.Offset.Zero

                        if (available.y < 0f && pullOffsetPx > 0f) {
                            val previous = pullOffsetPx
                            pullOffsetPx = (pullOffsetPx + available.y).coerceAtLeast(0f)
                            if (pullOffsetPx < refreshThresholdPx) {
                                thresholdReached = false
                            }
                            return androidx.compose.ui.geometry.Offset(0f, pullOffsetPx - previous)
                        }

                        return androidx.compose.ui.geometry.Offset.Zero
                    }

                    override fun onPostScroll(
                            consumed: androidx.compose.ui.geometry.Offset,
                            available: androidx.compose.ui.geometry.Offset,
                            source: NestedScrollSource
                    ): androidx.compose.ui.geometry.Offset {
                        if (source != NestedScrollSource.UserInput || isRefreshing) {
                            return androidx.compose.ui.geometry.Offset.Zero
                        }

                        if (available.y > 0f && !listState.canScrollBackward) {
                            val previous = pullOffsetPx
                            val resistance =
                                    0.55f - ((previous / maxPullPx).coerceIn(0f, 1f) * 0.2f)
                            pullOffsetPx =
                                    (previous + available.y * resistance).coerceAtMost(maxPullPx)

                            if (pullOffsetPx >= refreshThresholdPx && !thresholdReached) {
                                thresholdReached = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (pullOffsetPx < refreshThresholdPx) {
                                thresholdReached = false
                            }

                            return androidx.compose.ui.geometry.Offset(0f, available.y)
                        }

                        return androidx.compose.ui.geometry.Offset.Zero
                    }

                    override suspend fun onPostFling(
                            consumed: Velocity,
                            available: Velocity
                    ): Velocity {
                        if (!isRefreshing && pullOffsetPx >= refreshThresholdPx) {
                            pullOffsetPx = refreshHoldPx
                            viewModel.refreshPrayerData()
                        } else if (!isRefreshing) {
                            pullOffsetPx = 0f
                            thresholdReached = false
                        }

                        return Velocity.Zero
                    }
                }
            }

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().nestedScroll(pullRefreshConnection)) {
            LazyColumn(
                    state = listState,
                    contentPadding =
                            PaddingValues(
                                    top = contentPadding.calculateTopPadding() + 22.dp,
                                    bottom = contentPadding.calculateBottomPadding() + 16.dp
                            ),
                    modifier =
                            Modifier.fillMaxSize().graphicsLayer {
                                translationY = animatedPullOffsetPx
                            }
            ) {
                item {
                    val sunriseTime = (displayPrayers + displayExtraTimings)
                        .firstOrNull {
                            it.name.contains("shuruq", ignoreCase = true) ||
                            it.name.contains("sunrise", ignoreCase = true)
                        }?.time ?: ""
                    val sunsetTime = displayPrayers
                        .firstOrNull { it.name.equals("Maghrib", ignoreCase = true) }
                        ?.time ?: ""

                    if (displayPrayers.isEmpty()) {
                        PrayerSunMergedCardShimmer(
                            modifier = Modifier.padding(horizontal = 14.dp).testTag("home-prayer-loading")
                        )
                    } else {
                        PrayerSunMergedCard(
                            currentPrayer = currentPrayer,
                            nextPrayer = nextPrayer,
                            sunT = sunArcT,
                            allPrayers = displayPrayers,
                            makruhZones = uiState.makruhZones,
                            darkTheme = darkTheme,
                            sunriseTime = sunriseTime,
                            sunsetTime = sunsetTime,
                            locationLabel = locationLabel,
                            source = uiState.calculationSource,
                            modifier = Modifier.padding(horizontal = 14.dp).testTag("home-prayer-card")
                        )
                    }
                }

                uiState.prayerDataNotice?.let { notice ->
                    item { PrayerSourceCard(notice) }
                }

                item { Spacer(modifier = Modifier.height(14.dp)) }

                if (uiState.showUpcomingEventsOnHome) {
                    item {
                        Box(Modifier.testTag("home-events")) {
                            EventsStrip(
                                header = displayEventsHeader,
                                events = displayEvents,
                                calendarEvents = displayCalendarEvents,
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(14.dp)) }
                }

                item {
                    PrayerSlab(
                            prayers = displayPrayers,
                            extraTimings =
                                    if (uiState.showExtraPrayerTimingsOnHome) displayExtraTimings
                                    else emptyList(),
                            doneStates = doneStates,
                            onQuickActionTap = { selectedQuickAction = it },
                            onToggleDoneAttempt = { name ->
                                val prayers = displayPrayers.filterNot { it.isExtra }
                                val tappedIndex = prayers.indexOfFirst { it.name == name }
                                if (tappedIndex == -1) {
                                    PrayerToggleOutcome(PrayerToggleResult.REJECTED_OUT_OF_ORDER)
                                } else if (doneStates[name] == true) {
                                    val rewound = doneStates.toMutableMap()
                                    prayers.drop(tappedIndex).forEach { prayer ->
                                        rewound[prayer.name] = false
                                    }
                                    doneStates = rewound
                                    PrayerToggleOutcome(PrayerToggleResult.REWOUND)
                                } else {
                                    val nextCompletable =
                                            prayers
                                                    .firstOrNull { prayer ->
                                                        !(doneStates[prayer.name] ?: false)
                                                    }
                                                    ?.name
                                    when {
                                        nextCompletable == null ->
                                                PrayerToggleOutcome(
                                                        result =
                                                                PrayerToggleResult
                                                                        .REJECTED_TOO_EARLY,
                                                        suggestedPrayerName =
                                                                prayers
                                                                        .firstOrNull {
                                                                            !(doneStates[it.name]
                                                                                    ?: false)
                                                                        }
                                                                        ?.name
                                                )
                                        nextCompletable != name ->
                                                PrayerToggleOutcome(
                                                        result =
                                                                PrayerToggleResult
                                                                        .REJECTED_OUT_OF_ORDER,
                                                        suggestedPrayerName = nextCompletable
                                                )
                                        else -> {
                                            doneStates =
                                                    doneStates.toMutableMap().apply {
                                                        this[name] = true
                                                    }
                                            PrayerToggleOutcome(PrayerToggleResult.COMPLETED)
                                        }
                                    }
                                }
                            },
                            //                    ayahText = uiState.ayahText,
                            ayahRef = uiState.ayahRef,
                            darkTheme = darkTheme,
                            modifier = Modifier.fillMaxWidth().testTag("home-prayer-list")
                    )
                }

            }

            KhushuPullRefreshIndicator(
                    // Lock fill at 100% the instant threshold is reached so there's no
                    // dip during the async gap before isRefreshing becomes true.
                    progress = if (thresholdReached || isRefreshing) 1f else pullProgress,
                    isRefreshing = isRefreshing,
                    darkTheme = darkTheme,
                    modifier =
                            Modifier.align(Alignment.TopCenter)
                                    .padding(top = contentPadding.calculateTopPadding() + 14.dp)
                                    .size(52.dp)
                                    .graphicsLayer {
                                        translationY =
                                                (animatedPullOffsetPx * 0.22f) -
                                                        with(density) { 8.dp.toPx() }
                                    }
            )
        }

        KhushuAppBar(
                title = "",
                onSettingsClick = onSettingsClick,
                centerOverlayContent = { HomeHijriBadge(badge = hijriBadge) },
                modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    selectedQuickAction?.let {
        QiblaCompassDialog(
            bearingDegrees = qiblaBearing,
            locationLabel = locationLabel,
            latitude = uiState.locationLat,
            longitude = uiState.locationLng,
            onDismiss = { selectedQuickAction = null }
        )
    }

    if (showTimeOverrideDialog) {
        AlertDialog(
                onDismissRequest = { showTimeOverrideDialog = false },
                title = { Text("Preview Prayer Time") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                                "Set a temporary 24-hour clock time to test next prayer and makruh transitions."
                        )
                        OutlinedTextField(
                                value = previewHourText,
                                onValueChange = {
                                    previewHourText = it.filter(Char::isDigit).take(2)
                                },
                                label = { Text("Hour (0-23)") }
                        )
                        OutlinedTextField(
                                value = previewMinuteText,
                                onValueChange = {
                                    previewMinuteText = it.filter(Char::isDigit).take(2)
                                },
                                label = { Text("Minute (0-59)") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                val hour = previewHourText.toIntOrNull()
                                val minute = previewMinuteText.toIntOrNull()
                                if (hour != null && minute != null) {
                                    viewModel.setPreviewTime(hour, minute)
                                    showTimeOverrideDialog = false
                                }
                            }
                    ) { Text("Apply") }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                                onClick = {
                                    viewModel.clearPreviewTime()
                                    showTimeOverrideDialog = false
                                }
                        ) { Text("Reset") }
                        TextButton(onClick = { showTimeOverrideDialog = false }) { Text("Close") }
                    }
                }
        )
    }
}

@Composable
private fun PrayerSourceCard(notice: com.kaizen.khushu.data.repository.PrayerDataNotice) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = { showDetails = true },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            .heightIn(min = 48.dp).testTag("home-prayer-source"),
        shape = RoundedCornerShape(16.dp),
        color = if (notice.approximate) colors.tertiaryContainer else colors.surfaceContainer,
        contentColor = if (notice.approximate) colors.onTertiaryContainer else colors.onSurface,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(notice.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Details", style = MaterialTheme.typography.labelSmall, color = colors.primary)
            }
            if (notice.approximate || notice.manualAdjustments) {
                Spacer(Modifier.height(4.dp))
                Text(notice.summary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(notice.title) },
            text = {
                Text(notice.summary + "\n\n" + notice.details,
                    modifier = Modifier.verticalScroll(rememberScrollState()))
            },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Close") } },
        )
    }
}
