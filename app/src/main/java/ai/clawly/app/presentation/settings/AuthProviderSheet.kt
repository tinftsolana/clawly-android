package ai.clawly.app.presentation.settings

import ai.clawly.app.domain.model.AuthProviderConfig
import ai.clawly.app.domain.model.HostingType
import ai.clawly.app.domain.model.ManagedInstanceStatus
import ai.clawly.app.ui.theme.ClawlyColors
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * AuthProviderSheet - Hosting type selection matching iOS AuthProviderSheet.swift
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthProviderSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    onProviderConfigured: () -> Unit,
    onCreateManagedInstance: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onSaveSelfHosted: (url: String, token: String) -> Unit
) {
    var showSelfHostedConfig by remember { mutableStateOf(false) }
    var showProvisioningView by remember { mutableStateOf(false) }
    var isCreatingInstance by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }

    if (!isPresented) return

    if (showSelfHostedConfig) {
        SelfHostedConfigSheet(
            onDismiss = { showSelfHostedConfig = false },
            onSave = { url, token ->
                onSaveSelfHosted(url, token)
                showSelfHostedConfig = false
                onDismiss()
                onProviderConfigured()
            }
        )
        return
    }

    if (showProvisioningView) {
        ProvisioningStatusSheet(
            onDismiss = { showProvisioningView = false },
            onReady = {
                showProvisioningView = false
                onDismiss()
                onProviderConfigured()
            }
        )
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClawlyColors.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ClawlyColors.secondaryText
                        )
                    }
                }

                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = ClawlyColors.accentPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Choose Your Setup",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClawlyColors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "How would you like to connect to AI?",
                        fontSize = 14.sp,
                        color = ClawlyColors.secondaryText
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Hosting options
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Managed Hosting (recommended)
                    HostingOptionRow(
                        hostingType = HostingType.Managed,
                        isRecommended = true,
                        isLoading = isCreatingInstance,
                        onClick = {
                            isCreatingInstance = true
                            onCreateManagedInstance(
                                {
                                    isCreatingInstance = false
                                    showProvisioningView = true
                                },
                                { errorMsg ->
                                    isCreatingInstance = false
                                    error = errorMsg
                                    showError = true
                                }
                            )
                        }
                    )

                    // Self-Hosted
                    HostingOptionRow(
                        hostingType = HostingType.SelfHosted,
                        isRecommended = false,
                        isLoading = false,
                        onClick = { showSelfHostedConfig = true }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Info text
                Text(
                    text = "Managed hosting includes automatic updates and support.",
                    fontSize = 12.sp,
                    color = ClawlyColors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
        }
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text("Error") },
            text = { Text(error ?: "An unknown error occurred") },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun HostingOptionRow(
    hostingType: HostingType,
    isRecommended: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val iconColor = when (hostingType) {
        HostingType.Managed -> ClawlyColors.accentPrimary
        HostingType.SelfHosted -> Color.Cyan
    }

    val icon = when (hostingType) {
        HostingType.Managed -> Icons.Default.Favorite
        HostingType.SelfHosted -> Icons.Default.Home
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClawlyColors.surface)
            .border(
                width = if (isRecommended) 2.dp else 1.dp,
                color = if (isRecommended) ClawlyColors.accentPrimary.copy(alpha = 0.5f)
                else ClawlyColors.surfaceBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = !isLoading) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(iconColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = iconColor,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
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
                    text = hostingType.displayName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
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
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = hostingType.description,
                fontSize = 13.sp,
                color = ClawlyColors.secondaryText
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ClawlyColors.textMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * Self-Hosted Config Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfHostedConfigSheet(
    initialUrl: String = "",
    initialToken: String = "",
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf(initialToken) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClawlyColors.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Top bar with cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            color = ClawlyColors.accentPrimary
                        )
                    }
                }

                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = Color.Cyan,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Self-Hosted Gateway",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClawlyColors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // URL Field
                Column {
                    Text(
                        text = "Gateway URL",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = ClawlyColors.secondaryText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = { Text("wss://your-gateway-url.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ClawlyColors.surfaceBorder,
                            unfocusedBorderColor = ClawlyColors.surfaceBorder,
                            focusedTextColor = ClawlyColors.textPrimary,
                            unfocusedTextColor = ClawlyColors.textPrimary,
                            cursorColor = ClawlyColors.accentPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Token Field
                Column {
                    Text(
                        text = "Auth Token",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = ClawlyColors.secondaryText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        placeholder = { Text("Enter your auth token") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ClawlyColors.surfaceBorder,
                            unfocusedBorderColor = ClawlyColors.surfaceBorder,
                            focusedTextColor = ClawlyColors.textPrimary,
                            unfocusedTextColor = ClawlyColors.textPrimary,
                            cursorColor = ClawlyColors.accentPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Save button
                val isValid = url.trim().isNotEmpty()
                Button(
                    onClick = { onSave(url.trim(), token.trim()) },
                    enabled = isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClawlyColors.accentPrimary,
                        disabledContainerColor = ClawlyColors.accentPrimary.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Save & Connect",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Provisioning Status Sheet
 */
@Composable
fun ProvisioningStatusSheet(
    status: ManagedInstanceStatus = ManagedInstanceStatus.Queued,
    lastError: String? = null,
    onDismiss: () -> Unit,
    onReady: () -> Unit,
    onRetry: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = { if (!status.isProvisioning) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !status.isProvisioning,
            dismissOnClickOutside = !status.isProvisioning
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClawlyColors.background)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Close button (only when not provisioning)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!status.isProvisioning) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = ClawlyColors.secondaryText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Status indicator
                StatusIndicator(status = status)

                Spacer(modifier = Modifier.height(32.dp))

                // Status text
                Text(
                    text = getStatusTitle(status),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClawlyColors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = getStatusDescription(status),
                    fontSize = 14.sp,
                    color = ClawlyColors.secondaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                // Error message
                if (lastError != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = lastError,
                        fontSize = 13.sp,
                        color = ClawlyColors.error,
                        modifier = Modifier.padding(horizontal = 32.dp)
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
                                .height(56.dp)
                                .padding(horizontal = 24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ClawlyColors.accentPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Continue to Chat",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    ManagedInstanceStatus.Failed -> {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ClawlyColors.error
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Retry",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    else -> {}
                }

                // Estimated time
                if (status.isProvisioning) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "This usually takes 2-3 minutes",
                        fontSize = 12.sp,
                        color = ClawlyColors.textTertiary
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun StatusIndicator(status: ManagedInstanceStatus) {
    val color = getStatusColor(status)

    Box(
        modifier = Modifier
            .size(120.dp)
            .background(color.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (status.isProvisioning) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = color,
                strokeWidth = 4.dp
            )
        } else {
            Icon(
                imageVector = getStatusIcon(status),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

private fun getStatusTitle(status: ManagedInstanceStatus): String = when (status) {
    ManagedInstanceStatus.Queued -> "Queued"
    ManagedInstanceStatus.Provisioning -> "Setting Up Server"
    ManagedInstanceStatus.Installing -> "Installing Software"
    ManagedInstanceStatus.Ready -> "Ready!"
    ManagedInstanceStatus.Failed -> "Setup Failed"
    ManagedInstanceStatus.Suspended -> "Suspended"
}

private fun getStatusDescription(status: ManagedInstanceStatus): String = when (status) {
    ManagedInstanceStatus.Queued -> "Your server is in the queue. Setup will begin shortly."
    ManagedInstanceStatus.Provisioning -> "Creating your dedicated AI server..."
    ManagedInstanceStatus.Installing -> "Installing the AI software on your server..."
    ManagedInstanceStatus.Ready -> "Your server is ready! You can now start chatting."
    ManagedInstanceStatus.Failed -> "Something went wrong. Please try again."
    ManagedInstanceStatus.Suspended -> "Your server has been suspended."
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

private val ManagedInstanceStatus.isProvisioning: Boolean
    get() = this == ManagedInstanceStatus.Queued ||
            this == ManagedInstanceStatus.Provisioning ||
            this == ManagedInstanceStatus.Installing
