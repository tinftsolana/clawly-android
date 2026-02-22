package ai.clawly.app.presentation.apikeys

import ai.clawly.app.data.remote.gateway.GatewayService
import ai.clawly.app.domain.model.ConnectionStatus
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val TAG = "ApiKeysViewModel"

data class ConfiguredApiKey(
    val skillKey: String,
    val envName: String,
    val hasValue: Boolean,
    val displayName: String
)

data class ApiKeysUiState(
    val configuredKeys: List<ConfiguredApiKey> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val configHash: String? = null
)

@HiltViewModel
class ApiKeysViewModel @Inject constructor(
    private val gatewayService: GatewayService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiKeysUiState())
    val uiState: StateFlow<ApiKeysUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gatewayService.connectionStatus.collect { status ->
                if (status == ConnectionStatus.Online && _uiState.value.configuredKeys.isEmpty()) {
                    fetchConfig()
                }
            }
        }
    }

    fun fetchConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            gatewayService.getConfig()
                .onSuccess { response ->
                    val keys = parseConfiguredKeys(response.config)
                    _uiState.update {
                        it.copy(
                            configuredKeys = keys,
                            configHash = response.hash,
                            isLoading = false
                        )
                    }
                    Log.d(TAG, "Fetched config, found ${keys.size} configured keys")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to fetch config", error)
                    _uiState.update {
                        it.copy(
                            error = error.message ?: "Failed to fetch configuration",
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun parseConfiguredKeys(config: JsonObject?): List<ConfiguredApiKey> {
        if (config == null) return emptyList()

        val keys = mutableListOf<ConfiguredApiKey>()

        try {
            val skills = config["skills"]?.jsonObject
            val entries = skills?.get("entries")?.jsonObject

            entries?.forEach { (skillKey, skillConfig) ->
                val skillObj = skillConfig.jsonObject

                // Check for apiKey
                val apiKey = skillObj["apiKey"]?.jsonPrimitive?.content
                if (!apiKey.isNullOrEmpty()) {
                    keys.add(
                        ConfiguredApiKey(
                            skillKey = skillKey,
                            envName = "apiKey",
                            hasValue = true,
                            displayName = formatDisplayName(skillKey, "API Key")
                        )
                    )
                }

                // Check for env vars
                val env = skillObj["env"]?.jsonObject
                env?.forEach { (envName, envValue) ->
                    val value = envValue.jsonPrimitive.content
                    if (value.isNotEmpty()) {
                        keys.add(
                            ConfiguredApiKey(
                                skillKey = skillKey,
                                envName = envName,
                                hasValue = true,
                                displayName = formatDisplayName(skillKey, envName)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing config", e)
        }

        return keys
    }

    private fun formatDisplayName(skillKey: String, envName: String): String {
        val skillName = skillKey
            .replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        return if (envName == "apiKey") {
            "$skillName API Key"
        } else {
            "$skillName - $envName"
        }
    }

    fun saveApiKey(skillKey: String, envName: String, value: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, successMessage = null) }

            gatewayService.configureSkillEnv(skillKey, envName, value)
                .onSuccess {
                    Log.d(TAG, "Successfully saved $envName for $skillKey")
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            successMessage = "API key saved successfully"
                        )
                    }
                    // Refresh config to update list
                    fetchConfig()
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to save API key", error)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = error.message ?: "Failed to save API key"
                        )
                    }
                }
        }
    }

    fun deleteApiKey(skillKey: String, envName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, successMessage = null) }

            // Delete by setting to empty string
            gatewayService.configureSkillEnv(skillKey, envName, "")
                .onSuccess {
                    Log.d(TAG, "Successfully deleted $envName for $skillKey")
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            successMessage = "API key deleted"
                        )
                    }
                    fetchConfig()
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to delete API key", error)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = error.message ?: "Failed to delete API key"
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
