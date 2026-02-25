package ai.clawly.app.presentation.settings

import ai.clawly.app.domain.model.HostingType
import ai.clawly.app.domain.model.ManagedInstanceStatus
import ai.clawly.app.ui.theme.ClawlyColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Full-screen Auth Provider selection screen
 * Matches settings screen design with glow effect
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthProviderScreen(
    onNavigateBack: () -> Unit,
    onConfigured: () -> Unit,
    onNavigateToInstanceSetup: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    viewModel: AuthProviderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val density = LocalDensity.current
    val glowColor = ClawlyColors.accentPrimary
    val topBarHeight = 56.dp

    // Handle self-hosted config dialog
    if (uiState.showSelfHostedConfig) {
        SelfHostedConfigScreen(
            initialUrl = uiState.selfHostedUrl,
            initialToken = uiState.selfHostedToken,
            onDismiss = { viewModel.hideSelfHostedConfig() },
            onSave = { url, token ->
                viewModel.saveSelfHosted(
                    url = url,
                    token = token,
                    onSuccess = { onConfigured() },
                    onError = { /* Error shown via uiState.showError */ }
                )
            }
        )
        return
    }

    // Handle provisioning status view
    if (uiState.showProvisioningView) {
        ProvisioningStatusScreen(
            status = uiState.provisioningStatus,
            lastError = uiState.provisioningError,
            onDismiss = {
                viewModel.hideProvisioningView()
                onNavigateBack()
            },
            onReady = {
                viewModel.hideProvisioningView()
                onConfigured()
            },
            onRetry = { viewModel.retryProvisioning() }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClawlyColors.background)
    ) {
        // Top radial glow effect
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.3f),
                                glowColor.copy(alpha = 0.15f),
                                glowColor.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(widthPx / 2f, 0f),
                            radius = heightPx * 1.2f
                        )
                    )
            )
        }

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(top = topBarHeight)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            ClawlyColors.accentPrimary.copy(alpha = 0.15f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = ClawlyColors.accentPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Set Up Clawly",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClawlyColors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Choose how you want to connect",
                    fontSize = 15.sp,
                    color = ClawlyColors.secondaryText
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Options section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ClawlyColors.surfaceElevated)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                // OpenClaw Cloud (recommended)
                SetupOptionRow(
                    icon = Icons.Default.Favorite,
                    iconTint = ClawlyColors.accentPrimary,
                    title = "OpenClaw Cloud",
                    subtitle = "Easiest way to get started",
                    isRecommended = true,
                    isLoading = uiState.isCreatingInstance,
                    onClick = {
                        if (!uiState.hasPremiumAccess) {
                            onNavigateToPaywall()
                        } else {
                            viewModel.createManagedInstance(
                                onSuccess = { viewModel.showProvisioningView() },
                                onError = { /* Error handled in ViewModel */ }
                            )
                        }
                    }
                )

                // Divider
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    thickness = 0.5.dp
                )

                // Self-Hosted
                SetupOptionRow(
                    icon = Icons.Default.Home,
                    iconTint = Color.Cyan,
                    title = "Self-Hosted",
                    subtitle = "Connect to your own server",
                    isRecommended = false,
                    isLoading = false,
                    onClick = { viewModel.showSelfHostedConfig() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info text
            Text(
                text = "OpenClaw Cloud includes automatic setup, updates, and support. Self-hosted requires your own OpenClaw gateway.",
                fontSize = 13.sp,
                color = ClawlyColors.textMuted,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))
        }

        // Floating header with gradient fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ClawlyColors.background,
                            ClawlyColors.background.copy(alpha = 0.85f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = with(density) { 100.dp.toPx() }
                    )
                )
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ClawlyColors.accentPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    // Error dialog
    if (uiState.showError) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(uiState.error ?: "An unknown error occurred") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun SetupOptionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isRecommended: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading) { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(iconTint.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = iconTint,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClawlyColors.textPrimary
                )

                if (isRecommended) {
                    Text(
                        text = "Recommended",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(
                                ClawlyColors.accentPrimary,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = ClawlyColors.secondaryText
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ClawlyColors.textMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Self-Hosted Config Screen with matching design
 */
@Composable
private fun SelfHostedConfigScreen(
    initialUrl: String = "",
    initialToken: String = "",
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf(initialToken) }

    val density = LocalDensity.current
    val glowColor = Color.Cyan
    val topBarHeight = 56.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClawlyColors.background)
    ) {
        // Top radial glow effect (cyan for self-hosted)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.25f),
                                glowColor.copy(alpha = 0.12f),
                                glowColor.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            center = Offset(widthPx / 2f, 0f),
                            radius = heightPx * 1.2f
                        )
                    )
            )
        }

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(top = topBarHeight)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Color.Cyan.copy(alpha = 0.15f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = Color.Cyan,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Self-Hosted Gateway",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClawlyColors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connect to your own OpenClaw server",
                    fontSize = 15.sp,
                    color = ClawlyColors.secondaryText
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Form section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ClawlyColors.surfaceElevated)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(20.dp)
            ) {
                // URL Field
                Text(
                    text = "GATEWAY URL",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClawlyColors.secondaryText,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = {
                        Text(
                            "wss://your-gateway.com",
                            color = ClawlyColors.textMuted
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Cyan,
                        unfocusedBorderColor = ClawlyColors.surfaceBorder,
                        focusedTextColor = ClawlyColors.textPrimary,
                        unfocusedTextColor = ClawlyColors.textPrimary,
                        cursorColor = Color.Cyan
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Token Field
                Text(
                    text = "AUTH TOKEN (OPTIONAL)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClawlyColors.secondaryText,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    placeholder = {
                        Text(
                            "Enter your auth token",
                            color = ClawlyColors.textMuted
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Cyan,
                        unfocusedBorderColor = ClawlyColors.surfaceBorder,
                        focusedTextColor = ClawlyColors.textPrimary,
                        unfocusedTextColor = ClawlyColors.textPrimary,
                        cursorColor = Color.Cyan
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            val isValid = url.trim().isNotEmpty()
            Button(
                onClick = { onSave(url.trim(), token.trim()) },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Cyan,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.Cyan.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Connect",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        // Floating header with gradient fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ClawlyColors.background,
                            ClawlyColors.background.copy(alpha = 0.85f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = with(density) { 100.dp.toPx() }
                    )
                )
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Cyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Provisioning Status Screen with matching design
 */
@Composable
private fun ProvisioningStatusScreen(
    status: ManagedInstanceStatus,
    lastError: String?,
    onDismiss: () -> Unit,
    onReady: () -> Unit,
    onRetry: () -> Unit
) {
    val isProvisioning = status == ManagedInstanceStatus.Queued ||
            status == ManagedInstanceStatus.Provisioning ||
            status == ManagedInstanceStatus.Installing

    val density = LocalDensity.current
    val glowColor = getStatusColor(status)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClawlyColors.background)
    ) {
        // Top radial glow effect (color based on status)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.3f),
                                glowColor.copy(alpha = 0.15f),
                                glowColor.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(widthPx / 2f, 0f),
                            radius = heightPx * 1.2f
                        )
                    )
            )
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Status indicator
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(glowColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isProvisioning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = glowColor,
                        strokeWidth = 4.dp
                    )
                } else {
                    Icon(
                        imageVector = getStatusIcon(status),
                        contentDescription = null,
                        tint = glowColor,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Status text
            Text(
                text = getStatusTitle(status),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ClawlyColors.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = getStatusDescription(status),
                fontSize = 15.sp,
                color = ClawlyColors.secondaryText,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            // Error message
            if (lastError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = lastError,
                    fontSize = 13.sp,
                    color = ClawlyColors.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action button
            when (status) {
                ManagedInstanceStatus.Ready -> {
                    Button(
                        onClick = onReady,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClawlyColors.terminalGreen
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Continue",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                    }
                }
                ManagedInstanceStatus.Failed -> {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClawlyColors.error
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Try Again",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                else -> {}
            }

            // Estimated time or dismiss
            if (isProvisioning) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "This usually takes 2-3 minutes",
                    fontSize = 13.sp,
                    color = ClawlyColors.textMuted
                )
            }

            // Close button for non-provisioning states
            if (!isProvisioning && status != ManagedInstanceStatus.Ready) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Close",
                        color = ClawlyColors.secondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Close button in corner (only when not provisioning)
        if (!isProvisioning) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = ClawlyColors.secondaryText
                )
            }
        }
    }
}

private fun getStatusTitle(status: ManagedInstanceStatus): String = when (status) {
    ManagedInstanceStatus.Queued -> "In Queue"
    ManagedInstanceStatus.Provisioning -> "Setting Up"
    ManagedInstanceStatus.Installing -> "Almost There"
    ManagedInstanceStatus.Ready -> "You're All Set!"
    ManagedInstanceStatus.Failed -> "Setup Failed"
    ManagedInstanceStatus.Suspended -> "Suspended"
}

private fun getStatusDescription(status: ManagedInstanceStatus): String = when (status) {
    ManagedInstanceStatus.Queued -> "Your Clawly instance is queued and will begin setup shortly."
    ManagedInstanceStatus.Provisioning -> "Creating your dedicated AI server..."
    ManagedInstanceStatus.Installing -> "Installing software on your server..."
    ManagedInstanceStatus.Ready -> "Clawly is ready to chat! Your credits are loaded and you're good to go."
    ManagedInstanceStatus.Failed -> "Something went wrong during setup. Please try again."
    ManagedInstanceStatus.Suspended -> "Your instance has been suspended."
}

private fun getStatusIcon(status: ManagedInstanceStatus): ImageVector = when (status) {
    ManagedInstanceStatus.Queued,
    ManagedInstanceStatus.Provisioning,
    ManagedInstanceStatus.Installing -> Icons.Default.Refresh
    ManagedInstanceStatus.Ready -> Icons.Default.CheckCircle
    ManagedInstanceStatus.Failed -> Icons.Default.Clear
    ManagedInstanceStatus.Suspended -> Icons.Default.Warning
}

private fun getStatusColor(status: ManagedInstanceStatus): Color = when (status) {
    ManagedInstanceStatus.Queued,
    ManagedInstanceStatus.Provisioning,
    ManagedInstanceStatus.Installing -> ClawlyColors.accentPrimary
    ManagedInstanceStatus.Ready -> ClawlyColors.terminalGreen
    ManagedInstanceStatus.Failed -> ClawlyColors.error
    ManagedInstanceStatus.Suspended -> ClawlyColors.warning
}
