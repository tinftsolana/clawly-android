package ai.clawly.app.presentation.settings

import ai.clawly.app.data.remote.ControlPlaneService
import ai.clawly.app.domain.model.AuthProviderConfig
import ai.clawly.app.domain.repository.AuthProviderRepository
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "InstanceSetupViewModel"

data class InstanceSetupUiState(
    val currentConfig: AuthProviderConfig = AuthProviderConfig.empty(),
    val selectedProvider: AIProviderType = AIProviderType.OpenAIOAuth,
    val openaiApiKey: String = "",
    val anthropicApiKey: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val showError: Boolean = false,
    val oauthUrl: String? = null,
    val showOAuthWebView: Boolean = false
)

@HiltViewModel
class InstanceSetupViewModel @Inject constructor(
    private val repository: AuthProviderRepository,
    private val controlPlaneService: ControlPlaneService
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstanceSetupUiState())
    val uiState: StateFlow<InstanceSetupUiState> = _uiState.asStateFlow()

    val tenantId: String?
        get() = _uiState.value.currentConfig.managedInstance?.tenantId

    init {
        observeConfig()
    }

    private fun observeConfig() {
        viewModelScope.launch {
            repository.currentConfig.collect { config ->
                _uiState.update { it.copy(currentConfig = config) }
            }
        }
    }

    fun selectProvider(provider: AIProviderType) {
        _uiState.update { it.copy(selectedProvider = provider) }
    }

    fun updateOpenAIApiKey(key: String) {
        _uiState.update { it.copy(openaiApiKey = key) }
    }

    fun updateAnthropicApiKey(key: String) {
        _uiState.update { it.copy(anthropicApiKey = key) }
    }

    /**
     * Start OpenAI OAuth flow - gets auth URL and shows WebView
     * Matches iOS flow from spec:
     * 1. POST /instances/{tenantId}/auth/openai/start
     * 2. Server returns authUrl
     * 3. Present WebView with authUrl
     */
    fun connectOpenAIOAuth(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentTenantId = tenantId
        if (currentTenantId == null) {
            onError("No managed instance configured")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = controlPlaneService.startOpenAIOAuth(currentTenantId)
                result.fold(
                    onSuccess = { authUrl ->
                        Log.d(TAG, "OAuth URL received: $authUrl")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                oauthUrl = authUrl,
                                showOAuthWebView = true
                            )
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Start OAuth failed", e)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "Failed to start OAuth",
                                showError = true
                            )
                        }
                        onError(e.message ?: "OAuth connection failed")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "OAuth failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "OAuth connection failed",
                        showError = true
                    )
                }
                onError(e.message ?: "OAuth connection failed")
            }
        }
    }

    /**
     * Complete OAuth after WebView intercepts localhost callback
     * Matches iOS flow: POST /instances/{tenantId}/auth/openai/callback
     */
    fun completeOpenAIOAuth(
        callbackUrl: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentTenantId = tenantId
        if (currentTenantId == null) {
            onError("No managed instance configured")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showOAuthWebView = false) }
            try {
                val result = controlPlaneService.completeOpenAIOAuth(currentTenantId, callbackUrl)
                result.fold(
                    onSuccess = {
                        Log.d(TAG, "OAuth completed successfully")
                        repository.setSelectedAiProvider("openai_oauth")
                        _uiState.update { it.copy(isLoading = false) }
                        onSuccess()
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Complete OAuth failed", e)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "Failed to complete OAuth",
                                showError = true
                            )
                        }
                        onError(e.message ?: "OAuth completion failed")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "OAuth completion failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "OAuth completion failed",
                        showError = true
                    )
                }
                onError(e.message ?: "OAuth completion failed")
            }
        }
    }

    fun dismissOAuthWebView() {
        _uiState.update { it.copy(showOAuthWebView = false, oauthUrl = null) }
    }

    /**
     * Save OpenAI API key to instance
     * Matches iOS flow: POST /instances/{tenantId}/auth/openai/key
     */
    fun saveOpenAIApiKey(
        apiKey: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentTenantId = tenantId
        if (currentTenantId == null) {
            onError("No managed instance configured")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Validate API key format (matches iOS validation)
                if (!apiKey.startsWith("sk-") || apiKey.length < 20) {
                    throw IllegalArgumentException("Invalid API key format. Must start with 'sk-' and be at least 20 characters.")
                }

                // Call Control Plane API to save the key
                val result = controlPlaneService.setOpenAIApiKey(currentTenantId, apiKey)
                result.fold(
                    onSuccess = {
                        Log.d(TAG, "OpenAI API key saved successfully")
                        repository.setSelectedAiProvider("openai_api_key")
                        _uiState.update { it.copy(isLoading = false) }
                        onSuccess()
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Save OpenAI API key failed", e)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "Failed to save API key",
                                showError = true
                            )
                        }
                        onError(e.message ?: "Failed to save API key")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Save API key failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to save API key",
                        showError = true
                    )
                }
                onError(e.message ?: "Failed to save API key")
            }
        }
    }

    /**
     * Save Anthropic API key to instance
     * Matches iOS flow: POST /instances/{tenantId}/auth/anthropic/key
     */
    fun saveAnthropicApiKey(
        apiKey: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentTenantId = tenantId
        if (currentTenantId == null) {
            onError("No managed instance configured")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Validate API key format (matches iOS validation)
                if (!apiKey.startsWith("sk-ant-") || apiKey.length < 20) {
                    throw IllegalArgumentException("Invalid API key format. Must start with 'sk-ant-' and be at least 20 characters.")
                }

                // Call Control Plane API to save the key
                val result = controlPlaneService.setAnthropicApiKey(currentTenantId, apiKey)
                result.fold(
                    onSuccess = {
                        Log.d(TAG, "Anthropic API key saved successfully")
                        repository.setSelectedAiProvider("anthropic")
                        _uiState.update { it.copy(isLoading = false) }
                        onSuccess()
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Save Anthropic API key failed", e)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "Failed to save API key",
                                showError = true
                            )
                        }
                        onError(e.message ?: "Failed to save API key")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Save Anthropic key failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to save API key",
                        showError = true
                    )
                }
                onError(e.message ?: "Failed to save API key")
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(showError = false, error = null) }
    }
}
