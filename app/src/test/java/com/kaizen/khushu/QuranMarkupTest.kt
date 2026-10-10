package com.kaizen.khushu

import com.kaizen.khushu.logic.QuranMarkup
import com.kaizen.khushu.logic.TranslationData
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class QuranMarkupTest {
    @Test fun nestedTajweedPreservesLettersAndBothRulesWithoutLeakingTags() {
        val text = "وَلَا تَعْتَد<tajweed class=madda_obligatory>ُوٓ<tajweed class=slnt>اْ</tajweed>‌ۚ</tajweed> <span class=end>١٩٠</span>"
        assertEquals("وَلَا تَعْتَدُوٓاْ‌ۚ", QuranMarkup.plainText(text))
        val inner = QuranMarkup.segments(text).single { it.text == "اْ" }
        assertEquals(listOf("madda_obligatory", "slnt"), inner.rules)
        assertEquals(listOf("madda_obligatory"), QuranMarkup.segments(text).single { it.text == "‌ۚ" }.rules)
    }

    @Test fun everyBundledVerseRetainsItsArabicTextWithoutProviderEndNumbers() {
        val verses = Json.parseToJsonElement(File("src/main/assets/quran/uthmani_tajweed.json").readText()).jsonObject
        assertEquals(6236, verses.size)
        for ((key, value) in verses) {
            val markup = value.jsonPrimitive.content
            // Independent source transformation for the bundled, known HTML-like dataset.
            val expected = markup.replace(Regex("<span class=end>[^<]*</span>"), "")
                .replace(Regex("<[^>]+>"), "").trim()
            assertEquals(key, expected, QuranMarkup.plainText(markup))
            assertFalse(key, '<' in QuranMarkup.plainText(markup))
        }
    }

    @Test fun footnotesAreRemovedByMarkupRatherThanDeletingLegitimateNumbers() {
        assertEquals("Allāh, Merciful. 40 days & nights", QuranMarkup.plainText(
            "Allāh,<sup foot_note=1>1</sup> Merciful.<sup>2</sup> 40 days &amp; nights"))
        assertEquals("One Two", TranslationData.parse("""{"1:1":"One<br>Two"}""")["1:1"])
    }
}
