package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class KeyboardLayer {
    LETTERS,
    HAMZA_EXTRAS,
    NUMBERS
}

// Pre-allocated immutable key rows (zero allocation during recomposition)
private val LETTERS_ROW_1 = listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "د")
private val LETTERS_ROW_2 = listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط")
private val LETTERS_ROW_3 = listOf("ئ", "ء", "ؤ", "ر", "لا", "ى", "ة", "و", "ز", "ظ")

private val EXTRAS_ROW_1 = listOf("أ", "إ", "آ", "ذ", "لأ", "لإ", "لآ", "،", "؛", "؟", "!")
private val EXTRAS_ROW_2 = listOf("َ", "ُ", "ِ", "ً", "ٌ", "ٍ", "ّ", "ْ", "ـ", ".", ":")
private val EXTRAS_ROW_3 = listOf("(", ")", "«", "»", "-", "/", "%", "×", "÷", "=")

private val NUMBERS_ROW_1 = listOf("١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠")
private val NUMBERS_ROW_2 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
private val NUMBERS_ROW_3 = listOf("،", "؛", "؟", "!", ".", ":", "-", "/", "(", ")")

private val KEY_SHAPE = RoundedCornerShape(10.dp)
private val SEND_KEY_SHAPE = RoundedCornerShape(12.dp)

@Composable
fun ArabicKeyboardPanel(
    canSend: Boolean,
    onInsertText: (String) -> Unit,
    onBackspace: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    var layer by remember { mutableStateOf(KeyboardLayer.LETTERS) }
    val latestInsert by rememberUpdatedState(onInsertText)
    val latestBackspace by rememberUpdatedState(onBackspace)
    val latestSend by rememberUpdatedState(onSend)

    val stableInsert: (String) -> Unit = remember { { text -> latestInsert(text) } }
    val stableInsertAndReturnToLetters: (String) -> Unit = remember {
        { text ->
            latestInsert(text)
            layer = KeyboardLayer.LETTERS
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(horizontal = 5.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (layer) {
                    KeyboardLayer.LETTERS -> {
                        FastKeyRow(keys = LETTERS_ROW_1, onKeyDown = stableInsert)
                        FastKeyRow(keys = LETTERS_ROW_2, onKeyDown = stableInsert)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FastTapActionKey(
                                weight = 1.35f,
                                isActive = false,
                                onTrigger = { layer = KeyboardLayer.HAMZA_EXTRAS }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.keyboard_shift_desc),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            LETTERS_ROW_3.forEach { key ->
                                FastLetterKey(
                                    label = key,
                                    onTrigger = { stableInsert(key) }
                                )
                            }
                            RepeatableBackspaceKey(
                                weight = 1.35f,
                                contentDescription = stringResource(R.string.keyboard_backspace_desc),
                                onBackspace = { latestBackspace() }
                            )
                        }
                    }

                    KeyboardLayer.HAMZA_EXTRAS -> {
                        FastKeyRow(keys = EXTRAS_ROW_1, onKeyDown = stableInsertAndReturnToLetters)
                        FastKeyRow(keys = EXTRAS_ROW_2, onKeyDown = stableInsertAndReturnToLetters)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FastTapActionKey(
                                weight = 1.35f,
                                isActive = true,
                                onTrigger = { layer = KeyboardLayer.LETTERS }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.keyboard_shift_desc),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            EXTRAS_ROW_3.forEach { key ->
                                FastLetterKey(
                                    label = key,
                                    onTrigger = { stableInsertAndReturnToLetters(key) }
                                )
                            }
                            RepeatableBackspaceKey(
                                weight = 1.35f,
                                contentDescription = stringResource(R.string.keyboard_backspace_desc),
                                onBackspace = { latestBackspace() }
                            )
                        }
                    }

                    KeyboardLayer.NUMBERS -> {
                        FastKeyRow(keys = NUMBERS_ROW_1, onKeyDown = stableInsert)
                        FastKeyRow(keys = NUMBERS_ROW_2, onKeyDown = stableInsert)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FastTapActionKey(
                                weight = 1.4f,
                                isActive = false,
                                onTrigger = { layer = KeyboardLayer.LETTERS }
                            ) {
                                Text(
                                    text = "أ ب",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1
                                )
                            }
                            NUMBERS_ROW_3.forEach { key ->
                                FastLetterKey(
                                    label = key,
                                    onTrigger = { stableInsert(key) }
                                )
                            }
                            RepeatableBackspaceKey(
                                weight = 1.4f,
                                contentDescription = stringResource(R.string.keyboard_backspace_desc),
                                onBackspace = { latestBackspace() }
                            )
                        }
                    }
                }

                // Bottom control row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FastTapActionKey(
                        weight = 1.4f,
                        isActive = layer == KeyboardLayer.NUMBERS,
                        onTrigger = {
                            layer = if (layer == KeyboardLayer.NUMBERS) {
                                KeyboardLayer.LETTERS
                            } else {
                                KeyboardLayer.NUMBERS
                            }
                        }
                    ) {
                        Text(
                            text = if (layer == KeyboardLayer.NUMBERS) "عربي" else "١٢٣",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (layer == KeyboardLayer.NUMBERS) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            maxLines = 1
                        )
                    }
                    FastLetterKey(
                        label = "ذ",
                        weight = 0.9f,
                        onTrigger = { stableInsert("ذ") }
                    )
                    FastLetterKey(
                        label = "،",
                        weight = 0.9f,
                        onTrigger = { stableInsert("، ") }
                    )
                    FastLetterKey(
                        label = stringResource(R.string.keyboard_space),
                        weight = 3.6f,
                        onTrigger = { stableInsert(" ") }
                    )
                    FastLetterKey(
                        label = "؟",
                        weight = 0.9f,
                        onTrigger = { stableInsert("؟") }
                    )
                    FastSendKey(
                        canSend = canSend,
                        weight = 1.7f,
                        contentDescription = stringResource(R.string.keyboard_send),
                        onTrigger = { latestSend() }
                    )
                }
            }
        }
    }
}

