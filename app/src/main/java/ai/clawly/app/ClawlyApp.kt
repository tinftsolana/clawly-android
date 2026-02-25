package ai.clawly.app

import ai.clawly.app.BuildConfig
import ai.clawly.app.data.auth.FirebaseAuthService
import ai.clawly.app.data.auth.FirebaseAuthState
import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.data.remote.ControlPlaneService
import ai.clawly.app.data.remote.gateway.DeviceIdentityManager
import ai.clawly.app.data.service.PurchaseService
import ai.clawly.app.navigation.ClawlyNavHost
import ai.clawly.app.ui.theme.ClawlyColors
import com.revenuecat.purchases.Purchases
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUiState(
    val isLoading: Boolean = true,
    val showOnboarding: Boolean = false,
    val isFirebaseSignedIn: Boolean = false
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferences: GatewayPreferences,
    private val firebaseAuthService: FirebaseAuthService,
    private val purchaseService: PurchaseService,
    private val controlPlaneService: ControlPlaneService,
    private val deviceIdentityManager: DeviceIdentityManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        checkOnboardingStatus()
        observeDebugSettings()
        observeFirebaseAuth()
    }

    private fun checkOnboardingStatus() {
        viewModelScope.launch {
            val alwaysShow = preferences.alwaysShowOnboarding.first()
            val completed = preferences.onboardingCompleted.first()
            val signedIn = if (BuildConfig.IS_WEB2) firebaseAuthService.isSignedIn else true

            _uiState.value = AppUiState(
                isLoading = false,
                showOnboarding = alwaysShow || !completed,
                isFirebaseSignedIn = signedIn
            )

            // Always associate RevenueCat with stable deviceId on app start (web2).
            if (BuildConfig.IS_WEB2) {
                try {
                    val deviceId = deviceIdentityManager.loadOrCreateIdentity()?.deviceId
                    if (!deviceId.isNullOrEmpty()) {
                        Purchases.sharedInstance.logIn(deviceId, null)
                        purchaseService.checkSubscriptionStatus()
                    }
                } catch (_: Exception) { }
            }

            if (BuildConfig.IS_WEB2) {
                syncPurchasesOnStartup()
            }
        }
    }

    private fun syncPurchasesOnStartup() {
        viewModelScope.launch {
            val userId = resolveSyncUserId()
            if (userId.isNullOrEmpty()) {
                android.util.Log.w("AppViewModel", "Startup sync-purchases skipped: no userId available")
                return@launch
            }
            controlPlaneService.syncPurchases(userId).fold(
                onSuccess = { credits ->
                    android.util.Log.d("AppViewModel", "Startup sync-purchases success: credits=$credits")
                },
                onFailure = { e ->
                    android.util.Log.e("AppViewModel", "Startup sync-purchases failed", e)
                }
            )
        }
    }

    private suspend fun resolveSyncUserId(): String? {
        return deviceIdentityManager.loadOrCreateIdentity()?.deviceId
    }

    private fun observeFirebaseAuth() {
        viewModelScope.launch {
            firebaseAuthService.authState.collect { state ->
                val signedIn = state is FirebaseAuthState.Authenticated
                _uiState.value = _uiState.value.copy(isFirebaseSignedIn = signedIn)
            }
        }
    }

    private fun observeDebugSettings() {
        // Observe changes to "always show onboarding" debug setting
        viewModelScope.launch {
            preferences.alwaysShowOnboarding.collect { alwaysShow ->
                if (alwaysShow) {
                    // If debug setting enabled, show onboarding immediately
                    _uiState.value = _uiState.value.copy(showOnboarding = true)
                } else {
                    // If disabled, check if user completed onboarding
                    val completed = preferences.onboardingCompleted.first()
                    _uiState.value = _uiState.value.copy(showOnboarding = !completed)
                }
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
            _uiState.value = _uiState.value.copy(showOnboarding = false)
        }
    }
}

/**
 * Main entry point for the Clawly chat feature.
 * Can be used as a standalone app or embedded in the existing app.
 */
@Composable
fun ClawlyApp(
    viewModel: AppViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        // Show loading state while checking onboarding status
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClawlyColors.background)
        )
    } else {
        ClawlyNavHost(
            modifier = Modifier
                .fillMaxSize()
                .background(ClawlyColors.background),
            showOnboarding = uiState.showOnboarding,
            isFirebaseSignedIn = uiState.isFirebaseSignedIn,
            onOnboardingComplete = { viewModel.completeOnboarding() }
        )
    }
}
