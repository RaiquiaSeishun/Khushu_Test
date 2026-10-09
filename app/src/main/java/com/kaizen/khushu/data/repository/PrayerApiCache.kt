package com.kaizen.khushu.data.repository

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A request URL includes every calculation input. Entries remain useful after process death. */
class PrayerApiCache(private val clock: () -> Long = System::currentTimeMillis) {
    @Serializable
    private data class Entry(val savedAt: Long, val timings: Map<String, String>)

    private val mutex = Mutex()
    private val memory = linkedMapOf<String, Entry>()
    private val pending = mutableMapOf<String, CompletableDeferred<Map<String, String>?>>()
    private val lifetimeMs = 24 * 60 * 60_000L

    suspend fun get(
        requestUrl: String,
        directory: File?,
        fetch: suspend () -> Map<String, String>?,
    ): Map<String, String>? {
        val hash = MessageDigest.getInstance("SHA-256").digest(requestUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = directory?.let { File(it, "$hash.json") }
        fun valid(entry: Entry): Boolean = clock() - entry.savedAt in 0 until lifetimeMs

        var owner = false
        val request = mutex.withLock {
            val cached = memory[requestUrl] ?: file?.let {
                runCatching { Json.decodeFromString<Entry>(it.readText()) }.getOrNull()
            }
            if (cached != null && valid(cached)) {
                memory[requestUrl] = cached
                trimMemory()
                return cached.timings
            }
            memory.remove(requestUrl)
            pending.getOrPut(requestUrl) { owner = true; CompletableDeferred() }
        }
        if (!owner) return request.await()
        try {
            val timings = fetch()
            if (timings != null) mutex.withLock {
                val entry = Entry(clock(), timings.toMap())
                memory[requestUrl] = entry
                trimMemory()
                // Cache failures must not discard a successful network response.
                if (file != null) runCatching {
                    directory!!.mkdirs()
                    val temporary = File(directory, "$hash.tmp")
                    temporary.writeText(Json.encodeToString(entry))
                    if (!temporary.renameTo(file)) temporary.delete()
                    directory.listFiles()?.filter { it.extension == "json" }
                        ?.sortedByDescending { it.lastModified() }?.drop(32)?.forEach { it.delete() }
                }
            }
            request.complete(timings)
            return timings
        } catch (failure: Throwable) {
            request.completeExceptionally(failure)
            throw failure
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { pending.remove(requestUrl) }
            }
        }
    }

    private fun trimMemory() {
        while (memory.size > 32) memory.remove(memory.keys.first())
    }
}
