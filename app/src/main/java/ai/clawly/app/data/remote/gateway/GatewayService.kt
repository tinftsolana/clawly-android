package ai.clawly.app.data.remote.gateway

import ai.clawly.app.domain.model.ConfigResponse
import ai.clawly.app.domain.model.ConnectionStatus
import ai.clawly.app.domain.model.GatewayMessage
import ai.clawly.app.domain.model.SkillsStatusResponse
import ai.clawly.app.domain.model.StreamingState
import ai.clawly.app.domain.model.ThinkingLevel
import ai.clawly.app.domain.repository.AttachmentPayload
import ai.clawly.app.domain.repository.ChatHistoryMessage
import ai.clawly.app.domain.repository.SkillPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for gateway WebSocket operations
 */
interface GatewayService {
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
     */
    suspend fun sendMessage(
        message: String,
        thinkingLevel: ThinkingLevel = ThinkingLevel.Medium,
        skills: List<SkillPayload> = emptyList(),
        attachments: List<AttachmentPayload> = emptyList()
    ): Result<Unit>

    /** Fetch chat history */
    suspend fun fetchHistory(sessionKey: String? = null): Result<List<ChatHistoryMessage>>

    /** Abort current response */
    suspend fun abortChat(sessionKey: String? = null): Result<Unit>

    /** Fetch skills status from gateway */
    suspend fun fetchSkillsStatus(): Result<SkillsStatusResponse>

    /** Update skill enabled/disabled state */
    suspend fun updateSkill(skillKey: String, enabled: Boolean): Result<Unit>

    /** Get current config (for hash) */
    suspend fun getConfig(): Result<ConfigResponse>

    /** Configure skill environment variable */
    suspend fun configureSkillEnv(skillKey: String, envName: String, value: String): Result<Unit>
}
