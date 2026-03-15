package ai.clawly.app.presentation.chat.components

import ai.clawly.app.R
import ai.clawly.app.domain.model.ConnectionStatus
import ai.clawly.app.ui.theme.ClawlyColors
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatNavBar(
    connectionStatus: ConnectionStatus,
    onSettingsClick: () -> Unit,
    showPremiumCrown: Boolean,
    onPremiumClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            // Transparent - gradient fade handled by parent
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Settings button (left)
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = ClawlyColors.secondaryText,
                modifier = Modifier.size(20.dp)
            )
        }

        // Center: Clawly logo + name + status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.clawly),
                    contentDescription = "Clawly",
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Clawly",
                    color = ClawlyColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = connectionStatus.displayText,
                color = connectionStatus.color,
                fontSize = 12.sp
            )
        }

        if (showPremiumCrown) {
            IconButton(
                onClick = onPremiumClick,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_crown),
                    contentDescription = "Get premium",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            // Status dot (right)
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                ConnectionDot(status = connectionStatus)
            }
        }
    }
}

@Composable
private fun ConnectionDot(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val targetColor = status.color

    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "connection_color"
    )

    // Pulsing animation for connecting state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val alpha = if (status is ConnectionStatus.Connecting) pulseAlpha else 1f

    Box(
        modifier = modifier
            .size(8.dp)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                spotColor = color.copy(alpha = 0.5f)
            )
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

// Extension properties for ConnectionStatus
private val ConnectionStatus.displayText: String
    get() = when (this) {
        is ConnectionStatus.Online -> "Connected"
        is ConnectionStatus.Connecting -> "Connecting..."
        is ConnectionStatus.Offline -> "Offline"
        is ConnectionStatus.Paused -> "Paused"
        is ConnectionStatus.Error -> "Error"
    }

private val ConnectionStatus.color: Color
    get() = when (this) {
        is ConnectionStatus.Online -> Color(0xFF34C759) // Green
        is ConnectionStatus.Connecting -> Color(0xFFFF9500) // Orange
        is ConnectionStatus.Offline -> Color(0xFF8E8E93) // Gray
        is ConnectionStatus.Paused -> Color(0xFFFF9500) // Orange
        is ConnectionStatus.Error -> Color(0xFFFF3B30) // Red
    }
