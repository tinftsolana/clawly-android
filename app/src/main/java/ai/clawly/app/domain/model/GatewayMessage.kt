package ai.clawly.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a message received from or sent to the gateway
 */
@Serializable
data class GatewayMessage(
    val type: String,
    val payload: GatewayPayload? = null,
    val runId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
