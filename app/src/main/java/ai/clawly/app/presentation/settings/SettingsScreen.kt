package ai.clawly.app.presentation.settings

import ai.clawly.app.domain.model.HostingType
import ai.clawly.app.presentation.wallet.WalletViewModel
import ai.clawly.app.ui.theme.ClawlyColors
import ai.clawly.app.ui.util.DrawIfWeb3
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Simple Settings Screen - kept for backward compatibility
 * See FullSettingsScreen for the complete implementation with all sections
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToGatewayConfig: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val walletState by walletViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontSize = 20.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ClawlyColors.background
                )
            )
        },
        containerColor = ClawlyColors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Connection section
            SettingsSectionSimple(title = "Connection") {
                val config = uiState.currentAuthConfig
                val statusText = when {
                    config.isConfigured -> "Connected"
                    config.isProvisioning -> "Provisioning..."
                    config.hostingType == HostingType.Managed -> "Managed Hosting"
                    config.hostingType == HostingType.SelfHosted -> "Self-Hosted"
                    else -> "Not configured"
                }

                SettingsItemSimple(
                    icon = Icons.Default.Settings,
                    title = "Gateway",
                    subtitle = statusText,
                    onClick = onNavigateToGatewayConfig
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Wallet section (Web3 only)
            DrawIfWeb3 {
                SettingsSectionSimple(title = "Wallet") {
                    if (walletState.isWalletConnected) {
                        SettingsItemSimple(
                            icon = Icons.Default.Lock,
                            title = "Connected",
                            subtitle = walletState.shortenedAddress,
                            onClick = { walletViewModel.disconnectWallet() }
                        )
                    } else {
                        SettingsItemSimple(
                            icon = Icons.Default.Lock,
                            title = if (walletState.isConnecting) "Connecting..." else "Connect Wallet",
                            subtitle = "Connect your Solana wallet",
                            onClick = {
                                if (!walletState.isConnecting) {
                                    walletViewModel.connectWallet()
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // Features section
            SettingsSectionSimple(title = "Features") {
                SettingsToggleItemSimple(
                    icon = Icons.Default.Notifications,
                    title = "Text-to-Speech",
                    subtitle = "Read responses aloud",
                    checked = uiState.ttsEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setTtsEnabled(enabled)
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Danger zone
            SettingsSectionSimple(title = "Data") {
                SettingsItemSimple(
                    icon = Icons.Filled.Delete,
                    title = "Clear Connection",
                    subtitle = "Remove gateway configuration",
                    iconTint = ClawlyColors.error,
                    onClick = {
                        viewModel.logout()
                    }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // App info
            Text(
                text = "Clawly for Android",
                color = ClawlyColors.textMuted,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun SettingsSectionSimple(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title.uppercase(),
            color = ClawlyColors.textMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ClawlyColors.surface)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsItemSimple(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ClawlyColors.textPrimary,
                fontSize = 17.sp
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = ClawlyColors.secondaryText,
                    fontSize = 15.sp
                )
            }
        }

        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ClawlyColors.textMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsToggleItemSimple(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ClawlyColors.textPrimary,
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ClawlyColors.textPrimary,
                fontSize = 17.sp
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = ClawlyColors.secondaryText,
                    fontSize = 15.sp
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ClawlyColors.accentPrimary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = ClawlyColors.surfaceBorder
            )
        )
    }
}
