package ai.clawly.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Configuration for the authentication provider
 * Supports both managed hosting (control plane provisioned) and self-hosted (user's own gateway)
 */
@Serializable
data class AuthProviderConfig(
    val hostingType: HostingType? = null,

    // For self-hosted
    val wssUrl: String? = null,
    val wssToken: String? = null,

    // For managed hosting
    val managedInstance: ManagedInstanceInfo? = null
) {
    /**
     * Check if the provider is fully configured and ready to use
     */
    val isConfigured: Boolean
        get() = when (hostingType) {
            HostingType.SelfHosted -> !wssUrl.isNullOrEmpty()
            HostingType.Managed -> managedInstance?.isReady == true
            null -> false
        }

    /**
     * Check if managed instance is still provisioning
     */
    val isProvisioning: Boolean
        get() = hostingType == HostingType.Managed && managedInstance?.status?.isInProgress == true

    /**
     * Get the effective gateway URL (works for both hosting types)
     */
    val effectiveGatewayUrl: String?
        get() = when (hostingType) {
            HostingType.SelfHosted -> wssUrl
            HostingType.Managed -> managedInstance?.gatewayUrl
            null -> null
        }

    /**
     * Get the effective gateway token (works for both hosting types)
     */
    val effectiveGatewayToken: String?
        get() = when (hostingType) {
            HostingType.SelfHosted -> wssToken
            HostingType.Managed -> managedInstance?.gatewayToken
            null -> null
        }

    companion object {
        fun empty() = AuthProviderConfig()

        fun selfHosted(url: String, token: String? = null) = AuthProviderConfig(
            hostingType = HostingType.SelfHosted,
            wssUrl = url,
            wssToken = token
        )

        fun managed(instance: ManagedInstanceInfo) = AuthProviderConfig(
            hostingType = HostingType.Managed,
            managedInstance = instance
        )
    }
}
