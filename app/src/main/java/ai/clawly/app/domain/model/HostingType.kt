package ai.clawly.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Type of hosting for the AI gateway
 */
@Serializable
enum class HostingType {
    @SerialName("managed")
    Managed,

    @SerialName("self_hosted")
    SelfHosted;

    val displayName: String
        get() = when (this) {
            Managed -> "Managed Hosting"
            SelfHosted -> "Self-Hosted"
        }

    val description: String
        get() = when (this) {
            Managed -> "We'll set up a server for you"
            SelfHosted -> "Connect to your own gateway"
        }

    val iconName: String
        get() = when (this) {
            Managed -> "cloud.fill"
            SelfHosted -> "server.rack"
        }

    companion object {
        fun fromString(value: String?): HostingType? {
            return when (value?.lowercase()) {
                "managed" -> Managed
                "self_hosted", "selfhosted" -> SelfHosted
                else -> null
            }
        }
    }
}
