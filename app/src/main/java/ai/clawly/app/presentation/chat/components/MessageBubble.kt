package ai.clawly.app.presentation.chat.components

import ai.clawly.app.R
import ai.clawly.app.domain.model.ChatMessage
import ai.clawly.app.domain.model.MessageAttachment
import ai.clawly.app.ui.theme.ClawlyColors
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

// Bubble shapes matching iOS
private val UserBubbleShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomStart = 20.dp,
    bottomEnd = 4.dp
)

private val AssistantBubbleShape = RoundedCornerShape(
    topStart = 4.dp,
    topEnd = 20.dp,
    bottomStart = 20.dp,
    bottomEnd = 20.dp
)

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!message.isUser) {
            // Clawly avatar for assistant
            Image(
                painter = painterResource(id = R.drawable.clawly),
                contentDescription = "Clawly",
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // Attachments above text for user messages
            if (message.isUser && message.hasAttachments) {
                AttachmentPreview(
                    attachments = message.attachments,
                    isUser = true
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Message bubble
            if (message.content.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(if (message.isUser) UserBubbleShape else AssistantBubbleShape)
                        .background(
                            if (message.isUser) ClawlyColors.bubbleUser else ClawlyColors.bubbleAssistant
                        )
                        .then(
                            if (!message.isUser) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = ClawlyColors.bubbleAssistantBorder,
                                    shape = AssistantBubbleShape
                                )
                            } else Modifier
                        )
                ) {
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.content))
                                showMenu = false
                            }
                        )
                    }

                    if (message.isUser) {
                        // User messages: plain text with white color
                        Text(
                            text = message.content,
                            color = ClawlyColors.textPrimary,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    } else {
                        // Assistant messages: markdown rendering
                        MarkdownContent(
                            content = message.content,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Timestamp
            Text(
                text = timeFormatter.format(message.timestamp),
                color = ClawlyColors.textTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (message.isUser) {
            Spacer(modifier = Modifier.width(8.dp))
        }

        if (!message.isUser) {
            Spacer(modifier = Modifier.weight(1f).widthIn(min = 40.dp))
        }
    }
}

@Composable
private fun AttachmentPreview(
    attachments: List<MessageAttachment>,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        attachments.take(4).forEach { attachment ->
            val bitmap = remember(attachment.id) {
                try {
                    BitmapFactory.decodeByteArray(attachment.imageData, 0, attachment.imageData.size)
                } catch (e: Exception) {
                    null
                }
            }

            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                )
            }
        }
    }
}

/**
 * Assistant content renderer.
 * Uses plain Compose Text to avoid AppCompat widget requirements from markdown library.
 */
@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = content,
        modifier = modifier,
        style = TextStyle(
            color = ClawlyColors.textPrimary,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
    )
}
