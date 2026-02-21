package ai.clawly.app.presentation.gateway

import ai.clawly.app.domain.model.ConnectionStatus
import ai.clawly.app.ui.theme.ClawlyColors
import ai.clawly.app.ui.theme.ClawlySpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: GatewayConfigViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GatewayConfigEvent.NavigateBack -> onNavigateBack()
                is GatewayConfigEvent.SaveSuccess -> { /* Toast can be shown if needed */ }
                is GatewayConfigEvent.ShowError -> { /* Handled by state */ }
            }
        }
    }

    Scaffold(
        topBar = {
            GatewayConfigTopBar(
                onCancel = viewModel::cancel
            )
        },
        containerColor = ClawlyColors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(ClawlySpacing.xxl.dp)
        ) {
            // Connection status indicator
            ConnectionStatusBanner(status = state.connectionStatus)

            Spacer(modifier = Modifier.height(ClawlySpacing.lg.dp))

            // Debug defaults banner (if applicable)
            if (state.isUsingDebugDefaults) {
                DebugDefaultsBanner()
                Spacer(modifier = Modifier.height(ClawlySpacing.lg.dp))
            }

            // Gateway URL field
            GatewayTextField(
                label = "Gateway URL",
                value = state.gatewayUrl,
                onValueChange = viewModel::onUrlChange,
                placeholder = "wss://your-gateway-url.com",
                error = state.urlError,
                enabled = !state.isUsingDebugDefaults,
                isMonospace = true
            )

            Spacer(modifier = Modifier.height(ClawlySpacing.xxl.dp))

            // Auth Token field
            GatewayTextField(
                label = "Auth Token",
                value = state.gatewayToken,
                onValueChange = viewModel::onTokenChange,
                placeholder = "Enter your auth token",
                enabled = !state.isUsingDebugDefaults,
                isMonospace = true,
                isPassword = true
            )

            Spacer(modifier = Modifier.weight(1f))

            // Save & Reconnect button
            Button(
                onClick = viewModel::saveAndReconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !state.isUsingDebugDefaults && !state.isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClawlyColors.accentPrimary,
                    disabledContainerColor = ClawlyColors.accentPrimary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Save & Reconnect",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // Error dialog
    if (state.showError) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = {
                Text(
                    text = "Error",
                    color = ClawlyColors.textPrimary
                )
            },
            text = {
                Text(
                    text = state.errorMessage ?: "An unknown error occurred",
                    color = ClawlyColors.secondaryText
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text(
                        text = "OK",
                        color = ClawlyColors.accentPrimary
                    )
                }
            },
            containerColor = ClawlyColors.surface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GatewayConfigTopBar(onCancel: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = "Gateway Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = ClawlyColors.textPrimary
            )
        },
        navigationIcon = {
            TextButton(onClick = onCancel) {
                Text(
                    text = "Cancel",
                    color = ClawlyColors.accentPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ClawlyColors.background
        )
    )
}

@Composable
private fun ConnectionStatusBanner(status: ConnectionStatus) {
    val (statusText, statusColor) = when (status) {
        is ConnectionStatus.Online -> "Connected" to ClawlyColors.success
        is ConnectionStatus.Connecting -> "Connecting..." to ClawlyColors.warning
        is ConnectionStatus.Offline -> "Offline" to ClawlyColors.secondaryText
        is ConnectionStatus.Error -> "Error: ${status.message}" to ClawlyColors.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(statusColor.copy(alpha = 0.1f))
            .padding(ClawlySpacing.md.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(ClawlySpacing.sm.dp))
        Text(
            text = statusText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = statusColor
        )
    }
}

@Composable
private fun DebugDefaultsBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ClawlyColors.accentPrimary.copy(alpha = 0.1f))
            .padding(ClawlySpacing.md.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = ClawlyColors.accentPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(ClawlySpacing.sm.dp))
        Text(
            text = "Using debug defaults. Turn off in Advanced settings.",
            fontSize = 13.sp,
            color = ClawlyColors.secondaryText
        )
    }
}

@Composable
private fun GatewayTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    isMonospace: Boolean = false,
    isPassword: Boolean = false,
    error: String? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = ClawlyColors.secondaryText,
            modifier = Modifier.padding(bottom = ClawlySpacing.xs.dp)
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            placeholder = {
                Text(
                    text = placeholder,
                    color = ClawlyColors.textTertiary
                )
            },
            textStyle = if (isMonospace) {
                TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp)
            } else {
                TextStyle(fontSize = 15.sp)
            },
            visualTransformation = if (isPassword && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Close
                            else Icons.Default.Search,
                            contentDescription = if (passwordVisible) "Hide" else "Show",
                            tint = ClawlyColors.secondaryText
                        )
                    }
                }
            } else null,
            isError = error != null,
            supportingText = if (error != null) {
                { Text(error, color = ClawlyColors.error) }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ClawlyColors.textPrimary,
                unfocusedTextColor = ClawlyColors.textPrimary,
                disabledTextColor = ClawlyColors.textMuted,
                focusedContainerColor = ClawlyColors.surfaceElevated,
                unfocusedContainerColor = ClawlyColors.surfaceElevated,
                disabledContainerColor = ClawlyColors.surface,
                focusedBorderColor = ClawlyColors.accentPrimary,
                unfocusedBorderColor = ClawlyColors.surfaceBorder,
                disabledBorderColor = ClawlyColors.surfaceBorder.copy(alpha = 0.5f),
                errorBorderColor = ClawlyColors.error,
                cursorColor = ClawlyColors.accentPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
    }
}
