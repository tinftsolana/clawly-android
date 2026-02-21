package ai.clawly.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Response from skills.status gateway method
 */
@Serializable
data class SkillsStatusResponse(
    val skills: List<ServerSkill> = emptyList()
)

/**
 * Skill data from the server
 */
@Serializable
data class ServerSkill(
    val name: String,
    val description: String? = null,
    val skillKey: String,
    val primaryEnv: String? = null,
    val emoji: String? = null,
    val disabled: Boolean = false,
    val eligible: Boolean = true,
    val missing: SkillRequirements? = null
)

/**
 * Missing requirements that prevent a skill from being eligible
 */
@Serializable
data class SkillRequirements(
    val bins: List<String> = emptyList(),
    val anyBins: List<String> = emptyList(),
    val env: List<String> = emptyList(),
    val config: List<String> = emptyList(),
    val os: List<String> = emptyList()
) {
    val hasMissingBins: Boolean
        get() = bins.isNotEmpty() || anyBins.isNotEmpty()

    val hasConfigurableEnv: Boolean
        get() = env.isNotEmpty()
}

/**
 * Response from config.get gateway method
 */
@Serializable
data class ConfigResponse(
    val hash: String,
    val config: JsonObject? = null
)
