package ai.clawly.app.presentation.paywall

import android.util.Log
import ai.clawly.app.domain.model.UserWalletDetails
import ai.clawly.app.domain.usecase.ConnectWalletUseCase
import ai.clawly.app.domain.usecase.WalletConnectionUseCase
import ai.clawly.app.domain.usecase.Web3CreditsUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "Web3PaywallViewModel"

data class Web3PaywallUiState(
    val walletAddress: String? = null,
    val isWalletConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isPurchasing: Boolean = false,
    val selectedPackageId: String? = null,
    val purchaseSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class Web3PaywallViewModel @Inject constructor(
    private val walletConnectionUseCase: WalletConnectionUseCase,
    private val connectWalletUseCase: ConnectWalletUseCase,
    private val web3CreditsUseCase: Web3CreditsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(Web3PaywallUiState())
    val uiState: StateFlow<Web3PaywallUiState> = _uiState.asStateFlow()

    init {
        observeWalletConnection()
        tryRestoreSession()
    }

    private fun observeWalletConnection() {
        viewModelScope.launch {
            walletConnectionUseCase.walletDetails.collect { details ->
                when (details) {
                    is UserWalletDetails.Connected -> {
                        Log.d(TAG, "Wallet connected: ${details.publicKey}")
                        _uiState.update { state ->
                            state.copy(
                                walletAddress = details.publicKey,
                                isWalletConnected = true,
                                isConnecting = false,
                                errorMessage = null
                            )
                        }
                    }
                    is UserWalletDetails.NotConnected -> {
                        Log.d(TAG, "Wallet not connected")
                        _uiState.update { state ->
                            state.copy(
                                walletAddress = null,
                                isWalletConnected = false,
                                isConnecting = false
                            )
                        }
                    }
                }
            }
        }
    }

    private fun tryRestoreSession() {
        viewModelScope.launch {
            connectWalletUseCase.tryToRestoreAuthToken()
        }
    }

    fun connectWallet() {
        _uiState.update { it.copy(isConnecting = true, errorMessage = null) }
        viewModelScope.launch {
            val success = connectWalletUseCase.connect()
            if (!success) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        errorMessage = "Failed to connect wallet. Please try again."
                    )
                }
            }
        }
    }

    fun selectPackage(packageId: String) {
        _uiState.update { it.copy(selectedPackageId = packageId) }
    }

    fun purchaseCredits() {
        val state = _uiState.value
        if (!state.isWalletConnected || state.selectedPackageId == null) return

        _uiState.update { it.copy(isPurchasing = true, errorMessage = null) }

        viewModelScope.launch {
            // TODO: Implement actual purchase flow with Solana transaction
            // For now, mock the purchase
            try {
                Log.d(TAG, "Purchasing package: ${state.selectedPackageId} for wallet: ${state.walletAddress}")

                // Simulate network delay
                kotlinx.coroutines.delay(1500)

                // Add credits based on selected package (mocked)
                val creditsToAdd = when (state.selectedPackageId) {
                    "starter" -> 100
                    "standard" -> 500
                    "pro" -> 1000
                    "whale" -> 5000
                    else -> 100
                }
                web3CreditsUseCase.addCredits(creditsToAdd)
                Log.d(TAG, "Added $creditsToAdd credits")

                // Mock success
                _uiState.update {
                    it.copy(
                        isPurchasing = false,
                        purchaseSuccess = true
                    )
                }
                Log.d(TAG, "Purchase successful (mocked)")
            } catch (e: Exception) {
                Log.e(TAG, "Purchase failed", e)
                _uiState.update {
                    it.copy(
                        isPurchasing = false,
                        errorMessage = "Purchase failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
