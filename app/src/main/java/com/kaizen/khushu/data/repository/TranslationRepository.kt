package com.kaizen.khushu.data.repository

import android.content.Context
import android.util.AtomicFile
import com.kaizen.khushu.data.model.TranslationMeta
import com.kaizen.khushu.logic.TranslationData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

object TranslationRepository {
    private val cache = ConcurrentHashMap<String, Map<String, String>>()
    private fun safeId(id: String) = id.matches(Regex("[A-Za-z0-9_-]+"))
    private fun expectedKeys(context: Context): List<String> = QuranRepository.getChapters(context)
        .flatMap { chapter -> (1..chapter.versesCount).map { "${chapter.id}:$it" } }

    fun isDownloaded(context: Context, id: String): Boolean = load(context, id).isNotEmpty()

    /** Includes bundled editions, which are available offline without a download. */
    fun downloadedIds(context: Context): Set<String> = TranslationMeta.BUNDLED +
        File(context.filesDir, "translations").listFiles().orEmpty()
            .filter { it.extension == "json" && isDownloaded(context, it.nameWithoutExtension) }
            .map { it.nameWithoutExtension }

    suspend fun download(context: Context, id: String, url: String, onProgress: (Float) -> Unit = {}) =
        downloadFrom(context, id, url, onProgress, ::fetch)

    internal suspend fun downloadFrom(context: Context, id: String, url: String,
        onProgress: (Float) -> Unit = {}, fetcher: suspend (String) -> String) = withContext(Dispatchers.IO) {
        require(safeId(id)) { "Invalid translation ID" }
        val keys = expectedKeys(context)
        val map = when {
            url.startsWith("qf:translation:") -> {
                val resourceId = url.substringAfterLast(':').toIntOrNull() ?: error("Invalid translation resource")
                TranslationData.qf(fetcher("https://api.quran.com/api/v4/quran/translations/$resourceId"), keys, resourceId)
            }
            "{surah}" in url -> {
                val combined = linkedMapOf<String, String>()
                for (chapter in 1..114) {
                    coroutineContext.ensureActive()
                    val part = TranslationData.parse(fetcher(url.replace("{surah}", chapter.toString())))
                    require(part.keys == keys.filter { it.startsWith("$chapter:") }.toSet()) { "Translation chapter is incomplete" }
                    combined.putAll(part)
                    onProgress(chapter / 114f * 0.95f)
                }
                combined
            }
            else -> TranslationData.parse(fetcher(url))
        }
        TranslationData.validate(map, keys.toSet())
        coroutineContext.ensureActive()
        install(context, id, map)
        onProgress(1f)
    }

    private suspend fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Translation source is unavailable")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = reader.read(buffer)
                    if (count < 0) break
                    result.append(buffer, 0, count)
                    if (result.length > 10_000_000) throw IOException("Translation response is too large")
                }
                result.toString()
            }
        } finally { connection.disconnect() }
    }

    internal fun install(context: Context, id: String, map: Map<String, String>) {
        require(safeId(id))
        TranslationData.validate(map, expectedKeys(context).toSet())
        val dir = File(context.filesDir, "translations").apply { mkdirs() }
        val atomic = AtomicFile(File(dir, "$id.json"))
        val stream = atomic.startWrite()
        try {
            stream.write(TranslationData.encode(map).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
            cache[id] = map
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    fun load(context: Context, id: String): Map<String, String> {
        if (!safeId(id)) return emptyMap()
        cache[id]?.let { return it }
        return try {
            val content = if (id in TranslationMeta.BUNDLED) {
                context.assets.open("translations/$id.json").bufferedReader().use { it.readText() }
            } else {
                val file = File(context.filesDir, "translations/$id.json")
                // AtomicFile can recover a previously valid file after an interrupted replacement.
                if (!file.exists() && !File(file.path + ".bak").exists()) return emptyMap()
                AtomicFile(file).openRead().bufferedReader().use { it.readText() }
            }
            val map = TranslationData.validate(TranslationData.parse(content), expectedKeys(context).toSet())
            cache[id] = map
            map
        } catch (_: Exception) { emptyMap() }
    }

    fun getTranslation(map: Map<String, String>, surah: Int, ayah: Int): String? = map["$surah:$ayah"]
    fun getCachedMap(id: String): Map<String, String>? = cache[id]
    internal fun clearCache() = cache.clear()
}
