package com.kaizen.khushu

import com.kaizen.khushu.logic.TranslationData
import org.junit.Assert.*
import org.junit.Test

class TranslationDataTest {
    @Test fun fawazAndQuranEncKeepTheirOwnVerseNumbersAndCleanFootnotes() {
        assertEquals(mapOf("2:255" to "Example"), TranslationData.parse(
            """{"quran":[{"chapter":2,"verse":255,"text":"Example"}]}"""))
        assertEquals(mapOf("2:255" to "Example"), TranslationData.parse(
            """{"result":[{"sura":"2","aya":"255","translation":"Example<sup>1</sup>","footnotes":"note"}]}"""))
    }

    @Test fun duplicateAndIncompleteResponsesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { TranslationData.parse(
            """{"quran":[{"chapter":1,"verse":1,"text":"A"},{"chapter":1,"verse":1,"text":"B"}]}""") }
        val keys = (1..6236).map { "fixture:$it" }.toSet()
        val full = keys.associateWith { "fixture" }
        assertEquals(full, TranslationData.validate(full, keys))
        assertThrows(IllegalArgumentException::class.java) { TranslationData.validate(full - "fixture:6236", keys) }
        assertThrows(IllegalArgumentException::class.java) { TranslationData.validate(full + ("fixture:1" to " "), keys) }
        assertThrows(IllegalArgumentException::class.java) { TranslationData.validate(full + ("extra:1" to "fixture"), keys) }
    }

    @Test fun qfRequiresTheRequestedResourceAndTheCompleteCanonicalRowOrder() {
        val response = """{"translations":[{"resource_id":20,"text":"First<sup>1</sup>"},{"resource_id":20,"text":"Second"}]}"""
        assertEquals(mapOf("1:1" to "First", "1:2" to "Second"), TranslationData.qf(response, listOf("1:1", "1:2"), 20))
        assertThrows(IllegalArgumentException::class.java) { TranslationData.qf(response, listOf("1:1", "1:2"), 54) }
        assertThrows(IllegalArgumentException::class.java) { TranslationData.qf(response, listOf("1:1"), 20) }
    }
}
