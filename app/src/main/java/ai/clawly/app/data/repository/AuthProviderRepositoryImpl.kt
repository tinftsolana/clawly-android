package ai.clawly.app.data.repository

import ai.clawly.app.BuildConfig
import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.data.remote.ControlPlaneService
import ai.clawly.app.data.remote.gateway.DeviceIdentityManager
import ai.clawly.app.data.remote.gateway.GatewayService
import ai.clawly.app.domain.model.*
import ai.clawly.app.domain.repository.AuthProviderRepository
import ai.clawly.app.domain.repository.WalletRepository
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthProviderRepository"

/**
 * Implementation of AuthProviderRepository
 * Matches iOS AuthProviderService.swift exactly
 */
@Singleton
class AuthProviderRepositoryImpl @Inject constructor(
    private val preferences: GatewayPreferences,
    private val controlPlaneService: ControlPlaneService,
    private val gatewayService: GatewayService,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val walletRepository: WalletRepository
) : AuthProviderRepository {

    private val _currentConfig = MutableStateFlow(AuthProviderConfig.empty())
    override val currentConfig: StateFlow<AuthProviderConfig> = _currentConfig.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val POLL_INTERVAL_MS = 5000L
    }

    /**
     * Get current user ID
     * - Web3 builds: Uses wallet address (publicKey)
     * - Web2 builds: Uses stable device identity
     */
    val currentUserId: String
        get() {
            // Check debug override first
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
                // Fallback to device identity if wallet not connected
                Log.w(TAG, "Web3 build but wallet not connected, falling back to device identity")
            }

            // Web2 + fallback: use stable device identity
            val identity = runBlocking { deviceIdentityManager.loadOrCreateIdentity() }
            return identity?.deviceId ?: "android-${System.currentTimeMillis()}"
        }

    init {
        // Load saved config on init
        scope.launch {
            loadSavedConfig()
        }
        setupPairingSubscription()
    }

    private suspend fun loadSavedConfig() {
        val hostingType = HostingType.fromString(preferences.getHostingTypeSync())

        val config = when (hostingType) {
            HostingType.SelfHosted -> {
                AuthProviderConfig.selfHosted(
                    url = preferences.getGatewayUrlSync(),
                    token = preferences.getGatewayTokenSync().ifEmpty { null }
                )
            }
            HostingType.Managed -> {
                // Load managed instance info
                val tenantId = preferences.managedTenantId.first()
                if (tenantId != null) {
                    val status = ManagedInstanceStatus.fromString(
                        preferences.managedStatus.first()
                    ) ?: ManagedInstanceStatus.Queued

                    val instance = ManagedInstanceInfo(
                        tenantId = tenantId,
                        status = status,
                        gatewayUrl = preferences.managedGatewayUrl.first(),
                        gatewayToken = preferences.managedGatewayToken.first()
                    )

                    val config = AuthProviderConfig.managed(instance)

                    // Sync status from server on startup
                    if (instance.status.isInProgress) {
                        // Still provisioning - start polling
                        startPolling(tenantId)
                    } else {
                        // Already ready - fetch latest to ensure gateway config is current
                        syncManagedInstanceOnStartup(tenantId)
                    }

                    config
                } else {
                    AuthProviderConfig.empty()
                }
            }
            null -> AuthProviderConfig.empty()
        }

        _currentConfig.value = config
        if (config.hostingType == HostingType.Managed) {
            enforceManagedSessionKey()
        }
    }

    private fun setupPairingSubscription() {
        scope.launch {
            gatewayService.pairingRequired.collect { requestId ->
                launch {
                    autoApprovePairing(
                        requestId = requestId,
                        source = "pairingRequired",
                        reconnectAfterApproval = true
                    )
                }
            }
        }

        scope.launch {
            gatewayService.pairingRequested.collect { requestId ->
                // pairingRequested usually comes from another device/session:
                // auto-approve but do not force reconnect.
                launch {
                    autoApprovePairing(
                        requestId = requestId,
                        source = "pairingRequested",
                        reconnectAfterApproval = false
                    )
                }
            }
        }
    }

    private suspend fun autoApprovePairing(
        requestId: String,
        source: String,
        reconnectAfterApproval: Boolean,
        attempt: Int = 0
    ) {
        val config = _currentConfig.value
        val effectiveHostingType = if (config.hostingType != null) {
            config.hostingType
        } else {
            HostingType.fromString(preferences.getHostingTypeSync())
        }
        val tenantId = config.managedInstance?.tenantId ?: preferences.getTenantIdSync()
        val initialRequestId = requestId.trim()
        Log.d(
            TAG,
            "$source received, requestId=$initialRequestId, hostingType=$effectiveHostingType, tenantId=$tenantId, attempt=$attempt"
        )

        if (effectiveHostingType != HostingType.Managed || tenantId.isNullOrEmpty()) {
            Log.d(TAG, "Skipping $source auto-approve: not managed or tenantId missing")
            return
        }

        var requestIdToApprove = initialRequestId
        if (requestIdToApprove.isEmpty()) {
            requestIdToApprove = resolvePendingPairingRequestIdFromList(tenantId, source) ?: ""
        }
        if (requestIdToApprove.isEmpty()) {
            Log.w(TAG, "No pending pairing requestId found for $source, skipping approve")
            if (reconnectAfterApproval) {
                reconnectGatewayForManaged()
            }
            return
        }

        Log.d(TAG, "Auto-approving device pairing, requestId=$requestIdToApprove, source=$source")
        val result = controlPlaneService.approvePairing(
            tenantId = tenantId,
            requestId = requestIdToApprove,
            userId = currentUserId
        )
        if (result.isSuccess) {
            Log.d(TAG, "Pairing approved ($source)")
            if (reconnectAfterApproval) {
                reconnectGatewayForManaged()
            }
            return
        }

        val error = result.exceptionOrNull()
        val errorText = (error?.message ?: error.toString()).lowercase()
        val timeoutRetry = attempt < 2 && errorText.contains("gateway_ws_open_timeout")
        val pairingRequestRace = attempt < 2 &&
            error is ai.clawly.app.data.remote.ControlPlaneException.ServerError &&
            error.statusCode == 400 &&
            error.message.contains("pairing_request_failed", ignoreCase = true)
        val willRetry = timeoutRetry || pairingRequestRace
        Log.e(TAG, "Pairing approval failed ($source): $errorText")

        if (willRetry) {
            val nextAttempt = attempt + 1
            val delayMs = if (pairingRequestRace) 350L else (1.2 * nextAttempt * 1000).toLong()
            val retryRequestId = if (pairingRequestRace) {
                resolvePendingPairingRequestIdFromList(tenantId, "$source:resolve-after-failed-approve")
                    ?: requestIdToApprove
            } else {
                requestIdToApprove
            }
            Log.d(TAG, "Retrying pairing approve in ${delayMs}ms ($source, attempt $nextAttempt)")
            delay(delayMs)
            autoApprovePairing(
                requestId = retryRequestId,
                source = "$source:retry",
                reconnectAfterApproval = reconnectAfterApproval,
                attempt = nextAttempt
            )
            return
        }

        if (reconnectAfterApproval) {
            reconnectGatewayForManaged()
        }
    }

    override suspend fun tryApprovePendingPairingFromList(source: String, reconnectAfterApproval: Boolean): Boolean {
        val config = _currentConfig.value
        val effectiveHostingType = if (config.hostingType != null) {
            config.hostingType
        } else {
            HostingType.fromString(preferences.getHostingTypeSync())
        }
        val tenantId = config.managedInstance?.tenantId ?: preferences.getTenantIdSync()

        if (effectiveHostingType != HostingType.Managed || tenantId.isNullOrEmpty()) {
            Log.d(TAG, "Skipping $source list-based approve: not managed or tenantId missing")
            return false
        }

        val requestId = resolvePendingPairingRequestIdFromList(tenantId, source) ?: return false
        autoApprovePairing(
            requestId = requestId,
            source = "$source:list",
            reconnectAfterApproval = reconnectAfterApproval
        )
        return true
    }

    private suspend fun resolvePendingPairingRequestIdFromList(tenantId: String, source: String): String? {
        val listResult = controlPlaneService.getPairingDevices(
            tenantId = tenantId,
            userId = currentUserId
        )
        if (listResult.isFailure) {
            Log.e(TAG, "Failed to fetch pairing list for $source", listResult.exceptionOrNull())
            return null
        }

        val pairingPayload = listResult.getOrNull()?.pairing
        val resolved = extractPendingRequestId(pairingPayload)
        if (resolved.isNullOrEmpty()) {
            Log.d(TAG, "Pairing list has no pending requestId for $source")
            return null
        }

        Log.d(TAG, "Resolved pending requestId from list: $resolved ($source)")
        return resolved
    }

    private fun extractPendingRequestId(pairingPayload: JsonElement?): String? {
        if (pairingPayload == null) return null

        val pendingCandidates = when (pairingPayload) {
            is JsonObject -> sequenceOf(
                pairingPayload["pending"],
                pairingPayload["requests"],
                pairingPayload["devices"]
            )
            is JsonArray -> sequenceOf(pairingPayload)
            else -> emptySequence()
        }

        for (candidate in pendingCandidates) {
            val requestId = extractPendingRequestIdFromArray(candidate)
            if (!requestId.isNullOrEmpty()) return requestId
        }
        return null
    }

    private fun extractPendingRequestIdFromArray(element: JsonElement?): String? {
        val entries = element as? JsonArray ?: return null
        val pendingStates = setOf("pending", "requested", "awaiting_approval")

        return entries.firstNotNullOfOrNull { item ->
            val obj = item as? JsonObject ?: return@firstNotNullOfOrNull null
            val status = obj["status"]?.jsonPrimitive?.contentOrNull?.lowercase()
            val state = obj["state"]?.jsonPrimitive?.contentOrNull?.lowercase()
            if (status != null && status !in pendingStates) return@firstNotNullOfOrNull null
            if (state != null && state !in pendingStates) return@firstNotNullOfOrNull null

            obj["requestId"]?.jsonPrimitive?.contentOrNull
                ?: obj["id"]?.jsonPrimitive?.contentOrNull
        }
    }

    private suspend fun enforceManagedSessionKey() {
        val config = _currentConfig.value
        if (config.hostingType != HostingType.Managed) return

        val targetKey = preferences.getOrCreateManagedSessionKey()
        val currentKey = preferences.getSessionKeySync().trim()
        if (currentKey == targetKey) return

        preferences.setSessionKey(targetKey)
        Log.d(TAG, "Enforced managed session key: $targetKey")
    }

    private suspend fun reconnectGatewayForManaged() {
        gatewayService.reconnect()
    }

    /**
     * Sync managed instance status on startup (matches iOS)
     */
    private suspend fun syncManagedInstanceOnStartup(tenantId: String) {
        _isSyncing.value = true
        val userId = currentUserId

        val result = controlPlaneService.getInstance(tenantId, userId)
        result.fold(
            onSuccess = { instance ->
                updateManagedInstance(instance)
                _isSyncing.value = false

                // If still provisioning, start polling
                if (instance.status.isInProgress) {
                    startPolling(tenantId)
                }
            },
            onFailure = { e ->
                Log.e(TAG, "Startup sync failed", e)
                _isSyncing.value = false
            }
        )
    }

    override suspend fun configureSelfHosted(url: String, token: String?) {
        Log.d(TAG, "Configuring self-hosted: $url")

        stopPolling()

        // Save to preferences
        preferences.setHostingType("self_hosted")
        preferences.setGatewayUrl(url)
        preferences.setGatewayToken(token ?: "")
        preferences.clearManagedInfo()

        // Update config
        val newConfig = AuthProviderConfig.selfHosted(url, token)
        Log.d(TAG, "Setting currentConfig: hostingType=${newConfig.hostingType}, isConfigured=${newConfig.isConfigured}, wssUrl=${newConfig.wssUrl}")
        _currentConfig.value = newConfig

        // Trigger gateway reconnect
        gatewayService.reconnect()
    }

    /**
     * Create or get existing managed instance (matches iOS createManagedInstance)
     */
    suspend fun createManagedInstance(): Result<ManagedInstanceInfo> {
        val userId = currentUserId
        Log.d(TAG, "Creating managed instance for userId: $userId")

        // 1. First check for existing tenants
        val tenantsResult = controlPlaneService.getUserTenants(userId)

        return tenantsResult.fold(
            onSuccess = { existingTenants ->
                if (existingTenants.isNotEmpty()) {
                    // Use existing tenant
                    val existingTenant = existingTenants.first()
                    Log.d(TAG, "Found existing tenant: ${existingTenant.tenantId}")

                    // Configure with existing tenant
                    configureManaged(existingTenant.tenantId)

                    // Update with full info
                    updateManagedInstance(existingTenant)

                    // Start polling if still provisioning
                    if (existingTenant.status.isInProgress) {
                        startPolling(existingTenant.tenantId)
                    }

                    Result.success(existingTenant)
                } else {
                    // 2. No existing tenants - create new instance
                    Log.d(TAG, "No existing tenants, creating new instance")
                    val createResult = controlPlaneService.createInstance(userId)

                    createResult.fold(
                        onSuccess = { newInstance ->
                            Log.d(TAG, "Created new instance: ${newInstance.tenantId}")

                            // Configure managed hosting
                            configureManaged(newInstance.tenantId)

                            // Start polling for status
                            if (newInstance.status.isInProgress) {
                                startPolling(newInstance.tenantId)
                            }

                            Result.success(newInstance)
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Failed to create instance", e)
                            Result.failure(e)
                        }
                    )
                }
            },
            onFailure = { e ->
                Log.e(TAG, "Failed to get user tenants", e)
                // Try creating anyway
                val createResult = controlPlaneService.createInstance(userId)
                createResult.fold(
                    onSuccess = { newInstance ->
                        configureManaged(newInstance.tenantId)
                        if (newInstance.status.isInProgress) {
                            startPolling(newInstance.tenantId)
                        }
                        Result.success(newInstance)
                    },
                    onFailure = { createError ->
                        Result.failure(createError)
                    }
                )
            }
        )
    }

    override suspend fun configureManaged(tenantId: String) {
        Log.d(TAG, "Configuring managed: $tenantId")

        // Save hosting type
        preferences.setHostingType("managed")
        preferences.setManagedTenantId(tenantId)
        preferences.clearSelfHostedInfo()

        // Update config with tenant ID (status will be updated by polling)
        _currentConfig.value = AuthProviderConfig.managed(
            ManagedInstanceInfo(tenantId, ManagedInstanceStatus.Queued)
        )
        enforceManagedSessionKey()
    }

    override suspend fun updateManagedInstance(info: ManagedInstanceInfo) {
        Log.d(TAG, "Updating managed instance: ${info.status}")

        // Save to preferences
        preferences.setManagedStatus(info.status.name.lowercase())
        preferences.setManagedGatewayUrl(info.gatewayUrl)
        preferences.setManagedGatewayToken(info.gatewayToken)

        // Update config
        _currentConfig.value = AuthProviderConfig.managed(info)
        enforceManagedSessionKey()

        // If ready and has gateway URL, auto-activate OpenClaw proxy and connect
        if (info.isReady && !info.gatewayUrl.isNullOrEmpty()) {
            stopPolling()

            // OpenClaw proxy is pre-configured on the server by default
            // No need to call activate - just set the preference
            val currentProvider = runBlocking { preferences.getSelectedAiProviderSync() }
            if (currentProvider != "openclaw") {
                Log.d(TAG, "Instance ready, setting OpenClaw as default provider (pre-configured on server)")
                preferences.setSelectedAiProvider("openclaw")
            }

            // Only update preferences and reconnect if URL changed
            val currentUrl = runBlocking { preferences.getGatewayUrlSync() }
            if (currentUrl != info.gatewayUrl) {
                preferences.setGatewayUrl(info.gatewayUrl)
                preferences.setGatewayToken(info.gatewayToken ?: "")
                gatewayService.reconnect()
                Log.d(TAG, "Instance ready, gateway configured: ${info.gatewayUrl}")
            } else {
                Log.d(TAG, "Instance ready, gateway URL unchanged, skipping reconnect")
            }
        } else if (info.isReady && info.gatewayUrl.isNullOrEmpty()) {
            Log.w(TAG, "Instance is ready but gatewayUrl is missing!")
        }

        // If failed, stop polling
        if (info.status == ManagedInstanceStatus.Failed ||
            info.status == ManagedInstanceStatus.Suspended
        ) {
            stopPolling()
        }
    }

    override suspend fun clearConfig() {
        Log.d(TAG, "Clearing config")

        stopPolling()
        preferences.setHostingType(null)
        preferences.clearSelfHostedInfo()
        preferences.clearManagedInfo()
        preferences.setSelectedAiProvider(null)
        preferences.setSessionKey(GatewayPreferences.DEFAULT_SESSION_KEY)
        preferences.clearManagedSessionKey()

        _currentConfig.value = AuthProviderConfig.empty()

        gatewayService.disconnect()
    }

    override fun getSelectedAiProvider(): String? {
        return runBlocking { preferences.getSelectedAiProviderSync() }
    }

    override suspend fun setSelectedAiProvider(provider: String) {
        preferences.setSelectedAiProvider(provider)
    }

    private fun startPolling(tenantId: String) {
        stopPolling()
        _isSyncing.value = true

        val userId = currentUserId

        pollingJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)

                val result = controlPlaneService.getInstance(tenantId, userId)
                result.onSuccess { instance ->
                    updateManagedInstance(instance)

                    // Stop polling if no longer in progress
                    if (!instance.status.isInProgress) {
                        _isSyncing.value = false
                        Log.d(TAG, "Polling stopped: status is ${instance.status}")
                        break
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Polling failed", e)
                }
            }
        }

        Log.d(TAG, "Started polling for tenant: $tenantId")
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _isSyncing.value = false
        Log.d(TAG, "Stopped polling")
    }
}
