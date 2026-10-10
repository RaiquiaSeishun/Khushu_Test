package com.kaizen.khushu

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.ViewModelStore
import com.kaizen.khushu.data.repository.SettingsRepository
import com.kaizen.khushu.ui.screens.quran.QuranReaderScreen
import com.kaizen.khushu.ui.screens.quran.QuranViewModel
import com.kaizen.khushu.ui.screens.quran.QuranAudioViewModel
import com.kaizen.khushu.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
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

    @Test fun actualReaderShowsDownloadedTranslationAfterModeAndEditionSwitches() =
        withFiles(listOf("eng_mustafakhattaba", "test_reader_second")) {
            TranslationRepository.install(context, "eng_mustafakhattaba", fixture("saved Mustafa fixture"))
            TranslationRepository.install(context, "test_reader_second", fixture("second edition fixture"))
            val repository = SettingsRepository(context)
            val original = runBlocking { repository.settingsFlow.first() }
            val store = ViewModelStore()
            val chapter = mutableIntStateOf(1)
            lateinit var quran: QuranViewModel
            lateinit var settings: SettingsViewModel
            lateinit var audio: QuranAudioViewModel
            try {
                runBlocking {
                    repository.updateSelectedTranslationLang("eng_mustafakhattaba")
                    repository.updateShowTranslation(true)
                    repository.setShowTafsir(false)
                    repository.updateSelectedScript("uthmani")
                    repository.updateUseGpsLocation(false)
                }
                compose.runOnIdle {
                    val app = context.applicationContext as Application
                    quran = QuranViewModel(app).also { store.put("quran", it) }
                    settings = SettingsViewModel(repository, context).also { store.put("settings", it) }
                    audio = QuranAudioViewModel(app).also { store.put("audio", it) }
                }
                compose.setContent {
                    MaterialTheme {
                        QuranReaderScreen(surahNumber = chapter.intValue, onBack = {}, onNextSurah = { chapter.intValue = it },
                            viewModel = quran, settingsViewModel = settings, media3Controller = null, quranAudioViewModel = audio)
                    }
                }
                fun awaitTranslation(text: String) {
                    compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText(text).assertIsDisplayed()
                }
                awaitTranslation("saved Mustafa fixture 1:1")
                compose.onNodeWithText("Reading", useUnmergedTree = true).performClick()
                compose.onNodeWithText("saved Mustafa fixture 1:1").assertDoesNotExist()
                compose.onNodeWithText("Verse by Verse", useUnmergedTree = true).performClick()
                awaitTranslation("saved Mustafa fixture 1:1")
                runBlocking { repository.updateSelectedTranslationLang("test_reader_second") }
                awaitTranslation("second edition fixture 1:1")
                compose.onNodeWithText("saved Mustafa fixture 1:1").assertDoesNotExist()
                // Opening the longest chapter must retain its translation after preparation.
                compose.runOnIdle { chapter.intValue = 2 }
                compose.waitUntil(30_000) { quran.loadedSurah.value == 2 && !quran.isLoading.value }
                compose.runOnIdle { assertEquals(286, quran.currentBlocks.value.size) }
                compose.onNodeWithTag("quran-verses").performScrollToNode(hasText("second edition fixture 2:1"))
                compose.onNodeWithText("second edition fixture 2:1").assertIsDisplayed()
                compose.runOnIdle { chapter.intValue = 114 }
                compose.waitUntil(30_000) { quran.loadedSurah.value == 114 && !quran.isLoading.value }
                compose.onNodeWithTag("quran-verses").performScrollToNode(hasText("second edition fixture 114:1"))
                compose.onNodeWithText("second edition fixture 114:1").assertIsDisplayed()
                compose.onNodeWithText("second edition fixture 2:1").assertDoesNotExist()
                runBlocking { repository.updateShowTranslation(false) }
                compose.waitUntil(10_000) { compose.onAllNodesWithText("second edition fixture 114:1").fetchSemanticsNodes().isEmpty() }
                runBlocking { repository.updateShowTranslation(true) }
                awaitTranslation("second edition fixture 114:1")
            } finally {
                compose.runOnIdle { store.clear() }
                runBlocking {
                    repository.updateSelectedTranslationLang(original.selectedTranslationLang)
                    repository.updateShowTranslation(original.showTranslation)
                    repository.setShowTafsir(original.showTafsir)
                    repository.updateSelectedScript(original.selectedScript)
                    repository.updateUseGpsLocation(original.useGpsLocation)
                }
            }
        }

    @Test fun rapidlySupersededChapterLoadsCannotPublishTheWrongChapter() {
        val store = ViewModelStore()
        lateinit var quran: QuranViewModel
        try {
            compose.runOnIdle {
                quran = QuranViewModel(context.applicationContext as Application).also { store.put("quran", it) }
                quran.loadSurah(2, "en_20")
                quran.loadSurah(1, "ur_54")
                quran.loadSurah(114, "en_20")
            }
            compose.waitUntil(30_000) { quran.loadedSurah.value == 114 && !quran.isLoading.value }
            compose.runOnIdle {
                assertEquals(6, quran.currentBlocks.value.size)
                assertTrue(quran.currentBlocks.value.all { it.surah == 114 })
                assertEquals(QuranRepository.getTranslation(context, 114, "en_20"), quran.currentTranslation.value)
                assertTrue(quran.currentBlocks.value.all { !it.translationEn.isNullOrBlank() })
            }
        } finally { compose.runOnIdle { store.clear() } }
    }
}
