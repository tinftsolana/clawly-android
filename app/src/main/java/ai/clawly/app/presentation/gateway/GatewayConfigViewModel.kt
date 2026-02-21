package ai.clawly.app.presentation.gateway

import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.data.remote.gateway.GatewayService
import ai.clawly.app.domain.repository.AuthProviderRepository
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "GatewayConfigViewModel"

@HiltViewModel
class GatewayConfigViewModel @Inject constructor(
    private val gatewayPreferences: GatewayPreferences,
    private val gatewayService: GatewayService,
    private val authProviderRepository: AuthProviderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GatewayConfigState())
    val state: StateFlow<GatewayConfigState> = _state.asStateFlow()

    private val _events = Channel<GatewayConfigEvent>()
    val events = _events.receiveAsFlow()

    init {
        loadConfig()
        observeConnectionStatus()
        observeDebugDefaults()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            // Use effective URL/token which respects hosting type
            val url = gatewayPreferences.getEffectiveGatewayUrl()
            val token = gatewayPreferences.getEffectiveGatewayToken()

            _state.update {
                it.copy(
                    gatewayUrl = url,
                    gatewayToken = token,
                    isLoading = false
                )
            }
        }
    }

    private fun observeConnectionStatus() {
        viewModelScope.launch {
            gatewayService.connectionStatus.collect { status ->
                _state.update { it.copy(connectionStatus = status) }
            }
        }
    }

    private fun observeDebugDefaults() {
        viewModelScope.launch {
            gatewayPreferences.useDebugDefaults.collect { useDebugDefaults ->
                _state.update { it.copy(isUsingDebugDefaults = useDebugDefaults) }
            }
        }
    }

    fun onUrlChange(url: String) {
        _state.update {
            it.copy(
                gatewayUrl = url,
                urlError = validateUrl(url)
            )
        }
    }

    fun onTokenChange(token: String) {
        _state.update { it.copy(gatewayToken = token) }
    }

    fun saveAndReconnect() {
        val currentState = _state.value

        // Validate
        val urlError = validateUrl(currentState.gatewayUrl)
        if (urlError != null) {
            _state.update { it.copy(urlError = urlError) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }

            try {
                Log.d(TAG, "Saving self-hosted config: ${currentState.gatewayUrl}")

                // Use AuthProviderRepository to properly configure self-hosted hosting
                // This sets hosting type, saves URL/token, and triggers reconnect
                authProviderRepository.configureSelfHosted(
                    url = currentState.gatewayUrl,
                    token = currentState.gatewayToken.ifEmpty { null }
                )

                _state.update { it.copy(isSaving = false) }
                _events.send(GatewayConfigEvent.SaveSuccess)
                _events.send(GatewayConfigEvent.NavigateBack)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save config", e)
                _state.update {
                    it.copy(
                        isSaving = false,
                        showError = true,
                        errorMessage = e.message ?: "Failed to save configuration"
                    )
                }
                _events.send(GatewayConfigEvent.ShowError(e.message ?: "Failed to save configuration"))
            }
        }
    }

    fun cancel() {
        viewModelScope.launch {
            _events.send(GatewayConfigEvent.NavigateBack)
        }
    }

    fun clearError() {
        _state.update { it.copy(showError = false, errorMessage = null) }
    }

    private fun validateUrl(url: String): String? {
        if (url.isBlank()) return "URL is required"
        if (!url.startsWith("wss://") && !url.startsWith("ws://")) {
            return "URL must start with wss:// or ws://"
        }
        return null
    }
}
