package com.example.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.AppMode
import com.example.ai.ImageQuotaManager
import com.example.ai.ImageUtils
import com.example.ai.MessageRole
import com.example.ai.QaMessage
import com.example.ui.theme.ArabicQaTheme
import com.example.ui.theme.CobaltGlow
import com.example.ui.theme.DeepIndigoGlow
import com.example.ui.theme.StarBlue
import com.example.ui.theme.StarGreen
import com.example.ui.theme.StarRed
import com.example.ui.theme.StarYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QaChatScreen(
    viewModel: QaViewModel,
    userDisplayName: String = "",
    userEmail: String? = null,
    onSignOut: () -> Unit = {}
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val attachedImage by viewModel.attachedImage.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val remainingImageQuota by viewModel.remainingImageQuota.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appConfig by viewModel.appConfig.collectAsStateWithLifecycle()
    val isSuspended by viewModel.isSuspended.collectAsStateWithLifecycle()
    val ownerNote by viewModel.ownerNote.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val inspectedUser by viewModel.inspectedUser.collectAsStateWithLifecycle()
    val inspectedUserMessages by viewModel.inspectedUserMessages.collectAsStateWithLifecycle()
    val ownerActionFeedback by viewModel.ownerActionFeedback.collectAsStateWithLifecycle()

    val isGenerating = uiState is QaUiState.Loading ||
        uiState is QaUiState.Streaming ||
        uiState is QaUiState.GeneratingImage

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val copiedMessage = stringResource(R.string.copied_snackbar)
    val savedMessage = stringResource(R.string.image_saved_snackbar)
    val saveFailedMessage = stringResource(R.string.image_save_failed_snackbar)
    val defaultVisionPrompt = stringResource(R.string.default_vision_question)

    var showArabicKeyboard by remember { mutableStateOf(false) }
    var zoomedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showOwnerPanel by remember { mutableStateOf(false) }
    var dismissedAnnouncement by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ownerActionFeedback) {
        val feedback = ownerActionFeedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(feedback)
        viewModel.clearOwnerFeedback()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachImageFromUri(uri)
        }
    }

    val launchPhotoPicker = remember {
        {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    val stableInsertText: (String) -> Unit = remember(viewModel) { { viewModel.insertTextAtCursor(it) } }
    val stableBackspace: () -> Unit = remember(viewModel) { { viewModel.backspaceAtCursor() } }
    val stableInputValueChange: (TextFieldValue) -> Unit = remember(viewModel) { { viewModel.onInputValueChange(it) } }
    val stableSend: () -> Unit = remember(viewModel, defaultVisionPrompt) {
        { viewModel.send(defaultVisionPrompt = defaultVisionPrompt) }
    }
    val stableStop: () -> Unit = remember(viewModel) { { viewModel.stopGenerating() } }

    LaunchedEffect(messages.size, uiState) {
        val totalItems = messages.size +
            (if (uiState is QaUiState.Loading ||
                uiState is QaUiState.Streaming ||
                uiState is QaUiState.GeneratingImage
            ) 1 else 0) +
            (if (uiState is QaUiState.Error) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    ArabicQaTheme(darkTheme = isDarkTheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                CobaltBottomGlowBackground(isDarkTheme = isDarkTheme)

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        MinimalTopHeader(
                            selectedMode = selectedMode,
                            isDarkTheme = isDarkTheme,
                            hasMessages = messages.isNotEmpty() || uiState is QaUiState.Error,
                            remainingImageQuota = remainingImageQuota,
                            dailyImageLimit = appConfig.dailyImageLimit.coerceIn(1, 50),
                            userDisplayName = userDisplayName,
                            userEmail = userEmail,
                            isOwner = viewModel.isOwner,
                            onSelectMode = { mode -> viewModel.setMode(mode) },
                            onToggleTheme = { viewModel.toggleTheme() },
                            onNewChat = { viewModel.startNewConversation() },
                            onOpenOwnerPanel = { showOwnerPanel = true },
                            onSignOut = onSignOut
                        )
                    },
                    bottomBar = {
                        ChatInputDock(
                            inputValueFlow = viewModel.inputValue,
                            hasInputTextFlow = viewModel.hasInputText,
                            attachedImage = attachedImage,
                            selectedMode = selectedMode,
                            isGenerating = isGenerating,
                            showArabicKeyboard = showArabicKeyboard,
                            isDarkTheme = isDarkTheme,
                            onPickImage = launchPhotoPicker,
                            onClearAttachedImage = { viewModel.clearAttachedImage() },
                            onToggleArabicKeyboard = {
                                val next = !showArabicKeyboard
                                showArabicKeyboard = next
                                if (next) keyboardController?.hide()
                            },
                            onInputValueChange = stableInputValueChange,
                            onInsertText = stableInsertText,
                            onBackspace = stableBackspace,
                            onSend = stableSend,
                            onStop = stableStop
                        )
                    }
                ) { innerPadding ->
                    val showLockedOut = !viewModel.isOwner && (isSuspended || appConfig.maintenanceMode)
                    if (showLockedOut) {
                        AccessLockedNotice(
                            isSuspended = isSuspended,
                            customMessage = if (isSuspended) {
                                ownerNote?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.account_suspended_default)
                            } else {
                                appConfig.maintenanceMessage.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.maintenance_mode_default)
                            },
                            onSignOut = onSignOut,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 760.dp)
                            ) {
                                val announcementText = appConfig.announcement.trim()
                                if (announcementText.isNotEmpty() && dismissedAnnouncement != announcementText) {
                                    OwnerAnnouncementBanner(
                                        text = announcementText,
                                        onDismiss = { dismissedAnnouncement = announcementText }
                                    )
                                }

                                if (messages.isEmpty() &&
                                    uiState !is QaUiState.Loading &&
                                    uiState !is QaUiState.Streaming &&
                                    uiState !is QaUiState.GeneratingImage &&
                                    uiState !is QaUiState.Error
                                ) {
                                    MinimalHeroHome(
                                        userDisplayName = userDisplayName,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .weight(1f)
                                    )
                                } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(18.dp)
                                ) {
                                    items(
                                        items = messages,
                                        key = { it.id }
                                    ) { message ->
                                        ChatBubbleItem(
                                            message = message,
                                            onCopy = { textToCopy ->
                                                clipboardManager.setText(AnnotatedString(textToCopy))
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(copiedMessage)
                                                }
                                            },
                                            onZoomImage = { bmp -> zoomedBitmap = bmp },
                                            onSaveImage = { bmp ->
                                                coroutineScope.launch {
                                                    val ok = withContext(Dispatchers.IO) {
                                                        ImageUtils.saveBitmapToGallery(context, bmp)
                                                    }
                                                    snackbarHostState.showSnackbar(
                                                        if (ok) savedMessage else saveFailedMessage
                                                    )
                                                }
                                            }
                                        )
                                    }

                                    when (val state = uiState) {
                                        is QaUiState.Loading -> {
                                            item(key = "thinking_indicator") {
                                                ThinkingIndicatorBubble(
                                                    label = stringResource(R.string.thinking_label)
                                                )
                                            }
                                        }

                                        is QaUiState.GeneratingImage -> {
                                            item(key = "generating_image_indicator") {
                                                ImageGeneratingCard()
                                            }
                                        }

                                        is QaUiState.Streaming -> {
                                            item(key = "streaming_bubble") {
                                                StreamingAssistantBubble(
                                                    text = state.partialText,
                                                    onCopyCode = { code ->
                                                        clipboardManager.setText(AnnotatedString(code))
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar(copiedMessage)
                                                        }
                                                    }
                                                )
                                            }
                                        }

                                        is QaUiState.Error -> {
                                            item(key = "error_banner") {
                                                ErrorMessageCard(
                                                    message = state.message,
                                                    onRetry = { viewModel.retryLast() }
                                                )
                                            }
                                        }

                                        else -> Unit
                                    }
                                }
                                }
                            }
                        }
                    }
                }

                zoomedBitmap?.let { bmp ->
                    FullscreenImageDialog(
                        bitmap = bmp,
                        onDismiss = { zoomedBitmap = null }
                    )
                }

                if (showOwnerPanel && viewModel.isOwner) {
                    OwnerControlSheet(
                        allUsers = allUsers,
                        inspectedUser = inspectedUser,
                        inspectedUserMessages = inspectedUserMessages,
                        appConfig = appConfig,
                        onDismiss = { showOwnerPanel = false },
                        onSelectUserToInspect = { user -> viewModel.selectUserToInspect(user) },
                        onToggleSuspend = { user -> viewModel.toggleUserSuspension(user) },
                        onDeleteSingleMessage = { targetUid, msgId ->
                            viewModel.deleteSingleMessageAsOwner(targetUid, msgId)
                        },
                        onClearUserChats = { targetUid ->
                            viewModel.clearUserChatAsOwner(targetUid)
                        },
                        onSaveAppConfig = { announcement, directive, limit, maintenance, maintMsg ->
                            viewModel.saveOwnerAppConfig(
                                announcement = announcement,
                                ownerDirective = directive,
                                dailyImageLimit = limit,
                                maintenanceMode = maintenance,
                                maintenanceMessage = maintMsg
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun CobaltBottomGlowBackground(isDarkTheme: Boolean) {
    val transition = rememberInfiniteTransition(label = "cobalt_glow")
    val breath by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        if (isDarkTheme) {
            // Pure deep midnight top fading into rich royal cobalt bottom glow (like screenshot)
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.52f to Color.Transparent,
                        0.82f to DeepIndigoGlow.copy(alpha = 0.36f * breath),
                        1.0f to CobaltGlow.copy(alpha = 0.56f * breath)
                    )
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CobaltGlow.copy(alpha = 0.38f * breath),
                        DeepIndigoGlow.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 1.02f),
                    radius = w * 0.95f * breath
                ),
                radius = w * 0.95f * breath,
                center = Offset(w * 0.5f, h * 1.02f)
            )
        } else {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.60f to Color.Transparent,
                        1.0f to StarBlue.copy(alpha = 0.10f * breath)
                    )
                )
            )
        }
    }
}

