package ai.clawly.app.presentation.wallet

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

private const val TAG = "WalletViewModel"

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletConnectionUseCase: WalletConnectionUseCase,
    private val connectWalletUseCase: ConnectWalletUseCase,
    private val web3CreditsUseCase: Web3CreditsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    init {
        observeWalletConnection()
        observeCredits()
        tryRestoreSession()
    }

    private fun observeCredits() {
        viewModelScope.launch {
            web3CreditsUseCase.creditsFlow.collect { credits ->
                Log.d(TAG, "Credits updated: $credits")
                _uiState.update { it.copy(credits = credits) }
            }
        }
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
                                accountLabel = details.accountLabel,
                                shortenedAddress = shortenAddress(details.publicKey),
                                isWalletConnected = true,
                                isConnecting = false,
                                isDisconnecting = false,
                                errorMessage = null
                            )
                        }
                    }
                    is UserWalletDetails.NotConnected -> {
                        Log.d(TAG, "Wallet not connected")
                        _uiState.update { state ->
                            state.copy(
                                walletAddress = "",
                                accountLabel = "",
                                shortenedAddress = "",
                                isWalletConnected = false,
                                isConnecting = false,
                                isDisconnecting = false
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

    fun disconnectWallet() {
        _uiState.update { it.copy(isDisconnecting = true) }
        viewModelScope.launch {
            connectWalletUseCase.disconnect {
                Log.d(TAG, "Wallet disconnected callback")
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun shortenAddress(address: String): String {
        return if (address.length > 10) {
            "${address.take(6)}...${address.takeLast(4)}"
        } else {
            address
        }
    }
}
