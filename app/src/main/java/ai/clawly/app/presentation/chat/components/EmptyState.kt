package ai.clawly.app.presentation.chat.components

import ai.clawly.app.BuildConfig
import ai.clawly.app.R
import ai.clawly.app.ui.theme.ClawlyColors
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private data class SuggestionChip(
    val emoji: String,
    val shortText: String,
    val fullMessage: String
)

private val suggestions = listOf(
    SuggestionChip("💻", "Write code", "Help me write some code"),
    SuggestionChip("🎙️", "Transcribe", "Transcribe my voice memo"),
    SuggestionChip("✉️", "Emails", "Summarize my unread emails"),
    SuggestionChip("✈️", "Travel", "Book hotels & flights"),
    SuggestionChip("📅", "Calendar", "What's on my calendar today?"),
    SuggestionChip("📝", "Draft", "Draft a professional email"),
    SuggestionChip("🔍", "Research", "Help me research a topic")
)

@Composable
fun EmptyState(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Staggered fade-in states
    var showLogo by remember { mutableStateOf(false) }
    var showGreeting by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }
    var showChips by remember { mutableStateOf(false) }
    var chipsFloating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showLogo = true
        delay(200)
        showGreeting = true
        delay(150)
        showSubtitle = true
        delay(200)
        showChips = true
        delay(100)
        chipsFloating = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Clawly logo (no additional shadow - ambient glow from background is enough)
        AnimatedVisibility(
            visible = showLogo,
            enter = fadeIn(animationSpec = tween(400)) +
                    scaleIn(initialScale = 0.8f, animationSpec = tween(400))
        ) {
            Image(
                painter = painterResource(id = R.drawable.clawly),
                contentDescription = "Clawly",
                modifier = Modifier.size(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Greeting
        AnimatedVisibility(
            visible = showGreeting,
            enter = fadeIn(animationSpec = tween(350)) +
                    slideInVertically(
                        initialOffsetY = { 20 },
                        animationSpec = tween(350)
                    )
        ) {
            Text(
                text = "Hey! I'm Clawly.",
                color = ClawlyColors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle
        AnimatedVisibility(
            visible = showSubtitle,
            enter = fadeIn(animationSpec = tween(300)) +
                    slideInVertically(
                        initialOffsetY = { 15 },
                        animationSpec = tween(300)
                    )
        ) {
            Text(
                text = "Ask me anything or try:",
                color = ClawlyColors.secondaryText,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Floating suggestion chips - matching iOS layout
        AnimatedVisibility(
            visible = showChips,
            enter = fadeIn(animationSpec = tween(400))
        ) {
            FloatingSuggestionChips(
                chipsFloating = chipsFloating,
                onSuggestionClick = onSuggestionClick
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FloatingSuggestionChips(
    chipsFloating: Boolean,
    onSuggestionClick: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        if (BuildConfig.IS_WEB3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingChip("", "Send SOL", 0.0, chipsFloating,
                    onClick = { onSuggestionClick("Send 2 SOL to Alex") },
                    iconRes = R.drawable.ic_solana)
                FloatingChip("\uD83D\uDD04", "Swap", 0.3, chipsFloating,
                    onClick = { onSuggestionClick("Swap 10 USDC to SOL") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingChip("\uD83D\uDCB0", "Lend USDT", 0.5, chipsFloating,
                    onClick = { onSuggestionClick("Lend 500 USDT on Kamino") })
                FloatingChip("\uD83C\uDFE6", "Borrow", 0.2, chipsFloating,
                    onClick = { onSuggestionClick("Borrow SOL against my USDC") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingChip("\uD83E\uDD16", "Auto Trade", 0.7, chipsFloating,
                    onClick = { onSuggestionClick("DCA \$50 into SOL weekly") })
                FloatingChip("\uD83D\uDCB8", "Stake", 0.4, chipsFloating,
                    onClick = { onSuggestionClick("Stake my SOL for best yield") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingChip("\uD83D\uDCC8", "Portfolio", 0.6, chipsFloating,
                    onClick = { onSuggestionClick("Show my portfolio and balances") })
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingChip("\uD83D\uDCBB", "Write code", 0.0, chipsFloating,
                    onClick = { onSuggestionClick("Help me write some code") })
                FloatingChip("\uD83C\uDF99\uFE0F", "Transcribe", 0.3, chipsFloating,
                    onClick = { onSuggestionClick("Transcribe my voice memo") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingChip("✉\uFE0F", "Emails", 0.5, chipsFloating,
                    onClick = { onSuggestionClick("Summarize my unread emails") })
                FloatingChip("✈\uFE0F", "Travel", 0.2, chipsFloating,
                    onClick = { onSuggestionClick("Book hotels & flights") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingChip("\uD83D\uDCC5", "Calendar", 0.7, chipsFloating,
                    onClick = { onSuggestionClick("What's on my calendar today?") })
                FloatingChip("\uD83D\uDCDD", "Draft", 0.4, chipsFloating,
                    onClick = { onSuggestionClick("Draft a professional email") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingChip("\uD83D\uDD0D", "Research", 0.6, chipsFloating,
                    onClick = { onSuggestionClick("Help me research a topic") })
            }
        }
    }
}

@Composable
private fun FloatingChip(
    emoji: String,
    text: String,
    delay: Double,
    isFloating: Boolean,
    onClick: () -> Unit,
    iconRes: Int? = null
) {
    val haptic = LocalHapticFeedback.current

    // Floating animation
    val infiniteTransition = rememberInfiniteTransition(label = "float_$text")
    val offset by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1800,
                easing = EaseInOut,
                delayMillis = (delay * 1000).toInt()
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset_$text"
    )

    Row(
        modifier = Modifier
            .graphicsLayer {
                translationY = if (isFloating) offset else 0f
            }
            .clip(RoundedCornerShape(20.dp))
            .background(ClawlyColors.surfaceElevated)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(
                text = emoji,
                fontSize = 16.sp
            )
        }
        Text(
            text = text,
            color = ClawlyColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
