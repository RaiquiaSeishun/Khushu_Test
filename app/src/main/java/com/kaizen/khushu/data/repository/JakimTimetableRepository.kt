package com.kaizen.khushu.data.repository

import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** Official, explicitly selected zone timetables. No location is sent to e-Solat. */
class JakimTimetableRepository(
    private val directory: File?,
    private val client: OkHttpClient,
    private val endpoint: String = "https://www.e-solat.gov.my/index.php",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Result(val times: Map<String, Date>?, val notice: String)
    @Serializable private data class Snapshot(val fetchedAt: Long, val response: String)
    private val mutex = Mutex()
    private val memory = mutableMapOf<String, Snapshot>()
    private val failedAt = mutableMapOf<String, Long>()

    companion object {
        val timeZone: ZoneId = ZoneId.of("Asia/Kuala_Lumpur")
        private const val REFRESH_MS = 24 * 60 * 60_000L
        private val months = listOf(
            listOf("jan"), listOf("feb"), listOf("mac", "mar"), listOf("apr"),
            listOf("mei", "may"), listOf("jun"), listOf("jul"), listOf("ogo", "ogos", "aug"),
            listOf("sep"), listOf("okt", "oct"), listOf("nov"), listOf("dis", "dec"),
        ).flatMapIndexed { index, names -> names.map { it to index + 1 } }.toMap()
        fun localDate(date: Date): LocalDate = date.toInstant().atZone(timeZone).toLocalDate()
    }

    suspend fun get(date: Date, zone: String, forceRefresh: Boolean = false): Result = withContext(Dispatchers.IO) {
        if (zone !in JakimZones.labels) return@withContext Result(null,
            "Choose an official JAKIM prayer zone in Settings. Showing approximate local fallback times.")
        val day = localDate(date)
        val month = YearMonth.from(day)
        val key = "$zone-$month"
        mutex.withLock {
            val file = directory?.let { File(it, "$key.json") }
            val disk = file?.let { runCatching { Json.decodeFromString<Snapshot>(it.readText()) }.getOrNull() }
            var snapshot = listOfNotNull(memory[key], disk).maxByOrNull { it.fetchedAt }
            var cached = snapshot?.let { runCatching { parse(it.response, zone, month) }.getOrNull() }
            val age = snapshot?.let { clock() - it.fetchedAt }
            var fromCache = true
            val retryAllowed = forceRefresh || (failedAt[key]?.let { clock() - it !in 0 until 60_000L } ?: true)
            if (!retryAllowed && cached == null) return@withLock Result(null,
                "Official JAKIM timetable unavailable for $zone / $day. Showing approximate local fallback times.")
            if (retryAllowed && (forceRefresh || cached == null || age == null || age !in 0 until REFRESH_MS)) {
                try {
                    val url = endpoint.toHttpUrl().newBuilder()
                        .addQueryParameter("r", "esolatApi/takwimsolat")
                        .addQueryParameter("period", "duration")
                        .addQueryParameter("zone", zone).build()
                    val body = FormBody.Builder()
                        .add("datestart", month.atDay(1).toString())
                        .add("dateend", month.atEndOfMonth().toString()).build()
                    client.newCall(Request.Builder().url(url).post(body).build()).execute().use { response ->
                        coroutineContext.ensureActive()
                        check(response.isSuccessful) { "HTTP ${response.code}" }
                        val raw = response.body!!.string()
                        cached = parse(raw, zone, month)
                        snapshot = Snapshot(clock(), raw)
                    }
                    coroutineContext.ensureActive()
                    failedAt.remove(key)
                    memory[key] = snapshot!!
                    fromCache = false
                    runCatching {
                        if (file != null) {
                            directory.mkdirs()
                            val temporary = File(directory, "$key.tmp")
                            temporary.writeText(Json.encodeToString(snapshot))
                            if (!temporary.renameTo(file)) temporary.delete()
                            directory.listFiles()?.filter { it.extension == "json" }
                                ?.sortedByDescending { it.lastModified() }?.drop(24)?.forEach { it.delete() }
                        }
                    }
                    while (memory.size > 24) memory.remove(memory.keys.first())
                } catch (failure: CancellationException) { throw failure }
                catch (_: Exception) {
                    coroutineContext.ensureActive()
                    failedAt[key] = clock()
                    if (cached == null) return@withLock Result(null,
                        "Official JAKIM timetable unavailable for $zone / $day. Showing approximate local fallback times.")
                }
            }
            val retrieved = Instant.ofEpochMilli(snapshot!!.fetchedAt).atZone(timeZone)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            Result(cached!!.getValue(day),
                "${if (fromCache) "Cached official" else "Official"} JAKIM/e-Solat · $zone · $day · retrieved $retrieved. " +
                    "Night fractions are derived, not official timetable entries." +
                    if (key in failedAt) " Revision check unavailable; using the saved timetable." else "")
        }
    }

    private fun parse(raw: String, zone: String, month: YearMonth): Map<LocalDate, Map<String, Date>> {
        val root = Json.parseToJsonElement(raw).jsonObject
        require(root["status"]?.jsonPrimitive?.content == "OK!")
        require(root["zone"]?.jsonPrimitive?.content == zone)
        val days = linkedMapOf<LocalDate, Map<String, Date>>()
        val mapping = linkedMapOf("Fajr" to "fajr", "Sunrise" to "syuruk", "Dhuhr" to "dhuhr",
            "Asr" to "asr", "Maghrib" to "maghrib", "Isha" to "isha", "Imsak" to "imsak")
        for (entry in root.getValue("prayerTime").jsonArray) {
            val row = entry.jsonObject
            val parts = row.getValue("date").jsonPrimitive.content.split('-')
            require(parts.size == 3)
            val day = LocalDate.of(parts[2].toInt(), months.getValue(parts[1].lowercase(Locale.ROOT)), parts[0].toInt())
            require(YearMonth.from(day) == month && day !in days)
            val times = mapping.mapValues { (_, field) ->
                val text = row.getValue(field).jsonPrimitive.content
                require(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]").matches(text))
                Date.from(day.atTime(LocalTime.parse(text)).atZone(timeZone).toInstant())
            }
            val ordered = listOf("Imsak", "Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha").map { times.getValue(it) }
            require(ordered.zipWithNext().all { (a, b) -> a.before(b) })
            days[day] = times
        }
        require(days.keys == (1..month.lengthOfMonth()).map { month.atDay(it) }.toSet())
        return days
    }
}
