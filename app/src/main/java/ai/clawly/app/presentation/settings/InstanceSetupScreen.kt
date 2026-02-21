package ai.clawly.app.presentation.settings

import ai.clawly.app.ui.theme.ClawlyColors
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Full-screen Instance Setup (AI Provider selection) screen
 * Reuses composables from InstanceSetupSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceSetupScreen(
    onNavigateBack: () -> Unit,
    viewModel: InstanceSetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect AI Provider") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ClawlyColors.background,
                    titleContentColor = ClawlyColors.textPrimary,
                    navigationIconContentColor = ClawlyColors.textPrimary
                )
            )
        },
        containerColor = ClawlyColors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ClawlyColors.terminalGreen,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connect AI Provider",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClawlyColors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Choose how to connect your AI",
                    fontSize = 14.sp,
                    color = ClawlyColors.secondaryText
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Provider selection
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "PROVIDER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClawlyColors.textTertiary,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                )

                AIProviderType.entries.forEach { provider ->
                    AIProviderCard(
                        provider = provider,
                        isSelected = uiState.selectedProvider == provider,
                        onSelect = { viewModel.selectProvider(provider) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Authentication section
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "AUTHENTICATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClawlyColors.textTertiary,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                )

                when (uiState.selectedProvider) {
                    AIProviderType.OpenAIOAuth -> {
                        OpenAIOAuthSection(
                            isLoading = uiState.isLoading,
                            onLogin = {
                                viewModel.connectOpenAIOAuth(
                                    onSuccess = { onNavigateBack() },
                                    onError = { /* Error handled in ViewModel */ }
                                )
                            }
                        )
                    }
                    AIProviderType.OpenAIApiKey -> {
                        OpenAIApiKeySection(
                            apiKey = uiState.openaiApiKey,
                            onApiKeyChange = { viewModel.updateOpenAIApiKey(it) },
                            isLoading = uiState.isLoading,
                            onSubmit = {
                                viewModel.saveOpenAIApiKey(
                                    uiState.openaiApiKey.trim(),
                                    onSuccess = { onNavigateBack() },
                                    onError = { /* Error handled in ViewModel */ }
                                )
                            },
                            onHelpClick = {
                                uriHandler.openUri("https://platform.openai.com/api-keys")
                            }
                        )
                    }
                    AIProviderType.Anthropic -> {
                        AnthropicApiKeySection(
                            apiKey = uiState.anthropicApiKey,
                            onApiKeyChange = { viewModel.updateAnthropicApiKey(it) },
                            isLoading = uiState.isLoading,
                            onSubmit = {
                                viewModel.saveAnthropicApiKey(
                                    uiState.anthropicApiKey.trim(),
                                    onSuccess = { onNavigateBack() },
                                    onError = { /* Error handled in ViewModel */ }
                                )
                            },
                            onHelpClick = {
                                uriHandler.openUri("https://console.anthropic.com/settings/keys")
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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

    // OAuth WebView dialog
    if (uiState.showOAuthWebView && uiState.oauthUrl != null) {
        OAuthWebViewDialog(
            authUrl = uiState.oauthUrl!!,
            onCallbackReceived = { callbackUrl ->
                viewModel.completeOpenAIOAuth(
                    callbackUrl = callbackUrl,
                    onSuccess = { onNavigateBack() },
                    onError = { /* Error handled in ViewModel */ }
                )
            },
            onDismiss = { viewModel.dismissOAuthWebView() }
        )
    }
}

/**
 * OAuth WebView Dialog matching iOS WKWebView flow
 * Intercepts localhost callback URLs to complete OAuth
 */
@Composable
private fun OAuthWebViewDialog(
    authUrl: String,
    onCallbackReceived: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Login with OpenAI")
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false

                                    // Intercept localhost callback (matches iOS behavior)
                                    if (url.startsWith("http://localhost")) {
                                        onCallbackReceived(url)
                                        return true
                                    }
                                    return false
                                }
                            }

                            loadUrl(authUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = ClawlyColors.accentPrimary
                    )
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun AIProviderCard(
    provider: AIProviderType,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val providerColor = Color(provider.color)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClawlyColors.surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) providerColor else ClawlyColors.surfaceBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelect() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(providerColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getProviderIcon(provider),
                contentDescription = null,
                tint = providerColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = ClawlyColors.textPrimary
            )
            Text(
                text = provider.description,
                fontSize = 12.sp,
                color = ClawlyColors.secondaryText
            )
        }

        // Selection indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .border(
                    width = 2.dp,
                    color = if (isSelected) providerColor else ClawlyColors.surfaceBorder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(providerColor, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun OpenAIOAuthSection(
    isLoading: Boolean,
    onLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClawlyColors.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = Color(AIProviderType.OpenAIOAuth.color),
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Login with OpenAI",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = ClawlyColors.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Sign in with your OpenAI account to connect",
            fontSize = 13.sp,
            color = ClawlyColors.secondaryText
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onLogin,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(AIProviderType.OpenAIOAuth.color)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "Login with OpenAI",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun OpenAIApiKeySection(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    isLoading: Boolean,
    onSubmit: () -> Unit,
    onHelpClick: () -> Unit
) {
    val isValid = apiKey.trim().let { it.startsWith("sk-") && it.length >= 20 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClawlyColors.surface)
            .padding(16.dp)
    ) {
        Text(
            text = "ChatGPT API Key",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = ClawlyColors.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Enter your API key from platform.openai.com",
            fontSize = 12.sp,
            color = ClawlyColors.secondaryText
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            placeholder = { Text("sk-...") },
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

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSubmit,
            enabled = !isLoading && isValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(AIProviderType.OpenAIApiKey.color),
                disabledContainerColor = Color(AIProviderType.OpenAIApiKey.color).copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "Save API Key",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onHelpClick,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = ClawlyColors.accentPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Get an API key",
                fontSize = 13.sp,
                color = ClawlyColors.accentPrimary
            )
        }
    }
}

@Composable
private fun AnthropicApiKeySection(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    isLoading: Boolean,
    onSubmit: () -> Unit,
    onHelpClick: () -> Unit
) {
    val isValid = apiKey.trim().let { it.startsWith("sk-ant-") && it.length >= 20 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClawlyColors.surface)
            .padding(16.dp)
    ) {
        Text(
            text = "Anthropic API Key",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = ClawlyColors.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Enter your API key from console.anthropic.com",
            fontSize = 12.sp,
            color = ClawlyColors.secondaryText
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            placeholder = { Text("sk-ant-api03-...") },
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

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSubmit,
            enabled = !isLoading && isValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(AIProviderType.Anthropic.color),
                disabledContainerColor = Color(AIProviderType.Anthropic.color).copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "Save API Key",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onHelpClick,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = ClawlyColors.accentPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Get an API key",
                fontSize = 13.sp,
                color = ClawlyColors.accentPrimary
            )
        }
    }
}

private fun getProviderIcon(provider: AIProviderType): ImageVector = when (provider) {
    AIProviderType.OpenAIOAuth -> Icons.Filled.Person
    AIProviderType.Anthropic -> Icons.Default.Star
    AIProviderType.OpenAIApiKey -> Icons.Default.Lock
}
