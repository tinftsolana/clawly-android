package ai.clawly.app.presentation.chat.components

import ai.clawly.app.ui.theme.ClawlyColors
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class QuickActionType(
    val title: String,
    val icon: ImageVector,
    val iconColor: Color,
    val subtitle: String,
    val placeholder: String,
    val promptPrefix: String
) {
    NewTask(
        title = "New Task",
        icon = Icons.Default.CheckCircle,
        iconColor = Color(0xFF4ADE80),
        subtitle = "Clawly will help you get it done",
        placeholder = "What needs to be done?",
        promptPrefix = "[SYSTEM] The user wants to create a new task. Help them set it up and confirm when done.\n[USER] "
    ),
    RemindMe(
        title = "Remind Me",
        icon = Icons.Default.Notifications,
        iconColor = Color(0xFFF59E0B),
        subtitle = "Clawly will remind you on time",
        placeholder = "What should I remind you about?",
        promptPrefix = "[SYSTEM] The user wants to set a reminder. Help them schedule it and confirm when it's set.\n[USER] "
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsSheet(
    onDismiss: () -> Unit,
    onSelectPhoto: () -> Unit,
    onSelectIntegration: () -> Unit,
    onSendQuickAction: (fullPrompt: String, displayText: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ClawlyColors.background,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 16.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        var currentState by remember { mutableStateOf<QuickActionType?>(null) }

        AnimatedContent(
            targetState = currentState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "quick_action_state"
        ) { actionType ->
            if (actionType == null) {
                // Grid view
                QuickActionsGrid(
                    onPhotoClick = {
                        onDismiss()
                        onSelectPhoto()
                    },
                    onIntegrationClick = {
                        onDismiss()
                        onSelectIntegration()
                    },
                    onNewTaskClick = { currentState = QuickActionType.NewTask },
                    onRemindMeClick = { currentState = QuickActionType.RemindMe }
                )
            } else {
                // Input view
                QuickActionInputView(
                    type = actionType,
                    onBack = { currentState = null },
                    onSend = { text ->
                        val fullPrompt = actionType.promptPrefix + text
                        onDismiss()
                        onSendQuickAction(fullPrompt, text)
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickActionsGrid(
    onPhotoClick: () -> Unit,
    onIntegrationClick: () -> Unit,
    onNewTaskClick: () -> Unit,
    onRemindMeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Photo + New Integration
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(28.dp)
                    )
                },
                label = "Photo",
                onClick = onPhotoClick,
                modifier = Modifier.weight(1f)
            )
            ActionCard(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = ClawlyColors.accentPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                label = "New Integration",
                onClick = onIntegrationClick,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: New Task + Remind Me
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(28.dp)
                    )
                },
                label = "New Task",
                onClick = onNewTaskClick,
                modifier = Modifier.weight(1f)
            )
            ActionCard(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(28.dp)
                    )
                },
                label = "Remind Me",
                onClick = onRemindMeClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActionCard(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(90.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ClawlyColors.surface)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            icon()
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ClawlyColors.textPrimary
            )
        }
    }
}

@Composable
private fun QuickActionInputView(
    type: QuickActionType,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Back + icon + title row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = ClawlyColors.accentPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Icon(
                imageVector = type.icon,
                contentDescription = null,
                tint = type.iconColor,
                modifier = Modifier.size(24.dp)
            )

            Column {
                Text(
                    text = type.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = type.subtitle,
                    fontSize = 12.sp,
                    color = ClawlyColors.secondaryText
                )
            }
        }

        // Input pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(ClawlyColors.surfaceElevated)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                textStyle = TextStyle(
                    color = ClawlyColors.textPrimary,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(ClawlyColors.textPrimary),
                maxLines = 5,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 32.dp)
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = type.placeholder,
                                color = ClawlyColors.secondaryText,
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )

            // Send button
            val canSend = inputText.trim().isNotEmpty()
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) ClawlyColors.accentPrimary
                        else ClawlyColors.accentPrimary.copy(alpha = 0.4f)
                    )
                    .then(
                        if (canSend) Modifier.clickable {
                            onSend(inputText.trim())
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
