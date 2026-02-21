package ai.clawly.app.domain.repository

import ai.clawly.app.domain.model.ConnectionStatus
import ai.clawly.app.domain.model.GatewayMessage
import ai.clawly.app.domain.model.StreamingState
import ai.clawly.app.domain.model.ThinkingLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Chat history message from the gateway
 */
data class ChatHistoryMessage(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long?
) {
    val isUser: Boolean get() = role.equals("user", ignoreCase = true)
    val isAssistant: Boolean get() = role.equals("assistant", ignoreCase = true)
}

/**
 * Repository interface for gateway operations
 */
interface GatewayRepository {
    /** Current connection status */
    val connectionStatus: StateFlow<ConnectionStatus>

    /** Incoming messages from the gateway */
    val incomingMessages: Flow<GatewayMessage>

    /** Current streaming state */
    val streamingState: StateFlow<StreamingState>

    /** Connect to the gateway */
    suspend fun connect()

    /** Disconnect from the gateway */
    suspend fun disconnect()

    /** Reconnect to the gateway */
    suspend fun reconnect()

    /**
     * Send a chat message
     * @param message The message content
     * @param thinkingLevel The thinking level for the response
     * @param skills Optional skill payloads
     * @param attachments Optional file attachments
     */
    suspend fun sendMessage(
        message: String,
        thinkingLevel: ThinkingLevel = ThinkingLevel.Medium,
        skills: List<SkillPayload> = emptyList(),
        attachments: List<AttachmentPayload> = emptyList()
    ): Result<Unit>

    /** Fetch chat history for the current session */
    suspend fun fetchHistory(sessionKey: String? = null): Result<List<ChatHistoryMessage>>

    /** Abort the current chat response */
    suspend fun abortChat(sessionKey: String? = null): Result<Unit>
}

/**
 * Skill payload for sending with messages
 */
data class SkillPayload(
    val id: String,
    val name: String,
    val content: String
)

/**
 * Attachment payload for sending files/images
 */
data class AttachmentPayload(
    val type: String = "file",
    val mimeType: String,
    val fileName: String,
    val content: String // Base64 encoded
) {
    companion object {
        fun fromImageData(data: ByteArray, fileName: String, mimeType: String): AttachmentPayload {
            return AttachmentPayload(
                type = "file",
                mimeType = mimeType,
                fileName = fileName,
                content = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
            )
        }
    }
}
