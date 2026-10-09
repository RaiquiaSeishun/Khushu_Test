package com.kaizen.khushu.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.kaizen.khushu.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String,
    @SerialName("body") val body: String? = null,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("assets") val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

data class UpdateInfo(
    val latestVersionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
)

sealed interface UpdateCheckResult {
    data object Checking : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult
    data class Error(val message: String, val isRetryable: Boolean = false) : UpdateCheckResult
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data class Ready(val apkFile: File) : DownloadState
    data class Failed(val message: String) : DownloadState
}

object AppUpdateRepository {
    private const val GITHUB_API_URL = "https://api.github.com/repos/RaiquiaSeishun/Khushu_Test/releases/latest"
    private const val GITHUB_ACCEPT_HEADER = "application/vnd.github.v3+json"
    // Must match the authority declared in AndroidManifest.xml for the FileProvider.
    // Using applicationId so it resolves correctly across product flavors.
    private val PROVIDER_AUTHORITY: String by lazy { "${BuildConfig.APPLICATION_ID}.update_provider" }
    private const val USER_AGENT = "Sukun/${BuildConfig.VERSION_NAME} (Android; +https://github.com/RaiquiaSeishun/Khushu_Test)"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Whether this build supports in-app updates (full/GitHub flavor only). */
    val isUpdateableBuild: Boolean get() = BuildConfig.FLAVOR == "full"

    /**
     * Checks for an update on GitHub. Returns [UpdateCheckResult.Error] immediately
     * if called on a non-updateable build (F-Droid).
     */
    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (!isUpdateableBuild) {
            return@withContext UpdateCheckResult.Error(
                "In-app updates are not available on this build. Please update through F-Droid.",
                isRetryable = false
            )
        }
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", GITHUB_ACCEPT_HEADER)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val msg = when (response.code) {
                    403 -> "Rate-limited by GitHub. Try again later."
                    404 -> "Release endpoint not found."
                    in 500..599 -> "GitHub server error (${response.code}). Try again later."
                    else -> "GitHub API returned ${response.code}"
                }
                return@withContext UpdateCheckResult.Error(msg, isRetryable = true)
            }

            val body = response.body?.string() ?: return@withContext UpdateCheckResult.Error("Empty response from GitHub", isRetryable = true)
            val release = json.decodeFromString<GitHubRelease>(body)

            // Prerelease policy: skip prerelease releases on stable channel.
            if (release.prerelease) {
                return@withContext UpdateCheckResult.Error(
                    "Latest release is a pre-release. Download it manually from GitHub.",
                    isRetryable = false
                )
            }

            val tagVersion = release.tagName.removePrefix("v")
            val currentVersionName = BuildConfig.VERSION_NAME
                .substringBefore("+") // strip any +gitHash suffix for semantic compare

            if (compareVersions(tagVersion, currentVersionName) < 0) {
                return@withContext UpdateCheckResult.Error(
                    "Installed version ($currentVersionName) is newer than the latest release ($tagVersion). You may be on a development build.",
                    isRetryable = false
                )
            }
            if (compareVersions(tagVersion, currentVersionName) == 0) {
                return@withContext UpdateCheckResult.UpToDate
            }

            // Look for a 'full' APK asset — prefer universal, fall back to ABI-specific.
            val apkAsset = release.assets.find {
                it.name.contains("full", ignoreCase = true) &&
                    it.name.endsWith(".apk", ignoreCase = true) &&
                    it.name.contains("universal", ignoreCase = true)
            } ?: release.assets.find {
                it.name.contains("full", ignoreCase = true) &&
                    it.name.endsWith(".apk", ignoreCase = true)
            }

            if (apkAsset == null) {
                val assetNames = release.assets.joinToString(", ") { it.name }
                return@withContext UpdateCheckResult.Error(
                    "No full-APK asset found in latest release (found: $assetNames)",
                    isRetryable = false
                )
            }

            UpdateCheckResult.UpdateAvailable(
                UpdateInfo(
                    latestVersionName = tagVersion,
                    releaseNotes = release.body ?: "",
                    downloadUrl = apkAsset.browserDownloadUrl,
                )
            )
        } catch (e: java.net.SocketTimeoutException) {
            UpdateCheckResult.Error("Connection timed out. Check your internet connection.", isRetryable = true)
        } catch (e: java.net.UnknownHostException) {
            UpdateCheckResult.Error("No internet connection.", isRetryable = true)
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Unknown error checking for updates.", isRetryable = true)
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit,
    ): DownloadState = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(downloadUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext DownloadState.Failed("Download failed: ${response.code}")
            }

            val body = response.body ?: return@withContext DownloadState.Failed("Empty response body")
            val contentLength = body.contentLength()
            val fileName = "khushu-update-${BuildConfig.VERSION_NAME}.apk"
            val directory = File(context.cacheDir, "updates")
            if (!directory.isDirectory && !directory.mkdirs()) {
                return@withContext DownloadState.Failed("Cannot create update cache")
            }
            val file = File(directory, fileName)

            FileOutputStream(file).use { output ->
                val inputStream = body.byteStream()
                val buffer = ByteArray(8192)
                var bytesRead = 0L
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        onProgress(bytesRead.toFloat() / contentLength)
                    }
                }
            }

            DownloadState.Ready(file)
        } catch (e: Exception) {
            DownloadState.Failed(e.message ?: "Download failed")
        }
    }

    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, PROVIDER_AUTHORITY, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Returns > 0 if v1 is newer, < 0 if older, 0 if equal. */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}
