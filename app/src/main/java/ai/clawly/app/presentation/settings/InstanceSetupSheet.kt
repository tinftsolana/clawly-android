package ai.clawly.app.presentation.settings

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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Instance Setup Sheet - AI Provider selection matching iOS InstanceSetupSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceSetupSheet(
    tenantId: String?,
    onDismiss: () -> Unit,
    onOpenAIOAuth: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onOpenAIApiKey: (apiKey: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onAnthropicApiKey: (apiKey: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit
) {
    var selectedProvider by remember { mutableStateOf(AIProviderType.OpenAIOAuth) }
    var openaiApiKey by remember { mutableStateOf("") }
    var anthropicApiKey by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }

    val uriHandler = LocalUriHandler.current

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
                    .verticalScroll(rememberScrollState())
            ) {
                // Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                        AIProviderRow(
                            provider = provider,
                            isSelected = selectedProvider == provider,
                            onSelect = { selectedProvider = provider }
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

                    when (selectedProvider) {
                        AIProviderType.OpenAIOAuth -> {
                            OpenAIOAuthView(
                                isLoading = isLoading,
                                onLogin = {
                                    isLoading = true
                                    onOpenAIOAuth(
                                        {
                                            isLoading = false
                                            onDismiss()
                                        },
                                        { errorMsg ->
                                            isLoading = false
                                            error = errorMsg
                                            showError = true
                                        }
                                    )
                                }
                            )
                        }
                        AIProviderType.OpenAIApiKey -> {
                            OpenAIApiKeyView(
                                apiKey = openaiApiKey,
                                onApiKeyChange = { openaiApiKey = it },
                                isLoading = isLoading,
                                onSubmit = {
                                    isLoading = true
                                    onOpenAIApiKey(
                                        openaiApiKey.trim(),
                                        {
                                            isLoading = false
                                            onDismiss()
                                        },
                                        { errorMsg ->
                                            isLoading = false
                                            error = errorMsg
                                            showError = true
                                        }
                                    )
                                },
                                onHelpClick = {
                                    uriHandler.openUri("https://platform.openai.com/api-keys")
                                }
                            )
                        }
                        AIProviderType.Anthropic -> {
                            AnthropicAuthView(
                                apiKey = anthropicApiKey,
                                onApiKeyChange = { anthropicApiKey = it },
                                isLoading = isLoading,
                                onSubmit = {
                                    isLoading = true
                                    onAnthropicApiKey(
                                        anthropicApiKey.trim(),
                                        {
                                            isLoading = false
                                            onDismiss()
                                        },
                                        { errorMsg ->
                                            isLoading = false
                                            error = errorMsg
                                            showError = true
                                        }
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
private fun AIProviderRow(
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
private fun OpenAIOAuthView(
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
                    imageVector = Icons.Default.ArrowForward,
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
private fun OpenAIApiKeyView(
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
private fun AnthropicAuthView(
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
