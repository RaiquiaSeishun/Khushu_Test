package com.kaizen.khushu.logic

import kotlinx.serialization.json.*

/** Decode supported provider formats without accepting duplicates, missing verses or blank text. */
object TranslationData {
    private val whitespace = Regex("\\s+")
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(content: String): Map<String, String> {
        val root = json.parseToJsonElement(content).jsonObject
        val rows = root["quran"] ?: root["result"]
        if (rows == null) return root.mapValues { (_, value) -> clean(value.jsonPrimitive.content) }
        val result = linkedMapOf<String, String>()
        for (row in rows.jsonArray) {
            val item = row.jsonObject
            val chapter = (item["chapter"] ?: item["sura"])?.jsonPrimitive?.intOrNull
                ?: error("Translation is missing a chapter number")
            val verse = (item["verse"] ?: item["aya"])?.jsonPrimitive?.intOrNull
                ?: error("Translation is missing a verse number")
            val text = (item["text"] ?: item["translation"])?.jsonPrimitive?.content
                ?: error("Translation is missing verse text")
            val key = "$chapter:$verse"
            require(result.put(key, clean(text)) == null) { "Duplicate translation verse: $key" }
        }
        return result
    }
    fun validate(map: Map<String, String>, expectedKeys: Set<String>): Map<String, String> {
        require(expectedKeys.size == 6236 && map.keys == expectedKeys && map.values.none { it.isBlank() || '\uFFFD' in it }) {
            "Translation is incomplete or damaged"
        }
        return map
    }
    fun qf(content: String, expectedKeys: List<String>, resourceId: Int): Map<String, String> {
        val root = json.parseToJsonElement(content).jsonObject
        val rows = root["translations"]?.jsonArray ?: error("Translation is unavailable")
        require(rows.size == expectedKeys.size) { "Translation is incomplete" }
        // This bulk endpoint returns translations in canonical chapter/verse order.
        return expectedKeys.zip(rows).associate { (key, row) ->
            val item = row.jsonObject
            require(item["resource_id"]?.jsonPrimitive?.intOrNull == resourceId) { "Wrong translation resource" }
            key to clean(item["text"]!!.jsonPrimitive.content)
        }
    }
    private fun clean(text: String) = QuranMarkup.plainText(text).replace(whitespace, " ").trim()
    fun encode(map: Map<String, String>): String = JsonObject(map.mapValues { JsonPrimitive(it.value) }).toString()
}
