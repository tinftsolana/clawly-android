package ai.clawly.app.notifications

import ai.clawly.app.BuildConfig
import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.data.remote.ControlPlaneService
import ai.clawly.app.data.remote.RegisterPushTokenRequest
import ai.clawly.app.data.remote.gateway.DeviceIdentityManager
import ai.clawly.app.domain.repository.WalletRepository
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PushTokenManager"
private const val DEBUG_USER_ID = "e598337531b6e6f100f74de3acc6bece14627b0cb1a2ca7ea8f60f941d043b4a"

@Singleton
class PushTokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controlPlaneService: ControlPlaneService,
    private val gatewayPreferences: GatewayPreferences,
    private val walletRepository: WalletRepository,
    private val deviceIdentityManager: DeviceIdentityManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        if (BuildConfig.IS_WEB3) {
            scope.launch {
                walletRepository.publicKeyFlow
                    .distinctUntilChanged()
                    .collect { address ->
                        if (address.isNotEmpty()) {
                            Log.d(TAG, "Wallet changed, attempting push registration")
                            registerIfNeeded(force = true, explicitUserId = address)
                        }
                    }
            }
        } else {
            scope.launch {
                registerIfNeeded()
            }
        }
    }

    fun registerIfNeeded(
        force: Boolean = false,
        explicitToken: String? = null,
        explicitUserId: String? = null
    ) {
        scope.launch {
            if (!hasNotificationPermission()) {
                Log.d(TAG, "Skipping push registration: permission not granted")
                return@launch
            }

            val userId = explicitUserId ?: resolveCurrentUserId()
            if (userId.isNullOrEmpty()) {
                Log.d(TAG, "No userId available for push registration")
                return@launch
            }

            val token = explicitToken ?: fetchFirebaseToken()
            if (token.isNullOrEmpty()) {
                Log.w(TAG, "Unable to fetch Firebase token for push registration")
                return@launch
            }

            val lastToken = gatewayPreferences.getLastPushTokenSync()
            val lastUser = gatewayPreferences.getLastPushUserIdSync()
            if (!force && token == lastToken && userId == lastUser) {
                Log.d(TAG, "Push token already registered for user $userId")
                return@launch
            }

            val request = RegisterPushTokenRequest(
                userId = userId,
                token = token,
                platform = "android",
                buildFlavor = if (BuildConfig.IS_WEB3) "web3" else "web2",
                appVersion = BuildConfig.VERSION_NAME
            )

            controlPlaneService.registerPushToken(request)
                .onSuccess {
                    Log.d(TAG, "Push token registered for user ${userId.take(8)}...")
                    gatewayPreferences.setLastPushRegistration(token, userId)
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to register push token", error)
                }
        }
    }

    fun handleNewFirebaseToken(token: String) {
        Log.d(TAG, "Handling refreshed Firebase token")
        registerIfNeeded(force = true, explicitToken = token)
    }

    private suspend fun resolveCurrentUserId(): String? {
        if (BuildConfig.DEBUG && gatewayPreferences.getUseDebugUserIdSync()) {
            return DEBUG_USER_ID
        }

        if (BuildConfig.IS_WEB3) {
            val walletAddress = walletRepository.publicKeyFlow.first().trim()
            if (walletAddress.isNotEmpty()) {
                return walletAddress
            }
            return null
        }

        return deviceIdentityManager.loadOrCreateIdentity()?.deviceId
    }

    private suspend fun fetchFirebaseToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Firebase token", e)
            null
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
