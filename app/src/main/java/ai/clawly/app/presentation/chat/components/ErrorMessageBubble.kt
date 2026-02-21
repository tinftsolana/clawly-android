package ai.clawly.app.presentation.chat.components

import ai.clawly.app.domain.model.ChatErrorType
import ai.clawly.app.domain.model.ChatMessage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ErrorMessageBubble(
    message: ChatMessage,
    onRetry: () -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Error avatar
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFF3B30).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = Color(0xFFFF3B30),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Error message bubble
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(Color(0xFF2C2C2E))
                    .widthIn(max = 280.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = message.content,
                        color = Color(0xFFFF6B6B),
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (message.errorType) {
                            ChatErrorType.SendFailed -> {
                                OutlinedButton(
                                    onClick = onRetry,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("Retry", fontSize = 13.sp)
                                }
                            }
                            ChatErrorType.Timeout -> {
                                OutlinedButton(
                                    onClick = onRetry,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("Retry", fontSize = 13.sp)
                                }
                            }
                            ChatErrorType.ConnectionError -> {
                                OutlinedButton(
                                    onClick = onReconnect,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("Reconnect", fontSize = 13.sp)
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
