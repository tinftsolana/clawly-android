package ai.clawly.app.presentation.chat

import ai.clawly.app.BuildConfig
import ai.clawly.app.analytics.AmplitudeAnalyticsService
import ai.clawly.app.data.auth.FirebaseAuthService
import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.data.remote.RemoteConfigFlags
import ai.clawly.app.data.remote.ControlPlaneService
import ai.clawly.app.data.remote.gateway.GatewayService
import ai.clawly.app.data.service.PurchaseService
import ai.clawly.app.domain.manager.SkillsManager
import ai.clawly.app.domain.model.*
import ai.clawly.app.domain.model.SignRequestBubbleState
import ai.clawly.app.domain.repository.AuthProviderRepository
import ai.clawly.app.domain.repository.ChatRepository
import ai.clawly.app.domain.repository.AttachmentPayload
import ai.clawly.app.domain.repository.SolanaAuthRepository
import ai.clawly.app.domain.usecase.ConnectWalletUseCase
import ai.clawly.app.domain.usecase.GatewayConnectionUseCase
import ai.clawly.app.domain.usecase.GetUserIdentityUseCase
import ai.clawly.app.domain.usecase.UserIdentity
import ai.clawly.app.domain.usecase.Web2CreditsUseCase
import ai.clawly.app.domain.usecase.Web3CreditsUseCase
import android.util.Log
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject

