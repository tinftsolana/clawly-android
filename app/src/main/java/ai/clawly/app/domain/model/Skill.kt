package ai.clawly.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Skill model - represents a server-side skill that can be enabled/disabled
 */
@Serializable
data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val isEnabled: Boolean,
    val category: SkillCategory = SkillCategory.Server,
    val markdownContent: String = "",
    val isServerSkill: Boolean = true,
    val skillKey: String? = null,
    val missingRequirements: SkillMissingRequirements? = null,
    val isEligible: Boolean = true,
    val primaryEnv: String? = null
)

@Serializable
enum class SkillCategory(val displayName: String) {
    Server("Server")
}

/**
 * Missing requirements that prevent a skill from working
 */
@Serializable
data class SkillMissingRequirements(
    val bins: List<String> = emptyList(),        // Missing binaries/executables
    val anyBins: List<String> = emptyList(),     // Missing any of these binaries
    val env: List<String> = emptyList(),         // Missing environment variables (API keys, etc)
    val config: List<String> = emptyList(),      // Missing config values
    val os: List<String> = emptyList()           // Missing OS requirements
) {
    val hasMissing: Boolean
        get() = bins.isNotEmpty() || anyBins.isNotEmpty() || env.isNotEmpty() || config.isNotEmpty() || os.isNotEmpty()

    val hasConfigurableEnv: Boolean
        get() = env.isNotEmpty()

    val hasMissingBins: Boolean
        get() = bins.isNotEmpty() || anyBins.isNotEmpty()

    val summary: String
        get() {
            val parts = mutableListOf<String>()
            if (bins.isNotEmpty()) {
                parts.add("Install: ${bins.joinToString(", ")}")
            }
            if (env.isNotEmpty()) {
                parts.add("Set: ${env.joinToString(", ")}")
            }
            if (config.isNotEmpty()) {
                parts.add("Configure: ${config.joinToString(", ")}")
            }
            return parts.joinToString(" • ")
        }
}

/**
 * Skill payload sent to gateway with chat messages
 */
@Serializable
data class SkillPayload(
    val id: String,
    val name: String,
    val content: String  // The markdown instructions
)

/**
 * Filter options for skills list
 */
enum class SkillFilter(val displayName: String) {
    All("All"),
    Active("Active"),
    Inactive("Inactive")
}
