package ai.clawly.app.presentation.setupwizard

import ai.clawly.app.R
import ai.clawly.app.domain.model.ConnectionStatus
import ai.clawly.app.presentation.chat.components.ErrorMessageBubble
import ai.clawly.app.presentation.chat.components.FloatingInputBar
import ai.clawly.app.presentation.chat.components.MessageBubble
import ai.clawly.app.presentation.chat.components.TypingIndicator
import ai.clawly.app.ui.theme.ClawlyColors
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupWizardScreen(
    onBackClick: () -> Unit,
    initialPrompt: String? = null,
    viewModel: SetupWizardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // API Key bottom sheet
    var showApiKeySheet by remember { mutableStateOf(false) }
    var apiKeyValue by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Auto-send initial prompt if provided
    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrEmpty() && !state.hasStarted) {
            viewModel.send(initialPrompt)
        }
    }

    // Auto-scroll helper
    fun scrollToBottom() {
        scope.launch {
            val totalItems = state.messages.size +
                (if (state.isAssistantTyping) 1 else 0) +
                (if (state.hasStarted) 0 else 1) + // welcome item
                1 // bottom spacer
            if (totalItems > 0) {
                listState.animateScrollToItem(totalItems - 1)
            }
        }
    }

    // Scroll on events from VM
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SetupWizardEvent.ScrollToBottom -> scrollToBottom()
            }
        }
    }

    // Auto-scroll on new messages, typing, and streaming
    LaunchedEffect(state.messages.size, state.isAssistantTyping, state.streamingContent) {
        if (state.messages.isNotEmpty() || state.isAssistantTyping) {
            kotlinx.coroutines.delay(50)
            scrollToBottom()
        }
    }

    // Auto-scroll when keyboard opens/closes
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (state.messages.isNotEmpty()) {
            kotlinx.coroutines.delay(100)
            scrollToBottom()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Setup Wizard",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            "Powered by Clawly",
                            fontSize = 11.sp,
                            color = ClawlyColors.textMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Welcome state
                if (!state.hasStarted) {
                    item(key = "welcome") {
                        WelcomeState(
                            onSuggestionClick = { suggestion ->
                                viewModel.send(suggestion)
                            }
                        )
                    }
                }

                // Messages
                items(
                    items = state.messages,
                    key = { it.id }
                ) { message ->
                    when {
                        message.content.startsWith("__api_key_request__:") -> {
                            ApiKeyRequestBubble(
                                message = message,
                                onClick = {
                                    apiKeyValue = ""
                                    showApiKeySheet = true
                                }
                            )
                        }
                        message.isError -> {
                            ErrorMessageBubble(
                                message = message,
                                onRetry = { viewModel.retryLast() },
                                onReconnect = { viewModel.reconnect() }
                            )
                        }
                        else -> {
                            MessageBubble(message = message)
                        }
                    }
                }

                // Typing indicator
                if (state.isAssistantTyping) {
                    item(key = "typing") {
                        TypingIndicator(streamingContent = state.streamingContent)
                    }
                }

                // Bottom spacer
                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Gradient fade at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                ClawlyColors.background.copy(alpha = 0.92f),
                                ClawlyColors.background
                            )
                        )
                    )
            )

            // Floating input bar (reuses chat's FloatingInputBar)
            FloatingInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    val text = inputText.trim()
                    if (text.isNotEmpty()) {
                        viewModel.send(text)
                        inputText = ""
                    }
                },
                onAddAttachment = {},
                onRemoveAttachment = {},
                onAbort = {},
                isAssistantTyping = state.isAssistantTyping,
                isAborting = false,
                pendingAttachments = emptyList(),
                enabled = state.connectionStatus == ConnectionStatus.Online,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // API Key Entry Bottom Sheet
    if (showApiKeySheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showApiKeySheet = false
                apiKeyValue = ""
            },
            sheetState = sheetState,
            containerColor = ClawlyColors.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            ApiKeyEntrySheet(
                request = state.pendingApiKeyRequest,
                value = apiKeyValue,
                onValueChange = { apiKeyValue = it },
                onSubmit = {
                    viewModel.submitApiKey(apiKeyValue)
                    showApiKeySheet = false
                    apiKeyValue = ""
                },
                onCancel = {
                    showApiKeySheet = false
                    apiKeyValue = ""
                }
            )
        }
    }
}

