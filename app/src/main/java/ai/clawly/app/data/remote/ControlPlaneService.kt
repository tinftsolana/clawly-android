package ai.clawly.app.data.remote

import ai.clawly.app.BuildConfig
import ai.clawly.app.data.auth.FirebaseAuthService
import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.domain.model.ManagedInstanceInfo
import ai.clawly.app.domain.model.ManagedInstanceStatus
import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ControlPlaneService"

// MARK: - API Response Models (matching iOS exactly)

@Serializable
data class CreateTenantResponse(
    val tenantId: String,
    val status: String
)

@Serializable
data class GetTenantResponse(
    val tenantId: String,
    val status: String,
    val lastError: String? = null,
    val instance: TenantInstanceResponse? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class TenantInstanceResponse(
    val provider: String? = null,
    val serverId: Int? = null,
    val serverName: String? = null,
    val vmIp: String? = null,
    val sshUser: String? = null,
    val gatewayPort: Int? = null,
    val gatewayUrl: String? = null,
    val gatewayToken: String? = null,
    val flyAppName: String? = null,
    val flyMachineId: String? = null,
    val flyVolumeId: String? = null
)

@Serializable
data class ControlPlaneErrorResponse(
    val error: String
)

@Serializable
data class OpenAIOAuthStartResponse(
    val authUrl: String
)

@Serializable
data class UserResponse(
    val userId: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val credits: Long = 0
)

@Serializable
data class ProviderAuthResponse(
    val ok: Boolean,
    val provider: String? = null
)

@Serializable
data class PairingDevicesResponse(
    val ok: Boolean,
    val pairingType: String? = null,
    val method: String? = null,
    val pairing: JsonElement? = null
)

/**
 * Service for interacting with the control plane API
 * Matches iOS ControlPlaneService.swift exactly
 */
@Singleton
class ControlPlaneService @Inject constructor(
    private val preferences: GatewayPreferences,
    private val firebaseAuthService: FirebaseAuthService
) {
    companion object {
        const val BASE_URL = "http://157.245.185.252:3003"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(TAG, message)
                }
            }
            level = LogLevel.ALL
        }
        install(DefaultRequest) {
            header("X-Platform", if (BuildConfig.IS_WEB3) "web3" else "web2")
        }
    }

    /**
     * Add bypass token header if debug bypass is enabled
     */
    private suspend fun HttpRequestBuilder.addBypassTokenIfNeeded() {
        if (preferences.getUseBypassTokenSync()) {
            val token = preferences.getBypassTokenSync()
            if (token.isNotEmpty()) {
                header("X-Bypass-Token", token)
                Log.d(TAG, "Added bypass token to request")
            }
        }
    }

    /**
     * Add auth headers:
     * - Firebase Bearer token for signed-in web2 users
     * - X-User-Id fallback for user-scoped requests
     */
    private suspend fun HttpRequestBuilder.addAuthHeaders(userId: String?) {
        if (BuildConfig.IS_WEB2 && firebaseAuthService.isSignedIn) {
            firebaseAuthService.getIdToken(forceRefresh = false).onSuccess { token ->
                Log.d(TAG, "AUTH: Using Bearer token (Firebase signed in)")
                header("Authorization", "Bearer $token")
            }.onFailure { e ->
                Log.e(TAG, "AUTH: Firebase signed in but getIdToken FAILED, falling back to X-User-Id", e)
            }
        } else {
            Log.d(TAG, "AUTH: Firebase not signed in (isWeb2=${BuildConfig.IS_WEB2}, isSignedIn=${firebaseAuthService.isSignedIn})")
        }
        if (!userId.isNullOrBlank()) {
            Log.d(TAG, "AUTH: Using X-User-Id=$userId")
            header("X-User-Id", userId)
        } else {
            Log.w(TAG, "AUTH: No Firebase token and no X-User-Id available")
        }
    }

    // MARK: - Auth

    /**
     * Login with Firebase token, optionally linking a guest device ID.
     * POST /auth/login with Bearer token + optional X-User-Id for guest linking.
     * Returns the backend userId from the response.
     */
    suspend fun login(firebaseToken: String, guestUserId: String?): Result<String?> {
        return try {
            Log.d(TAG, "Calling POST /auth/login (guestUserId=${guestUserId != null})")

            val response = client.post("$BASE_URL/auth/login") {
                header("Authorization", "Bearer $firebaseToken")
                if (!guestUserId.isNullOrEmpty()) {
                    header("X-User-Id", guestUserId)
                }
            }

            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                Log.d(TAG, "Login successful, response: $body")
                // Try to extract userId from response
                val backendUserId = try {
                    json.decodeFromString<UserResponse>(body).userId
                } catch (e: Exception) {
                    Log.d(TAG, "Login response has no userId field")
                    null
                }
                Log.d(TAG, "Backend userId: $backendUserId")
                Result.success(backendUserId)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Login failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            Result.failure(e)
        }
    }

    // MARK: - Instance Management (matching iOS)

    /**
     * Create a new managed instance
     * iOS: POST /instances with X-User-Id header
     */
    suspend fun createInstance(userId: String): Result<ManagedInstanceInfo> {
        return try {
            Log.d(TAG, "Creating instance for userId: $userId")

            val response = client.post("$BASE_URL/instances") {
                contentType(ContentType.Application.Json)
                addAuthHeaders(userId)
                addBypassTokenIfNeeded()
                setBody("{}")
            }

            if (response.status.value == 201) {
                val createResponse = response.body<CreateTenantResponse>()
                Log.d(TAG, "Instance created: ${createResponse.tenantId}")
                Result.success(
                    ManagedInstanceInfo(
                        tenantId = createResponse.tenantId,
                        status = ManagedInstanceStatus.fromString(createResponse.status) ?: ManagedInstanceStatus.Queued
                    )
                )
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Create instance failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create instance error", e)
            Result.failure(e)
        }
    }

    /**
     * Get instance status
     * iOS: GET /instances/{tenantId} with X-User-Id header
     */
    suspend fun getInstance(tenantId: String, userId: String): Result<ManagedInstanceInfo> {
        return try {
            Log.d(TAG, "Getting instance: $tenantId for userId: $userId")

            val response = client.get("$BASE_URL/instances/$tenantId") {
                addAuthHeaders(userId)
                addBypassTokenIfNeeded()
            }

            if (response.status.isSuccess()) {
                val tenantResponse = response.body<GetTenantResponse>()
                Result.success(tenantResponse.toManagedInstanceInfo())
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Get instance failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get instance error", e)
            Result.failure(e)
        }
    }

    /**
     * Get user's tenants/instances
     * iOS: GET /users/{userId}/tenants with X-User-Id header
     */
    suspend fun getUserTenants(userId: String): Result<List<ManagedInstanceInfo>> {
        return try {
            Log.d(TAG, "Getting tenants for userId: $userId")

            val response = client.get("$BASE_URL/users/$userId/tenants") {
                addAuthHeaders(userId)
            }

            if (response.status.isSuccess()) {
                val tenants = response.body<List<GetTenantResponse>>()
                Log.d(TAG, "Found ${tenants.size} tenants")
                Result.success(tenants.map { it.toManagedInstanceInfo() })
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Get user tenants failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get user tenants error", e)
            Result.failure(e)
        }
    }

    /**
     * Refresh instance status (manual readiness check)
     * iOS: POST /instances/{tenantId}/refresh with X-User-Id header
     */
    suspend fun refreshInstance(tenantId: String, userId: String): Result<ManagedInstanceInfo> {
        return try {
            Log.d(TAG, "Refreshing instance: $tenantId")

            val response = client.post("$BASE_URL/instances/$tenantId/refresh") {
                contentType(ContentType.Application.Json)
                addAuthHeaders(userId)
                addBypassTokenIfNeeded()
                setBody("{}")
            }

            if (response.status.isSuccess()) {
                val tenantResponse = response.body<GetTenantResponse>()
                Result.success(tenantResponse.toManagedInstanceInfo())
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Refresh instance failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Refresh instance error", e)
            Result.failure(e)
        }
    }

    /**
     * Delete an instance
     * iOS: DELETE /instances/{tenantId} with X-User-Id header
     */
    suspend fun deleteInstance(tenantId: String, userId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Deleting instance: $tenantId")

            val response = client.delete("$BASE_URL/instances/$tenantId") {
                addAuthHeaders(userId)
                addBypassTokenIfNeeded()
            }

            if (response.status.isSuccess()) {
                Log.d(TAG, "Instance deleted: $tenantId")
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Delete instance failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete instance error", e)
            Result.failure(e)
        }
    }

    // MARK: - Provider Authentication

    /**
     * Start OpenAI OAuth flow
     * iOS: POST /instances/{tenantId}/auth/openai/start
     */
    suspend fun startOpenAIOAuth(tenantId: String): Result<String> {
        return try {
            Log.d(TAG, "Starting OpenAI OAuth for tenant: $tenantId")

            val response = client.post("$BASE_URL/instances/$tenantId/auth/openai/start") {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }

            if (response.status.isSuccess()) {
                val oauthResponse = response.body<OpenAIOAuthStartResponse>()
                Log.d(TAG, "OAuth URL received: ${oauthResponse.authUrl}")
                Result.success(oauthResponse.authUrl)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Start OAuth failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Start OAuth error", e)
            Result.failure(e)
        }
    }

    /**
     * Complete OpenAI OAuth flow with callback URL
     * iOS: POST /instances/{tenantId}/auth/openai/callback
     */
    suspend fun completeOpenAIOAuth(tenantId: String, callbackUrl: String): Result<Unit> {
        return try {
            Log.d(TAG, "Completing OpenAI OAuth for tenant: $tenantId")

            val response = client.post("$BASE_URL/instances/$tenantId/auth/openai/callback") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("callbackUrl" to callbackUrl))
            }

            if (response.status.isSuccess()) {
                Log.d(TAG, "OAuth completed successfully")
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Complete OAuth failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Complete OAuth error", e)
            Result.failure(e)
        }
    }

    /**
     * Set OpenAI API key for instance
     */
    suspend fun setOpenAIApiKey(tenantId: String, apiKey: String): Result<Unit> {
        return try {
            val response = client.post("$BASE_URL/instances/$tenantId/auth/openai/key") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("apiKey" to apiKey))
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Set OpenAI API key error", e)
            Result.failure(e)
        }
    }

    /**
     * Set Anthropic API key for instance
     */
    suspend fun setAnthropicApiKey(tenantId: String, apiKey: String): Result<Unit> {
        return try {
            val response = client.post("$BASE_URL/instances/$tenantId/auth/anthropic/key") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("apiKey" to apiKey))
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Set Anthropic API key error", e)
            Result.failure(e)
        }
    }

    /**
     * Set OpenRouter API key for instance
     */
    suspend fun setOpenRouterApiKey(tenantId: String, apiKey: String): Result<Unit> {
        return try {
            val response = client.post("$BASE_URL/instances/$tenantId/auth/openrouter/key") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("apiKey" to apiKey))
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Set OpenRouter API key error", e)
            Result.failure(e)
        }
    }

    /**
     * Set GLM (Zhipu AI) API key for instance
     */
    suspend fun setGlmApiKey(tenantId: String, apiKey: String): Result<Unit> {
        return try {
            val response = client.post("$BASE_URL/instances/$tenantId/auth/glm/key") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("apiKey" to apiKey))
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Set GLM API key error", e)
            Result.failure(e)
        }
    }

    /**
     * Set MiniMax API key for instance
     */
    suspend fun setMiniMaxApiKey(tenantId: String, apiKey: String): Result<Unit> {
        return try {
            val response = client.post("$BASE_URL/instances/$tenantId/auth/minimax/key") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("apiKey" to apiKey))
            }

            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Set MiniMax API key error", e)
            Result.failure(e)
        }
    }

    // MARK: - OpenClaw Proxy

    /**
     * Activate OpenClaw proxy for instance
     * This sets up the proxy to use credits for chat
     */
    suspend fun activateOpenClawProxy(tenantId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Activating OpenClaw proxy for tenant: $tenantId")

            val response = client.post("$BASE_URL/instances/$tenantId/auth/openclaw/activate") {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }

            if (response.status.isSuccess()) {
                Log.d(TAG, "OpenClaw proxy activated")
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Activate OpenClaw failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Activate OpenClaw error", e)
            Result.failure(e)
        }
    }

    // MARK: - User & Credits

    /**
     * Get current user info using Bearer token only (no userId needed).
     * Returns the backend's user object including the actual userId.
     */
    suspend fun getMe(): Result<UserResponse> {
        return try {
            Log.d(TAG, "Getting /me (Bearer token only)")

            val token = firebaseAuthService.getIdToken(forceRefresh = false).getOrNull()
            if (token == null) {
                Log.e(TAG, "getMe failed: not signed in")
                return Result.failure(Exception("Not signed in"))
            }

            val response = client.get("$BASE_URL/me") {
                header("Authorization", "Bearer $token")
            }

            if (response.status.isSuccess()) {
                val userResponse = response.body<UserResponse>()
                Log.d(TAG, "getMe success: userId=${userResponse.userId}, credits=${userResponse.credits}")
                Result.success(userResponse)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "getMe failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMe error", e)
            Result.failure(e)
        }
    }

    /**
     * Get user info including credits balance
     */
    suspend fun getUser(userId: String): Result<UserResponse> {
        return try {
            Log.d(TAG, "Getting user: $userId")

            val response = client.get("$BASE_URL/users/$userId") {
                addAuthHeaders(userId)
            }

            if (response.status.isSuccess()) {
                val userResponse = response.body<UserResponse>()
                Log.d(TAG, "User credits: ${userResponse.credits}")
                Result.success(userResponse)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Get user failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get user error", e)
            Result.failure(e)
        }
    }

    /**
     * Sync purchases and update credits
     */
    suspend fun syncPurchases(userId: String): Result<Long> {
        return try {
            Log.d(TAG, "Syncing purchases for user: $userId")

            val response = client.post("$BASE_URL/me/sync-purchases") {
                contentType(ContentType.Application.Json)
                header("X-User-Id", userId)
                // Keep a single X-User-Id value. addAuthHeaders(null) only adds Bearer token when available.
                addAuthHeaders(null)
                setBody("{}")
            }

            if (response.status.isSuccess()) {
                val syncResponse = response.body<Map<String, Long>>()
                val credits = syncResponse["credits"] ?: 0L
                Log.d(TAG, "Sync complete, credits: $credits")
                Result.success(credits)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Sync purchases failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync purchases error", e)
            Result.failure(e)
        }
    }

    /**
     * Add debug credits to a user (requires bypass token)
     * POST /dev/users/:userId/add-credits
     */
    suspend fun addDebugCredits(userId: String, amount: Long, bypassToken: String): Result<Long> {
        return try {
            Log.d(TAG, "Adding $amount debug credits for user: $userId")

            val response = client.post("$BASE_URL/dev/users/$userId/add-credits") {
                contentType(ContentType.Application.Json)
                header("X-Bypass-Token", bypassToken)
                setBody(mapOf("amount" to amount))
            }

            if (response.status.isSuccess()) {
                val result = response.body<Map<String, Long>>()
                val newCredits = result["credits"] ?: 0L
                Log.d(TAG, "Debug credits added, new balance: $newCredits")
                Result.success(newCredits)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Add debug credits failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Add debug credits error", e)
            Result.failure(e)
        }
    }

    // MARK: - Pairing

    /**
     * Approve a device pairing request
     * POST /instances/:tenantId/pairing/devices/:requestId/approve
     */
    suspend fun approvePairing(tenantId: String, requestId: String, userId: String? = null): Result<Unit> {
        return try {
            Log.d(TAG, "Approving pairing request: $requestId for tenant: $tenantId")

            val response = client.post("$BASE_URL/instances/$tenantId/pairing/devices/$requestId/approve") {
                contentType(ContentType.Application.Json)
                addAuthHeaders(userId)
                addBypassTokenIfNeeded()
                setBody("{}")
            }

            if (response.status.isSuccess()) {
                Log.d(TAG, "Pairing approved successfully")
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Pairing approval failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pairing approval error", e)
            Result.failure(e)
        }
    }

    /**
     * Get current pairing state (pending + approved devices)
     * GET /instances/:tenantId/pairing/devices
     */
    suspend fun getPairingDevices(tenantId: String, userId: String? = null): Result<PairingDevicesResponse> {
        return try {
            Log.d(TAG, "Fetching pairing devices for tenant: $tenantId")

            val response = client.get("$BASE_URL/instances/$tenantId/pairing/devices") {
                addAuthHeaders(userId)
                addBypassTokenIfNeeded()
            }

            if (response.status.isSuccess()) {
                val result = response.body<PairingDevicesResponse>()
                Log.d(TAG, "Pairing devices fetched: method=${result.method}, hasPairing=${result.pairing != null}")
                Result.success(result)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Get pairing devices failed: ${response.status} - $errorBody")
                Result.failure(mapError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get pairing devices error", e)
            Result.failure(e)
        }
    }

    // MARK: - Private Helpers

    private fun GetTenantResponse.toManagedInstanceInfo(): ManagedInstanceInfo {
        return ManagedInstanceInfo(
            tenantId = tenantId,
            status = ManagedInstanceStatus.fromString(status) ?: ManagedInstanceStatus.Queued,
            lastError = lastError,
            gatewayUrl = instance?.gatewayUrl,
            gatewayToken = instance?.gatewayToken
        )
    }

    private fun mapError(statusCode: Int, body: String): Exception {
        val errorMsg = try {
            json.decodeFromString<ControlPlaneErrorResponse>(body).error
        } catch (e: Exception) {
            body
        }

        return when (errorMsg) {
            "missing_x_user_id" -> ControlPlaneException.MissingUserId
            "user_not_found" -> ControlPlaneException.UserNotFound
            "subscription_required" -> ControlPlaneException.SubscriptionRequired
            "forbidden" -> ControlPlaneException.Forbidden
            "invalid_anthropic_api_key" -> ControlPlaneException.InvalidAnthropicApiKey
            "invalid_anthropic_setup_token" -> ControlPlaneException.InvalidAnthropicSetupToken
            "oauth_state_not_found_or_expired" -> ControlPlaneException.OAuthStateExpired
            "oauth_state_tenant_mismatch" -> ControlPlaneException.OAuthStateMismatch
            else -> when (statusCode) {
                404 -> ControlPlaneException.NotFound
                403 -> ControlPlaneException.Forbidden
                else -> ControlPlaneException.ServerError(statusCode, errorMsg)
            }
        }
    }
}

// MARK: - Control Plane Exceptions

sealed class ControlPlaneException : Exception() {
    object MissingUserId : ControlPlaneException() {
        private fun readResolve(): Any = MissingUserId
        override val message = "User ID is required"
    }
    object UserNotFound : ControlPlaneException() {
        private fun readResolve(): Any = UserNotFound
        override val message = "User not found"
    }
    object SubscriptionRequired : ControlPlaneException() {
        private fun readResolve(): Any = SubscriptionRequired
        override val message = "An active subscription is required"
    }
    object Forbidden : ControlPlaneException() {
        private fun readResolve(): Any = Forbidden
        override val message = "Access forbidden"
    }
    object NotFound : ControlPlaneException() {
        private fun readResolve(): Any = NotFound
        override val message = "Instance not found"
    }
    object InvalidAnthropicApiKey : ControlPlaneException() {
        private fun readResolve(): Any = InvalidAnthropicApiKey
        override val message = "Invalid Anthropic API key format"
    }
    object InvalidAnthropicSetupToken : ControlPlaneException() {
        private fun readResolve(): Any = InvalidAnthropicSetupToken
        override val message = "Invalid Anthropic setup token"
    }
    object OAuthStateExpired : ControlPlaneException() {
        private fun readResolve(): Any = OAuthStateExpired
        override val message = "OAuth session expired. Please try again."
    }
    object OAuthStateMismatch : ControlPlaneException() {
        private fun readResolve(): Any = OAuthStateMismatch
        override val message = "OAuth state mismatch. Please try again."
    }
    data class ServerError(val statusCode: Int, override val message: String) : ControlPlaneException()
}
