package ai.clawly.app.domain.repository

import ai.clawly.app.data.remote.solana.PurchaseRecord
import ai.clawly.app.data.remote.solana.SolanaOffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for Solana payments and credit purchases
 */
interface SolanaPaymentsRepository {

    /** Flow of available credit offers */
    val offersFlow: StateFlow<List<SolanaOffer>>

    /** Flow indicating if offers are loading */
    val isLoadingOffers: StateFlow<Boolean>

    /** Flow of last error */
    val errorFlow: StateFlow<String?>

    /**
     * Load available credit offers from server
     */
    suspend fun loadOffers(): Result<List<SolanaOffer>>

    /**
     * Purchase credits using Solana transaction
     *
     * Flow:
     * 1. Get unsigned transaction from server
     * 2. Sign and send via wallet
     * 3. Sync credits after confirmation
     *
     * @param walletAddress Solana wallet address
     * @param offerId ID of the offer to purchase
     * @param signAndSendTransaction Lambda to sign and send transaction, returns tx signature
     * @return Credits received
     */
    suspend fun purchaseCredits(
        walletAddress: String,
        offerId: String,
        signAndSendTransaction: suspend (ByteArray) -> String?
    ): Result<Int>

    /**
     * Sync credits from blockchain
     * @param walletAddress Solana wallet address
     * @return Total credits
     */
    suspend fun syncCredits(walletAddress: String): Result<Long>

    /**
     * Get purchase history
     * @param walletAddress Solana wallet address
     */
    suspend fun getPurchaseHistory(walletAddress: String): Result<List<PurchaseRecord>>

    /**
     * Get user credits from server
     * @param walletAddress Solana wallet address
     */
    suspend fun getUserCredits(walletAddress: String): Result<Int>
}
