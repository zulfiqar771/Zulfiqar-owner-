package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.MessageRole
import com.example.data.CloudChatMessageDoc
import com.example.data.OWNER_EMAIL
import com.example.data.OwnerAppConfigDoc
import com.example.data.UserProfileDoc
import com.example.data.isOwnerEmail
import com.example.ui.theme.StarGreen
import com.example.ui.theme.StarYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val OwnerGold = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerControlSheet(
    allUsers: List<UserProfileDoc>,
    inspectedUser: UserProfileDoc?,
    inspectedUserMessages: List<CloudChatMessageDoc>,
    appConfig: OwnerAppConfigDoc,
    onDismiss: () -> Unit,
    onSelectUserToInspect: (UserProfileDoc?) -> Unit,
    onToggleSuspend: (UserProfileDoc) -> Unit,
    onDeleteSingleMessage: (targetUserId: String, messageId: String) -> Unit,
    onClearUserChats: (targetUserId: String) -> Unit,
    onSaveAppConfig: (
        announcement: String,
        ownerDirective: String,
        dailyImageLimit: Int,
        maintenanceMode: Boolean,
        maintenanceMessage: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = {
            onSelectUserToInspect(null)
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = OwnerGold.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, OwnerGold.copy(alpha = 0.45f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AdminPanelSettings,
                                contentDescription = null,
                                tint = OwnerGold,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.owner_panel_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = OWNER_EMAIL,
                                style = MaterialTheme.typography.labelSmall,
                                color = OwnerGold
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            onSelectUserToInspect(null)
                            onDismiss()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("owner_close_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null
                        )
                    }
                }

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("${stringResource(R.string.owner_tab_users)} (${allUsers.size})") },
                        modifier = Modifier.testTag("owner_tab_users")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.owner_tab_commands)) },
                        modifier = Modifier.testTag("owner_tab_commands")
                    )
                }

                if (selectedTab == 0) {
                    if (inspectedUser == null) {
                        UsersRosterTab(
                            allUsers = allUsers,
                            onInspectUser = onSelectUserToInspect,
                            onToggleSuspend = onToggleSuspend
                        )
                    } else {
                        UserMessagesInspectorTab(
                            user = inspectedUser,
                            messages = inspectedUserMessages,
                            onBack = { onSelectUserToInspect(null) },
                            onDeleteSingleMessage = { msgId ->
                                onDeleteSingleMessage(inspectedUser.userId, msgId)
                            },
                            onClearAllChats = {
                                onClearUserChats(inspectedUser.userId)
                            }
                        )
                    }
                } else {
                    OwnerCommandsTab(
                        appConfig = appConfig,
                        onSaveAppConfig = onSaveAppConfig
                    )
                }
            }
        }
    }
}

