package ai.clawly.app.navigation

import kotlinx.serialization.Serializable

/**
 * Navigation destinations for the Clawly app
 */
object ClawlyDestinations {
    const val ONBOARDING = "onboarding"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val PAYWALL = "paywall"
    const val PROVIDER_SETUP = "provider_setup"
    const val GATEWAY_CONFIG = "gateway_config"
    const val AUTH_PROVIDER = "auth_provider"
    const val INSTANCE_SETUP = "instance_setup"
    const val SKILLS = "skills"
    const val API_KEYS = "api_keys"
}

/**
 * Type-safe navigation routes
 */
@Serializable
object OnboardingRoute

@Serializable
object ChatRoute

@Serializable
object SettingsRoute

@Serializable
object PaywallRoute

@Serializable
object ProviderSetupRoute

@Serializable
object GatewayConfigRoute

@Serializable
object AuthProviderRoute

@Serializable
object InstanceSetupRoute

@Serializable
object SkillsRoute

@Serializable
object ApiKeysRoute

@Serializable
object LoginRoute

@Serializable
object Web3PaywallRoute

@Serializable
data class SetupWizardRoute(val initialPrompt: String? = null)
