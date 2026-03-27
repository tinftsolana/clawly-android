package ai.clawly.app.presentation.chat.components

import ai.clawly.app.R
import ai.clawly.app.domain.model.ChatMessage
import ai.clawly.app.ui.theme.ClawlyColors
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ApiKeyRequestBubble(
    message: ChatMessage,
    onClick: () -> Unit
) {
    val parts = message.content
        .removePrefix("__api_key_request__:")
        .split(":")
    val keyName = parts.firstOrNull() ?: "API_KEY"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Image(
            painter = painterResource(id = R.drawable.clawly),
            contentDescription = "Clawly",
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ClawlyColors.surface)
                .border(
                    width = 1.dp,
                    color = ClawlyColors.accentPrimary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(onClick = onClick)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = ClawlyColors.accentPrimary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enter API Key",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ClawlyColors.textPrimary
                    )
                    Text(
                        text = keyName,
                        fontSize = 12.sp,
                        color = ClawlyColors.secondaryText
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ClawlyColors.secondaryText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