@Composable
internal fun AuroraStarGlyph(
    size: Dp,
    animate: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "gemini_star")
    val pulseScale = if (animate) {
        transition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "star_pulse"
        ).value
    } else {
        1f
    }

    val colorShift = if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(4800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "star_shift"
        ).value
    } else {
        0.5f
    }

    Canvas(
        modifier = Modifier
            .size(size)
            .scale(pulseScale)
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        val outer = w * 0.45f
        val curveTension = w * 0.11f
        val starPath = Path().apply {
            moveTo(cx, cy - outer)
            cubicTo(cx + curveTension * 0.15f, cy - curveTension, cx + curveTension, cy - curveTension * 0.15f, cx + outer, cy)
            cubicTo(cx + curveTension, cy + curveTension * 0.15f, cx + curveTension * 0.15f, cy + curveTension, cx, cy + outer)
            cubicTo(cx - curveTension * 0.15f, cy + curveTension, cx - curveTension, cy + curveTension * 0.15f, cx - outer, cy)
            cubicTo(cx - curveTension, cy - curveTension * 0.15f, cx - curveTension * 0.15f, cy - curveTension, cx, cy - outer)
            close()
        }

        val shift = colorShift * w * 0.35f
        drawPath(
            path = starPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    StarRed,
                    StarYellow,
                    StarGreen,
                    StarBlue
                ),
                start = Offset(0f + shift, 0f),
                end = Offset(w, h)
            )
        )
    }
}

