package ai.clawly.app.presentation.chat.components

import ai.clawly.app.R
import ai.clawly.app.ui.theme.ClawlyColors
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun TypingIndicator(
    streamingContent: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Clawly avatar
        Image(
            painter = painterResource(id = R.drawable.clawly),
            contentDescription = "Clawly",
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(BubbleShapeAssistant)
                .background(ClawlyColors.bubbleAssistant)
                .border(
                    width = 1.dp,
                    color = ClawlyColors.bubbleAssistantBorder,
                    shape = BubbleShapeAssistant
                )
                .widthIn(max = 280.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (streamingContent.isNotEmpty()) {
                // Streaming content with cursor
                StreamingContent(content = streamingContent)
            } else {
                // Animated dots
                AnimatedDots()
            }
        }

        Spacer(modifier = Modifier.weight(1f).widthIn(min = 40.dp))
    }
}

private val BubbleShapeAssistant = RoundedCornerShape(
    topStart = 4.dp,
    topEnd = 20.dp,
    bottomStart = 20.dp,
    bottomEnd = 20.dp
)

@Composable
private fun AnimatedDots(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val delay = index * 150

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0.3f at 0
                        1f at 300
                        0.3f at 600
                        0.3f at 900
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(delay)
                ),
                label = "dot_alpha_$index"
            )

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ClawlyColors.secondaryText.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun StreamingContent(
    content: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")

    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Column(modifier = modifier) {
        // Use markdown rendering for streaming content
        MarkdownContent(
            content = content
        )

        // Pulsing cursor below text
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(2.dp)
                    .height(14.dp)
                    .background(ClawlyColors.accentPrimary.copy(alpha = cursorAlpha))
            )
        }
    }
}
