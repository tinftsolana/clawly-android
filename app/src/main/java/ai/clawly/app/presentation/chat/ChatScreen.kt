package ai.clawly.app.presentation.chat

import ai.clawly.app.BuildConfig
import ai.clawly.app.presentation.chat.components.*
import ai.clawly.app.ui.theme.ClawlyColors
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import java.util.UUID

@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    onNavigateToProviderSetup: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var showGatewayResolvingAlert by remember { mutableStateOf(false) }

    // Voice recording state
    var wantsRecording by remember { mutableStateOf(false) }
    var hasRecordPermission by remember { mutableStateOf(false) }

    // Speech recognizer
    val speechRecognizer = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    // Create speech intent
    fun createSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 30000L)
        }
    }

    // Restart listening callback
    val restartListening: () -> Unit = {
        if (wantsRecording && speechRecognizer != null) {
            try {
                speechRecognizer.startListening(createSpeechIntent())
            } catch (e: Exception) {
                Log.e("ChatScreen", "Failed to restart listening", e)
                wantsRecording = false
                viewModel.setRecording(false)
            }
        }
    }

    // Set up recognition listener
    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                viewModel.setRecording(true)
                Log.d("ChatScreen", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("ChatScreen", "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                viewModel.updateRmsLevel(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("ChatScreen", "End of speech")
            }

            override fun onError(error: Int) {
                Log.e("ChatScreen", "Speech recognition error: $error")
                // Auto-restart for recoverable errors if user still wants recording
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (wantsRecording) {
                            restartListening()
                        } else {
                            viewModel.setRecording(false)
                        }
                    }
                    else -> {
                        wantsRecording = false
                        viewModel.setRecording(false)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull() ?: ""
                Log.d("ChatScreen", "Results: $recognizedText")

                if (recognizedText.isNotBlank()) {
                    // Append to input text
                    val currentText = inputText
                    val separator = if (currentText.isNotEmpty() && !currentText.endsWith(" ")) " " else ""
                    inputText = currentText + separator + recognizedText
                }

                // Auto-restart if user still wants to record
                if (wantsRecording) {
                    restartListening()
                } else {
                    viewModel.setRecording(false)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull() ?: ""
                if (partialText.isNotBlank()) {
                    viewModel.updatePartialResult(partialText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        onDispose {
            speechRecognizer?.destroy()
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (isGranted && wantsRecording && speechRecognizer != null) {
            speechRecognizer.startListening(createSpeechIntent())
        } else if (!isGranted) {
            wantsRecording = false
        }
    }

    // Toggle recording function
    val toggleRecording: () -> Unit = toggleRecording@{
        if (speechRecognizer == null) {
            Log.e("ChatScreen", "Speech recognition not available")
            return@toggleRecording
        }

        if (wantsRecording) {
            // Stop recording
            wantsRecording = false
            speechRecognizer.stopListening()
            viewModel.setRecording(false)
        } else {
            // Start recording
            wantsRecording = true
            if (hasRecordPermission) {
                speechRecognizer.startListening(createSpeechIntent())
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val fileName = "image-${UUID.randomUUID()}.${mimeType.substringAfter("/")}"

                    viewModel.addAttachment(
                        PendingAttachment(
                            data = bytes,
                            fileName = fileName,
                            mimeType = mimeType
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatScreen", "Failed to load image", e)
            }
        }
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ChatEvent.ShowPaywall -> onNavigateToPaywall()
                is ChatEvent.ShowLogin -> onNavigateToLogin()
                is ChatEvent.ShowConfigPrompt -> {
                    android.widget.Toast.makeText(context, "Config needed - check settings", android.widget.Toast.LENGTH_SHORT).show()
                    onNavigateToSettings()
                }
                is ChatEvent.ShowProviderSetup -> onNavigateToProviderSetup()
                is ChatEvent.ShowGatewayResolvingAlert -> {
                    showGatewayResolvingAlert = true
                }
                is ChatEvent.ShowError -> {
                    android.widget.Toast.makeText(context, "Error: ${event.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
                is ChatEvent.ScrollToBottom -> {
                    // Handled by auto-scroll
                }
                is ChatEvent.MessageSent -> {
                    inputText = ""
                }
                is ChatEvent.SpeakText -> {
                    // TTS integration - could inject TTSService here
                }
            }
        }
    }

    // Connection is managed by GatewayConnectionUseCase (auto-connect + foreground reconnect)

    val density = LocalDensity.current
    val imeHeight = WindowInsets.ime.getBottom(density)
    val imeVisible = imeHeight > 0

    val listState = rememberAutoScrollState(
        messages = uiState.messages,
        isTyping = uiState.isAssistantTyping,
        imeVisible = imeVisible
    )

    // Top glow gradient colors
    val glowColor = ClawlyColors.accentPrimary

    // Approximate heights for content padding (so messages scroll under bars)
    val topBarHeight = 56.dp
    val bottomBarHeight = 70.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClawlyColors.background)
    ) {
        // Top radial glow effect (background layer)
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

        // Content layer - fills entire screen, scrolls under bars
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            if (uiState.messages.isEmpty() && !uiState.isAssistantTyping) {
                EmptyState(
                    onSuggestionClick = { suggestion ->
                        inputText = suggestion
                        viewModel.sendMessage(suggestion)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = topBarHeight)
                )
            } else {
                MessageList(
                    messages = uiState.messages,
                    isAssistantTyping = uiState.isAssistantTyping,
                    streamingContent = uiState.streamingContent,
                    onRetry = { viewModel.retryLastMessage() },
                    onReconnect = { viewModel.reconnect() },
                    listState = listState,
                    contentPadding = PaddingValues(
                        top = topBarHeight + 48.dp,
                        bottom = bottomBarHeight + 16.dp
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (uiState.isResolvingGatewayAccess) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topBarHeight + 12.dp, start = 16.dp, end = 16.dp),
                    color = Color(0xFFFF9500).copy(alpha = 0.16f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "Pairing in progress. Reconnecting...",
                        color = Color(0xFFFFC15E),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // Error snackbar
            if (uiState.showError && uiState.error != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomBarHeight + 48.dp)
                        .padding(horizontal = 16.dp),
                    containerColor = ClawlyColors.error,
                    action = {
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("Dismiss", color = Color.White)
                        }
                    }
                ) {
                    Text(uiState.error!!, color = Color.White)
                }
            }
        }

        // Top nav bar - floating with subtle gradient fade (matches bottom)
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
            ChatNavBar(
                connectionStatus = uiState.connectionStatus,
                onSettingsClick = onNavigateToSettings,
                showPremiumCrown = BuildConfig.IS_WEB2 && !uiState.hasPremiumAccess,
                onPremiumClick = onNavigateToPaywall,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Bottom input bar - floating with subtle gradient fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .imePadding()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            ClawlyColors.background.copy(alpha = 0.7f),
                            ClawlyColors.background.copy(alpha = 0.95f)
                        ),
                        startY = 0f,
                        endY = with(density) { 60.dp.toPx() }
                    )
                )
        ) {
            FloatingInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() || uiState.pendingAttachments.isNotEmpty()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                onAddAttachment = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemoveAttachment = { viewModel.removeAttachment(it) },
                onAbort = { viewModel.abortResponse() },
                isAssistantTyping = uiState.isAssistantTyping,
                isAborting = uiState.isAborting,
                pendingAttachments = uiState.pendingAttachments,
                enabled = uiState.isConnected && !uiState.isResolvingGatewayAccess,
                onMicClick = toggleRecording,
                isRecording = uiState.isRecording,
                rmsLevel = uiState.recordingRmsLevel,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    }

    if (showGatewayResolvingAlert) {
        AlertDialog(
            onDismissRequest = { showGatewayResolvingAlert = false },
            confirmButton = {
                TextButton(onClick = { showGatewayResolvingAlert = false }) {
                    Text("OK")
                }
            },
            title = { Text("Resolving Access") },
            text = {
                Text("Gateway access is still being resolved. Please wait a few seconds, then send again.")
            }
        )
    }
}
