package com.kaizen.khushu.data.repository

import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Community geography lookup only. Published prayer entries still come directly from JAKIM. */
class JakimZoneLocator(
    private val cacheFile: File? = null,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).followRedirects(false).build(),
    private val endpoint: String = "https://api.waktusolat.app/zones/",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Result(val zone: String?, val notice: String)
    private data class Area(val zone: String, val district: String)
    private val mutex = Mutex()
    private var snapshot: JsonObject? = null

    suspend fun locate(lat: Double, lng: Double, accuracy: Float): Result = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0 ||
                !accuracy.isFinite() || accuracy <= 0 || accuracy > 1_000) {
                return@withLock Result(null, "A location accurate to 1 km or better is needed. Retry GPS or choose a zone manually.")
            }
            val key = String.format(Locale.US, "%.6f,%.6f", lat, lng)
            val radius = max(30.0, accuracy.toDouble())
            val saved = snapshot ?: runCatching { Json.parseToJsonElement(cacheFile!!.readText()).jsonObject }.getOrNull()
            if (saved != null && runCatching {
                    saved.getValue("key").jsonPrimitive.content == key &&
                    saved.getValue("radius").jsonPrimitive.double >= radius &&
                    clock() - saved.getValue("savedAt").jsonPrimitive.long in 0..86_400_000L &&
                    saved.getValue("zone").jsonPrimitive.content in JakimZones.labels
                }.getOrDefault(false)) {
                snapshot = saved
                return@withLock Result(saved.getValue("zone").jsonPrimitive.content,
                    "Saved GPS zone from the Waktu Solat community map for this location. Verify it against your official zone, especially in special/highland areas.")
            }
            try {
                withTimeout(25_000L) {
                    val center = query(lat, lng)
                    // District maps cannot distinguish these published special/mukim zones reliably.
                    val district = center.district.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
                    val needsManual = when (center.zone) {
                        "KDH02" -> district == "yan"
                        "PRK06" -> "larut" in district || "matang" in district || "taiping" in district
                        "SBH07" -> district == "ranau"
                        "PHG04" -> district in setOf("bentong", "raub")
                        "PHG02", "PHG07" -> district == "rompin"
                        "KTN01", "KTN02" -> district == "guamusang"
                        "PRK03", "PRK04" -> true
                        "SWK01" -> district == "limbang"
                        else -> false
                    }
                    if (needsManual) return@withTimeout Result(null,
                        "This area includes special or subdistrict prayer zones that the community map cannot reliably distinguish. Choose your official zone manually.")
                    val deltaLat = radius / 111_320.0
                    val deltaLng = radius / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
                    val surrounding = coroutineScope {
                        listOf(lat + deltaLat to lng, lat - deltaLat to lng,
                            lat to lng + deltaLng, lat to lng - deltaLng).map { (a, b) -> async { query(a, b) } }.awaitAll()
                    }
                    if (surrounding.any { it.zone != center.zone }) return@withTimeout Result(null,
                        "GPS uncertainty reaches more than one prayer zone. Retry with a more precise fix or choose your zone manually.")
                    val json = buildJsonObject {
                        put("key", key); put("radius", radius); put("zone", center.zone); put("savedAt", clock())
                    }
                    snapshot = json
                    cacheFile?.let { file ->
                        runCatching {
                            file.parentFile?.mkdirs()
                            val temporary = File(file.path + ".tmp")
                            temporary.writeText(json.toString())
                            check(temporary.renameTo(file))
                        }
                    }
                    Result(center.zone, "GPS selected ${center.zone} using the Waktu Solat community map. Verify your official zone, especially in special/highland areas.")
                }
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                Result(null, "GPS zone lookup timed out. Retry online or choose your zone manually.")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Result(null, "No reliable GPS zone result. You may be offline, outside Malaysia or near a mapped boundary. Retry or choose your zone manually.")
            }
        }
    }

    private suspend fun query(lat: Double, lng: Double): Area {
        val url = endpoint.toHttpUrl().newBuilder()
            .addPathSegment(String.format(Locale.US, "%.6f", lat))
            .addPathSegment(String.format(Locale.US, "%.6f", lng)).build()
        val raw = suspendCancellableCoroutine<String> { continuation ->
            val call = client.newCall(Request.Builder().url(url).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use {
                            check(it.isSuccessful)
                            it.body!!.string().also { body -> check(body.length <= 65_536) }
                        }
                        if (continuation.isActive) continuation.resume(text)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
        val json = Json.parseToJsonElement(raw).jsonObject
        require(listOf("zone", "state", "district").all { json.getValue(it).jsonPrimitive.isString })
        val zone = json.getValue("zone").jsonPrimitive.content
        require(zone in JakimZones.labels)
        val states = when {
            zone == "WLY01" -> setOf("WLY", "KUL", "PJY")
            zone == "WLY02" -> setOf("WLY", "LBN")
            zone.startsWith("NGS") -> setOf("NGS", "NSN")
            else -> setOf(zone.take(3))
        }
        require(json.getValue("state").jsonPrimitive.content in states)
        val district = json.getValue("district").jsonPrimitive.content
        require(district.isNotBlank())
        return Area(zone, district)
    }
}
