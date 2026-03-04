package ai.clawly.app.presentation.paywall

import android.util.Log
import ai.clawly.app.data.remote.solana.SolanaOffer
import ai.clawly.app.domain.model.UserWalletDetails
import ai.clawly.app.domain.repository.SolanaPaymentsRepository
import ai.clawly.app.domain.usecase.ConnectWalletUseCase
import ai.clawly.app.domain.usecase.WalletConnectionUseCase
import ai.clawly.app.domain.usecase.Web3CreditsUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "Web3PaywallViewModel"

data class Web3PaywallUiState(
    val walletAddress: String? = null,
    val isWalletConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isPurchasing: Boolean = false,
    val isLoadingOffers: Boolean = false,
    val isWaitingForConfirmation: Boolean = false,
    val isRestoringCredits: Boolean = false,
    val selectedPackageId: String? = null,
    val purchaseSuccess: Boolean = false,
    val errorMessage: String? = null,
    val currentCredits: Int = 0,
    val creditsReceived: Int = 0
)

@HiltViewModel
class Web3PaywallViewModel @Inject constructor(
    private val walletConnectionUseCase: WalletConnectionUseCase,
    private val connectWalletUseCase: ConnectWalletUseCase,
    private val web3CreditsUseCase: Web3CreditsUseCase,
    private val solanaPaymentsRepository: SolanaPaymentsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(Web3PaywallUiState())
    val uiState: StateFlow<Web3PaywallUiState> = _uiState.asStateFlow()

    /** Available credit offers from API */
    val offers: StateFlow<List<SolanaOffer>> = solanaPaymentsRepository.offersFlow

    /** Loading state for offers */
    val isLoadingOffers: StateFlow<Boolean> = solanaPaymentsRepository.isLoadingOffers

    private var confirmationPollingJob: Job? = null
    private companion object {
        const val CONFIRMATION_POLL_INTERVAL_MS = 10_000L // 10 seconds
        const val MAX_CONFIRMATION_ATTEMPTS = 30 // 5 minutes max
    }

    init {
        observeWalletConnection()
        observeCredits()
        tryRestoreSession()
        loadOffers()
    }

    private fun observeCredits() {
        viewModelScope.launch {
            web3CreditsUseCase.creditsFlow.collect { credits ->
                _uiState.update { it.copy(currentCredits = credits) }
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
                                isWalletConnected = true,
                                isConnecting = false,
                                errorMessage = null
                            )
                        }
                        // Sync credits when wallet connects
                        syncCredits(details.publicKey)
                    }
                    is UserWalletDetails.NotConnected -> {
                        Log.d(TAG, "Wallet not connected")
                        stopConfirmationPolling()
                        _uiState.update { state ->
                            state.copy(
                                walletAddress = null,
                                isWalletConnected = false,
                                isConnecting = false,
                                isWaitingForConfirmation = false
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

    private fun loadOffers() {
        viewModelScope.launch {
            Log.d(TAG, "Loading credit offers...")
            _uiState.update { it.copy(isLoadingOffers = true) }

            solanaPaymentsRepository.loadOffers()
                .onSuccess { offers ->
                    Log.d(TAG, "Loaded ${offers.size} offers")
                    _uiState.update { it.copy(isLoadingOffers = false) }
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to load offers: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isLoadingOffers = false,
                            errorMessage = "Failed to load offers: ${error.message}"
                        )
                    }
                }
        }
    }

    private fun syncCredits(walletAddress: String) {
        viewModelScope.launch {
            solanaPaymentsRepository.syncCredits(walletAddress)
                .onSuccess { credits ->
                    Log.d(TAG, "Credits synced: $credits")
                }
                .onFailure { error ->
                    Log.w(TAG, "Failed to sync credits: ${error.message}")
                }
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
        if (!state.isWalletConnected || state.selectedPackageId == null) {
            Log.w(TAG, "Cannot purchase: wallet not connected or no package selected")
            return
        }

        val walletAddress = state.walletAddress ?: return
        val creditsBefore = state.currentCredits

        _uiState.update { it.copy(isPurchasing = true, errorMessage = null) }

        viewModelScope.launch {
            Log.d(TAG, "Starting purchase for offer: ${state.selectedPackageId}, credits before: $creditsBefore")

            solanaPaymentsRepository.purchaseCredits(
                walletAddress = walletAddress,
                offerId = state.selectedPackageId,
                signAndSendTransaction = { transactionBytes ->
                    Log.d(TAG, "Requesting wallet to sign transaction...")
                    connectWalletUseCase.signAndSendTransaction(transactionBytes)
                }
            ).onSuccess { _ ->
                Log.d(TAG, "Transaction sent! Starting confirmation polling...")
                _uiState.update {
                    it.copy(
                        isPurchasing = false,
                        isWaitingForConfirmation = true
                    )
                }
                // Start polling for credit confirmation
                startConfirmationPolling(walletAddress, creditsBefore)
            }.onFailure { error ->
                Log.e(TAG, "Purchase failed: ${error.message}")
                _uiState.update {
                    it.copy(
                        isPurchasing = false,
                        errorMessage = when {
                            error.message?.contains("rejected", ignoreCase = true) == true ->
                                "Transaction was rejected"
                            error.message?.contains("insufficient", ignoreCase = true) == true ->
                                "Insufficient SOL balance"
                            else -> "Purchase failed: ${error.message}"
                        }
                    )
                }
            }
        }
    }

    private fun startConfirmationPolling(walletAddress: String, creditsBefore: Int) {
        confirmationPollingJob?.cancel()
        confirmationPollingJob = viewModelScope.launch {
            var attempts = 0
            while (isActive && attempts < MAX_CONFIRMATION_ATTEMPTS) {
                delay(CONFIRMATION_POLL_INTERVAL_MS)
                attempts++
                Log.d(TAG, "Polling for confirmation (attempt $attempts)...")

                solanaPaymentsRepository.syncCredits(walletAddress)
                    .onSuccess { newCreditsLong ->
                        val newCredits = newCreditsLong.toInt()
                        Log.d(TAG, "Credits after sync: $newCredits (was $creditsBefore)")
                        if (newCredits > creditsBefore) {
                            val creditsReceived = newCredits - creditsBefore
                            Log.d(TAG, "Purchase confirmed! Received $creditsReceived credits")
                            _uiState.update {
                                it.copy(
                                    isWaitingForConfirmation = false,
                                    purchaseSuccess = true,
                                    creditsReceived = creditsReceived
                                )
                            }
                            return@launch
                        }
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Sync failed during confirmation: ${error.message}")
                    }
            }

            // Timeout - credits didn't change
            Log.w(TAG, "Confirmation timeout after $attempts attempts")
            _uiState.update {
                it.copy(
                    isWaitingForConfirmation = false,
                    errorMessage = "Purchase confirmation timeout. Please check your credits later."
                )
            }
        }
    }

    private fun stopConfirmationPolling() {
        confirmationPollingJob?.cancel()
        confirmationPollingJob = null
    }

    fun refreshOffers() {
        loadOffers()
    }

    fun restoreCredits() {
        val walletAddress = _uiState.value.walletAddress ?: return

        _uiState.update { it.copy(isRestoringCredits = true, errorMessage = null) }

        viewModelScope.launch {
            Log.d(TAG, "Restoring credits for wallet: $walletAddress")
            solanaPaymentsRepository.syncCredits(walletAddress)
                .onSuccess { credits ->
                    Log.d(TAG, "Credits restored: $credits")
                    _uiState.update { it.copy(isRestoringCredits = false) }
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to restore credits: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isRestoringCredits = false,
                            errorMessage = "Failed to restore credits: ${error.message}"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun resetPurchaseState() {
        stopConfirmationPolling()
        _uiState.update {
            it.copy(
                purchaseSuccess = false,
                isWaitingForConfirmation = false,
                creditsReceived = 0,
                selectedPackageId = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopConfirmationPolling()
    }
}
