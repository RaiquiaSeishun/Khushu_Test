package com.kaizen.khushu

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaizen.khushu.data.model.ContentSource
import com.kaizen.khushu.data.repository.QuranRepository
import com.kaizen.khushu.data.repository.TranslationRepository
import com.kaizen.khushu.logic.TranslationData
import com.kaizen.khushu.ui.components.TranslationPickerSheet
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class QuranReliabilityInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val keys get() = QuranRepository.getChapters(context).flatMap { chapter ->
        (1..chapter.versesCount).map { "${chapter.id}:$it" }
    }
    // Clearly synthetic text, confined to the emulator's private test files.
    private fun fixture(label: String = "fixture") = keys.associateWith { "$label $it" }
    private fun withFiles(ids: List<String>, check: () -> Unit) {
        val files = ids.flatMap { id -> listOf(".json", ".json.bak", ".json.new").map { File(context.filesDir, "translations/$id$it") } }
        val backups = files.associateWith { if (it.exists()) it.readBytes() else null }
        TranslationRepository.clearCache()
        try { check() } finally {
            for ((file, bytes) in backups) if (bytes == null) file.delete() else { file.parentFile!!.mkdirs(); file.writeBytes(bytes) }
            TranslationRepository.clearCache()
        }
    }

    @Test fun failedAndIncompleteDownloadsPreserveThePreviousValidatedEdition() = withFiles(listOf("test_translation", "test_partial")) {
        runBlocking {
            val original = fixture("original")
            TranslationRepository.install(context, "test_translation", original)
            try {
                TranslationRepository.downloadFrom(context, "test_translation", "https://example.test/edition") { throw IOException("interrupted") }
                fail("Interrupted download should fail")
            } catch (_: IOException) { }
            try {
                TranslationRepository.downloadFrom(context, "test_translation", "https://example.test/edition") {
                    """{"quran":[{"chapter":1,"verse":1,"text":"incomplete fixture"}]}"""
                }
                fail("Incomplete edition should fail")
            } catch (_: IllegalArgumentException) { }
            TranslationRepository.clearCache()
            assertEquals(original, TranslationRepository.load(context, "test_translation"))
            val broken = File(context.filesDir, "translations/test_partial.json")
            broken.writeText("{\"quran\":[")
            assertFalse(TranslationRepository.isDownloaded(context, "test_partial"))
            assertEquals(emptyMap<String, String>(), TranslationRepository.load(context, "test_partial"))
            assertFalse("No unfinished replacement should remain", File(context.filesDir, "translations/test_translation.json.new").exists())
        }
    }

    @Test fun qfAndQuranEncRoutesInstallAllVersesUsingTheirProviderFormats() = withFiles(listOf("test_qf", "test_quranenc")) {
        runBlocking {
            val requests = mutableListOf<String>()
            val qf = JsonObject(mapOf("translations" to JsonArray(keys.map {
                JsonObject(mapOf("resource_id" to JsonPrimitive(20), "text" to JsonPrimitive("fixture $it<sup>1</sup>")))
            }))).toString()
            TranslationRepository.downloadFrom(context, "test_qf", "qf:translation:20") { url -> requests += url; qf }
            assertEquals(listOf("https://api.quran.com/api/v4/quran/translations/20"), requests)
            assertEquals(fixture(), TranslationRepository.load(context, "test_qf"))
            requests.clear()
            TranslationRepository.downloadFrom(context, "test_quranenc", "https://example.test/{surah}") { url ->
                requests += url
                val chapter = url.substringAfterLast('/').toInt()
                JsonObject(mapOf("result" to JsonArray(keys.filter { it.startsWith("$chapter:") }.map {
                    JsonObject(mapOf("sura" to JsonPrimitive(chapter.toString()), "aya" to JsonPrimitive(it.substringAfter(':')),
                        "translation" to JsonPrimitive("fixture $it")))
                }))).toString()
            }
            assertEquals((1..114).map { "https://example.test/$it" }, requests)
            assertEquals(fixture(), TranslationRepository.load(context, "test_quranenc"))
        }
    }

    @Test fun downloadedCategoryShowsSavedEditionsAcrossSourcesAndSwitchesWithoutDownloading() =
        withFiles(listOf("eng_mustafakhattaba", "19", "eng_mustafakhattabg")) {
            TranslationRepository.install(context, "eng_mustafakhattaba", fixture())
            TranslationRepository.install(context, "19", fixture())
            File(context.filesDir, "translations/eng_mustafakhattabg.json").writeText("{}")
            var selected: String? = null
            compose.setContent {
                MaterialTheme {
                    TranslationPickerSheet(selectedId = "eng_mustafakhattaba", selectedSource = ContentSource.QURANENC,
                        isDownloading = false, progress = 0f, onSelectSource = {}, onSelect = { selected = it.id }, onDismiss = {})
                }
            }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Mustafa Khattab Allah Edition").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Downloaded").assertIsSelected()
            compose.onNodeWithText("Mustafa Khattab Allah Edition").assertIsDisplayed()
            compose.onNodeWithText("Mohammed Marmaduke William Pickthall").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals("19", selected) }
            compose.onNodeWithText("Mustafa Khattab God Edition").assertDoesNotExist()
            compose.onNodeWithText("Browse").performClick()
            compose.onNodeWithText("Source").assertIsDisplayed()
        }
}
