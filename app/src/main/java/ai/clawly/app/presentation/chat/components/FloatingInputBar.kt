package ai.clawly.app.presentation.chat.components

import ai.clawly.app.R
import ai.clawly.app.presentation.chat.PendingAttachment
import ai.clawly.app.ui.theme.ClawlyColors
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FloatingInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAddAttachment: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAbort: () -> Unit,
    isAssistantTyping: Boolean,
    isAborting: Boolean,
    pendingAttachments: List<PendingAttachment>,
    enabled: Boolean,
    onMicClick: () -> Unit = {},
    isRecording: Boolean = false,
    rmsLevel: Float = 0f,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val hasContent = value.isNotBlank() || pendingAttachments.isNotEmpty()
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
    ) {
        // Glass pill container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(26.dp),
                    spotColor = Color.Black.copy(alpha = 0.15f)
                )
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF1F1F1F)) // Dark fill like iOS
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(26.dp)
                )
        ) {
            // Attachment previews INSIDE the pill
            AnimatedVisibility(
                visible = pendingAttachments.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .padding(bottom = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pendingAttachments.forEach { attachment ->
                        AttachmentThumbnail(
                            attachment = attachment,
                            onRemove = { onRemoveAttachment(attachment.id) }
                        )
                    }
                }
            }

            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Plus button for attachments (hidden when recording)
                if (!isRecording) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable(enabled = pendingAttachments.size < 4) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddAttachment()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add attachment",
                            tint = ClawlyColors.secondaryText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Show wave animation when recording, text input otherwise
                if (isRecording) {
                    VoiceWaveAnimation(
                        rmsLevel = rmsLevel,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    )
                } else {
                    // Text input
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = true,
                        textStyle = TextStyle(
                            color = ClawlyColors.textPrimary,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(ClawlyColors.textPrimary),
                        maxLines = 5,
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (value.isEmpty()) {
                                    Text(
                                        text = "Message",
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
                }

                // Action button (send/mic/stop) — stays bottom-aligned
                Box(modifier = Modifier.padding(bottom = 2.dp)) {
                    ActionButton(
                        hasContent = hasContent,
                        isTyping = isAssistantTyping,
                        isAborting = isAborting,
                        isRecording = isRecording,
                        onSend = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSend()
                        },
                        onAbort = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAbort()
                        },
                        onMicClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMicClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    hasContent: Boolean,
    isTyping: Boolean,
    isAborting: Boolean,
    isRecording: Boolean,
    onSend: () -> Unit,
    onAbort: () -> Unit,
    onMicClick: () -> Unit
) {
    when {
        // Recording indicator
        isRecording -> {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ClawlyColors.accentPrimary)
                    .clickable { onMicClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_microphone),
                    contentDescription = "Recording",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Send button always available when there's content
        hasContent -> {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ClawlyColors.accentPrimary)
                    .clickable { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Mic button when empty
        else -> {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onMicClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_microphone),
                    contentDescription = "Voice input",
                    tint = ClawlyColors.secondaryText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentThumbnail(
    attachment: PendingAttachment,
    onRemove: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val bitmap = remember(attachment.id) {
        try {
            BitmapFactory.decodeByteArray(attachment.data, 0, attachment.data.size)
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier.size(60.dp)
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Attachment",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
            )
        }

        // Remove button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRemove()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun VoiceWaveAnimation(
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val barCount = 5
    // Normalize rmsLevel (typically -2 to 10 dB range)
    val normalizedLevel = ((rmsLevel + 2f) / 12f).coerceIn(0f, 1f)

    // Animated values for each bar with different phases
    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    val animations = (0 until barCount).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 600,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(index * 100)
            ),
            label = "bar$index"
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        animations.forEachIndexed { index, animation ->
            val baseHeight = animation.value
            val rmsBoost = normalizedLevel * 0.5f
            val height = (baseHeight + rmsBoost).coerceIn(0.2f, 1f)

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ClawlyColors.accentPrimary)
            )
        }
    }
}
