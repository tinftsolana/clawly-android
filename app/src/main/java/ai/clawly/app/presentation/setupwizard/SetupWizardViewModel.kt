package ai.clawly.app.presentation.setupwizard

import ai.clawly.app.data.remote.gateway.GatewayService
import ai.clawly.app.domain.model.ChatErrorType
import ai.clawly.app.domain.model.ChatMessage
import ai.clawly.app.domain.model.ConnectionStatus
import ai.clawly.app.domain.model.StreamingState
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SetupWizardVM"

data class ApiKeyRequest(
    val keyName: String,
    val skillKey: String
)

data class SetupWizardUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isAssistantTyping: Boolean = false,
    val streamingContent: String = "",
    val pendingApiKeyRequest: ApiKeyRequest? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Offline
) {
    val hasStarted: Boolean get() = messages.isNotEmpty()
}

sealed interface SetupWizardEvent {
    data object ScrollToBottom : SetupWizardEvent
}

@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    private val gatewayService: GatewayService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupWizardUiState())
    val uiState: StateFlow<SetupWizardUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SetupWizardEvent>()
    val events: SharedFlow<SetupWizardEvent> = _events.asSharedFlow()

    private var pendingResponseCount = 0

    init {
        observeConnectionStatus()
        observeStreamingState()
        observeIncomingMessages()
    }

    private fun observeConnectionStatus() {
        viewModelScope.launch {
            gatewayService.connectionStatus.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }
    }

    private fun observeStreamingState() {
        viewModelScope.launch {
            gatewayService.streamingState.collect { state ->
                when (state) {
                    is StreamingState.Idle -> {
                        _uiState.update { it.copy(streamingContent = "") }
                    }
                    is StreamingState.Streaming -> {
                        _uiState.update {
                            it.copy(
                                isAssistantTyping = true,
                                streamingContent = state.partialContent
                            )
                        }
                    }
                    is StreamingState.Complete -> {
                        _uiState.update { it.copy(streamingContent = "") }
                    }
                }
            }
        }
    }

    private fun observeIncomingMessages() {
        viewModelScope.launch {
            gatewayService.incomingMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }
    }

    private fun handleIncomingMessage(message: ai.clawly.app.domain.model.GatewayMessage) {
        Log.d(TAG, "handleIncomingMessage: type=${message.type}")

        pendingResponseCount = (pendingResponseCount - 1).coerceAtLeast(0)
        val stillTyping = pendingResponseCount > 0
        _uiState.update { it.copy(isAssistantTyping = stillTyping, streamingContent = "") }

        val content = message.payload?.content

        if (content.isNullOrEmpty()) {
            Log.d(TAG, "Received non-content message: ${message.type}")
            emitScrollToBottom()
            return
        }

        if (message.type == "chat.error") {
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(
                        content = content,
                        isUser = false,
                        isError = true,
                        errorType = ChatErrorType.SendFailed
                    )
                )
            }
        } else {
            // Check for API key request tag
            val apiKeyReq = parseApiKeyTag(content)
            if (apiKeyReq != null) {
                _uiState.update { it.copy(pendingApiKeyRequest = apiKeyReq) }
                val cleaned = cleanContent(content)
                val newMessages = it_addMessages(cleaned, apiKeyReq)
                _uiState.update { it.copy(messages = it.messages + newMessages) }
            } else {
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage(
                            content = content,
                            isUser = false
                        )
                    )
                }
            }
        }
        emitScrollToBottom()
    }

    // Parse <enter_api_key name="..." skill="..." /> from response
    private fun parseApiKeyTag(content: String): ApiKeyRequest? {
        val regex = Regex("""<enter_api_key\s+name="([^"]+)"\s+skill="([^"]+)"\s*/>""")
        val match = regex.find(content) ?: return null
        return ApiKeyRequest(
            keyName = match.groupValues[1],
            skillKey = match.groupValues[2]
        )
    }

    // Strip the XML tag from display content
    private fun cleanContent(content: String): String {
        return content.replace(
            Regex("""<enter_api_key\s+name="[^"]+"\s+skill="[^"]+"\s*/>"""),
            ""
        ).trim()
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_uiState.value.isAssistantTyping) return

        val userMsg = ChatMessage.userMessage(trimmed)
        _uiState.update { it.copy(messages = it.messages + userMsg, isAssistantTyping = true) }
        emitScrollToBottom()

        // Build prompt — first message gets system context
        val prompt = if (_uiState.value.messages.count { it.isUser } <= 1) {
            """[SYSTEM] You are helping the user set up an integration. Follow these rules:

1. API KEYS: If the user needs to provide an API key or secret, respond with the tag <enter_api_key name="KEY_NAME" skill="SKILL_KEY" /> in your message. KEY_NAME is the environment variable name (e.g. SLACK_API_TOKEN). SKILL_KEY is the skill identifier (e.g. slack). The app will show a secure input field for the user to enter it.

2. INTEGRATIONS FILE: After the integration is fully set up and verified working, you MUST update the integrations registry file at /data/.openclaw/integrations.json. If the file doesn't exist, create it. The file format is:
{
  "integrations": [
    {
      "id": "skill_key",
      "name": "Human-readable Name",
      "icon": "emoji_icon",
      "status": "connected",
      "configuredAt": "ISO8601 timestamp"
    }
  ]
}
When adding a new integration, preserve existing entries. Set status to "connected" when verified working. Use a relevant emoji as icon (e.g. for Slack, for Notion, for Email, etc.).

3. COMPLETION: When you have verified the integration works, confirm to the user it's connected and mention you've updated the integrations registry.

[USER] I want to set up an integration: $trimmed. Please guide me through the setup step by step."""
        } else {
            trimmed
        }

        pendingResponseCount++
        viewModelScope.launch {
            gatewayService.sendMessage(message = prompt).onFailure { error ->
                pendingResponseCount = (pendingResponseCount - 1).coerceAtLeast(0)
                _uiState.update {
                    it.copy(
                        isAssistantTyping = false,
                        messages = it.messages + ChatMessage(
                            content = "Failed to send: ${error.message ?: "Unknown error"}",
                            isUser = false,
                            isError = true,
                            errorType = ChatErrorType.SendFailed
                        )
                    )
                }
                emitScrollToBottom()
            }
        }
    }

    private fun it_addMessages(cleaned: String, apiKeyReq: ApiKeyRequest): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        if (cleaned.isNotEmpty()) {
            messages.add(ChatMessage(content = cleaned, isUser = false))
        }
        // Add a special placeholder message for the key entry bubble
        messages.add(
            ChatMessage(
                content = "__api_key_request__:${apiKeyReq.keyName}:${apiKeyReq.skillKey}",
                isUser = false
            )
        )
        return messages
    }

    fun submitApiKey(value: String) {
        val req = _uiState.value.pendingApiKeyRequest ?: return
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return

        // Remove the key entry bubble
        _uiState.update {
            it.copy(
                messages = it.messages.filterNot { msg ->
                    msg.content.startsWith("__api_key_request__:")
                },
                pendingApiKeyRequest = null
            )
        }

        // Send as /config set chat command — same as iOS
        val configCommand = "/config set env.${req.keyName}=\"${trimmed}\""

        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage(
                    content = "API key ${req.keyName} saved.",
                    isUser = false
                )
            )
        }
        emitScrollToBottom()

        viewModelScope.launch {
            gatewayService.sendMessage(message = configCommand).onFailure { error ->
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage(
                            content = "Failed to save key: ${error.message ?: "Unknown error"}",
                            isUser = false,
                            isError = true,
                            errorType = ChatErrorType.SendFailed
                        )
                    )
                }
                emitScrollToBottom()
                return@launch
            }

            // Ask AI to verify
            send("User entered the API key ${req.keyName}. It's now configured in the environment. Please verify the integration is working.")
        }
    }

    fun retryLast() {
        val lastUserMsg = _uiState.value.messages.lastOrNull { it.isUser } ?: return
        // Remove last error message if present
        val lastMsg = _uiState.value.messages.lastOrNull()
        if (lastMsg?.isError == true) {
            _uiState.update { it.copy(messages = it.messages.dropLast(1)) }
        }
        send(lastUserMsg.content)
    }

    fun reconnect() {
        viewModelScope.launch {
            gatewayService.reconnect()
        }
    }

    private fun emitScrollToBottom() {
        viewModelScope.launch {
            _events.emit(SetupWizardEvent.ScrollToBottom)
        }
    }
}
