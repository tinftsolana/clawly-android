package ai.clawly.app.presentation.settings

import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.data.repository.AuthProviderRepositoryImpl
import ai.clawly.app.domain.model.AuthProviderConfig
import ai.clawly.app.domain.model.ManagedInstanceStatus
import ai.clawly.app.domain.repository.AuthProviderRepository
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AuthProviderViewModel"

data class AuthProviderUiState(
    val currentConfig: AuthProviderConfig = AuthProviderConfig.empty(),
    val isCreatingInstance: Boolean = false,
    val isSyncing: Boolean = false,
    val showSelfHostedConfig: Boolean = false,
    val showProvisioningView: Boolean = false,
    val error: String? = null,
    val showError: Boolean = false,
    val provisioningStatus: ManagedInstanceStatus = ManagedInstanceStatus.Queued,
    val provisioningError: String? = null,
    val isReady: Boolean = false,
    // Self-hosted dialog pre-filled values
    val selfHostedUrl: String = "",
    val selfHostedToken: String = ""
)

@HiltViewModel
class AuthProviderViewModel @Inject constructor(
    private val repository: AuthProviderRepositoryImpl,
    private val preferences: GatewayPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthProviderUiState())
    val uiState: StateFlow<AuthProviderUiState> = _uiState.asStateFlow()

    init {
        observeConfig()
    }

    private fun observeConfig() {
        viewModelScope.launch {
            repository.currentConfig.collect { config ->
                _uiState.update { state ->
                    state.copy(
                        currentConfig = config,
                        provisioningStatus = config.managedInstance?.status ?: ManagedInstanceStatus.Queued,
                        provisioningError = config.managedInstance?.lastError,
                        isReady = config.managedInstance?.isReady == true
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.isSyncing.collect { syncing ->
                _uiState.update { it.copy(isSyncing = syncing) }
            }
        }
    }

    fun showSelfHostedConfig() {
        viewModelScope.launch {
            try {
                // Load current values from preferences
                val url = preferences.getEffectiveGatewayUrl()
                val token = preferences.getEffectiveGatewayToken()
                Log.d(TAG, "Showing self-hosted config with url=$url")
                _uiState.update {
                    it.copy(
                        showSelfHostedConfig = true,
                        selfHostedUrl = url,
                        selfHostedToken = token
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading self-hosted config", e)
                // Show dialog anyway with empty values
                _uiState.update {
                    it.copy(
                        showSelfHostedConfig = true,
                        selfHostedUrl = "",
                        selfHostedToken = ""
                    )
                }
            }
        }
    }

    fun hideSelfHostedConfig() {
        _uiState.update { it.copy(showSelfHostedConfig = false) }
    }

    fun saveSelfHosted(
        url: String,
        token: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Saving self-hosted config: $url")
                repository.configureSelfHosted(url, token.ifEmpty { null })
                _uiState.update { it.copy(showSelfHostedConfig = false) }
                Log.d(TAG, "Self-hosted config saved successfully")
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save self-hosted config", e)
                val errorMsg = e.message ?: "Failed to save configuration"
                _uiState.update {
                    it.copy(
                        error = errorMsg,
                        showError = true
                    )
                }
                onError(errorMsg)
            }
        }
    }

    fun createManagedInstance(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingInstance = true) }

            try {
                Log.d(TAG, "Creating managed instance...")

                val result = repository.createManagedInstance()
                result.fold(
                    onSuccess = { instance ->
                        Log.d(TAG, "Instance ready: ${instance.tenantId}, status: ${instance.status}")
                        _uiState.update {
                            it.copy(
                                isCreatingInstance = false,
                                showProvisioningView = true,
                                provisioningStatus = instance.status
                            )
                        }
                        onSuccess()
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Instance creation failed", e)
                        val errorMsg = e.message ?: "Failed to create instance"
                        _uiState.update {
                            it.copy(
                                isCreatingInstance = false,
                                error = errorMsg,
                                showError = true
                            )
                        }
                        onError(errorMsg)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                val errorMsg = e.message ?: "An unexpected error occurred"
                _uiState.update {
                    it.copy(
                        isCreatingInstance = false,
                        error = errorMsg,
                        showError = true
                    )
                }
                onError(errorMsg)
            }
        }
    }

    fun showProvisioningView() {
        _uiState.update { it.copy(showProvisioningView = true) }
    }

    fun hideProvisioningView() {
        _uiState.update { it.copy(showProvisioningView = false) }
    }

    fun retryProvisioning() {
        val tenantId = _uiState.value.currentConfig.managedInstance?.tenantId
        if (tenantId != null) {
            viewModelScope.launch {
                repository.configureManaged(tenantId)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(showError = false, error = null) }
    }
}