@Composable
private fun MinimalTopHeader(
    selectedMode: AppMode,
    isDarkTheme: Boolean,
    hasMessages: Boolean,
    remainingImageQuota: Int,
    dailyImageLimit: Int,
    userDisplayName: String,
    userEmail: String?,
    isOwner: Boolean,
    onSelectMode: (AppMode) -> Unit,
    onToggleTheme: () -> Unit,
    onNewChat: () -> Unit,
    onOpenOwnerPanel: () -> Unit,
    onSignOut: () -> Unit
) {
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Right side in RTL: App Name + Compact Unified Mode Dropdown Pill
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { modeMenuExpanded = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("mode_selector_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (selectedMode == AppMode.IMAGE_GEN) {
                        "${stringResource(R.string.mode_image_gen)} ($remainingImageQuota)"
                    } else {
                        stringResource(R.string.app_name)
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = modeMenuExpanded,
                onDismissRequest = { modeMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = stringResource(R.string.mode_auto),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = stringResource(R.string.mode_auto_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = StarBlue
                        )
                    },
                    onClick = {
                        onSelectMode(AppMode.AUTO)
                        modeMenuExpanded = false
                    },
                    modifier = Modifier.testTag("mode_auto_item")
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${stringResource(R.string.mode_image_gen)} ($remainingImageQuota/$dailyImageLimit)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Brush,
                            contentDescription = null,
                            tint = StarYellow
                        )
                    },
                    onClick = {
                        onSelectMode(AppMode.IMAGE_GEN)
                        modeMenuExpanded = false
                    },
                    modifier = Modifier.testTag("mode_image_gen_item")
                )
            }
        }

        // Left side in RTL: Theme, New Chat, and User Account / Sign-Out menu
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier
                    .size(44.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("theme_toggle_button")
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = stringResource(R.string.theme_toggle_desc),
                    tint = if (isDarkTheme) Color(0xFFFCD34D) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onNewChat,
                enabled = hasMessages,
                modifier = Modifier
                    .size(44.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("new_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddComment,
                    contentDescription = stringResource(R.string.new_chat_desc),
                    tint = if (hasMessages) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }

            if (isOwner) {
                IconButton(
                    onClick = onOpenOwnerPanel,
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("owner_panel_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AdminPanelSettings,
                        contentDescription = stringResource(R.string.owner_panel_desc),
                        tint = Color(0xFFF59E0B)
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { accountMenuExpanded = true },
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("account_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = stringResource(R.string.account_menu_desc),
                        tint = if (isOwner) Color(0xFFF59E0B) else MaterialTheme.colorScheme.secondary
                    )
                }

                DropdownMenu(
                    expanded = accountMenuExpanded,
                    onDismissRequest = { accountMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = userDisplayName.ifBlank { stringResource(R.string.user_label) },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isOwner) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = Color(0xFFF59E0B).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.owner_badge_label),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFFF59E0B),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (!userEmail.isNullOrBlank()) {
                                    Text(
                                        text = userEmail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        onClick = {},
                        enabled = false
                    )
                    if (isOwner) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.owner_panel_title),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFFF59E0B)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B)
                                )
                            },
                            onClick = {
                                accountMenuExpanded = false
                                onOpenOwnerPanel()
                            },
                            modifier = Modifier.testTag("menu_owner_panel_item")
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.sign_out_button),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            accountMenuExpanded = false
                            onSignOut()
                        },
                        modifier = Modifier.testTag("sign_out_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun OwnerAnnouncementBanner(
    text: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("owner_announcement_banner"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Campaign,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AccessLockedNotice(
    isSuspended: Boolean,
    customMessage: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = if (isSuspended) Icons.Rounded.Block else Icons.Rounded.Build,
                contentDescription = null,
                tint = if (isSuspended) MaterialTheme.colorScheme.error else Color(0xFFF59E0B),
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = stringResource(
                    if (isSuspended) R.string.account_suspended_title
                    else R.string.maintenance_mode_title
                ),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Text(
                text = customMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.minimumInteractiveComponentSize(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(stringResource(R.string.sign_out_button))
            }
        }
    }
}

@Composable
private fun MinimalHeroHome(
    userDisplayName: String,
    modifier: Modifier = Modifier
) {
    val firstName = remember(userDisplayName) {
        userDisplayName.trim().substringBefore(" ").takeIf { it.isNotBlank() && it != "أهلاً بك" }
    }
    val greeting = if (firstName != null) {
        stringResource(R.string.welcome_personal_prefix, firstName)
    } else {
        stringResource(R.string.welcome_title)
    }

    // Pure, uncluttered centered star + greeting matching the attached screenshot
    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.offset(y = (-24).dp)
        ) {
            AuroraStarGlyph(size = 54.dp, animate = true)

            Text(
                text = greeting,
                fontSize = 27.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: QaMessage,
    onCopy: (String) -> Unit,
    onZoomImage: (Bitmap) -> Unit,
    onSaveImage: (Bitmap) -> Unit
) {
    val isUser = message.role == MessageRole.USER
    if (isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 540.dp),
                shape = RoundedCornerShape(
                    topStart = 8.dp,
                    topEnd = 22.dp,
                    bottomStart = 22.dp,
                    bottomEnd = 22.dp
                ),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    message.imageBitmap?.let { bmp ->
                        ChatImageCard(
                            bitmap = bmp,
                            isGenerated = false,
                            onZoomClick = { onZoomImage(bmp) }
                        )
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    } else {
        // Sleek borderless editorial assistant response (Gemini/ChatGPT style)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AuroraStarGlyph(size = 20.dp, animate = false)
                    Text(
                        text = stringResource(R.string.assistant_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!message.isGeneratedImage && message.text.isNotBlank()) {
                    IconButton(
                        onClick = { onCopy(message.text) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("copy_answer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.copy_answer),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            message.imageBitmap?.let { bmp ->
                ChatImageCard(
                    bitmap = bmp,
                    isGenerated = message.isGeneratedImage,
                    onZoomClick = { onZoomImage(bmp) },
                    onSaveClick = { onSaveImage(bmp) }
                )
            }

            if (message.text.isNotBlank() && !message.isGeneratedImage) {
                FormattedAssistantText(
                    text = message.text,
                    textColor = MaterialTheme.colorScheme.onBackground,
                    onCopyCode = onCopy
                )
            } else if (message.isGeneratedImage && message.text.isNotBlank()) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StreamingAssistantBubble(
    text: String,
    onCopyCode: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AuroraStarGlyph(size = 20.dp, animate = true)
            Text(
                text = stringResource(R.string.assistant_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        FormattedAssistantText(
            text = text,
            textColor = MaterialTheme.colorScheme.onBackground,
            onCopyCode = onCopyCode
        )
    }
}

@Composable
private fun ThinkingIndicatorBubble(label: String) {
    val transition = rememberInfiniteTransition(label = "gemini_wave_thinking")

    val sparkleRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkle_spin"
    )
    val dot1Offset by transition.animateFloat(
        initialValue = 2f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1_y"
    )
    val dot2Offset by transition.animateFloat(
        initialValue = 2f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, delayMillis = 140, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2_y"
    )
    val dot3Offset by transition.animateFloat(
        initialValue = 2f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, delayMillis = 280, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3_y"
    )

    val dot1Alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1_a"
    )
    val dot2Alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, delayMillis = 140, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2_a"
    )
    val dot3Alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, delayMillis = 280, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3_a"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.rotate(sparkleRotation),
            contentAlignment = Alignment.Center
        ) {
            AuroraStarGlyph(size = 24.dp, animate = true)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dotSpecs = listOf(
                Triple(dot1Offset, dot1Alpha, StarBlue),
                Triple(dot2Offset, dot2Alpha, StarGreen),
                Triple(dot3Offset, dot3Alpha, StarYellow)
            )
            dotSpecs.forEach { (yOff, dAlpha, dColor) ->
                Box(
                    modifier = Modifier
                        .offset(y = yOff.dp)
                        .size(7.dp)
                        .scale(0.85f + (dAlpha * 0.2f))
                        .alpha(dAlpha)
                        .clip(CircleShape)
                        .background(dColor)
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ImageGeneratingCard() {
    Surface(
        modifier = Modifier.widthIn(max = 300.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                color = StarBlue,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp
            )
            Text(
                text = stringResource(R.string.generating_image_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorMessageCard(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier
                    .align(Alignment.End)
                    .minimumInteractiveComponentSize()
                    .testTag("retry_button"),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.retry_button),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ChatInputDock(
    inputValueFlow: StateFlow<TextFieldValue>,
    hasInputTextFlow: StateFlow<Boolean>,
    attachedImage: Bitmap?,
    selectedMode: AppMode,
    isGenerating: Boolean,
    showArabicKeyboard: Boolean,
    isDarkTheme: Boolean,
    onPickImage: () -> Unit,
    onClearAttachedImage: () -> Unit,
    onToggleArabicKeyboard: () -> Unit,
    onInputValueChange: (TextFieldValue) -> Unit,
    onInsertText: (String) -> Unit,
    onBackspace: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val hasInputText by hasInputTextFlow.collectAsStateWithLifecycle()
    val canSend = !isGenerating && (hasInputText || (selectedMode != AppMode.IMAGE_GEN && attachedImage != null))

    val placeholderText = if (selectedMode == AppMode.IMAGE_GEN) {
        stringResource(R.string.input_placeholder_image)
    } else {
        stringResource(R.string.input_placeholder_chat)
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .then(if (!showArabicKeyboard) Modifier.navigationBarsPadding() else Modifier)
    ) {
        // Attached Image Thumbnail Preview above floating pill
        AnimatedVisibility(visible = attachedImage != null) {
            attachedImage?.let { bmp ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(52.dp)) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = stringResource(R.string.attached_image_label),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.72f))
                                        .clickable(onClick = onClearAttachedImage)
                                        .testTag("clear_attached_image"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.remove_image_desc),
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.attached_image_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Sleek Floating Pill Dock (clean, minimal, zero neon clutter)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = if (isDarkTheme) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = if (isDarkTheme) 0.dp else 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onPickImage,
                    enabled = !isGenerating,
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("attach_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.attach_image_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onToggleArabicKeyboard,
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(
                            if (showArabicKeyboard) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            }
                        )
                        .testTag("arabic_keyboard_toggle")
                ) {
                    Icon(
                        imageVector = if (showArabicKeyboard) {
                            Icons.Rounded.KeyboardHide
                        } else {
                            Icons.Rounded.Keyboard
                        },
                        contentDescription = stringResource(
                            if (showArabicKeyboard) {
                                R.string.arabic_keyboard_toggle_close
                            } else {
                                R.string.arabic_keyboard_toggle_open
                            }
                        ),
                        tint = if (showArabicKeyboard) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                IsolatedQuestionTextField(
                    inputValueFlow = inputValueFlow,
                    placeholderText = placeholderText,
                    canSend = canSend,
                    showArabicKeyboard = showArabicKeyboard,
                    onFocusChange = { focused ->
                        if (focused && showArabicKeyboard) {
                            keyboardController?.hide()
                        }
                    },
                    onValueChange = onInputValueChange,
                    onSend = onSend,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(
                            when {
                                isGenerating -> MaterialTheme.colorScheme.errorContainer
                                canSend -> MaterialTheme.colorScheme.primary
                                else -> Color.Transparent
                            }
                        )
                        .clickable(enabled = isGenerating || canSend) {
                            if (isGenerating) onStop() else onSend()
                        }
                        .testTag(if (isGenerating) "stop_button" else "send_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isGenerating -> Icons.Rounded.Stop
                            selectedMode == AppMode.IMAGE_GEN -> Icons.Rounded.Brush
                            else -> Icons.AutoMirrored.Rounded.Send
                        },
                        contentDescription = stringResource(R.string.send_button_desc),
                        tint = when {
                            isGenerating -> MaterialTheme.colorScheme.onErrorContainer
                            canSend -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showArabicKeyboard,
            enter = expandVertically(tween(140)) + fadeIn(tween(120)),
            exit = shrinkVertically(tween(120)) + fadeOut(tween(100))
        ) {
            ArabicKeyboardPanel(
                canSend = canSend,
                onInsertText = onInsertText,
                onBackspace = onBackspace,
                onSend = onSend
            )
        }
    }
}

@Composable
private fun IsolatedQuestionTextField(
    inputValueFlow: StateFlow<TextFieldValue>,
    placeholderText: String,
    canSend: Boolean,
    showArabicKeyboard: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textFieldValue by inputValueFlow.collectAsStateWithLifecycle()

    TextField(
        value = textFieldValue,
        onValueChange = onValueChange,
        modifier = modifier
            .onFocusChanged { focusState ->
                onFocusChange(focusState.isFocused)
            }
            .testTag("question_input"),
        placeholder = {
            Text(
                text = placeholderText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        minLines = 1,
        maxLines = 5,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Send,
            hintLocales = LocaleList(Locale("ar"))
        ),
        keyboardActions = KeyboardActions(
            onSend = { if (canSend && !showArabicKeyboard) onSend() }
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = StarBlue
        )
    )
}
