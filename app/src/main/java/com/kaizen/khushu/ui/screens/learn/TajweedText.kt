package com.kaizen.khushu.ui.screens.learn

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kaizen.khushu.logic.QuranMarkup
import com.kaizen.khushu.ui.theme.ScheherazadeNew

@Composable
fun TajweedText(
    markup: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    appendMarker: Boolean = false
) {
    val annotatedString = remember(markup, color, appendMarker) {
        buildAnnotatedString {
            append(parseTajweed(markup, color))
            if (appendMarker) {
                // Append a special tag for the AyahEndMarker to be injected via inlineContent
                appendInlineContent("marker", "[marker]")
            }
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        fontFamily = ScheherazadeNew,
        fontSize = fontSize,
        lineHeight = lineHeight,
        textAlign = TextAlign.Center,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.bodyLarge.copy(
            textDirection = TextDirection.Rtl,
            fontFamily = ScheherazadeNew,
        ),
    )
}

/** Nested rules inherit their outer colour unless an inner rule supplies a different colour. */
internal fun parseTajweed(markup: String, defaultColor: Color): AnnotatedString = buildAnnotatedString {
    for (segment in QuranMarkup.segments(markup)) {
        val color = segment.rules.fold(defaultColor) { inherited, rule -> tajweedColor(rule, inherited) }
        withStyle(SpanStyle(color = color)) { append(segment.text) }
    }
}

fun buildAyahAnnotatedString(
    text: String,
    color: Color,
    appendMarker: Boolean = true
): AnnotatedString = buildAnnotatedString {
    append(text)
    if (appendMarker) {
        appendInlineContent("marker", "[marker]")
    }
}

private fun tajweedColor(rule: String, default: Color): Color = when (rule) {
    // Madd — prolongation (red)
    "madda_normal", "madda_permissible", "madda_obligatory", "madda_necessary" ->
        Color(0xFFFF5252)
    
    // Ghunna — nasalization (green)
    "ghunnah" -> Color(0xFF4CAF50)
    
    // Qalqalah — echo (light blue)
    "qalaqah" -> Color(0xFF40C4FF)
    
    // Ikhfa — concealment (gray)
    "ikhafa", "ikhafa_shafawi" -> Color(0xFF9E9E9E)
    
    // Idgham — assimilation (orange)
    "idgham_ghunnah", "idgham_wo_ghunnah", "idgham_mutajanisayn",
    "idgham_mutaqaribayn", "idgham_shafawi" -> Color(0xFFFF9800)
    
    // Iqlab — conversion (purple)
    "iqlab" -> Color(0xFFCE93D8)
    
    // Ham wasl / laam shamsiyah — light styling only
    "ham_wasl", "laam_shamsiyah" -> default.copy(alpha = 0.6f)
    
    // slnt — silent letter (dim)
    "slnt" -> default.copy(alpha = 0.4f)
    
    else -> default
}
