package ai.clawly.app.presentation.settings

import ai.clawly.app.BuildConfig
import ai.clawly.app.analytics.AmplitudeAnalyticsService
import ai.clawly.app.data.auth.FirebaseAuthService
import ai.clawly.app.data.auth.FirebaseAuthState
import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.data.remote.ControlPlaneService
import ai.clawly.app.data.remote.RemoteConfigFlags
import ai.clawly.app.data.remote.gateway.DeviceIdentityManager
import ai.clawly.app.data.remote.gateway.GatewayService
import ai.clawly.app.data.service.PurchaseService
import ai.clawly.app.domain.model.AuthProviderConfig
import ai.clawly.app.domain.model.ConnectionStatus
import ai.clawly.app.domain.model.HostingType
import ai.clawly.app.data.remote.ControlPlaneService.SolanaAuthRequiredException
import ai.clawly.app.domain.repository.AuthProviderRepository
import ai.clawly.app.domain.repository.ChatRepository
import ai.clawly.app.domain.repository.SolanaAuthRepository
import ai.clawly.app.domain.model.UserWalletDetails
import ai.clawly.app.domain.repository.WalletRepository
import ai.clawly.app.domain.usecase.ConnectWalletUseCase
import ai.clawly.app.domain.usecase.GatewayConnectionUseCase
import ai.clawly.app.domain.usecase.WalletConnectionUseCase
import ai.clawly.app.domain.usecase.Web3CreditsUseCase
import com.revenuecat.purchases.Purchases
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
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
    val userId: String = "",
    // Firebase account info (web2)
    val firebaseUserName: String? = null,
    val firebaseUserEmail: String? = null,
    val firebaseUserPhotoUrl: String? = null,
    val isFirebaseSignedIn: Boolean = false,
    val allowSelfHostedWithoutPremium: Boolean = false,
    // Test login
    val showTestLoginSheet: Boolean = false,
    val isTestLoggingIn: Boolean = false,
    val testLoginResult: String? = null,
    // Web3 SIWS sign-in
    val showSignInSheet: Boolean = false,
    val isSigning: Boolean = false,
    val hasValidJwt: Boolean = false
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
    private val chatRepository: ChatRepository,
    private val analytics: AmplitudeAnalyticsService,
    private val firebaseAuthService: FirebaseAuthService,
    private val purchaseService: PurchaseService,
    private val solanaAuthRepository: SolanaAuthRepository,
    private val connectWalletUseCase: ConnectWalletUseCase,
    private val walletConnectionUseCase: WalletConnectionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * Get current user ID
     * - Web3 builds: Uses wallet address (publicKey)
     * - Web2 builds: Uses stable device identity
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

            // Web2 + fallback: Use stable device identity
            val identity = runBlocking { deviceIdentityManager.loadOrCreateIdentity() }
            return identity?.deviceId ?: "android-${System.currentTimeMillis()}"
        }

    init {
        loadSettings()
        loadUserId()
        loadRemoteFlags()
        observeAuthConfig()
        observeConnectionStatus()
        observeFirebaseAuth()
        observeJwtStatus()
        if (BuildConfig.IS_WEB3) {
            observeWalletConnection()
        }
    }

    private fun observeJwtStatus() {
        if (!BuildConfig.IS_WEB3) return

        viewModelScope.launch {
            solanaAuthRepository.isAuthenticatedFlow.collect { isAuthenticated ->
                _uiState.update { it.copy(hasValidJwt = isAuthenticated) }
            }
        }
    }

    private fun observeWalletConnection() {
        viewModelScope.launch {
            walletConnectionUseCase.walletDetails.collect { details ->
                when (details) {
                    is UserWalletDetails.Connected -> {
                        android.util.Log.d("SettingsViewModel", "Wallet connected: ${details.publicKey}")
                        _uiState.update { it.copy(userId = details.publicKey) }
                        refreshAuthConfig()
                    }
                    is UserWalletDetails.NotConnected -> {
                        android.util.Log.d("SettingsViewModel", "Wallet disconnected")
                        _uiState.update {
                            it.copy(
                                userId = "",
                                hasValidJwt = false,
                                credits = 0
                            )
                        }
                    }
                }
            }
        }
    }

    private fun loadRemoteFlags() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()

        // Apply current cached value immediately.
        _uiState.update {
            it.copy(
                allowSelfHostedWithoutPremium = remoteConfig.getBoolean(RemoteConfigFlags.KEY_SELF_HOSTED_WITHOUT_PREMIUM)
            )
        }

        // Then refresh from backend; this resolves stale value during first screen open.
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                val enabled = remoteConfig.getBoolean(RemoteConfigFlags.KEY_SELF_HOSTED_WITHOUT_PREMIUM)
                android.util.Log.d(
                    "SettingsViewModel",
                    "Remote flag updated: self_hosted_without_premium_enabled=$enabled, success=${task.isSuccessful}"
                )
                _uiState.update { it.copy(allowSelfHostedWithoutPremium = enabled) }
            }
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

        // Resolve premium from RevenueCat with optional debug override.
        viewModelScope.launch {
            combine(
                purchaseService.subscriptionStatus,
                preferences.debugPremiumActive,
                preferences.debugPremiumOverride
            ) { subscriptionStatus, active, value ->
                Triple(subscriptionStatus.isActive, active, value)
            }.collect { (isActive, active, value) ->
                _uiState.update {
                    it.copy(
                        debugPremiumOverride = if (active) value else null,
                        isPremium = if (active) value else isActive
                    )
                }
            }
        }

        if (!BuildConfig.DEBUG) {
            _uiState.update {
                it.copy(
                    useDebugDefaults = false,
                    debugPremiumOverride = null,
                    useDebugUserId = false,
                    useBypassToken = false,
                    bypassToken = "",
                    isAddingDebugCredits = false,
                    debugCreditsResult = null
                )
            }
        }
    }

    /**
     * Refresh the current auth config - call when screen becomes visible
     */
    fun refreshAuthConfig() {
        loadRemoteFlags()
        // Force re-read from repository
        val currentConfig = authProviderRepository.currentConfig.value
        android.util.Log.d("SettingsViewModel", "refreshAuthConfig: hostingType=${currentConfig.hostingType}, isConfigured=${currentConfig.isConfigured}, isProvisioning=${currentConfig.isProvisioning}, managedInstance=${currentConfig.managedInstance?.tenantId}, firebaseSignedIn=${firebaseAuthService.isSignedIn}, currentUserId=$currentUserId")
        _uiState.update {
            it.copy(currentAuthConfig = currentConfig)
        }

        // If managed hosting, refresh status from server (handles app kill during provisioning)
        if (currentConfig.hostingType == HostingType.Managed && currentConfig.managedInstance != null) {
            viewModelScope.launch {
                authProviderRepository.refreshManagedInstanceStatus()
            }
        }

        if (currentConfig.hostingType == HostingType.Managed && currentConfig.isConfigured) {
            fetchCredits()
        }

        // Web3: fetch credits if wallet is connected (needed for premium access check before setup)
        if (BuildConfig.IS_WEB3 && currentConfig.hostingType != HostingType.Managed) {
            val walletAddress = runBlocking { walletRepository.publicKeyFlow.first() }
            if (walletAddress.isNotEmpty()) {
                fetchCredits()
            }
        }

        // Web2: check backend for existing tenants if signed in but no local config
        if (BuildConfig.IS_WEB2 && !currentConfig.isConfigured && !currentConfig.isProvisioning) {
            checkForExistingTenants()
        }

        // Web3: check backend for existing tenants if wallet connected but no local config
        if (BuildConfig.IS_WEB3 && !currentConfig.isConfigured && !currentConfig.isProvisioning) {
            val walletAddress = runBlocking { walletRepository.publicKeyFlow.first() }
            if (walletAddress.isNotEmpty()) {
                checkForExistingTenantsWeb3()
            }
        }
    }

    /**
     * Check if user has existing tenants on the backend (Web3)
     */
    private fun checkForExistingTenantsWeb3() {
        viewModelScope.launch {
            val walletAddress = walletRepository.publicKeyFlow.first()
            if (walletAddress.isEmpty()) {
                android.util.Log.d("SettingsViewModel", "checkForExistingTenantsWeb3: No wallet connected")
                return@launch
            }

            _uiState.update { it.copy(isSyncing = true) }
            android.util.Log.d("SettingsViewModel", "checkForExistingTenantsWeb3: Checking for tenants for wallet $walletAddress")

            controlPlaneService.getUserTenants(walletAddress).fold(
                onSuccess = { tenants ->
                    android.util.Log.d("SettingsViewModel", "checkForExistingTenantsWeb3: Found ${tenants.size} tenants")
                    if (tenants.isNotEmpty()) {
                        val existingTenant = tenants.first()
                        android.util.Log.d("SettingsViewModel", "checkForExistingTenantsWeb3: Using tenant ${existingTenant.tenantId}, status=${existingTenant.status}")
                        authProviderRepository.configureManaged(existingTenant.tenantId)
                        authProviderRepository.updateManagedInstance(existingTenant)
                    }
                    _uiState.update { it.copy(isSyncing = false) }
                },
                onFailure = { e ->
                    android.util.Log.e("SettingsViewModel", "checkForExistingTenantsWeb3: Failed", e)
                    _uiState.update { it.copy(isSyncing = false) }

                    // Show sign-in sheet if auth required
                    if (e is SolanaAuthRequiredException) {
                        _uiState.update { it.copy(showSignInSheet = true) }
                    }
                }
            )
        }
    }

    /**
     * Check if user has existing tenants on the backend (from iOS or another device)
     */
    private fun checkForExistingTenants() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }

            val userId = currentUserId
            android.util.Log.d("SettingsViewModel", "Checking for existing tenants for userId=$userId")
            controlPlaneService.getUserTenants(userId).fold(
                onSuccess = { tenants ->
                    android.util.Log.d("SettingsViewModel", "Found ${tenants.size} existing tenants")
                    if (tenants.isNotEmpty()) {
                        val existingTenant = tenants.first()
                        android.util.Log.d("SettingsViewModel", "Using existing tenant: ${existingTenant.tenantId}, status=${existingTenant.status}")
                        authProviderRepository.configureManaged(existingTenant.tenantId)
                        authProviderRepository.updateManagedInstance(existingTenant)
                    }
                    _uiState.update { it.copy(isSyncing = false) }
                },
                onFailure = { e ->
                    android.util.Log.e("SettingsViewModel", "Failed to check for existing tenants", e)
                    _uiState.update { it.copy(isSyncing = false) }
                }
            )
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
            // Don't fetch if wallet is not connected (web3)
            if (BuildConfig.IS_WEB3) {
                val walletAddress = walletRepository.publicKeyFlow.first()
                if (walletAddress.isEmpty()) return@launch
            }

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

                    // Show sign-in sheet if authentication required
                    if (e is SolanaAuthRequiredException) {
                        _uiState.update { it.copy(showSignInSheet = true) }
                    }
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
            chatRepository.clearMessages()
            gatewayConnectionUseCase.disconnect()
            if (BuildConfig.IS_WEB3) {
                web3CreditsUseCase.setCredits(0)
            }
            _uiState.update { it.copy(credits = 0) }
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
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            preferences.setUseDebugDefaults(enabled)
            _uiState.update { it.copy(useDebugDefaults = enabled) }
        }
    }

    fun setDebugPremiumOverride(value: Boolean?) {
        if (!BuildConfig.DEBUG) return
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
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            preferences.setAlwaysShowOnboarding(enabled)
            _uiState.update { it.copy(alwaysShowOnboarding = enabled) }
        }
    }

    fun setUseDebugUserId(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            preferences.setUseDebugUserId(enabled)
            _uiState.update { it.copy(useDebugUserId = enabled) }
        }
    }

    fun setUseBypassToken(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            preferences.setUseBypassToken(enabled)
            _uiState.update { it.copy(useBypassToken = enabled) }
        }
    }

    fun setBypassToken(token: String) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            preferences.setBypassToken(token)
            _uiState.update { it.copy(bypassToken = token) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(showError = false, error = null) }
    }

    // MARK: - Firebase Auth

    private fun observeFirebaseAuth() {
        viewModelScope.launch {
            firebaseAuthService.authState.collect { state ->
                when (state) {
                    is FirebaseAuthState.Authenticated -> {
                        _uiState.update {
                            it.copy(
                                isFirebaseSignedIn = true,
                                firebaseUserName = state.displayName,
                                firebaseUserEmail = state.email,
                                firebaseUserPhotoUrl = state.photoUrl
                            )
                        }
                    }
                    else -> {
                        _uiState.update {
                            it.copy(
                                isFirebaseSignedIn = false,
                                firebaseUserName = null,
                                firebaseUserEmail = null,
                                firebaseUserPhotoUrl = null
                            )
                        }
                    }
                }
            }
        }
    }

    fun signOutFirebase() {
        viewModelScope.launch {
            // Disconnect gateway first
            android.util.Log.d("SettingsViewModel", "Signing out: disconnecting gateway")
            gatewayConnectionUseCase.disconnect()

            // Clear hosting config
            android.util.Log.d("SettingsViewModel", "Signing out: clearing auth config")
            authProviderRepository.clearConfig()

            // Clear chat messages
            android.util.Log.d("SettingsViewModel", "Signing out: clearing chat messages")
            chatRepository.clearMessages()

            // Clear backend userId
            preferences.clearBackendUserId()
        }

        // Sign out of Firebase
        firebaseAuthService.signOut()

        // Sign out of RevenueCat
        try {
            Purchases.sharedInstance.logOut(null)
        } catch (_: Exception) { }
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
        if (!BuildConfig.DEBUG) return
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

    // MARK: - Test Login

    fun showTestLoginSheet() {
        _uiState.update { it.copy(showTestLoginSheet = true, testLoginResult = null) }
    }

    fun dismissTestLoginSheet() {
        _uiState.update { it.copy(showTestLoginSheet = false) }
    }

    fun testLogin(email: String, password: String) {
        val creds = RemoteConfigFlags.getTestCredentials()
        if (creds == null) {
            _uiState.update { it.copy(testLoginResult = "Error: No test credentials configured") }
            return
        }

        if (email != creds.login || password != creds.password) {
            _uiState.update { it.copy(testLoginResult = "Error: Invalid credentials") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTestLoggingIn = true, testLoginResult = null) }

            try {
                // Configure gateway with test WSS + token
                authProviderRepository.configureSelfHosted(creds.wss, creds.gateway)

                // Set premium
                setDebugPremiumOverride(true)

                // Gateway auto-connects via configureSelfHosted → reconnect()

                _uiState.update {
                    it.copy(
                        isTestLoggingIn = false,
                        showTestLoginSheet = false,
                        testLoginResult = "Connected to test gateway"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Test login failed", e)
                _uiState.update {
                    it.copy(
                        isTestLoggingIn = false,
                        testLoginResult = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearTestLoginResult() {
        _uiState.update { it.copy(testLoginResult = null) }
    }

    // MARK: - Web3 SIWS Sign-In

    fun showSignInSheet() {
        _uiState.update { it.copy(showSignInSheet = true) }
    }

    fun hideSignInSheet() {
        _uiState.update { it.copy(showSignInSheet = false) }
    }

    /**
     * Perform SIWS authentication.
     * Called when user taps "Sign" in the sign-in bottom sheet.
     */
    fun performSiwsSignIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSigning = true) }

            val walletAddress = walletRepository.publicKeyFlow.first()
            if (walletAddress.isEmpty()) {
                android.util.Log.e("SettingsViewModel", "SIWS: No wallet connected")
                _uiState.update { it.copy(isSigning = false, showSignInSheet = false) }
                return@launch
            }

            android.util.Log.d("SettingsViewModel", "SIWS: Starting authentication for ${walletAddress.take(8)}...")

            solanaAuthRepository.authenticate(walletAddress) { message ->
                connectWalletUseCase.signMessage(message)
            }.onSuccess {
                android.util.Log.d("SettingsViewModel", "SIWS: Authentication successful")
                _uiState.update { it.copy(isSigning = false, showSignInSheet = false) }
                // Refresh to fetch data with new JWT
                refreshAuthConfig()
            }.onFailure { e ->
                android.util.Log.e("SettingsViewModel", "SIWS: Authentication failed", e)
                _uiState.update { it.copy(isSigning = false) }
            }
        }
    }
}
