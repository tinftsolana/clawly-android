package ai.clawly.app.presentation.chat

import ai.clawly.app.domain.model.*

/**
 * UI state for the chat screen
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.Offline,
    val isAssistantTyping: Boolean = false,
    val streamingContent: String = "",
    val thinkingLevel: ThinkingLevel = ThinkingLevel.Medium,
    val isAborting: Boolean = false,
    val hasPremiumAccess: Boolean = false,
    val currentAuthProvider: AuthProviderConfig = AuthProviderConfig.empty(),
    val isResolvingGatewayAccess: Boolean = false,
    val error: String? = null,
    val showError: Boolean = false,
    val showConfigPrompt: Boolean = false,
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val pendingApiKeyRequest: ai.clawly.app.presentation.setupwizard.ApiKeyRequest? = null,
    val isRecording: Boolean = false,
    val recordingRmsLevel: Float = 0f,
    val partialRecognitionText: String = ""
) {
    val isConnected: Boolean get() = connectionStatus == ConnectionStatus.Online
    val hasAuthProvider: Boolean get() = currentAuthProvider.isConfigured
    val isProvisioning: Boolean get() = currentAuthProvider.isProvisioning
    val userMessageCount: Int get() = messages.count { it.isUser && !it.isError }
}

/**
 * Pending attachment for sending with message
 */
data class PendingAttachment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val data: ByteArray,
    val fileName: String,
    val mimeType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PendingAttachment
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
