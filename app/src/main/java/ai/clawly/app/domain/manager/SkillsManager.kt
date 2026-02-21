package ai.clawly.app.domain.manager

import ai.clawly.app.domain.model.Skill
import ai.clawly.app.domain.repository.SkillPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared manager for enabled skills state.
 * Used by SkillsViewModel to update enabled skills and ChatViewModel to access them.
 */
@Singleton
class SkillsManager @Inject constructor() {

    private val _enabledSkills = MutableStateFlow<List<SkillPayload>>(emptyList())
    val enabledSkills: StateFlow<List<SkillPayload>> = _enabledSkills.asStateFlow()

    /**
     * Update the list of enabled skills (called by SkillsViewModel)
     */
    fun updateEnabledSkills(skills: List<SkillPayload>) {
        _enabledSkills.value = skills
    }

    /**
     * Update enabled skills from Skill domain models
     */
    fun updateFromSkills(skills: List<Skill>) {
        val payloads = skills
            .filter { it.isEnabled && it.isEligible }
            .mapNotNull { skill ->
                skill.skillKey?.let { key ->
                    SkillPayload(
                        id = key,
                        name = skill.name,
                        content = skill.markdownContent
                    )
                }
            }
        _enabledSkills.value = payloads
    }

    /**
     * Get list of enabled skill payloads for sending with chat messages
     */
    fun getEnabledSkillPayloads(): List<SkillPayload> {
        return _enabledSkills.value
    }

    /**
     * Clear all enabled skills
     */
    fun clear() {
        _enabledSkills.value = emptyList()
    }
}