private const val TAG = "ChatViewModel"
private const val PAIRING_WARMUP_WINDOW_MS = 20_000L
private const val PAIRING_ALERT_COOLDOWN_MS = 8_000L

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val gatewayService: GatewayService,
    private val controlPlaneService: ControlPlaneService,
    private val chatRepository: ChatRepository,
    private val authProviderRepository: AuthProviderRepository,
    private val preferences: GatewayPreferences,
    private val analytics: AmplitudeAnalyticsService,
    private val skillsManager: SkillsManager,
    private val getUserIdentityUseCase: GetUserIdentityUseCase,
    private val web3CreditsUseCase: Web3CreditsUseCase,
    private val web2CreditsUseCase: Web2CreditsUseCase,
    private val connectWalletUseCase: ConnectWalletUseCase,
    private val gatewayConnectionUseCase: GatewayConnectionUseCase,
    private val firebaseAuthService: FirebaseAuthService,
    private val purchaseService: PurchaseService,
    private val solanaAuthRepository: SolanaAuthRepository,
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
    private var lastPresentedSignRequestId: String? = null
    private val signWsClient by lazy { OkHttpClient() }
    private var signWs: WebSocket? = null
    private var signWsToken: String? = null
    private val pendingSignData = mutableMapOf<String, SolanaSignRequestUi>()

    // Simple counter: incremented on send, decremented on response/error
    private var pendingResponseCount = 0

    val assistantName = "Clawly"

    private val _userIdentity = MutableStateFlow<UserIdentity>(UserIdentity.NotAuthenticated)
    val userIdentity: StateFlow<UserIdentity> = _userIdentity.asStateFlow()

    private val _web3Credits = MutableStateFlow(0)
    val web3Credits: StateFlow<Int> = _web3Credits.asStateFlow()

    private val _web2Credits = MutableStateFlow(0L)
    val web2Credits: StateFlow<Long> = _web2Credits.asStateFlow()

    private companion object {
        const val SIGN_STATUS_POLL_ATTEMPTS = 20
        const val SIGN_STATUS_POLL_DELAY_MS = 1500L
    }

    init {
        loadPersistedMessages()
        loadThinkingLevel()
        setupSubscriptions()
        if (BuildConfig.IS_WEB3) {
            observeWeb3Credits()
        }
        if (BuildConfig.IS_WEB2) {
            observeWeb2Credits()
        }
        checkAndSendPendingMessage()
        checkImmediateRateDialog()
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

    private fun checkImmediateRateDialog() {
        viewModelScope.launch {
            if (RemoteConfigFlags.isShowRateDialogImmediately() && !preferences.isInAppReviewPrompted()) {
                preferences.setInAppReviewPrompted()
                _events.emit(ChatEvent.RequestInAppReview)
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

    private fun observeWeb2Credits() {
        viewModelScope.launch {
            web2CreditsUseCase.creditsFlow.collect { credits ->
                _web2Credits.value = credits
                Log.d(TAG, "Web2 credits updated: $credits (${Web2CreditsUseCase.formatCreditsShort(credits)})")
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
                    is ConnectionStatus.Paused -> false
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
                    is ConnectionStatus.Offline -> {
                        Log.d(TAG, "Connection status: Offline")
                        pendingResponseCount = 0
                        _uiState.update { it.copy(isAssistantTyping = false, streamingContent = "") }
                    }
                    is ConnectionStatus.Paused -> Log.d(TAG, "Connection status: Paused")
                    is ConnectionStatus.Error -> {
                        Log.e(TAG, "Connection status: Error - ${status.message}")
                        pendingResponseCount = 0
                        _uiState.update { it.copy(isAssistantTyping = false, streamingContent = "") }
                    }
                }

                // Fetch history when first connecting
                if (status == ConnectionStatus.Online && wasOffline && !hasLoadedHistory) {
                    fetchChatHistory()
                }
                if (status == ConnectionStatus.Online) {
                    syncPendingSignRequests()
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
                        if (identity.isWeb3) startSignRequestsSocket()
                    }
                    is UserIdentity.NotAuthenticated -> {
                        Log.d(TAG, "User not authenticated")
                        stopSignRequestsSocket()
                        if (BuildConfig.IS_WEB3) {
                            Log.d(TAG, "Web3 wallet disconnected, stopping gateway")
                            gatewayConnectionUseCase.disconnect()
                        }
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
            // Also need to check connection for web3 — trigger reconnect instead of navigating away
            if (!state.isConnected) {
                Log.d(TAG, "Validation failed: web3 not connected, triggering reconnect")
                return SendValidationResult.GatewayReconnecting
            }
            // Web3 with credits and connected - allow
            return SendValidationResult.Allowed
        }

        // Web2 flow below
        val hasWeb2Credits = _web2Credits.value > 0

        // Premium users OR users with credits
        if (state.hasPremiumAccess || hasWeb2Credits) {
            // Still provisioning -> show config prompt (to show status)
            if (state.isProvisioning) {
                Log.d(TAG, "Validation failed: still provisioning")
                return SendValidationResult.ShowConfigPrompt
            }

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
            Log.d(TAG, "Validation passed (premium=${state.hasPremiumAccess}, web2Credits=$hasWeb2Credits)")
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

        // Deduct credit for web2 builds (if user has credits, not just subscription)
        if (BuildConfig.IS_WEB2 && _web2Credits.value > 0) {
            viewModelScope.launch {
                web2CreditsUseCase.deductCredit()
                Log.d(TAG, "Deducted credit for web2 message")
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

            // Prompt in-app review after 10 user messages (once)
            val userMsgCount = _uiState.value.userMessageCount
            if (userMsgCount == 10 && !preferences.isInAppReviewPrompted()) {
                preferences.setInAppReviewPrompted()
                _events.emit(ChatEvent.RequestInAppReview)
            }
        }

        // Send via gateway
        pendingResponseCount++
        Log.d(TAG, "pendingResponseCount++ = $pendingResponseCount")

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
                pendingResponseCount = (pendingResponseCount - 1).coerceAtLeast(0)
                Log.d(TAG, "Send failed, pendingResponseCount-- = $pendingResponseCount")
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
        Log.d(TAG, "handleIncomingMessage: type=${message.type}, runId=${message.runId}")

        // Skip silent messages (e.g. integrations fetch)
        val runId = message.runId
        if (runId != null && runId in gatewayService.silentRunIds) {
            Log.d(TAG, "Skipping silent runId: $runId")
            gatewayService.silentRunIds.remove(runId)
            return
        }

        // Each incoming message = one response received
        pendingResponseCount = (pendingResponseCount - 1).coerceAtLeast(0)
        Log.d(TAG, "pendingResponseCount-- = $pendingResponseCount")

        val stillTyping = pendingResponseCount > 0
        _uiState.update {
            it.copy(isAssistantTyping = stillTyping, streamingContent = "")
        }

        val content = message.payload?.content

        if (content.isNullOrEmpty()) {
            Log.d(TAG, "Received non-content message: ${message.type}")
            return
        }

        // Check if this is a pure sign request JSON — don't show as assistant text
        val signRequest = extractSignRequestFromJson(content) ?: extractSignRequestFromAssistantText(content)
        val isSignRequestOnly = extractSignRequestFromJson(content) != null

        maybeShowPairingResolvingPrompt(content)

        analytics.trackChatMessageReceived(type = message.type)

        // Only add assistant message if it's NOT a pure sign-request JSON
        if (!isSignRequestOnly) {
            // Check for API key request tag
            val apiKeyReq = parseApiKeyTag(content)
            if (apiKeyReq != null) {
                val cleaned = cleanApiKeyTagContent(content)
                val newMessages = _uiState.value.messages.toMutableList()
                if (cleaned.isNotEmpty()) {
                    newMessages.add(ChatMessage.assistantMessage(cleaned))
                }
                newMessages.add(ChatMessage(
                    content = "__api_key_request__:${apiKeyReq.keyName}:${apiKeyReq.skillKey}",
                    isUser = false
                ))
                _uiState.update { it.copy(messages = newMessages, pendingApiKeyRequest = apiKeyReq) }
                chatRepository.saveMessages(newMessages)
            } else {
                val assistantMessage = ChatMessage.assistantMessage(content)
                val updatedMessages = _uiState.value.messages + assistantMessage

                _uiState.update { it.copy(messages = updatedMessages) }
                chatRepository.saveMessages(updatedMessages)

                // Emit speak event for TTS
                _events.emit(ChatEvent.SpeakText(content))
            }
        }

        // Show sign request bubble AFTER assistant text
        if (signRequest != null && lastPresentedSignRequestId != signRequest.requestId) {
            lastPresentedSignRequestId = signRequest.requestId
            pendingSignData[signRequest.requestId] = signRequest
            Log.d(TAG, "Detected sign request: ${signRequest.requestId}")
            upsertSignRequestMessage(
                SignRequestBubbleState.ReadyToSign(
                    requestId = signRequest.requestId,
                    fromWallet = signRequest.fromWallet ?: signRequest.walletAddress,
                    toWallet = signRequest.toWallet,
                    txHash = signRequest.txHash
                )
            )
        }

        _events.emit(ChatEvent.ScrollToBottom)
    }

    private fun upsertSignRequestMessage(state: SignRequestBubbleState) {
        val messageId = "sign-request-${state.requestId}"
        _uiState.update { current ->
            val existingIndex = current.messages.indexOfFirst { it.id == messageId }
            val newMessage = ChatMessage.signRequestMessage(state)
            val updatedMessages = if (existingIndex >= 0) {
                current.messages.toMutableList().apply { set(existingIndex, newMessage) }
            } else {
                current.messages + newMessage
            }
            current.copy(messages = updatedMessages)
        }
        viewModelScope.launch {
            chatRepository.saveMessages(_uiState.value.messages)
            _events.emit(ChatEvent.ScrollToBottom)
        }
    }

    private fun findActiveSignRequest(): Pair<String, SolanaSignRequestUi>? {
        val activeMessage = _uiState.value.messages
            .lastOrNull { it.signRequestState is SignRequestBubbleState.ReadyToSign }
            ?: return null
        val requestId = activeMessage.signRequestState!!.requestId
        val signData = pendingSignData[requestId] ?: return null
        return requestId to signData
    }

    // Sign request detection is now handled inline in handleIncomingMessage()

    private fun startSignRequestsSocket() {
        if (!BuildConfig.IS_WEB3) return

        viewModelScope.launch {
            val token = solanaAuthRepository.getValidToken()
            if (token.isNullOrBlank()) {
                Log.w(TAG, "sign-ws: missing Solana JWT, skip connect")
                return@launch
            }
            if (signWs != null && signWsToken == token) return@launch

            stopSignRequestsSocket()
            signWsToken = token

            val wsUrl = ControlPlaneService.BASE_URL
                .replace("http://", "ws://")
                .replace("https://", "wss://")
                .trimEnd('/') + "/ws/sign-requests"

            Log.d(TAG, "sign-ws: connecting to $wsUrl")
            val request = Request.Builder()
                .url(wsUrl)
                .addHeader("Authorization", "Bearer $token")
                .build()

            signWs = signWsClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "sign-ws: connected")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "sign-ws: message ${text.take(300)}")
                    viewModelScope.launch {
                        handleSignSocketFrame(text)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "sign-ws: failure ${t.message}")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "sign-ws: closed code=$code reason=$reason")
                }
            })
        }
    }

    private fun stopSignRequestsSocket() {
        signWs?.close(1000, "chat-viewmodel-stop")
        signWs = null
        signWsToken = null
    }

    private suspend fun handleSignSocketFrame(text: String) {
        val obj = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "authenticated" -> {
                val wallet = obj["walletAddress"]?.jsonPrimitive?.contentOrNull
                Log.d(TAG, "sign-ws: authenticated wallet=${wallet ?: "?"}")
            }
            "sign.request", "sign.status" -> {
                val requestObj = obj["request"]?.let { it as? JsonObject } ?: return
                handleSignSocketRequest(requestObj)
            }
            "sign.pending" -> {
                val arr = obj["requests"]?.let { it as? JsonArray } ?: return
                val first = arr.firstOrNull()?.let { it as? JsonObject } ?: return
                handleSignSocketRequest(first)
            }
        }
    }

    private suspend fun handleSignSocketRequest(req: JsonObject) {
        val signRequest = parseSignRequestObject(req) ?: return
        if (signRequest.status != null && signRequest.status != "pending") {
            // Status update for existing request
            val messageId = "sign-request-${signRequest.requestId}"
            val exists = _uiState.value.messages.any { it.id == messageId }
            if (exists) {
                _events.emit(ChatEvent.ShowToast("Sign request status: ${signRequest.status}"))
            }
            return
        }

        if (lastPresentedSignRequestId == signRequest.requestId) return

        // Wait for assistant to finish typing before showing sign request
        while (_uiState.value.isAssistantTyping) {
            delay(200)
        }

        lastPresentedSignRequestId = signRequest.requestId
        pendingSignData[signRequest.requestId] = signRequest
        upsertSignRequestMessage(
            SignRequestBubbleState.ReadyToSign(
                requestId = signRequest.requestId,
                fromWallet = signRequest.fromWallet ?: signRequest.walletAddress,
                toWallet = signRequest.toWallet,
                txHash = signRequest.txHash
            )
        )
    }

    private fun syncPendingSignRequests() {
        viewModelScope.launch {
            val userId = (_userIdentity.value as? UserIdentity.Authenticated)?.userId ?: return@launch
            controlPlaneService.getPendingSignRequests(userId).onSuccess { requests ->
                val first = requests.firstOrNull() ?: return@onSuccess
                val signRequest = SolanaSignRequestUi(
                    requestId = first.requestId,
                    unsignedTxBase64 = first.unsignedTxBase64,
                    walletAddress = first.walletAddress,
                    txHash = first.txHash,
                    status = first.status,
                    expiresAt = first.expiresAt
                )
                if (lastPresentedSignRequestId == signRequest.requestId) return@onSuccess
                lastPresentedSignRequestId = signRequest.requestId
                pendingSignData[signRequest.requestId] = signRequest
                upsertSignRequestMessage(
                    SignRequestBubbleState.ReadyToSign(
                        requestId = signRequest.requestId,
                        fromWallet = signRequest.walletAddress,
                        toWallet = null,
                        txHash = signRequest.txHash
                    )
                )
            }
        }
    }

    fun rejectSignRequest() {
        val (requestId, signData) = findActiveSignRequest() ?: return
        val userId = (_userIdentity.value as? UserIdentity.Authenticated)?.userId

        viewModelScope.launch {
            controlPlaneService.rejectSignRequest(
                requestId = requestId,
                error = "user_rejected_on_android",
                userId = userId
            ).onSuccess {
                upsertSignRequestMessage(
                    SignRequestBubbleState.Rejected(
                        requestId = requestId,
                        fromWallet = signData.fromWallet ?: signData.walletAddress,
                        toWallet = signData.toWallet,
                        txHash = signData.txHash,
                        reason = "Transaction rejected"
                    )
                )
                pendingSignData.remove(requestId)
            }.onFailure { error ->
                Log.e(TAG, "Reject sign request failed", error)
                _events.emit(ChatEvent.ShowError(error.message ?: "Failed to reject sign request"))
            }
        }
    }

    fun approveSignRequest() {
        val (requestId, signData) = findActiveSignRequest() ?: return
        val userId = (_userIdentity.value as? UserIdentity.Authenticated)?.userId

        upsertSignRequestMessage(
            SignRequestBubbleState.Signing(
                requestId = requestId,
                fromWallet = signData.fromWallet ?: signData.walletAddress,
                toWallet = signData.toWallet,
                txHash = signData.txHash
            )
        )

        viewModelScope.launch {
            try {
                val unsignedTxBytes = Base64.decode(signData.unsignedTxBase64, Base64.DEFAULT)
                val signedTxBytes = connectWalletUseCase.signTransaction(unsignedTxBytes)
                if (signedTxBytes == null) {
                    upsertSignRequestMessage(
                        SignRequestBubbleState.Rejected(
                            requestId = requestId,
                            fromWallet = signData.fromWallet ?: signData.walletAddress,
                            toWallet = signData.toWallet,
                            txHash = signData.txHash,
                            reason = "Wallet signature cancelled"
                        )
                    )
                    pendingSignData.remove(requestId)
                    return@launch
                }

                val signedTxBase64 = Base64.encodeToString(signedTxBytes, Base64.NO_WRAP)
                controlPlaneService.approveSignRequest(
                    requestId = requestId,
                    signedTxBase64 = signedTxBase64,
                    userId = userId
                ).onSuccess {
//                    _events.emit(ChatEvent.ShowToast("Transaction sent to backend"))
                    pollForSubmittedSignature(requestId = requestId, userId = userId)
                }.onFailure { error ->
                    Log.e(TAG, "Approve sign request failed", error)
                    upsertSignRequestMessage(
                        SignRequestBubbleState.Rejected(
                            requestId = requestId,
                            fromWallet = signData.fromWallet ?: signData.walletAddress,
                            toWallet = signData.toWallet,
                            txHash = signData.txHash,
                            reason = error.message ?: "Failed to approve"
                        )
                    )
                    pendingSignData.remove(requestId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Approve sign request flow failed", e)
                upsertSignRequestMessage(
                    SignRequestBubbleState.Rejected(
                        requestId = requestId,
                        fromWallet = signData.fromWallet ?: signData.walletAddress,
                        toWallet = signData.toWallet,
                        txHash = signData.txHash,
                        reason = e.message ?: "Signing failed"
                    )
                )
                pendingSignData.remove(requestId)
            }
        }
    }

    private fun pollForSubmittedSignature(requestId: String, userId: String?) {
        val signData = pendingSignData[requestId]
        viewModelScope.launch {
            repeat(SIGN_STATUS_POLL_ATTEMPTS) { idx ->
                controlPlaneService.getSignRequestStatus(requestId, userId)
                    .onSuccess { status ->
                        val state = status.status.lowercase()
                        val sig = status.solanaSignature

                        if ((state == "submitted" || state == "confirmed") && !sig.isNullOrBlank()) {
                            upsertSignRequestMessage(
                                SignRequestBubbleState.Success(
                                    requestId = requestId,
                                    fromWallet = signData?.fromWallet ?: signData?.walletAddress,
                                    toWallet = signData?.toWallet,
                                    txHash = signData?.txHash,
                                    signature = sig,
                                    status = state
                                )
                            )
                            pendingSignData.remove(requestId)
                            return@launch
                        }

                        if (state == "failed" || state == "rejected" || state == "expired") {
                            val reason = status.error ?: state
                            upsertSignRequestMessage(
                                SignRequestBubbleState.Rejected(
                                    requestId = requestId,
                                    fromWallet = signData?.fromWallet ?: signData?.walletAddress,
                                    toWallet = signData?.toWallet,
                                    txHash = signData?.txHash,
                                    reason = "Sign request $state: $reason"
                                )
                            )
                            pendingSignData.remove(requestId)
                            return@launch
                        }
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Sign status poll failed [${idx + 1}/$SIGN_STATUS_POLL_ATTEMPTS]", error)
                    }

                delay(SIGN_STATUS_POLL_DELAY_MS)
            }

            _events.emit(ChatEvent.ShowToast("Signed. Waiting for network confirmation..."))
        }
    }

    private fun extractSignRequestFromJson(content: String): SolanaSignRequestUi? {
        return runCatching {
            val root = Json.parseToJsonElement(content).jsonObject
            val req = root["request"]?.jsonObject ?: root
            parseSignRequestObject(req)
        }.getOrNull()
    }

    private fun parseSignRequestObject(req: JsonObject): SolanaSignRequestUi? {
        val requestId = req["requestId"]?.jsonPrimitive?.contentOrNull ?: return null
        val unsignedTxBase64 = req["unsignedTxBase64"]?.jsonPrimitive?.contentOrNull ?: return null

        return SolanaSignRequestUi(
            requestId = requestId,
            unsignedTxBase64 = unsignedTxBase64,
            walletAddress = req["walletAddress"]?.jsonPrimitive?.contentOrNull,
            txHash = req["txHash"]?.jsonPrimitive?.contentOrNull,
            status = req["status"]?.jsonPrimitive?.contentOrNull,
            expiresAt = req["expiresAt"]?.jsonPrimitive?.longOrNull
        )
    }

    private fun extractSignRequestFromAssistantText(text: String): SolanaSignRequestUi? {
        val requestId = Regex("requestId:\\s*`([a-f0-9\\-]{36})`", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null

        val unsignedTx = Regex("unsignedTxBase64:\\s*`([A-Za-z0-9+/=]+)`", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null

        val fromWallet = Regex("from:\\s*`([^`]+)`", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
        val toWallet = Regex("to:\\s*`([^`]+)`", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
        val txHash = Regex("txHash:\\s*`([a-f0-9]+)`", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
        val status = Regex("status:\\s*`([^`]+)`", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)

        return SolanaSignRequestUi(
            requestId = requestId,
            unsignedTxBase64 = unsignedTx,
            fromWallet = fromWallet,
            toWallet = toWallet,
            txHash = txHash,
            status = status
        )
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
                    pendingResponseCount = 0
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
                    pendingResponseCount = 0
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
    // MARK: - API Key handling (same as setup wizard)

    private fun parseApiKeyTag(content: String): ai.clawly.app.presentation.setupwizard.ApiKeyRequest? {
        val regex = Regex("""<enter_api_key\s+name="([^"]+)"\s+skill="([^"]+)"\s*/>""")
        val match = regex.find(content) ?: return null
        return ai.clawly.app.presentation.setupwizard.ApiKeyRequest(
            keyName = match.groupValues[1],
            skillKey = match.groupValues[2]
        )
    }

    private fun cleanApiKeyTagContent(content: String): String {
        return content.replace(
            Regex("""<enter_api_key\s+name="[^"]+"\s+skill="[^"]+"\s*/>"""),
            ""
        ).trim()
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

        val confirmMsg = ChatMessage.assistantMessage("API key ${req.keyName} saved.")
        _uiState.update { it.copy(messages = it.messages + confirmMsg) }

        viewModelScope.launch {
            chatRepository.saveMessages(_uiState.value.messages)
            gatewayService.sendMessage(message = configCommand).onFailure { error ->
                Log.e(TAG, "Failed to send config command: ${error.message}")
            }
        }
    }

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
            pendingResponseCount++
            _uiState.update { it.copy(isAssistantTyping = true, streamingContent = "") }

            viewModelScope.launch {
                gatewayService.sendMessage(
                    message = message,
                    thinkingLevel = _uiState.value.thinkingLevel
                ).onFailure { error ->
                    pendingResponseCount = (pendingResponseCount - 1).coerceAtLeast(0)
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

    override fun onCleared() {
        stopSignRequestsSocket()
        signWsClient.dispatcher.executorService.shutdown()
        signWsClient.connectionPool.evictAll()
        super.onCleared()
    }
}
