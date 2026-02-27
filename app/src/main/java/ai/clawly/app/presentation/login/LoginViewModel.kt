package ai.clawly.app.presentation.login

import android.content.Context
import android.util.Log
import ai.clawly.app.data.auth.FirebaseAuthService
import ai.clawly.app.data.auth.FirebaseAuthState
import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.data.remote.ControlPlaneService
import ai.clawly.app.data.remote.gateway.DeviceIdentityManager
import ai.clawly.app.data.service.PurchaseService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.interfaces.LogInCallback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "LoginViewModel"

data class LoginUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val firebaseAuthService: FirebaseAuthService,
    private val controlPlaneService: ControlPlaneService,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val preferences: GatewayPreferences,
    private val purchaseService: PurchaseService
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            firebaseAuthService.authState.collect { state ->
                if (state is FirebaseAuthState.Authenticated) {
                    _uiState.update { it.copy(isSignedIn = true) }
                }
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            firebaseAuthService.signInWithGoogle(activityContext).fold(
                onSuccess = { user ->
                    Log.d(TAG, "Sign-in successful, calling POST /auth/login")

                    // Link guest account to Firebase on the backend
                    firebaseAuthService.getIdToken(forceRefresh = false).onSuccess { token ->
                        val guestDeviceId = deviceIdentityManager.loadOrCreateIdentity()?.deviceId
                        controlPlaneService.login(token, guestDeviceId).fold(
                            onSuccess = { backendUserId ->
                                Log.d(TAG, "Backend login linked successfully, backendUserId=$backendUserId")
                                if (backendUserId != null) {
                                    preferences.setBackendUserId(backendUserId)
                                }
                            },
                            onFailure = { e -> Log.e(TAG, "Backend login failed", e) }
                        )
                    }.onFailure { e ->
                        Log.e(TAG, "Failed to get Firebase ID token for backend login", e)
                    }

                    // Link RevenueCat
                    val revenueCatUserId = deviceIdentityManager.loadOrCreateIdentity()?.deviceId
                    Log.d(TAG, "Linking RevenueCat with deviceId: ${revenueCatUserId?.take(12)}...")
                    try {
                        if (!revenueCatUserId.isNullOrEmpty()) {
                            Purchases.sharedInstance.logIn(revenueCatUserId, object : LogInCallback {
                                override fun onReceived(
                                    customerInfo: com.revenuecat.purchases.CustomerInfo,
                                    created: Boolean
                                ) {
                                    Log.i(
                                        TAG,
                                        "RevenueCat logIn success (google sign-in): " +
                                            "created=$created, appUserId=${customerInfo.originalAppUserId}, " +
                                            "activeEntitlements=${customerInfo.entitlements.active.keys}, " +
                                            "activeSubscriptions=${customerInfo.activeSubscriptions}"
                                    )
                                }

                                override fun onError(error: PurchasesError) {
                                    Log.e(TAG, "RevenueCat logIn error (google sign-in): ${error.message}")
                                }
                            })
                        }
                        purchaseService.checkSubscriptionStatus()
                    } catch (e: Exception) {
                        Log.e(TAG, "RevenueCat logIn failed", e)
                    }
                    _uiState.update { it.copy(isLoading = false, isSignedIn = true) }
                },
                onFailure = { e ->
                    Log.e(TAG, "Sign-in failed", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Sign-in failed"
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
