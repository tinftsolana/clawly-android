package ai.clawly.app.presentation.settings

import ai.clawly.app.BuildConfig
import ai.clawly.app.analytics.AmplitudeAnalyticsService
import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.data.remote.ControlPlaneService
import ai.clawly.app.data.remote.gateway.DeviceIdentityManager
import ai.clawly.app.data.remote.gateway.GatewayService
import ai.clawly.app.domain.model.AuthProviderConfig
import ai.clawly.app.domain.model.ConnectionStatus
import ai.clawly.app.domain.model.HostingType
import ai.clawly.app.domain.repository.AuthProviderRepository
import ai.clawly.app.domain.repository.WalletRepository
import ai.clawly.app.domain.usecase.GatewayConnectionUseCase
import ai.clawly.app.domain.usecase.Web3CreditsUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

data class SettingsUiState(
    val ttsEnabled: Boolean = false,
    val selectedVoiceId: String? = null,
    val speechRate: Float = 1.0f,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Offline,
    val gatewayUrl: String = "",
    val gatewayToken: String = "",
    val isPremium: Boolean = false,
    val subscriptionPlan: String = "Free",
    val currentAuthConfig: AuthProviderConfig = AuthProviderConfig.empty(),
    val isSyncing: Boolean = false,
    val connectedProvider: AIProviderType? = null,
    val showError: Boolean = false,
    val error: String? = null,
    // Credits (for managed hosting with OpenClaw proxy)
    val credits: Long = 0,
    val isLoadingCredits: Boolean = false,
    val isAddingDebugCredits: Boolean = false,
    val debugCreditsResult: String? = null,
    // Debug options
    val useDebugDefaults: Boolean = false,
    val debugPremiumOverride: Boolean? = null,
    val gatewaySkillsEnabled: Boolean = false,
    val alwaysShowOnboarding: Boolean = false,
    val useDebugUserId: Boolean = false,
    val useBypassToken: Boolean = false,
    val bypassToken: String = "",
    val userId: String = ""
) {
    /** Format credits for display (e.g., 1,000,000 → "1.0M") */
    val creditsFormatted: String
        get() = when {
            credits >= 1_000_000_000 -> String.format("%.1fB", credits / 1_000_000_000.0)
            credits >= 1_000_000 -> String.format("%.1fM", credits / 1_000_000.0)
            credits >= 1_000 -> String.format("%.1fK", credits / 1_000.0)
            else -> credits.toString()
        }
}

