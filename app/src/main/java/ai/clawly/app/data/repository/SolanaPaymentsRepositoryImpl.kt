package ai.clawly.app.data.repository

import android.util.Base64
import android.util.Log
import ai.clawly.app.data.remote.solana.PurchaseRecord
import ai.clawly.app.data.remote.solana.SolanaApiService
import ai.clawly.app.data.remote.solana.SolanaOffer
import ai.clawly.app.domain.repository.SolanaPaymentsRepository
import ai.clawly.app.domain.repository.WalletRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SolanaPaymentsRepo"

@Singleton
class SolanaPaymentsRepositoryImpl @Inject constructor(
    private val apiService: SolanaApiService,
    private val walletRepository: WalletRepository
) : SolanaPaymentsRepository {

    private val _offersFlow = MutableStateFlow<List<SolanaOffer>>(emptyList())
    override val offersFlow: StateFlow<List<SolanaOffer>> = _offersFlow.asStateFlow()

    private val _isLoadingOffers = MutableStateFlow(false)
    override val isLoadingOffers: StateFlow<Boolean> = _isLoadingOffers.asStateFlow()

    private val _errorFlow = MutableStateFlow<String?>(null)
    override val errorFlow: StateFlow<String?> = _errorFlow.asStateFlow()

    override suspend fun loadOffers(): Result<List<SolanaOffer>> {
        _isLoadingOffers.value = true
        _errorFlow.value = null

        return try {
            val result = apiService.getOffers("SOL")

            result.onSuccess { offers ->
                _offersFlow.value = offers
                Log.d(TAG, "Loaded ${offers.size} offers")
            }.onFailure { error ->
                _errorFlow.value = error.message
                Log.e(TAG, "Failed to load offers: ${error.message}")
            }

            _isLoadingOffers.value = false
            result
        } catch (e: Exception) {
            _isLoadingOffers.value = false
            _errorFlow.value = e.message
            Log.e(TAG, "Error loading offers", e)
            Result.failure(e)
        }
    }

    override suspend fun purchaseCredits(
        walletAddress: String,
        offerId: String,
        signAndSendTransaction: suspend (ByteArray) -> String?
    ): Result<Int> {
        Log.d(TAG, "Starting purchase for offer: $offerId")

        // 1. Get unsigned transaction from server
        val txResult = apiService.getPurchaseTransaction(walletAddress, offerId)
        val txResponse = txResult.getOrElse { error ->
            Log.e(TAG, "Failed to get purchase transaction: ${error.message}")
            return Result.failure(error)
        }

        // 2. Decode base64 transaction
        val txBytes = try {
            Base64.decode(txResponse.transaction, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode transaction: ${e.message}")
            return Result.failure(Exception("Invalid transaction format"))
        }

        Log.d(TAG, "Transaction received, requesting wallet signature...")

        // 3. Sign and send via wallet
        val signature = signAndSendTransaction(txBytes)
        if (signature == null) {
            Log.e(TAG, "Transaction rejected by user")
            return Result.failure(Exception("Transaction rejected by user"))
        }

        Log.d(TAG, "Transaction sent, signature: ${signature.take(16)}...")

        // 4. Wait for blockchain confirmation
        Log.d(TAG, "Waiting for blockchain confirmation...")
        delay(3000)

        // 5. Sync credits from server
        val syncResult = syncCredits(walletAddress)

        return syncResult.map { totalCredits ->
            // Update local wallet credits
            walletRepository.setCredits(totalCredits.toInt())

            // Return credits for this purchase
            val offerCredits = _offersFlow.value.find { it.id == offerId }?.credits
                ?: txResponse.credits
                ?: 0

            Log.d(TAG, "Purchase complete! Credits: $offerCredits, Total: $totalCredits")
            offerCredits
        }.onFailure { error ->
            // Transaction succeeded but sync failed - user can retry sync later
            Log.w(TAG, "Transaction sent but sync failed: ${error.message}")
        }
    }

    override suspend fun syncCredits(walletAddress: String): Result<Long> {
        Log.d(TAG, "Syncing credits for wallet: ${walletAddress.take(8)}...")

        return apiService.syncPurchases(walletAddress).map { response ->
            val totalCredits = response.getTotalCredits()
            walletRepository.setCredits(totalCredits.toInt())
            Log.d(TAG, "Credits synced: $totalCredits")
            totalCredits
        }
    }

    override suspend fun getPurchaseHistory(walletAddress: String): Result<List<PurchaseRecord>> {
        return apiService.getPurchaseHistory(walletAddress)
    }

    override suspend fun getUserCredits(walletAddress: String): Result<Int> {
        return apiService.getUser(walletAddress).map { user ->
            user.credits ?: 0
        }
    }
}
