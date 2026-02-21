package ai.clawly.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Status of a managed hosting instance
 */
@Serializable
enum class ManagedInstanceStatus {
    @SerialName("queued")
    Queued,

    @SerialName("provisioning")
    Provisioning,

    @SerialName("installing")
    Installing,

    @SerialName("ready")
    Ready,

    @SerialName("failed")
    Failed,

    @SerialName("suspended")
    Suspended;

    val displayName: String
        get() = when (this) {
            Queued -> "Queued"
            Provisioning -> "Setting up server..."
            Installing -> "Installing software..."
            Ready -> "Ready"
            Failed -> "Failed"
            Suspended -> "Suspended"
        }

    val isInProgress: Boolean
        get() = when (this) {
            Queued, Provisioning, Installing -> true
            Ready, Failed, Suspended -> false
        }

    companion object {
        fun fromString(value: String?): ManagedInstanceStatus? {
            return entries.find { it.name.equals(value, ignoreCase = true) }
        }
    }
}