enum class AIProviderType(
    val displayName: String,
    val description: String,
    val icon: String,
    val color: Long
) {
    OpenAIOAuth(
        "Login with OpenAI",
        "Connect via OAuth",
        "person.badge.key",
        0xFF009999
    ),
    Anthropic(
        "Claude API Key",
        "Use your Anthropic API key",
        "brain.head.profile",
        0xFFCC8033
    ),
    OpenAIApiKey(
        "ChatGPT API Key",
        "Use your OpenAI API key",
        "key.fill",
        0xFF009999
    )
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: GatewayPreferences,
    private val authProviderRepository: AuthProviderRepository,
    private val gatewayService: GatewayService,
    private val controlPlaneService: ControlPlaneService,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val walletRepository: WalletRepository,
    private val web3CreditsUseCase: Web3CreditsUseCase,
    private val gatewayConnectionUseCase: GatewayConnectionUseCase,
    private val analytics: AmplitudeAnalyticsService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * Get current user ID
     * - Web3 builds: Uses wallet address (publicKey)
     * - Web2 builds: Uses device identity
     */
    private val currentUserId: String
        get() {
            val useDebug = runBlocking { preferences.getUseDebugUserIdSync() }
            if (useDebug) {
                return "e598337531b6e6f100f74de3acc6bece14627b0cb1a2ca7ea8f60f941d043b4a"
            }

            // Web3 builds: Use wallet address as userId
            if (BuildConfig.IS_WEB3) {
                val walletAddress = runBlocking { walletRepository.publicKeyFlow.first() }
                if (walletAddress.isNotEmpty()) {
                    return walletAddress
                }
                android.util.Log.w("SettingsViewModel", "Web3 build but wallet not connected, falling back to device identity")
            }

            // Web2 builds (or web3 fallback): Use device identity
            val identity = runBlocking { deviceIdentityManager.loadOrCreateIdentity() }
            return identity?.deviceId ?: "android-${System.currentTimeMillis()}"
        }

    init {
        loadSettings()
        loadUserId()
        observeAuthConfig()
        observeConnectionStatus()
    }

    private fun loadUserId() {
        viewModelScope.launch {
            val userId = currentUserId
            _uiState.update { it.copy(userId = userId) }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            // Load TTS settings
            preferences.ttsEnabled.collect { enabled ->
                _uiState.update { it.copy(ttsEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            preferences.speechRate.collect { rate ->
                _uiState.update { it.copy(speechRate = rate) }
            }
        }

        viewModelScope.launch {
            preferences.gatewayUrl.collect { url ->
                _uiState.update { it.copy(gatewayUrl = url) }
            }
        }

        viewModelScope.launch {
            preferences.gatewayToken.collect { token ->
                _uiState.update { it.copy(gatewayToken = token) }
            }
        }

        // Load debug settings
        loadDebugSettings()
    }

    private fun loadDebugSettings() {
        viewModelScope.launch {
            preferences.useDebugDefaults.collect { enabled ->
                _uiState.update { it.copy(useDebugDefaults = enabled) }
            }
        }

        viewModelScope.launch {
            preferences.gatewaySkillsEnabled.collect { enabled ->
                _uiState.update { it.copy(gatewaySkillsEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            preferences.alwaysShowOnboarding.collect { enabled ->
                _uiState.update { it.copy(alwaysShowOnboarding = enabled) }
            }
        }

        viewModelScope.launch {
            preferences.useDebugUserId.collect { enabled ->
                _uiState.update { it.copy(useDebugUserId = enabled) }
            }
        }

        viewModelScope.launch {
            preferences.useBypassToken.collect { enabled ->
                _uiState.update { it.copy(useBypassToken = enabled) }
            }
        }

        viewModelScope.launch {
            preferences.bypassToken.collect { token ->
                _uiState.update { it.copy(bypassToken = token) }
            }
        }

        // Load debug premium override
        viewModelScope.launch {
            combine(
                preferences.debugPremiumActive,
                preferences.debugPremiumOverride
            ) { active, value ->
                Pair(active, value)
            }.collect { (active, value) ->
                _uiState.update {
                    it.copy(
                        debugPremiumOverride = if (active) value else null,
                        isPremium = if (active) value else false
                    )
                }
            }
        }
    }

    /**
     * Refresh the current auth config - call when screen becomes visible
     */
    fun refreshAuthConfig() {
        val currentConfig = authProviderRepository.currentConfig.value
        android.util.Log.d("SettingsViewModel", "Refreshing config: hostingType=${currentConfig.hostingType}, isConfigured=${currentConfig.isConfigured}")
        _uiState.update {
            it.copy(currentAuthConfig = currentConfig)
        }
        if (currentConfig.hostingType == HostingType.Managed && currentConfig.isConfigured) {
            fetchCredits()
        }
    }

    private fun observeAuthConfig() {
        viewModelScope.launch {
            authProviderRepository.currentConfig.collect { config ->
                android.util.Log.d("SettingsViewModel", "Received config: hostingType=${config.hostingType}, isConfigured=${config.isConfigured}, wssUrl=${config.wssUrl}")
                _uiState.update {
                    it.copy(currentAuthConfig = config)
                }

                // Fetch credits when managed hosting is configured
                if (config.hostingType == HostingType.Managed && config.isConfigured) {
                    fetchCredits()
                }
            }
        }

        viewModelScope.launch {
            authProviderRepository.isSyncing.collect { syncing ->
                _uiState.update {
                    it.copy(isSyncing = syncing)
                }
            }
        }
    }

    fun fetchCredits() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCredits = true) }

            controlPlaneService.getUser(currentUserId).fold(
                onSuccess = { user ->
                    // Sync credits to local storage for Chat to use
                    web3CreditsUseCase.setCredits(user.credits.toInt())
                    android.util.Log.d("SettingsViewModel", "Synced ${user.credits} credits to local storage")

                    _uiState.update {
                        it.copy(
                            credits = user.credits,
                            isLoadingCredits = false
                        )
                    }
                },
                onFailure = { e ->
                    android.util.Log.e("SettingsViewModel", "Failed to fetch credits", e)
                    _uiState.update { it.copy(isLoadingCredits = false) }
                }
            )
        }
    }

    private fun observeConnectionStatus() {
        viewModelScope.launch {
            gatewayConnectionUseCase.connectionStatus.collect { status ->
                android.util.Log.d("SettingsViewModel", "Connection status changed: $status")
                _uiState.update {
                    it.copy(connectionStatus = status)
                }
            }
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        analytics.trackTtsEnabledChanged(enabled)
        viewModelScope.launch {
            preferences.setTtsEnabled(enabled)
            _uiState.update { it.copy(ttsEnabled = enabled) }
        }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch {
            preferences.setSpeechRate(rate)
            _uiState.update { it.copy(speechRate = rate) }
        }
    }

    fun updateGatewayConfig(url: String, token: String) {
        analytics.trackSettingsGatewayUpdated()
        viewModelScope.launch {
            preferences.setGatewayUrl(url)
            preferences.setGatewayToken(token)
            _uiState.update {
                it.copy(gatewayUrl = url, gatewayToken = token)
            }
            reconnect()
        }
    }

    fun reconnect() {
        analytics.trackSettingsReconnectTapped()
        viewModelScope.launch {
            gatewayConnectionUseCase.reconnect()
        }
    }

    fun logout() {
        viewModelScope.launch {
            authProviderRepository.clearConfig()
        }
    }


    fun setConnectedProvider(provider: AIProviderType?) {
        viewModelScope.launch {
            val providerString = provider?.name?.lowercase()
            preferences.setSelectedAiProvider(providerString)
            _uiState.update { it.copy(connectedProvider = provider) }
        }
    }

    // Debug settings
    fun setUseDebugDefaults(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setUseDebugDefaults(enabled)
            _uiState.update { it.copy(useDebugDefaults = enabled) }
        }
    }

    fun setDebugPremiumOverride(value: Boolean?) {
        analytics.trackDebugPremiumOverrideChanged(value)
        viewModelScope.launch {
            val active = value != null
            val premiumValue = value ?: false
            preferences.setDebugPremiumOverride(active, premiumValue)
            _uiState.update {
                it.copy(
                    debugPremiumOverride = value,
                    isPremium = premiumValue
                )
            }
        }
    }

    fun setGatewaySkillsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setGatewaySkillsEnabled(enabled)
            _uiState.update { it.copy(gatewaySkillsEnabled = enabled) }
        }
    }

    fun setAlwaysShowOnboarding(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAlwaysShowOnboarding(enabled)
            _uiState.update { it.copy(alwaysShowOnboarding = enabled) }
        }
    }

    fun setUseDebugUserId(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setUseDebugUserId(enabled)
            _uiState.update { it.copy(useDebugUserId = enabled) }
        }
    }

    fun setUseBypassToken(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setUseBypassToken(enabled)
            _uiState.update { it.copy(useBypassToken = enabled) }
        }
    }

    fun setBypassToken(token: String) {
        viewModelScope.launch {
            preferences.setBypassToken(token)
            _uiState.update { it.copy(bypassToken = token) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(showError = false, error = null) }
    }

    // MARK: - AI Provider API Key Setup (for managed hosting)

    private val currentTenantId: String?
        get() = _uiState.value.currentAuthConfig.managedInstance?.tenantId

    fun setOpenAIApiKey(apiKey: String, onResult: (Boolean, String?) -> Unit) {
        val tenantId = currentTenantId ?: run {
            onResult(false, "No managed instance configured")
            return
        }
        viewModelScope.launch {
            controlPlaneService.setOpenAIApiKey(tenantId, apiKey).fold(
                onSuccess = {
                    preferences.setSelectedAiProvider("openai")
                    onResult(true, null)
                },
                onFailure = { e ->
                    onResult(false, e.message ?: "Failed to set API key")
                }
            )
        }
    }

    fun setAnthropicApiKey(apiKey: String, onResult: (Boolean, String?) -> Unit) {
        val tenantId = currentTenantId ?: run {
            onResult(false, "No managed instance configured")
            return
        }
        viewModelScope.launch {
            controlPlaneService.setAnthropicApiKey(tenantId, apiKey).fold(
                onSuccess = {
                    preferences.setSelectedAiProvider("anthropic")
                    onResult(true, null)
                },
                onFailure = { e ->
                    onResult(false, e.message ?: "Failed to set API key")
                }
            )
        }
    }

    fun setOpenRouterApiKey(apiKey: String, onResult: (Boolean, String?) -> Unit) {
        val tenantId = currentTenantId ?: run {
            onResult(false, "No managed instance configured")
            return
        }
        viewModelScope.launch {
            controlPlaneService.setOpenRouterApiKey(tenantId, apiKey).fold(
                onSuccess = {
                    preferences.setSelectedAiProvider("openrouter")
                    onResult(true, null)
                },
                onFailure = { e ->
                    onResult(false, e.message ?: "Failed to set API key")
                }
            )
        }
    }

    fun setGlmApiKey(apiKey: String, onResult: (Boolean, String?) -> Unit) {
        val tenantId = currentTenantId ?: run {
            onResult(false, "No managed instance configured")
            return
        }
        viewModelScope.launch {
            controlPlaneService.setGlmApiKey(tenantId, apiKey).fold(
                onSuccess = {
                    preferences.setSelectedAiProvider("glm")
                    onResult(true, null)
                },
                onFailure = { e ->
                    onResult(false, e.message ?: "Failed to set API key")
                }
            )
        }
    }

    fun setMiniMaxApiKey(apiKey: String, onResult: (Boolean, String?) -> Unit) {
        val tenantId = currentTenantId ?: run {
            onResult(false, "No managed instance configured")
            return
        }
        viewModelScope.launch {
            controlPlaneService.setMiniMaxApiKey(tenantId, apiKey).fold(
                onSuccess = {
                    preferences.setSelectedAiProvider("minimax")
                    onResult(true, null)
                },
                onFailure = { e ->
                    onResult(false, e.message ?: "Failed to set API key")
                }
            )
        }
    }

    fun startOpenAIOAuth(onResult: (Boolean, String?) -> Unit) {
        val tenantId = currentTenantId ?: run {
            onResult(false, "No managed instance configured")
            return
        }
        viewModelScope.launch {
            controlPlaneService.startOpenAIOAuth(tenantId).fold(
                onSuccess = { authUrl ->
                    // The authUrl should be opened in browser
                    onResult(true, authUrl)
                },
                onFailure = { e ->
                    onResult(false, e.message ?: "Failed to start OAuth")
                }
            )
        }
    }

    // MARK: - Debug Credits

    /**
     * Add debug credits via bypass token
     * Adds 1 billion credits (1,000,000,000)
     */
    fun addDebugCredits() {
        val bypassToken = _uiState.value.bypassToken
        if (bypassToken.isBlank()) {
            _uiState.update { it.copy(debugCreditsResult = "Error: Bypass token required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAddingDebugCredits = true, debugCreditsResult = null) }

            controlPlaneService.addDebugCredits(
                userId = currentUserId,
                amount = 1_000_000_000L,
                bypassToken = bypassToken
            ).fold(
                onSuccess = { newCredits ->
                    // Sync credits to local storage for Chat to use
                    web3CreditsUseCase.setCredits(newCredits.toInt())
                    android.util.Log.d("SettingsViewModel", "Synced ${newCredits} credits to local storage after debug add")

                    _uiState.update {
                        it.copy(
                            isAddingDebugCredits = false,
                            credits = newCredits,
                            debugCreditsResult = "Added 1B credits! New balance: ${newCredits}"
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isAddingDebugCredits = false,
                            debugCreditsResult = "Error: ${e.message}"
                        )
                    }
                }
            )
        }
    }

    fun clearDebugCreditsResult() {
        _uiState.update { it.copy(debugCreditsResult = null) }
    }
}
