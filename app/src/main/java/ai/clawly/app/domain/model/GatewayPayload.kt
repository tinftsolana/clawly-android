package ai.clawly.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload structure for gateway messages
 */
@Serializable
data class GatewayPayload(
    val content: String? = null,
    val token: String? = null,
    @SerialName("session_key")
    val sessionKey: String? = null,
    val metadata: Map<String, String>? = null
)
