package ai.clawly.app.presentation.skills

import ai.clawly.app.data.remote.gateway.GatewayService
import ai.clawly.app.domain.manager.SkillsManager
import ai.clawly.app.domain.model.ConnectionStatus
import ai.clawly.app.domain.model.ServerSkill
import ai.clawly.app.domain.model.Skill
import ai.clawly.app.domain.model.SkillFilter
import ai.clawly.app.domain.model.SkillMissingRequirements
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SkillsViewModel"

data class SkillsUiState(
    val skills: List<Skill> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchText: String = "",
    val selectedFilter: SkillFilter = SkillFilter.All,
    val isConfiguringSkill: Boolean = false,
    val configurationError: String? = null,
    val showCreateSkillSheet: Boolean = false,
    val skillToConfig: Skill? = null
)

@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val gatewayService: GatewayService,
    private val skillsManager: SkillsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillsUiState())
    val uiState: StateFlow<SkillsUiState> = _uiState.asStateFlow()

    init {
        // Observe connection status to fetch skills when connected
        viewModelScope.launch {
            gatewayService.connectionStatus.collect { status ->
                if (status == ConnectionStatus.Online && _uiState.value.skills.isEmpty()) {
                    fetchSkills()
                }
            }
        }
    }

    /**
     * Skills that can be shown (excludes those needing server-side installation)
     */
    val availableSkills: List<Skill>
        get() = _uiState.value.skills.filter { skill ->
            // Hide skills that require server-side installations (missing bins)
            skill.missingRequirements?.hasMissingBins != true
        }

    /**
     * Filtered skills based on current search and filter
     */
    val filteredSkills: List<Skill>
        get() {
            var result = availableSkills

            // Apply filter chip
            result = when (_uiState.value.selectedFilter) {
                SkillFilter.All -> result
                SkillFilter.Active -> result.filter { it.isEnabled }
                SkillFilter.Inactive -> result.filter { !it.isEnabled }
            }

            // Apply search filter
            val searchText = _uiState.value.searchText
            if (searchText.isNotEmpty()) {
                result = result.filter { skill ->
                    skill.name.contains(searchText, ignoreCase = true) ||
                            skill.description.contains(searchText, ignoreCase = true)
                }
            }

            return result
        }

    val enabledCount: Int
        get() = availableSkills.count { it.isEnabled }

    val totalCount: Int
        get() = availableSkills.size

    fun countForFilter(filter: SkillFilter): Int = when (filter) {
        SkillFilter.All -> availableSkills.size
        SkillFilter.Active -> availableSkills.count { it.isEnabled }
        SkillFilter.Inactive -> availableSkills.count { !it.isEnabled }
    }

    fun fetchSkills() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            gatewayService.fetchSkillsStatus()
                .onSuccess { response ->
                    val skills = response.skills.map { serverSkill ->
                        mapServerSkillToSkill(serverSkill)
                    }
                    _uiState.update { it.copy(skills = skills, isLoading = false) }

                    // Update SkillsManager with enabled skills
                    skillsManager.updateFromSkills(skills)
                    Log.d(TAG, "Fetched ${skills.size} skills, ${enabledCount} enabled")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to fetch skills", error)
                    _uiState.update {
                        it.copy(
                            error = error.message ?: "Failed to fetch skills",
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun mapServerSkillToSkill(serverSkill: ServerSkill): Skill {
        return Skill(
            id = serverSkill.skillKey,
            name = serverSkill.name,
            description = serverSkill.description ?: "",
            icon = mapEmojiToIcon(serverSkill.emoji),
            isEnabled = !serverSkill.disabled,
            skillKey = serverSkill.skillKey,
            missingRequirements = serverSkill.missing?.let { missing ->
                SkillMissingRequirements(
                    bins = missing.bins,
                    anyBins = missing.anyBins,
                    env = missing.env,
                    config = missing.config,
                    os = missing.os
                )
            },
            isEligible = serverSkill.eligible,
            primaryEnv = serverSkill.primaryEnv
        )
    }

    private fun mapEmojiToIcon(emoji: String?): String {
        return when (emoji) {
            "magnifyingglass", "search" -> "magnifyingglass"
            "folder" -> "folder"
            "globe", "earth" -> "globe"
            "bubble.left", "chat" -> "bubble.left"
            "envelope", "email" -> "envelope"
            "calendar" -> "calendar"
            "clock" -> "clock"
            "wrench", "tool" -> "wrench"
            "paintpalette", "palette" -> "paintpalette"
            "chart.bar", "chart" -> "chart.bar"
            "lock" -> "lock"
            "photo", "image" -> "photo"
            "music.note", "music" -> "music.note"
            "iphone", "phone" -> "iphone"
            "laptopcomputer", "laptop" -> "laptopcomputer"
            "cpu" -> "cpu"
            "terminal", "code" -> "terminal"
            "pencil", "edit" -> "pencil"
            else -> "extension"
        }
    }

    fun toggleSkill(skillId: String) {
        val currentSkills = _uiState.value.skills.toMutableList()
        val index = currentSkills.indexOfFirst { it.id == skillId }
        if (index == -1) return

        val skill = currentSkills[index]
        val newEnabled = !skill.isEnabled
        val skillKey = skill.skillKey ?: return

        // Update locally immediately for responsive UI
        currentSkills[index] = skill.copy(isEnabled = newEnabled)
        _uiState.update { it.copy(skills = currentSkills) }
        skillsManager.updateFromSkills(currentSkills)

        // Send update to gateway
        viewModelScope.launch {
            gatewayService.updateSkill(skillKey, newEnabled)
                .onSuccess {
                    Log.d(TAG, "Successfully toggled skill $skillKey to enabled=$newEnabled")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to toggle skill $skillKey", error)
                    // Revert on failure
                    val revertedSkills = _uiState.value.skills.toMutableList()
                    val idx = revertedSkills.indexOfFirst { it.id == skillId }
                    if (idx != -1) {
                        revertedSkills[idx] = revertedSkills[idx].copy(isEnabled = !newEnabled)
                        _uiState.update { it.copy(skills = revertedSkills) }
                        skillsManager.updateFromSkills(revertedSkills)
                    }
                }
        }
    }

    fun setSearchText(text: String) {
        _uiState.update { it.copy(searchText = text) }
    }

    fun setFilter(filter: SkillFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun showCreateSkillSheet() {
        _uiState.update { it.copy(showCreateSkillSheet = true) }
    }

    fun hideCreateSkillSheet() {
        _uiState.update { it.copy(showCreateSkillSheet = false) }
    }

    fun showSkillConfig(skill: Skill) {
        _uiState.update { it.copy(skillToConfig = skill) }
    }

    fun hideSkillConfig() {
        _uiState.update { it.copy(skillToConfig = null) }
    }

    fun configureSkillEnv(
        skill: Skill,
        envName: String,
        value: String,
        onComplete: (Boolean) -> Unit
    ) {
        val skillKey = skill.skillKey ?: run {
            onComplete(false)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isConfiguringSkill = true, configurationError = null) }

            gatewayService.configureSkillEnv(skillKey, envName, value)
                .onSuccess {
                    Log.d(TAG, "Successfully configured $envName for skill $skillKey")
                    _uiState.update { it.copy(isConfiguringSkill = false) }
                    // Refresh skills to update eligibility
                    fetchSkills()
                    onComplete(true)
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to configure skill env", error)
                    _uiState.update {
                        it.copy(
                            isConfiguringSkill = false,
                            configurationError = error.message ?: "Configuration failed"
                        )
                    }
                    onComplete(false)
                }
        }
    }

    /**
     * Map server skill emoji to Material icon name
     */
    fun iconForSkill(iconName: String): String = when (iconName) {
        "magnifyingglass" -> "Search"
        "folder" -> "Folder"
        "globe" -> "Language"
        "bubble.left" -> "Chat"
        "envelope" -> "Email"
        "calendar" -> "CalendarMonth"
        "clock" -> "Schedule"
        "wrench" -> "Build"
        "paintpalette" -> "Palette"
        "chart.bar" -> "BarChart"
        "lock" -> "Lock"
        "photo" -> "Image"
        "music.note" -> "MusicNote"
        "iphone" -> "PhoneIphone"
        "laptopcomputer" -> "Computer"
        "cpu" -> "Memory"
        "terminal" -> "Terminal"
        "pencil" -> "Edit"
        else -> "Extension"
    }
}