@Composable
private fun UsersRosterTab(
    allUsers: List<UserProfileDoc>,
    onInspectUser: (UserProfileDoc) -> Unit,
    onToggleSuspend: (UserProfileDoc) -> Unit
) {
    if (allUsers.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.owner_no_users),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "users_summary") {
            Text(
                text = stringResource(R.string.owner_users_count, allUsers.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(
            items = allUsers,
            key = { it.userId }
        ) { user ->
            val isSelfOwner = isOwnerEmail(user.email)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    if (isSelfOwner) OwnerGold.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.displayName.ifBlank { stringResource(R.string.user_label) },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = user.email?.takeIf { it.isNotBlank() } ?: user.userId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val (statusLabel, statusColor) = when {
                            isSelfOwner -> stringResource(R.string.owner_badge_label) to OwnerGold
                            user.suspended -> stringResource(R.string.owner_status_suspended) to MaterialTheme.colorScheme.error
                            else -> stringResource(R.string.owner_status_active) to StarGreen
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = statusColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onInspectUser(user) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("owner_view_chats_${user.userId}"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forum,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.owner_view_chats),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }

                        if (!isSelfOwner) {
                            Button(
                                onClick = { onToggleSuspend(user) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("owner_suspend_${user.userId}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (user.suspended) StarGreen else MaterialTheme.colorScheme.errorContainer,
                                    contentColor = if (user.suspended) Color.White else MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(
                                    imageVector = if (user.suspended) Icons.Rounded.CheckCircle else Icons.Rounded.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(
                                        if (user.suspended) R.string.owner_activate_user
                                        else R.string.owner_suspend_user
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMessagesInspectorTab(
    user: UserProfileDoc,
    messages: List<CloudChatMessageDoc>,
    onBack: () -> Unit,
    onDeleteSingleMessage: (String) -> Unit,
    onClearAllChats: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null
                    )
                }
                Column {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user.email ?: user.userId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (messages.isNotEmpty()) {
                TextButton(
                    onClick = onClearAllChats,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = stringResource(R.string.owner_clear_user_chats),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        HorizontalDivider()

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.owner_no_user_messages),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = messages,
                    key = { it.id }
                ) { msg ->
                    val isUser = msg.role == MessageRole.USER.name
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isUser) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val timeText = msg.createdAt?.toDate()?.let { formatter.format(it) } ?: ""
                                Text(
                                    text = "${if (isUser) stringResource(R.string.user_label) else stringResource(R.string.assistant_label)}  •  $timeText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUser) MaterialTheme.colorScheme.primary else StarYellow
                                )
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            IconButton(
                                onClick = { onDeleteSingleMessage(msg.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerCommandsTab(
    appConfig: OwnerAppConfigDoc,
    onSaveAppConfig: (
        announcement: String,
        ownerDirective: String,
        dailyImageLimit: Int,
        maintenanceMode: Boolean,
        maintenanceMessage: String
    ) -> Unit
) {
    var directive by remember { mutableStateOf(appConfig.ownerDirective) }
    var announcement by remember { mutableStateOf(appConfig.announcement) }
    var imageLimit by remember { mutableIntStateOf(appConfig.dailyImageLimit.coerceIn(1, 50)) }
    var maintenanceMode by remember { mutableStateOf(appConfig.maintenanceMode) }
    var maintenanceMessage by remember { mutableStateOf(appConfig.maintenanceMessage) }

    LaunchedEffect(appConfig) {
        directive = appConfig.ownerDirective
        announcement = appConfig.announcement
        imageLimit = appConfig.dailyImageLimit.coerceIn(1, 50)
        maintenanceMode = appConfig.maintenanceMode
        maintenanceMessage = appConfig.maintenanceMessage
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = directive,
            onValueChange = { directive = it.take(1200) },
            label = { Text(stringResource(R.string.owner_directive_label)) },
            placeholder = { Text(stringResource(R.string.owner_directive_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("owner_directive_input"),
            minLines = 3,
            maxLines = 5,
            shape = RoundedCornerShape(16.dp)
        )

        OutlinedTextField(
            value = announcement,
            onValueChange = { announcement = it.take(400) },
            label = { Text(stringResource(R.string.owner_announcement_label)) },
            placeholder = { Text(stringResource(R.string.owner_announcement_hint)) },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Campaign, contentDescription = null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("owner_announcement_input"),
            minLines = 2,
            maxLines = 3,
            shape = RoundedCornerShape(16.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.owner_image_limit_label, imageLimit),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { if (imageLimit > 1) imageLimit-- },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(imageVector = Icons.Rounded.Remove, contentDescription = null)
                    }
                    Text(
                        text = imageLimit.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(
                        onClick = { if (imageLimit < 50) imageLimit++ },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.owner_maintenance_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = maintenanceMode,
                        onCheckedChange = { maintenanceMode = it },
                        modifier = Modifier.testTag("owner_maintenance_switch")
                    )
                }

                if (maintenanceMode) {
                    OutlinedTextField(
                        value = maintenanceMessage,
                        onValueChange = { maintenanceMessage = it.take(300) },
                        label = { Text(stringResource(R.string.owner_maintenance_msg_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                onSaveAppConfig(
                    announcement,
                    directive,
                    imageLimit,
                    maintenanceMode,
                    maintenanceMessage
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .minimumInteractiveComponentSize()
                .testTag("owner_save_commands_button"),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OwnerGold,
                contentColor = Color(0xFF111827)
            )
        ) {
            Text(
                text = stringResource(R.string.owner_save_commands),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