// MARK: - Welcome State

private data class ProviderIconData(
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeState(
    onSuggestionClick: (String) -> Unit
) {
    val suggestions = listOf("Slack", "Notion", "Email", "1Password", "Calendar", "Telegram")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 80.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Provider icons row
        ProviderIconsRow()

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Setup Wizard",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = ClawlyColors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Tell Clawly what you want to set up\nand it will guide you through it!",
            fontSize = 15.sp,
            color = ClawlyColors.secondaryText,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Suggestion chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    text = suggestion,
                    onClick = { onSuggestionClick(suggestion) }
                )
            }
        }
    }
}

@Composable
private fun ProviderIconsRow() {
    val icons = listOf(
        ProviderIconData(Icons.Default.Email, Color(0xFF3B82F6)),
        ProviderIconData(Icons.Default.Person, Color(0xFF4ADE80)),
        ProviderIconData(Icons.Default.Star, Color(0xFFF59E0B)),
        ProviderIconData(Icons.AutoMirrored.Filled.List, Color(0xFFA855F7)),
        ProviderIconData(Icons.Default.DateRange, Color(0xFFEC4899)),
        ProviderIconData(Icons.Default.Notifications, Color(0xFF06B6D4)),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icons.forEach { data ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(48.dp)
                    .shadow(
                        8.dp,
                        RoundedCornerShape(14.dp),
                        ambientColor = data.color.copy(alpha = 0.4f)
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(data.color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = data.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ClawlyColors.surface)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = ClawlyColors.secondaryText
        )
    }
}

// MARK: - API Key Request Bubble

@Composable
private fun ApiKeyRequestBubble(
    message: ai.clawly.app.domain.model.ChatMessage,
    onClick: () -> Unit
) {
    val parts = message.content
        .removePrefix("__api_key_request__:")
        .split(":")
    val keyName = parts.firstOrNull() ?: "API_KEY"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
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
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ClawlyColors.surface)
                .border(
                    width = 1.dp,
                    color = ClawlyColors.accentPrimary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(onClick = onClick)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = ClawlyColors.accentPrimary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enter API Key",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ClawlyColors.textPrimary
                    )
                    Text(
                        text = keyName,
                        fontSize = 12.sp,
                        color = ClawlyColors.secondaryText
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ClawlyColors.secondaryText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// MARK: - API Key Entry Sheet

@Composable
private fun ApiKeyEntrySheet(
    request: ApiKeyRequest?,
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = ClawlyColors.accentPrimary,
            modifier = Modifier.size(36.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (request != null) {
            Text(
                text = request.keyName,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = ClawlyColors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "for ${request.skillKey}",
                fontSize = 14.sp,
                color = ClawlyColors.secondaryText
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text("Paste your API key here", color = ClawlyColors.textMuted)
            },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ClawlyColors.textPrimary,
                unfocusedTextColor = ClawlyColors.textPrimary,
                cursorColor = ClawlyColors.accentPrimary,
                focusedBorderColor = Color.White.copy(alpha = 0.1f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = ClawlyColors.surfaceElevated,
                unfocusedContainerColor = ClawlyColors.surfaceElevated
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = ClawlyColors.secondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onSubmit,
            enabled = value.trim().isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ClawlyColors.accentPrimary,
                disabledContainerColor = ClawlyColors.accentPrimary.copy(alpha = 0.4f)
            )
        ) {
            Text(
                text = "Save Key",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Cancel",
            fontSize = 14.sp,
            color = ClawlyColors.secondaryText,
            modifier = Modifier.clickable(onClick = onCancel)
        )
    }
}
