package ai.clawly.app.presentation.settings

import ai.clawly.app.BuildConfig
import ai.clawly.app.domain.model.ConnectionStatus
import ai.clawly.app.domain.model.HostingType
import ai.clawly.app.presentation.wallet.WalletViewModel
import ai.clawly.app.ui.theme.ClawlyColors
import ai.clawly.app.ui.util.DrawIfWeb2
import ai.clawly.app.ui.util.DrawIfWeb3
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Full Settings screen matching iOS SettingsView.swift exactly
 * Including all sections: Voice, Agent, Hosting, Gateway, Subscription, About, Advanced
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullSettingsScreen(
    onBackClick: () -> Unit,
    onSignedOut: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateToSkills: () -> Unit,
    onNavigateToApiKeys: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    onNavigateToAuthProvider: () -> Unit,
    onNavigateToInstanceSetup: () -> Unit,
    onNavigateToGatewayConfig: () -> Unit = {},
    onNavigateToWeb3Paywall: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val walletUiState by walletViewModel.uiState.collectAsState()
    var showAdvanced by remember { mutableStateOf(false) }
    var showDisconnectConfirmation by remember { mutableStateOf(false) }

    // Refresh auth config every time screen resumes (back from setup, etc.)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAuthConfig()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Debounce navigation to prevent rapid taps causing white screen
    var isNavigating by remember { mutableStateOf(false) }
    val safeBackClick: () -> Unit = {
        if (!isNavigating) {
            isNavigating = true
            onBackClick()
        }
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val glowColor = ClawlyColors.accentPrimary
    val topBarHeight = 56.dp

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
        ) {
            val config = uiState.currentAuthConfig

            // ACCOUNT Section (Web2 only — Firebase Auth)
            DrawIfWeb2 {
                if (uiState.isFirebaseSignedIn) {
                    SettingsSection(title = "ACCOUNT", isFirst = true) {
                        SettingsRow(
                            icon = Icons.Default.Person,
                            iconTint = ClawlyColors.accentPrimary,
                            title = uiState.firebaseUserName ?: "User",
                            subtitle = uiState.firebaseUserEmail,
                            showChevron = false
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            iconTint = ClawlyColors.error,
                            title = "Sign Out",
                            titleColor = ClawlyColors.error,
                            onClick = {
                                viewModel.signOutFirebase()
                                onSignedOut()
                            }
                        )
                    }
                }
            }

            // CLAWLY Section
            val isClawlyFirst = !(BuildConfig.IS_WEB2 && uiState.isFirebaseSignedIn)
            SettingsSection(title = "CLAWLY", isFirst = isClawlyFirst) {
                if (config.isConfigured || config.isProvisioning) {
                    // Show Clawly status when configured
                    SettingsRow(
                        icon = if (uiState.isSyncing) null else Icons.Default.Favorite,
                        iconTint = ClawlyColors.accentPrimary,
                        showLoadingIcon = uiState.isSyncing,
                        title = "Clawly",
                        subtitle = if (uiState.isSyncing) "Setting up..." else getClawlyStatusText(config),
                        showChevron = false,
                        enabled = !uiState.isSyncing,
                        trailing = {
                            StatusIndicatorDot(
                                color = getHostingStatusColor(config, uiState.connectionStatus),
                                label = getHostingStatusLabel(config, uiState.connectionStatus)
                            )
                        }
                    )

                    // Credits for managed hosting
                    if (config.hostingType == HostingType.Managed && config.isConfigured) {
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Default.Star,
                            iconTint = ClawlyColors.terminalGreen,
                            title = "Credits",
                            showChevron = false,
                            trailing = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (uiState.isLoadingCredits) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = ClawlyColors.accentPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(
                                            text = uiState.creditsFormatted,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ClawlyColors.terminalGreen
                                        )
                                    }
                                }
                            },
                            onClick = { viewModel.fetchCredits() }
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Default.Add,
                            iconTint = ClawlyColors.accentPrimary,
                            title = "Buy Credits",
                            titleColor = ClawlyColors.accentPrimary,
                            onClick = onNavigateToPaywall
                        )
                    }

                    // Reconnect option when there's an error
                    if (uiState.connectionStatus is ConnectionStatus.Error ||
                        uiState.connectionStatus is ConnectionStatus.Offline) {
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Default.Refresh,
                            iconTint = ClawlyColors.warning,
                            title = "Reconnect",
                            subtitle = if (uiState.connectionStatus is ConnectionStatus.Error)
                                (uiState.connectionStatus as ConnectionStatus.Error).message
                                else null,
                            onClick = { viewModel.reconnect() }
                        )
                    }

                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        iconTint = ClawlyColors.error,
                        title = "Disconnect",
                        titleColor = ClawlyColors.error,
                        onClick = { viewModel.logout() }
                    )
                } else {
                    // Not configured - show setup CTA
                    // Web2: must be signed in first, then premium check
                    SettingsRow(
                        icon = Icons.Default.Favorite,
                        iconTint = ClawlyColors.accentPrimary,
                        title = "Set Up Clawly",
                        subtitle = if (BuildConfig.IS_WEB2 && !uiState.isFirebaseSignedIn)
                            "Sign in to get started"
                        else
                            "Get started with your AI assistant",
                        titleColor = ClawlyColors.accentPrimary,
                        onClick = {
                            if (BuildConfig.IS_WEB2 && !uiState.isFirebaseSignedIn) {
                                onNavigateToLogin()
                            } else if (uiState.isPremium) {
                                onNavigateToAuthProvider()
                            } else {
                                onNavigateToPaywall()
                            }
                        }
                    )
                }
            }

            // WALLET Section (Web3 only) - right after CLAWLY
            DrawIfWeb3 {
                SettingsSection(title = "WALLET") {
                    if (walletUiState.isWalletConnected) {
                        // Connected state - show wallet info, credits, and options
                        SettingsRow(
                            icon = Icons.Default.Lock,
                            iconTint = ClawlyColors.terminalGreen,
                            title = "Connected",
                            subtitle = walletUiState.shortenedAddress,
                            showChevron = false,
                            trailing = {
                                StatusIndicatorDot(
                                    color = ClawlyColors.terminalGreen,
                                    label = "Active"
                                )
                            }
                        )
                        SettingsDivider()

                        // Credits display
                        SettingsRow(
                            icon = Icons.Default.Star,
                            iconTint = if (walletUiState.credits > 0) ClawlyColors.terminalGreen else ClawlyColors.warning,
                            title = "Credits",
                            showChevron = false,
                            trailing = {
                                Text(
                                    text = "${walletUiState.credits}",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (walletUiState.credits > 0) ClawlyColors.terminalGreen else ClawlyColors.warning
                                )
                            }
                        )
                        SettingsDivider()

                        // Buy Credits button
                        SettingsRow(
                            icon = Icons.Default.Add,
                            iconTint = ClawlyColors.accentPrimary,
                            title = "Buy Credits",
                            titleColor = ClawlyColors.accentPrimary,
                            onClick = onNavigateToWeb3Paywall
                        )
                        SettingsDivider()

                        // Disconnect wallet
                        SettingsRow(
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            iconTint = ClawlyColors.error,
                            title = "Disconnect Wallet",
                            titleColor = ClawlyColors.error,
                            onClick = { walletViewModel.disconnectWallet() }
                        )
                    } else {
                        // Not connected - only show connect button
                        SettingsRow(
                            icon = Icons.Default.Lock,
                            iconTint = ClawlyColors.accentPrimary,
                            title = if (walletUiState.isConnecting) "Connecting..." else "Connect Wallet",
                            subtitle = "Link your Solana wallet to get started",
                            titleColor = ClawlyColors.accentPrimary,
                            showChevron = !walletUiState.isConnecting,
                            showLoadingIcon = walletUiState.isConnecting,
                            enabled = !walletUiState.isConnecting,
                            onClick = { walletViewModel.connectWallet() }
                        )
                    }
                }
            }

            // AGENT Section - only enabled when configured
            val isAgentConfigured = config.isConfigured
            SettingsSection(title = "AGENT") {
                SettingsRow(
                    icon = Icons.Default.Star,
                    iconTint = if (isAgentConfigured) ClawlyColors.accentPrimary else ClawlyColors.textMuted,
                    title = "Skills",
                    subtitle = if (isAgentConfigured) "Manage agent capabilities" else "Configure Clawly first",
                    enabled = isAgentConfigured,
                    onClick = if (isAgentConfigured) onNavigateToSkills else null
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.Lock,
                    iconTint = if (isAgentConfigured) ClawlyColors.accentPrimary else ClawlyColors.textMuted,
                    title = "API Keys",
                    subtitle = if (isAgentConfigured) "Configure secrets for skills" else "Configure Clawly first",
                    enabled = isAgentConfigured,
                    onClick = if (isAgentConfigured) onNavigateToApiKeys else null
                )
            }

            // SUBSCRIPTION Section (only for self-hosted or no hosting)
            // ABOUT Section
            SettingsSection(title = "ABOUT") {
                SettingsRow(
                    icon = Icons.Default.Info,
                    iconTint = ClawlyColors.accentPrimary,
                    title = "Version",
                    value = "1.0.0",
                    showChevron = false
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.Lock,
                    iconTint = ClawlyColors.accentPrimary,
                    title = "Privacy Policy",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/document/d/1s6ijRCCVNSvLnlC4andYPRJUUxveskF6c-2Km0sZdYU/edit?usp=sharing"))
                        context.startActivity(intent)
                    }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.List,
                    iconTint = ClawlyColors.accentPrimary,
                    title = "Terms of Use",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/document/d/1NUAcle14HNFpF8-JsKhEKdVnuXcNRg9uFlWV9uFbIUM/edit?usp=sharing"))
                        context.startActivity(intent)
                    }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.Email,
                    iconTint = ClawlyColors.accentPrimary,
                    title = "Contact Us",
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:clawlyagent@clawlyai.io")
                            putExtra(Intent.EXTRA_SUBJECT, "Clawly App Feedback")
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // ADVANCED Section
            SettingsSection(title = "ADVANCED") {
                SettingsRow(
                    icon = Icons.Default.Settings,
                    iconTint = ClawlyColors.secondaryText,
                    title = "Advanced Settings",
                    showChevron = false,
                    trailing = {
                        Icon(
                            imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = ClawlyColors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = { showAdvanced = !showAdvanced }
                )

                AnimatedVisibility(
                    visible = showAdvanced,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        SettingsDivider()
                        AdvancedContent(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                }
            }

            // Footer
            Text(
                text = "Built with \uD83E\uDD9E by AIClaw",
                fontSize = 14.sp,
                color = ClawlyColors.textMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally)
            )
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
                IconButton(onClick = safeBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ClawlyColors.accentPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "Settings",
                    fontSize = 20.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // Disconnect Confirmation
    if (showDisconnectConfirmation) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmation = false },
            title = { Text("Disconnect Provider") },
            text = {
                Text(
                    "This will disconnect ${uiState.connectedProvider?.displayName ?: "the AI provider"}. " +
                            "You'll need to reconnect to use the chat."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setConnectedProvider(null)
                        showDisconnectConfirmation = false
                    }
                ) {
                    Text("Disconnect", color = ClawlyColors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

}

@Composable
private fun AiProvidersSection(
    viewModel: SettingsViewModel
) {
    var expandedProvider by remember { mutableStateOf<String?>(null) }
    var apiKeyInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Column {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = ClawlyColors.accentPrimary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "AI PROVIDERS",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ClawlyColors.secondaryText,
                letterSpacing = 1.5.sp
            )
        }

        Text(
            text = "Set your own API keys to use different AI providers instead of credits.",
            fontSize = 13.sp,
            color = ClawlyColors.textMuted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // Success/Error messages
        successMessage?.let { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ClawlyColors.terminalGreen.copy(alpha = 0.15f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ClawlyColors.terminalGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = msg,
                    fontSize = 14.sp,
                    color = ClawlyColors.terminalGreen
                )
            }
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(3000)
                successMessage = null
            }
        }

        errorMessage?.let { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ClawlyColors.error.copy(alpha = 0.15f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ClawlyColors.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = msg,
                    fontSize = 14.sp,
                    color = ClawlyColors.error
                )
            }
        }

        // Provider list
        val providers = listOf(
            Triple("openrouter", "OpenRouter", "Access multiple models via OpenRouter"),
            Triple("anthropic", "Anthropic (Claude)", "Use Claude models directly"),
            Triple("openai", "OpenAI", "Use GPT models directly"),
            Triple("glm", "GLM (Zhipu AI)", "Use GLM models"),
            Triple("minimax", "MiniMax", "Use MiniMax models")
        )

        providers.forEach { (id, name, description) ->
            val isExpanded = expandedProvider == id

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (expandedProvider == id) {
                            expandedProvider = null
                            apiKeyInput = ""
                        } else {
                            expandedProvider = id
                            apiKeyInput = ""
                            errorMessage = null
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            fontSize = 16.sp,
                            color = ClawlyColors.textPrimary
                        )
                        Text(
                            text = description,
                            fontSize = 13.sp,
                            color = ClawlyColors.textMuted
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = ClawlyColors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it
                                errorMessage = null
                            },
                            placeholder = {
                                Text(
                                    text = "Enter API key",
                                    fontSize = 15.sp
                                )
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ClawlyColors.accentPrimary,
                                unfocusedBorderColor = ClawlyColors.surfaceBorder,
                                focusedTextColor = ClawlyColors.textPrimary,
                                unfocusedTextColor = ClawlyColors.textPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (apiKeyInput.isBlank()) {
                                    errorMessage = "Please enter an API key"
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null

                                val onResult: (Boolean, String?) -> Unit = { success, error ->
                                    isLoading = false
                                    if (success) {
                                        successMessage = "$name configured successfully"
                                        apiKeyInput = ""
                                        expandedProvider = null
                                    } else {
                                        errorMessage = error ?: "Failed to configure provider"
                                    }
                                }

                                when (id) {
                                    "openrouter" -> viewModel.setOpenRouterApiKey(apiKeyInput, onResult)
                                    "anthropic" -> viewModel.setAnthropicApiKey(apiKeyInput, onResult)
                                    "openai" -> viewModel.setOpenAIApiKey(apiKeyInput, onResult)
                                    "glm" -> viewModel.setGlmApiKey(apiKeyInput, onResult)
                                    "minimax" -> viewModel.setMiniMaxApiKey(apiKeyInput, onResult)
                                }
                            },
                            enabled = !isLoading && apiKeyInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ClawlyColors.accentPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Save API Key")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val config = uiState.currentAuthConfig
    val isManagedHosting = config.hostingType == HostingType.Managed && config.isConfigured

    Column {
        // AI Providers section (only for managed hosting)
        if (isManagedHosting) {
            AiProvidersSection(viewModel = viewModel)
            SettingsDivider()
        }

        // Voice customization header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = ClawlyColors.secondaryText,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "VOICE CUSTOMIZATION",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ClawlyColors.secondaryText,
                letterSpacing = 1.5.sp
            )
        }

        if (uiState.ttsEnabled) {
            // Speed slider
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Speed",
                        fontSize = 17.sp,
                        color = ClawlyColors.textPrimary
                    )
                    Text(
                        text = String.format("%.1fx", uiState.speechRate),
                        fontSize = 15.sp,
                        color = ClawlyColors.secondaryText
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = uiState.speechRate,
                    onValueChange = { viewModel.setSpeechRate(it) },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = ClawlyColors.accentPrimary,
                        activeTrackColor = ClawlyColors.accentPrimary
                    )
                )
            }
        }

        SettingsDivider()

        // DEBUG section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                tint = ClawlyColors.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "DEBUG OPTIONS",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ClawlyColors.secondaryText,
                letterSpacing = 1.5.sp
            )
        }

        // Show Onboarding toggle
        SettingsToggleRow(
            icon = null,
            title = "Show Onboarding",
            subtitle = "Shows onboarding on every app launch",
            checked = uiState.alwaysShowOnboarding,
            onCheckedChange = { viewModel.setAlwaysShowOnboarding(it) }
        )

        // Use Debug Defaults toggle
        SettingsToggleRow(
            icon = null,
            title = "Use Debug Defaults",
            checked = uiState.useDebugDefaults,
            onCheckedChange = { viewModel.setUseDebugDefaults(it) }
        )

        // Gateway Skills toggle
        SettingsToggleRow(
            icon = null,
            title = "Send Skills to Gateway",
            subtitle = "Enable when gateway supports skills param",
            checked = uiState.gatewaySkillsEnabled,
            onCheckedChange = { viewModel.setGatewaySkillsEnabled(it) }
        )

        // Override Premium toggle
        var debugPremiumActive by remember { mutableStateOf(uiState.debugPremiumOverride != null) }
        var debugPremiumValue by remember { mutableStateOf(uiState.debugPremiumOverride ?: false) }

        SettingsToggleRow(
            icon = null,
            title = "Override Premium",
            checked = debugPremiumActive,
            onCheckedChange = { active ->
                debugPremiumActive = active
                if (active) {
                    viewModel.setDebugPremiumOverride(debugPremiumValue)
                } else {
                    viewModel.setDebugPremiumOverride(null)
                }
            }
        )

        if (debugPremiumActive) {
            SettingsToggleRow(
                icon = null,
                title = "Premium Active",
                titleColor = if (debugPremiumValue) ClawlyColors.terminalGreen else ClawlyColors.textPrimary,
                checked = debugPremiumValue,
                onCheckedChange = {
                    debugPremiumValue = it
                    viewModel.setDebugPremiumOverride(it)
                }
            )
        }

        // Use Debug User ID toggle
        SettingsToggleRow(
            icon = null,
            title = "Use Debug User ID",
            subtitle = "Uses hardcoded user ID for testing",
            checked = uiState.useDebugUserId,
            onCheckedChange = { viewModel.setUseDebugUserId(it) }
        )

        // Use Bypass Token toggle
        SettingsToggleRow(
            icon = null,
            title = "Use Bypass Token",
            subtitle = "Skip RevenueCat subscription check",
            checked = uiState.useBypassToken,
            onCheckedChange = { viewModel.setUseBypassToken(it) }
        )

        if (uiState.useBypassToken) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(start = 48.dp)
                    .imePadding()
            ) {
                Text(
                    text = "Bypass Token",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClawlyColors.secondaryText
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.bypassToken,
                    onValueChange = { viewModel.setBypassToken(it) },
                    placeholder = { Text("Enter bypass token", fontSize = 15.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClawlyColors.accentPrimary,
                        unfocusedBorderColor = ClawlyColors.surfaceBorder,
                        focusedTextColor = ClawlyColors.textPrimary,
                        unfocusedTextColor = ClawlyColors.textPrimary
                    )
                )

                // Add Debug Credits button
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.addDebugCredits() },
                    enabled = uiState.bypassToken.isNotBlank() && !uiState.isAddingDebugCredits,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClawlyColors.terminalGreen,
                        disabledContainerColor = ClawlyColors.terminalGreen.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isAddingDebugCredits) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Adding Credits...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add 1B Debug Credits")
                    }
                }

                // Result message
                uiState.debugCreditsResult?.let { result ->
                    Spacer(modifier = Modifier.height(8.dp))
                    val isError = result.startsWith("Error")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isError) ClawlyColors.error.copy(alpha = 0.15f)
                                else ClawlyColors.terminalGreen.copy(alpha = 0.15f)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isError) ClawlyColors.error else ClawlyColors.terminalGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = result,
                            fontSize = 14.sp,
                            color = if (isError) ClawlyColors.error else ClawlyColors.terminalGreen
                        )
                    }
                    LaunchedEffect(result) {
                        kotlinx.coroutines.delay(5000)
                        viewModel.clearDebugCreditsResult()
                    }
                }
            }
        }

        // User ID display
        SettingsDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "User ID",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = ClawlyColors.secondaryText
            )
            Spacer(modifier = Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    text = uiState.userId.ifEmpty { "Loading..." },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ClawlyColors.textPrimary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    isFirst: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = ClawlyColors.secondaryText,
            letterSpacing = 1.sp,
            modifier = Modifier
                .padding(start = 8.dp, bottom = 10.dp)
                .padding(top = if (isFirst) 16.dp else 32.dp)
        )
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
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector? = null,
    iconTint: Color = ClawlyColors.textPrimary,
    showLoadingIcon: Boolean = false,
    title: String,
    titleColor: Color = ClawlyColors.textPrimary,
    subtitle: String? = null,
    value: String? = null,
    valueMonospace: String? = null,
    showChevron: Boolean = true,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) Modifier.clickable { onClick() }
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLoadingIcon) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = ClawlyColors.accentPrimary,
                strokeWidth = 2.5.dp
            )
            Spacer(modifier = Modifier.width(16.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint.copy(alpha = alpha),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                color = titleColor.copy(alpha = alpha)
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = ClawlyColors.secondaryText.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        if (value != null) {
            Text(
                text = value,
                fontSize = 15.sp,
                color = ClawlyColors.secondaryText.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        if (valueMonospace != null) {
            Text(
                text = valueMonospace,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = ClawlyColors.secondaryText.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        if (trailing != null) {
            trailing()
        } else if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ClawlyColors.textMuted.copy(alpha = alpha),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector? = null,
    iconTint: Color = ClawlyColors.accentPrimary,
    title: String,
    titleColor: Color = ClawlyColors.textPrimary,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                color = titleColor
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = ClawlyColors.secondaryText
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

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp, end = 16.dp),
        color = Color.White.copy(alpha = 0.06f),
        thickness = 0.5.dp
    )
}

@Composable
private fun StatusIndicatorDot(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = color
        )
    }
}

@Composable
private fun StyledPillButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

// Helper functions
private fun getHostingIconColor(hostingType: HostingType?): Color = when (hostingType) {
    HostingType.Managed -> ClawlyColors.accentPrimary
    HostingType.SelfHosted -> Color.Cyan
    null -> ClawlyColors.textMuted
}

private fun getHostingStatusText(config: ai.clawly.app.domain.model.AuthProviderConfig): String {
    return when {
        config.isProvisioning -> config.managedInstance?.status?.displayName ?: "Setting up..."
        config.hostingType == HostingType.Managed -> "OpenClaw Cloud"
        else -> config.effectiveGatewayUrl ?: "Custom server"
    }
}

private fun getClawlyStatusText(config: ai.clawly.app.domain.model.AuthProviderConfig): String {
    return when {
        config.isProvisioning -> config.managedInstance?.status?.displayName ?: "Setting up..."
        config.hostingType == HostingType.Managed -> "Ready to chat"
        config.hostingType == HostingType.SelfHosted -> "Self-hosted"
        else -> "Unknown"
    }
}

/**
 * Get hosting status color based on config AND gateway connection (matches iOS)
 * - Provisioning → Yellow
 * - Ready + Connected → Green
 * - Ready + Connecting → Yellow
 * - Ready + Disconnected → Gray
 * - Failed/Suspended → Red
 */
private fun getHostingStatusColor(
    config: ai.clawly.app.domain.model.AuthProviderConfig,
    connectionStatus: ConnectionStatus
): Color {
    return when {
        // Instance still provisioning
        config.isProvisioning -> ClawlyColors.warning
        // Instance failed or suspended
        config.managedInstance?.status == ai.clawly.app.domain.model.ManagedInstanceStatus.Failed -> ClawlyColors.error
        config.managedInstance?.status == ai.clawly.app.domain.model.ManagedInstanceStatus.Suspended -> ClawlyColors.error
        // Instance ready - check gateway connection
        config.isConfigured -> when (connectionStatus) {
            is ConnectionStatus.Online -> ClawlyColors.terminalGreen
            is ConnectionStatus.Connecting -> ClawlyColors.warning
            is ConnectionStatus.Offline -> ClawlyColors.textMuted
            is ConnectionStatus.Error -> ClawlyColors.error
        }
        else -> ClawlyColors.error
    }
}

/**
 * Get hosting status label based on config AND gateway connection (matches iOS)
 */
private fun getHostingStatusLabel(
    config: ai.clawly.app.domain.model.AuthProviderConfig,
    connectionStatus: ConnectionStatus
): String {
    return when {
        // Instance still provisioning - show specific status
        config.isProvisioning -> when (config.managedInstance?.status) {
            ai.clawly.app.domain.model.ManagedInstanceStatus.Queued -> "Queued"
            ai.clawly.app.domain.model.ManagedInstanceStatus.Provisioning -> "Setting up server..."
            ai.clawly.app.domain.model.ManagedInstanceStatus.Installing -> "Installing software..."
            else -> "Setting up"
        }
        // Instance failed or suspended
        config.managedInstance?.status == ai.clawly.app.domain.model.ManagedInstanceStatus.Failed -> "Failed"
        config.managedInstance?.status == ai.clawly.app.domain.model.ManagedInstanceStatus.Suspended -> "Suspended"
        // Instance ready - check gateway connection
        config.isConfigured -> when (connectionStatus) {
            is ConnectionStatus.Online -> "Connected"
            is ConnectionStatus.Connecting -> "Connecting..."
            is ConnectionStatus.Offline -> "Disconnected"
            is ConnectionStatus.Error -> "Error"
        }
        else -> "Error"
    }
}

private fun getConnectionStatusColor(status: ConnectionStatus): Color = when (status) {
    is ConnectionStatus.Online -> ClawlyColors.terminalGreen
    is ConnectionStatus.Connecting -> ClawlyColors.warning
    is ConnectionStatus.Offline -> ClawlyColors.textMuted
    is ConnectionStatus.Error -> ClawlyColors.error
}

private fun getConnectionStatusText(status: ConnectionStatus): String = when (status) {
    is ConnectionStatus.Online -> "Connected"
    is ConnectionStatus.Connecting -> "Connecting..."
    is ConnectionStatus.Offline -> "Offline"
    is ConnectionStatus.Error -> "Error"
}
