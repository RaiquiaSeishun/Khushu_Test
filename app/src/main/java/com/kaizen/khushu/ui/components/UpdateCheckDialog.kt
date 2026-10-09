package com.kaizen.khushu.ui.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaizen.khushu.data.repository.AppUpdateRepository
import com.kaizen.khushu.data.repository.DownloadState
import com.kaizen.khushu.data.repository.UpdateCheckResult
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun UpdateCheckDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checkResult by remember { mutableStateOf<UpdateCheckResult>(UpdateCheckResult.Checking) }
    // Cache the APK path as a simple string so it survives config changes across dialog show/hides.
    var cachedApkPath by rememberSaveable { mutableStateOf<String?>(null) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

    // Restore Ready state from cached path if it still exists on disk.
    LaunchedEffect(cachedApkPath) {
        if (cachedApkPath != null && downloadState !is DownloadState.Ready) {
            val file = File(cachedApkPath)
            if (file.exists()) {
                downloadState = DownloadState.Ready(file)
            } else {
                cachedApkPath = null
            }
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = (downloadState as? DownloadState.Downloading)?.progress ?: 0f,
        animationSpec = tween(100),
        label = "download_progress",
    )

    LaunchedEffect(Unit) {
        checkResult = AppUpdateRepository.checkForUpdate()
    }

    val onRetry: () -> Unit = {
        checkResult = UpdateCheckResult.Checking
        scope.launch {
            checkResult = AppUpdateRepository.checkForUpdate()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (downloadState !is DownloadState.Downloading) onDismiss()
        },
        title = {
            Text(
                text = when (checkResult) {
                    is UpdateCheckResult.Checking -> "Checking for Updates"
                    is UpdateCheckResult.UpToDate -> "Up to Date"
                    is UpdateCheckResult.UpdateAvailable -> "Update Available"
                    is UpdateCheckResult.Error -> "Check Failed"
                },
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (checkResult is UpdateCheckResult.UpdateAvailable)
                            Modifier.height(280.dp).verticalScroll(rememberScrollState())
                        else Modifier
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val result = checkResult) {
                    is UpdateCheckResult.Checking -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                            )
                        }
                        Text(
                            text = "Querying GitHub releases...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is UpdateCheckResult.UpToDate -> {
                        Text(
                            text = "Sukun ${com.kaizen.khushu.BuildConfig.VERSION_NAME} is the latest version.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    is UpdateCheckResult.UpdateAvailable -> {
                        Text(
                            text = "Version ${result.info.latestVersionName} is available.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Spacer(Modifier.height(4.dp))

                        if (result.info.releaseNotes.isNotBlank()) {
                            Text(
                                text = "Release Notes",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = result.info.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        when (val dl = downloadState) {
                            is DownloadState.Idle -> {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            downloadState = DownloadState.Downloading(0f)
                                            val resultState = AppUpdateRepository.downloadApk(
                                                context = context,
                                                downloadUrl = result.info.downloadUrl,
                                                onProgress = { progress ->
                                                    downloadState = DownloadState.Downloading(progress)
                                                },
                                            )
                                            downloadState = resultState
                                            if (resultState is DownloadState.Ready) {
                                                cachedApkPath = resultState.apkFile.absolutePath
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Download & Install")
                                }
                            }

                            is DownloadState.Downloading -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Downloading...",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        text = "${(animatedProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            is DownloadState.Ready -> {
                                Button(
                                    onClick = {
                                        val intent = AppUpdateRepository.createInstallIntent(context, dl.apkFile)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open installer", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Install Update")
                                }
                            }

                            is DownloadState.Failed -> {
                                Text(
                                    text = dl.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Check your internet connection and storage space, then try again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            downloadState = DownloadState.Downloading(0f)
                                            val resultState = AppUpdateRepository.downloadApk(
                                                context = context,
                                                downloadUrl = result.info.downloadUrl,
                                                onProgress = { progress ->
                                                    downloadState = DownloadState.Downloading(progress)
                                                },
                                            )
                                            downloadState = resultState
                                            if (resultState is DownloadState.Ready) {
                                                cachedApkPath = resultState.apkFile.absolutePath
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }

                    is UpdateCheckResult.Error -> {
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (checkResult) {
                is UpdateCheckResult.Checking -> {}
                is UpdateCheckResult.UpToDate -> {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                is UpdateCheckResult.UpdateAvailable -> {
                    if (downloadState is DownloadState.Ready) {
                        TextButton(onClick = onDismiss) { Text("Later") }
                    } else if (downloadState !is DownloadState.Downloading) {
                        TextButton(onClick = onDismiss) { Text("Later") }
                    }
                }
                is UpdateCheckResult.Error -> {
                    val err = checkResult as UpdateCheckResult.Error
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (err.isRetryable) {
                            TextButton(onClick = onRetry) { Text("Retry") }
                        }
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
            Unit
        },
    )
}