@Composable
private fun FastKeyRow(
    keys: List<String>,
    onKeyDown: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keys.forEach { key ->
            FastLetterKey(
                label = key,
                onTrigger = { onKeyDown(key) }
            )
        }
    }
}

@Composable
private fun RowScope.FastLetterKey(
    label: String,
    weight: Float = 1f,
    onTrigger: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val latestTrigger by rememberUpdatedState(onTrigger)
    val haptics = LocalHapticFeedback.current

    val containerColor = if (isPressed) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Box(
        modifier = Modifier
            .weight(weight)
            .height(44.dp)
            .clip(KEY_SHAPE)
            .background(containerColor)
            .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), KEY_SHAPE)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    latestTrigger()
                    waitForUpOrCancellation()
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = if (isPressed) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun RowScope.FastTapActionKey(
    weight: Float,
    isActive: Boolean,
    onTrigger: () -> Unit,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val latestTrigger by rememberUpdatedState(onTrigger)
    val haptics = LocalHapticFeedback.current

    val bg = when {
        isPressed || isActive -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    Box(
        modifier = Modifier
            .weight(weight)
            .height(44.dp)
            .clip(KEY_SHAPE)
            .background(bg)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    latestTrigger()
                    waitForUpOrCancellation()
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun RowScope.RepeatableBackspaceKey(
    weight: Float,
    contentDescription: String,
    onBackspace: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val latestBackspace by rememberUpdatedState(onBackspace)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .weight(weight)
            .height(44.dp)
            .clip(KEY_SHAPE)
            .background(
                if (isPressed) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    latestBackspace()
                    val repeatJob = scope.launch {
                        delay(320L)
                        while (true) {
                            latestBackspace()
                            delay(55L)
                        }
                    }
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        repeatJob.cancel()
                        isPressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Backspace,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun RowScope.FastSendKey(
    canSend: Boolean,
    weight: Float,
    contentDescription: String,
    onTrigger: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val latestTrigger by rememberUpdatedState(onTrigger)
    val latestCanSend by rememberUpdatedState(canSend)
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .weight(weight)
            .height(44.dp)
            .clip(SEND_KEY_SHAPE)
            .background(
                when {
                    !canSend -> MaterialTheme.colorScheme.surfaceVariant
                    isPressed -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (latestCanSend) {
                        isPressed = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestTrigger()
                    }
                    waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .testTag("arabic_keyboard_send"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Send,
            contentDescription = contentDescription,
            tint = if (canSend) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            },
            modifier = Modifier.size(20.dp)
        )
    }
}
