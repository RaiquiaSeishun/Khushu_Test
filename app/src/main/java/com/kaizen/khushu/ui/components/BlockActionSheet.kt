package com.kaizen.khushu.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaizen.khushu.data.model.*
import com.kaizen.khushu.data.repository.UserSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockActionSheet(
    block: ContentBlock,
    topicId: String,
    settings: UserSettings,
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onBookmark: () -> Unit,
    onPlayAyah: (() -> Unit)? = null,
    onPlayFromHere: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    
    val (title, contentToCopy, referenceToCopy) = when (block) {
        is AyahBlock -> Triple("Ayah Actions", block.textUthmani ?: "", block.display)
        is HadithBlock -> Triple("Hadith Actions", block.textEn ?: "", block.display)
        is HeadingBlock -> Triple("Section Actions", block.text, null)
        is ArabicBlock -> Triple("Arabic Text Actions", block.text, null)
        else -> Triple("Actions", "", null)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 1. Audio Actions (Top priority for Ayahs)
            if (block is AyahBlock) {
                onPlayAyah?.let {
                    ActionRow(
                        icon = Icons.Default.PlayArrow,
                        label = "Play Ayah",
                        onClick = it
                    )
                }
                onPlayFromHere?.let {
                    ActionRow(
                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        label = "Play from here",
                        onClick = it
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // 2. Personal Actions
            ActionRow(
                icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                label = if (isBookmarked) "Remove Bookmark" else "Add Bookmark",
                onClick = onBookmark
            )

            // 3. Sharing & Utility Actions
            ActionRow(
                icon = Icons.Default.Share,
                label = "Share",
                onClick = {
                    val shareIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, "$contentToCopy\n\n— $referenceToCopy")
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share via"))
                    onDismiss()
                }
            )

            if (contentToCopy.isNotBlank()) {
                ActionRow(
                    icon = Icons.Default.ContentCopy,
                    label = "Copy Text",
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Sukun Text", contentToCopy)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
            }

            if (referenceToCopy != null) {
                ActionRow(
                    icon = Icons.Default.Link,
                    label = "Copy Reference",
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Sukun Reference", referenceToCopy)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Reference copied: $referenceToCopy", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
