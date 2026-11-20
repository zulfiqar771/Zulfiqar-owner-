package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.CodeBlockAccent
import com.example.ui.theme.CodeBlockBackground
import com.example.ui.theme.CodeBlockHeader
import com.example.ui.theme.CodeBlockText

sealed interface MessageSegment {
    data class PlainText(val text: String) : MessageSegment
    data class CodeBlock(val language: String, val code: String) : MessageSegment
}

fun parseMessageSegments(rawText: String): List<MessageSegment> {
    if (!rawText.contains("```")) {
        return listOf(MessageSegment.PlainText(rawText))
    }

    val segments = mutableListOf<MessageSegment>()
    val regex = Regex("```([a-zA-Z0-9_+-]*)\\n?([\\s\\S]*?)```")
    var lastIndex = 0

    for (match in regex.findAll(rawText)) {
        if (match.range.first > lastIndex) {
            val textPart = rawText.substring(lastIndex, match.range.first).trim()
            if (textPart.isNotEmpty()) {
                segments.add(MessageSegment.PlainText(textPart))
            }
        }
        val lang = match.groupValues[1].ifBlank { "code" }.uppercase()
        val code = match.groupValues[2].trimEnd()
        if (code.isNotEmpty()) {
            segments.add(MessageSegment.CodeBlock(lang, code))
        }
        lastIndex = match.range.last + 1
    }

    if (lastIndex < rawText.length) {
        val tail = rawText.substring(lastIndex).trim()
        if (tail.isNotEmpty()) {
            segments.add(MessageSegment.PlainText(tail))
        }
    }

    return segments.ifEmpty { listOf(MessageSegment.PlainText(rawText)) }
}

@Composable
fun FormattedAssistantText(
    text: String,
    textColor: Color,
    onCopyCode: (String) -> Unit
) {
    val segments = remember(text) { parseMessageSegments(text) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MessageSegment.PlainText -> {
                    Text(
                        text = segment.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }

                is MessageSegment.CodeBlock -> {
                    CodeBlockCard(
                        language = segment.language,
                        code = segment.code,
                        onCopyCode = { onCopyCode(segment.code) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    onCopyCode: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = CodeBlockBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CodeBlockHeader)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = CodeBlockAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = language,
                            color = CodeBlockAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onCopyCode)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("copy_code_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.copy_code),
                            tint = CodeBlockText.copy(alpha = 0.85f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.copy_code),
                            color = CodeBlockText.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = code,
                        color = CodeBlockText,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun ChatImageCard(
    bitmap: Bitmap,
    isGenerated: Boolean,
    onZoomClick: () -> Unit,
    onSaveClick: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = if (isGenerated) {
                stringResource(R.string.image_generated_caption)
            } else {
                stringResource(R.string.attached_image_label)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onZoomClick),
            contentScale = ContentScale.Crop
        )

        if (isGenerated && onSaveClick != null) {
            FilledTonalButton(
                onClick = onSaveClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_generated_image_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.save_image),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun FullscreenImageDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.attached_image_label),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.remove_image_desc),
                    tint = Color.White
                )
            }
        }
    }
}
