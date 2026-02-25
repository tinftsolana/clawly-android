package ai.clawly.app.presentation.chat

import ai.clawly.app.BuildConfig
import ai.clawly.app.analytics.AmplitudeAnalyticsService
import ai.clawly.app.data.auth.FirebaseAuthService
import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.data.remote.RemoteConfigFlags
import ai.clawly.app.data.remote.gateway.GatewayService
import ai.clawly.app.data.service.PurchaseService
import ai.clawly.app.domain.manager.SkillsManager
import ai.clawly.app.domain.model.*
import ai.clawly.app.domain.repository.AuthProviderRepository
import ai.clawly.app.domain.repository.ChatRepository
import ai.clawly.app.domain.repository.AttachmentPayload
import ai.clawly.app.domain.usecase.GatewayConnectionUseCase
import ai.clawly.app.domain.usecase.GetUserIdentityUseCase
import ai.clawly.app.domain.usecase.UserIdentity
import ai.clawly.app.domain.usecase.Web3CreditsUseCase
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

private const val TAG = "ChatViewModel"
private const val PAIRING_WARMUP_WINDOW_MS = 20_000L
private const val PAIRING_ALERT_COOLDOWN_MS = 8_000L

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val gatewayService: GatewayService,
    private val chatRepository: ChatRepository,
    private val authProviderRepository: AuthProviderRepository,
    private val preferences: GatewayPreferences,
    private val analytics: AmplitudeAnalyticsService,
    private val skillsManager: SkillsManager,
    private val getUserIdentityUseCase: GetUserIdentityUseCase,
    private val web3CreditsUseCase: Web3CreditsUseCase,
    private val gatewayConnectionUseCase: GatewayConnectionUseCase,
    private val firebaseAuthService: FirebaseAuthService,
    private val purchaseService: PurchaseService,
    private val pendingMessageHolder: PendingMessageHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    // Free message limit for non-premium users
    private val freeMessageLimit = 2

    // Last user message for retry
    private var lastUserMessage: String? = null
    private var hasLoadedHistory = false
    private var pairingWarmupUntilMs: Long? = null
    private var lastPairingAlertAtMs: Long? = null

    val assistantName = "Clawly"

    private val _userIdentity = MutableStateFlow<UserIdentity>(UserIdentity.NotAuthenticated)
    val userIdentity: StateFlow<UserIdentity> = _userIdentity.asStateFlow()

    private val _web3Credits = MutableStateFlow(0)
    val web3Credits: StateFlow<Int> = _web3Credits.asStateFlow()

    init {
        loadPersistedMessages()
        loadThinkingLevel()
        setupSubscriptions()
        if (BuildConfig.IS_WEB3) {
            observeWeb3Credits()
        }
        checkAndSendPendingMessage()
    }

    private fun checkAndSendPendingMessage() {
        pendingMessageHolder.consumePendingMessage()?.let { message ->
            Log.d(TAG, "Found pending message, sending: ${message.take(50)}...")
            viewModelScope.launch {
                // Small delay to ensure connection is ready
                delay(500)
                sendMessage(message)
            }
        }
    }

    private fun observeWeb3Credits() {
        viewModelScope.launch {
            web3CreditsUseCase.creditsFlow.collect { credits ->
                _web3Credits.value = credits
                Log.d(TAG, "Web3 credits updated: $credits")
            }
        }
    }

    private fun loadPersistedMessages() {
        viewModelScope.launch {
            val messages = chatRepository.loadMessages()
            _uiState.update { it.copy(messages = messages) }
        }
        // Also observe for when messages are cleared (on logout)
        viewModelScope.launch {
            chatRepository.messages.collect { persistedMessages ->
                if (persistedMessages.isEmpty() && _uiState.value.messages.isNotEmpty()) {
                    Log.d(TAG, "Persisted messages cleared, clearing UI state")
                    _uiState.update { it.copy(messages = emptyList()) }
                    hasLoadedHistory = false
                }
            }
        }
    }

    private fun loadThinkingLevel() {
        viewModelScope.launch {
            val level = ThinkingLevel.fromString(preferences.getThinkingLevelSync())
            _uiState.update { it.copy(thinkingLevel = level) }
        }
    }

    private fun setupSubscriptions() {
        // Observe connection status from shared singleton
        viewModelScope.launch {
            gatewayConnectionUseCase.connectionStatus.collect { status ->
                val now = System.currentTimeMillis()
                val wasOffline = _uiState.value.connectionStatus != ConnectionStatus.Online
                val currentHostingType = _uiState.value.currentAuthProvider.hostingType
                val isManaged = currentHostingType == HostingType.Managed
                val shouldResolveGatewayAccess = when (status) {
                    is ConnectionStatus.Connecting -> {
                        isManaged && (pairingWarmupUntilMs?.let { it > now } == true)
                    }
                    is ConnectionStatus.Error -> {
                        val lower = status.message.lowercase()
                        val pairingRelated = lower.contains("pairing required") || lower.contains("not paired")
                        if (isManaged && pairingRelated) {
                            pairingWarmupUntilMs = now + PAIRING_WARMUP_WINDOW_MS
                            true
                        } else {
                            false
                        }
                    }
                    is ConnectionStatus.Online -> {
                        pairingWarmupUntilMs = null
                        false
                    }
                    is ConnectionStatus.Offline -> false
                }

                _uiState.update {
                    it.copy(
                        connectionStatus = status,
                        isResolvingGatewayAccess = shouldResolveGatewayAccess
                    )
                }
                when (status) {
                    is ConnectionStatus.Online -> Log.d(TAG, "Connection status: Online")
                    is ConnectionStatus.Connecting -> Log.d(TAG, "Connection status: Connecting")
                    is ConnectionStatus.Offline -> Log.d(TAG, "Connection status: Offline")
                    is ConnectionStatus.Error -> Log.e(TAG, "Connection status: Error - ${status.message}")
                }

                // Fetch history when first connecting
                if (status == ConnectionStatus.Online && wasOffline && !hasLoadedHistory) {
                    fetchChatHistory()
                }
            }
        }

        // Observe incoming messages
        viewModelScope.launch {
            gatewayService.incomingMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }

        // Observe streaming state
        viewModelScope.launch {
            gatewayService.streamingState.collect { state ->
                handleStreamingState(state)
            }
        }

        // Observe auth provider changes
        viewModelScope.launch {
            authProviderRepository.currentConfig.collect { config ->
                _uiState.update {
                    it.copy(
                        currentAuthProvider = config,
                        isResolvingGatewayAccess = if (config.hostingType == HostingType.Managed) {
                            it.isResolvingGatewayAccess
                        } else {
                            false
                        }
                    )
                }
                if (config.hostingType != HostingType.Managed) {
                    pairingWarmupUntilMs = null
                    lastPairingAlertAtMs = null
                }
            }
        }

        // Resolve premium from RevenueCat subscription + optional debug override
        viewModelScope.launch {
            combine(
                purchaseService.subscriptionStatus,
                preferences.debugPremiumActive,
                preferences.debugPremiumOverride
            ) { subscriptionStatus, debugActive, debugValue ->
                if (debugActive) debugValue else subscriptionStatus.isActive
            }.collect { hasPremium ->
                Log.d(TAG, "Premium access updated: $hasPremium")
                _uiState.update { it.copy(hasPremiumAccess = hasPremium) }
            }
        }

        // Observe user identity (wallet for web3, device for web2)
        viewModelScope.launch {
            getUserIdentityUseCase.identityFlow.collect { identity ->
                _userIdentity.value = identity
                when (identity) {
                    is UserIdentity.Authenticated -> {
                        Log.d(TAG, "User authenticated: ${identity.userId}, isWeb3: ${identity.isWeb3}")
                    }
                    is UserIdentity.NotAuthenticated -> {
                        Log.d(TAG, "User not authenticated")
                    }
                }
            }
        }
    }

    private fun handleStreamingState(state: StreamingState) {
        when (state) {
            is StreamingState.Idle -> {
                _uiState.update { it.copy(streamingContent = "") }
            }
            is StreamingState.Streaming -> {
                _uiState.update {
                    it.copy(
                        streamingContent = state.partialContent,
                        isAssistantTyping = true
                    )
                }
            }
            is StreamingState.Complete -> {
                _uiState.update { it.copy(streamingContent = "") }
            }
        }
    }

    private fun fetchChatHistory() {
        viewModelScope.launch {
            gatewayService.fetchHistory()
                .onSuccess { historyMessages ->
                    mergeHistoryWithLocal(historyMessages)
                    hasLoadedHistory = true
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to fetch history", error)
                    hasLoadedHistory = true
                }
        }
    }

    private fun mergeHistoryWithLocal(historyMessages: List<ai.clawly.app.domain.repository.ChatHistoryMessage>) {
        Log.d(TAG, "mergeHistoryWithLocal: received ${historyMessages.size} history messages")
        val currentMessages = _uiState.value.messages
        Log.d(TAG, "mergeHistoryWithLocal: current local messages: ${currentMessages.size}")
        val localIds = currentMessages.map { it.id }.toSet()
        val newMessages = mutableListOf<ChatMessage>()

        for (histMsg in historyMessages) {
            Log.d(TAG, "History msg: id=${histMsg.id}, role=${histMsg.role}, content=${histMsg.content.take(30)}...")
            if (histMsg.id in localIds) {
                Log.d(TAG, "  -> Skipping, already in local")
                continue
            }

            val chatMsg = ChatMessage(
                id = histMsg.id,
                content = histMsg.content,
                isUser = histMsg.isUser,
                timestamp = histMsg.timestamp ?: System.currentTimeMillis()
            )
            newMessages.add(chatMsg)
            Log.d(TAG, "  -> Added to newMessages")
        }

        Log.d(TAG, "mergeHistoryWithLocal: ${newMessages.size} new messages to merge")
        if (newMessages.isNotEmpty()) {
            val merged = newMessages + currentMessages
            _uiState.update { it.copy(messages = merged) }
            viewModelScope.launch {
                chatRepository.saveMessages(merged)
            }
            Log.d(TAG, "Merged ${newMessages.size} messages from history, total now: ${merged.size}")
        }
    }

    fun connect() {
        analytics.trackChatConnect()
        viewModelScope.launch {
            gatewayConnectionUseCase.connect()
        }
    }

    fun disconnect() {
        analytics.trackChatDisconnect()
        viewModelScope.launch {
            gatewayConnectionUseCase.disconnect()
        }
    }

    fun setThinkingLevel(level: ThinkingLevel) {
        analytics.trackChatThinkingLevelChanged(level.name.lowercase())
        _uiState.update { it.copy(thinkingLevel = level) }
        viewModelScope.launch {
            preferences.setThinkingLevel(level.name.lowercase())
        }
    }

    fun addAttachment(attachment: PendingAttachment) {
        val current = _uiState.value.pendingAttachments.toMutableList()
        if (current.size < 4) {
            current.add(attachment)
            _uiState.update { it.copy(pendingAttachments = current) }
        }
    }

    fun removeAttachment(attachmentId: String) {
        val current = _uiState.value.pendingAttachments.filterNot { it.id == attachmentId }
        _uiState.update { it.copy(pendingAttachments = current) }
    }

    fun clearAttachments() {
        _uiState.update { it.copy(pendingAttachments = emptyList()) }
    }

    /**
     * Validate if user can send a message
     */
    fun validateSendMessage(): SendValidationResult {
        val state = _uiState.value

        Log.d(TAG, "Validating send: hasPremium=${state.hasPremiumAccess}, hasAuthProvider=${state.hasAuthProvider}, isProvisioning=${state.isProvisioning}, isConnected=${state.isConnected}, hostingType=${state.currentAuthProvider.hostingType}, isConfigured=${state.currentAuthProvider.isConfigured}")
        val selfHostedWithoutPremium = RemoteConfigFlags.isSelfHostedWithoutPremiumEnabled()

        // Web3 builds: Check credits and connection
        if (BuildConfig.IS_WEB3) {
            val credits = _web3Credits.value
            Log.d(TAG, "Web3 validation: credits=$credits, isConnected=${state.isConnected}")
            if (credits <= 0) {
                Log.d(TAG, "Validation failed: no web3 credits")
                return SendValidationResult.ShowPaywall
            }
            // Also need to check connection for web3
            if (!state.isConnected) {
                Log.d(TAG, "Validation failed: web3 not connected to gateway")
                return SendValidationResult.ShowConfigPrompt
            }
            // Web3 with credits and connected - allow
            return SendValidationResult.Allowed
        }

        // Web2 flow below
        // Premium users
        if (state.hasPremiumAccess) {
            // Still provisioning -> show config prompt (to show status)
            if (state.isProvisioning) {
                Log.d(TAG, "Validation failed: still provisioning")
                return SendValidationResult.ShowConfigPrompt
            }

            // Note: For managed hosting, OpenClaw proxy is auto-activated, so no need to check AI provider
            // The old check for aiProvider is removed since we auto-configure OpenClaw on instance ready

            // Not connected to gateway -> show config prompt
            if (!state.isConnected || state.isResolvingGatewayAccess) {
                Log.d(TAG, "Validation failed: not connected")
                return if (state.currentAuthProvider.hostingType == HostingType.Managed) {
                    SendValidationResult.GatewayReconnecting
                } else {
                    SendValidationResult.ShowConfigPrompt
                }
            }

            if (!state.hasAuthProvider) {
                Log.w(TAG, "No auth provider in state, but gateway is connected; allowing send")
            }
            Log.d(TAG, "Validation passed")
            return SendValidationResult.Allowed
        }

        // Non-premium users
        // Temporary guest mode for web2: skip login requirement
        if (selfHostedWithoutPremium && state.currentAuthProvider.hostingType == HostingType.SelfHosted) {
            if (state.isProvisioning) {
                return SendValidationResult.ShowConfigPrompt
            }
            if (!state.isConnected) {
                return SendValidationResult.ShowConfigPrompt
            }
            return SendValidationResult.Allowed
        }

        // If not connected, show paywall immediately
        if (!state.isConnected) {
            return SendValidationResult.ShowPaywall
        }

        // If reached message limit, show paywall
        if (state.userMessageCount >= freeMessageLimit) {
            return SendValidationResult.ShowPaywall
        }

        return SendValidationResult.Allowed
    }

    /**
     * Send a message
     */
    fun sendMessage(text: String) {
        Log.d(TAG, "sendMessage called with: '$text'")
        val trimmedText = text.trim()
        val attachments = _uiState.value.pendingAttachments

        if (trimmedText.isEmpty() && attachments.isEmpty()) {
            Log.d(TAG, "sendMessage: empty text and no attachments, returning")
            return
        }

        // Validate
        val validation = validateSendMessage()
        Log.d(TAG, "sendMessage validation result: $validation")
        when (validation) {
            SendValidationResult.Allowed -> {
                // Continue to send
            }
            SendValidationResult.ShowPaywall -> {
                viewModelScope.launch { _events.emit(ChatEvent.ShowPaywall) }
                return
            }
            SendValidationResult.ShowLogin -> {
                viewModelScope.launch { _events.emit(ChatEvent.ShowLogin) }
                return
            }
            SendValidationResult.ShowConfigPrompt -> {
                viewModelScope.launch { _events.emit(ChatEvent.ShowConfigPrompt) }
                return
            }
            SendValidationResult.ShowProviderSetup -> {
                viewModelScope.launch { _events.emit(ChatEvent.ShowProviderSetup) }
                return
            }
            SendValidationResult.GatewayReconnecting -> {
                viewModelScope.launch {
                    _events.emit(ChatEvent.ShowGatewayResolvingAlert)
                    gatewayConnectionUseCase.reconnect()
                }
                return
            }
        }

        // Deduct credit for web3 builds
        if (BuildConfig.IS_WEB3) {
            viewModelScope.launch {
                web3CreditsUseCase.deductCredit()
                Log.d(TAG, "Deducted 1 credit for web3 message")
            }
        }

        // Store for retry
        lastUserMessage = trimmedText

        // Convert pending attachments to message attachments
        val messageAttachments = attachments.map {
            MessageAttachment(imageData = it.data, mimeType = it.mimeType)
        }

        // Add user message locally
        val userMessage = ChatMessage.userMessage(trimmedText, messageAttachments)
        val updatedMessages = _uiState.value.messages + userMessage

        _uiState.update {
            it.copy(
                messages = updatedMessages,
                isAssistantTyping = true,
                streamingContent = "",
                isResolvingGatewayAccess = false,
                pendingAttachments = emptyList()
            )
        }

        // Track analytics
        analytics.trackChatMessageSent(
            length = trimmedText.length,
            hasAttachments = attachments.isNotEmpty(),
            attachmentCount = attachments.size,
            thinkingLevel = _uiState.value.thinkingLevel.name.lowercase()
        )

        viewModelScope.launch {
            chatRepository.saveMessages(updatedMessages)
            _events.emit(ChatEvent.MessageSent)
            _events.emit(ChatEvent.ScrollToBottom)
        }

        // Send via gateway
        viewModelScope.launch {
            val attachmentPayloads = attachments.map {
                AttachmentPayload.fromImageData(it.data, it.fileName, it.mimeType)
            }

            val enabledSkills = skillsManager.getEnabledSkillPayloads()
            Log.d(TAG, "Sending message with ${enabledSkills.size} skills")

            gatewayService.sendMessage(
                message = trimmedText,
                thinkingLevel = _uiState.value.thinkingLevel,
                skills = enabledSkills,
                attachments = attachmentPayloads
            ).onFailure { error ->
                handleSendError(error)
            }
        }
    }

    private fun handleSendError(error: Throwable) {
        val errorMessage = ChatMessage.errorMessage(
            "Failed to send message. Please check your connection.",
            ChatErrorType.SendFailed
        )
        val updatedMessages = _uiState.value.messages + errorMessage

        _uiState.update {
            it.copy(
                messages = updatedMessages,
                isAssistantTyping = false,
                streamingContent = "",
                isResolvingGatewayAccess = false,
                error = error.message,
                showError = true
            )
        }

        viewModelScope.launch {
            chatRepository.saveMessages(updatedMessages)
        }
    }

    private suspend fun handleIncomingMessage(message: GatewayMessage) {
        _uiState.update {
            it.copy(isAssistantTyping = false, streamingContent = "")
        }

        val content = message.payload?.content
        if (content.isNullOrEmpty()) {
            Log.d(TAG, "Received non-content message: ${message.type}")
            return
        }

        maybeShowPairingResolvingPrompt(content)

        analytics.trackChatMessageReceived(type = message.type)

        val assistantMessage = ChatMessage.assistantMessage(content)
        val updatedMessages = _uiState.value.messages + assistantMessage

        _uiState.update { it.copy(messages = updatedMessages) }
        chatRepository.saveMessages(updatedMessages)

        // Emit speak event for TTS
        _events.emit(ChatEvent.SpeakText(content))
        _events.emit(ChatEvent.ScrollToBottom)
    }

    /**
     * Abort the current response
     */
    fun abortResponse() {
        val state = _uiState.value
        if (!state.isAssistantTyping || state.isAborting) return

        analytics.trackChatAbort()
        _uiState.update { it.copy(isAborting = true) }

        viewModelScope.launch {
            gatewayService.abortChat()
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isAborting = false,
                            isAssistantTyping = false,
                            streamingContent = ""
                        )
                    }
                    Log.d(TAG, "Response aborted")
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isAborting = false,
                            isAssistantTyping = false,
                            streamingContent = ""
                        )
                    }
                    Log.e(TAG, "Abort failed", error)
                }
        }
    }

    /**
     * Retry the last failed message
     */
    fun retryLastMessage() {
        analytics.trackChatRetryMessage(_uiState.value.thinkingLevel.name.lowercase())

        // Remove last error message if present
        val messages = _uiState.value.messages.toMutableList()
        if (messages.lastOrNull()?.isError == true) {
            messages.removeAt(messages.lastIndex)
            _uiState.update { it.copy(messages = messages) }
        }

        // Resend
        lastUserMessage?.let { message ->
            _uiState.update { it.copy(isAssistantTyping = true, streamingContent = "") }

            viewModelScope.launch {
                gatewayService.sendMessage(
                    message = message,
                    thinkingLevel = _uiState.value.thinkingLevel
                ).onFailure { error ->
                    handleSendError(error)
                }
            }
        }
    }

    /**
     * Reconnect to gateway
     */
    fun reconnect() {
        analytics.trackChatReconnect()
        viewModelScope.launch {
            gatewayConnectionUseCase.reconnect()
        }
    }

    /**
     * Clear chat history
     */
    fun clearHistory() {
        analytics.trackChatHistoryCleared()
        _uiState.update { it.copy(messages = emptyList()) }
        lastUserMessage = null

        viewModelScope.launch {
            chatRepository.clearMessages()
        }
    }

    private fun hasPairingSignalInAssistantText(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("pairing required")
                || lower.contains("not paired")
                || lower.contains("gateway isn't paired")
                || lower.contains("gateway isn’t paired")
    }

    private suspend fun maybeShowPairingResolvingPrompt(text: String) {
        val state = _uiState.value
        if (state.currentAuthProvider.hostingType != HostingType.Managed) return
        if (!hasPairingSignalInAssistantText(text)) return

        val now = System.currentTimeMillis()
        val lastShown = lastPairingAlertAtMs
        if (lastShown != null && now - lastShown < PAIRING_ALERT_COOLDOWN_MS) {
            return
        }

        lastPairingAlertAtMs = now
        pairingWarmupUntilMs = now + PAIRING_WARMUP_WINDOW_MS
        _uiState.update { it.copy(isResolvingGatewayAccess = true) }
        analytics.track("pairing_resolving_shown")

        val attempted = authProviderRepository.tryApprovePendingPairingFromList(
            source = "pairing_resolving_shown",
            reconnectAfterApproval = true
        )
        Log.d(TAG, "List-based pairing approve attempted: $attempted")
        if (!attempted) {
            val switched = maybeSwitchToDeviceScopedSessionKey()
            Log.d(TAG, "Pairing list empty, switched session key for recovery: $switched")
        }

        _events.emit(ChatEvent.ShowGatewayResolvingAlert)
        gatewayConnectionUseCase.reconnect()
    }

    private suspend fun maybeSwitchToDeviceScopedSessionKey(): Boolean {
        val currentSessionKey = preferences.getSessionKeySync().trim()
        if (currentSessionKey.isNotEmpty() && currentSessionKey != GatewayPreferences.DEFAULT_SESSION_KEY) {
            return false
        }

        val instanceId = preferences.getOrCreateInstanceId()
        val suffix = instanceId.replace("-", "").takeLast(12).ifEmpty {
            System.currentTimeMillis().toString(16)
        }
        val nextSessionKey = "agent:main:android-$suffix"
        if (currentSessionKey == nextSessionKey) return false

        preferences.setSessionKey(nextSessionKey)
        hasLoadedHistory = false
        Log.d(TAG, "Session key switched for managed pairing recovery: $nextSessionKey")
        return true
    }

    fun dismissError() {
        _uiState.update { it.copy(showError = false, error = null) }
    }

    fun setPremiumAccess(hasPremium: Boolean) {
        _uiState.update { it.copy(hasPremiumAccess = hasPremium) }
    }

    // Voice recording methods
    fun setRecording(recording: Boolean) {
        _uiState.update { it.copy(isRecording = recording) }
    }

    fun updateRmsLevel(rms: Float) {
        _uiState.update { it.copy(recordingRmsLevel = rms) }
    }

    fun appendRecognitionResult(text: String) {
        if (text.isBlank()) return
        val current = _uiState.value.partialRecognitionText
        val separator = if (current.isNotEmpty() && !current.endsWith(" ")) " " else ""
        _uiState.update { it.copy(partialRecognitionText = current + separator + text) }
    }

    fun updatePartialResult(partial: String) {
        _uiState.update { it.copy(partialRecognitionText = partial) }
    }

    fun clearPartialResult() {
        _uiState.update { it.copy(partialRecognitionText = "") }
    }

    fun getAndClearRecognitionText(): String {
        val text = _uiState.value.partialRecognitionText
        _uiState.update { it.copy(partialRecognitionText = "") }
        return text
    }
}
