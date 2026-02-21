package ai.clawly.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Information about a managed hosting instance
 */
@Serializable
data class ManagedInstanceInfo(
    val tenantId: String,
    val status: ManagedInstanceStatus,
    val lastError: String? = null,
    val gatewayUrl: String? = null,
    val gatewayToken: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
) {
    val isReady: Boolean
        get() = status == ManagedInstanceStatus.Ready && !gatewayUrl.isNullOrEmpty()

    /** Status is Ready but might be missing gatewayUrl */
    val hasReadyStatus: Boolean
        get() = status == ManagedInstanceStatus.Ready

    companion object {
        fun empty() = ManagedInstanceInfo(
            tenantId = "",
            status = ManagedInstanceStatus.Queued
        )
    }
}
