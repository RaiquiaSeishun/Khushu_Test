package com.kaizen.khushu.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kaizen.khushu.logic.QiblaDirection
import com.kaizen.khushu.ui.theme.BeVietnamPro
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** North and Qibla share the same true-north frame. No directional markers without a heading. */
@Composable
internal fun QiblaCompassDial(bearingDegrees: Double, trueHeading: Float?) {
    val northAngle = trueHeading?.let { Math.toRadians(QiblaDirection.relativeDegrees(0.0, it.toDouble())) }
    val qiblaAngle = trueHeading?.let {
        Math.toRadians(QiblaDirection.relativeDegrees(bearingDegrees, it.toDouble()))
    }
    val ringColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val northColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val needleColor = MaterialTheme.colorScheme.primary
    val labelRadius = with(LocalDensity.current) { 82.dp.toPx() }
    Box(modifier = Modifier.size(220.dp).testTag("qibla-compass-dial"), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val stroke = 8.dp.toPx()
            val radius = size.minDimension / 2f - stroke
            fun point(angle: Double, distance: Float) = Offset(
                center.x + sin(angle).toFloat() * distance,
                center.y - cos(angle).toFloat() * distance
            )
            drawCircle(color = ringColor, radius = radius, style = Stroke(width = stroke))
            if (northAngle != null && qiblaAngle != null) {
                drawLine(
                    color = northColor,
                    start = point(northAngle, radius),
                    end = point(northAngle, radius - 22.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = needleColor,
                    start = center,
                    end = point(qiblaAngle, radius * 0.78f),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(color = needleColor, radius = 7.dp.toPx(), center = center)
            }
        }
        if (northAngle != null) {
            Text(
                text = "N",
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold),
                color = northColor,
                modifier = Modifier.absoluteOffset {
                    IntOffset(
                        (sin(northAngle) * labelRadius).roundToInt(),
                        (-cos(northAngle) * labelRadius).roundToInt()
                    )
                }.testTag("qibla-north-marker")
            )
        }
    }
}
