package ai.clawly.app.data.remote.solana

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - Auth Models

@Serializable
data class NonceResponse(
    val nonce: String,
    val message: String? = null,
    val expiresAt: Long? = null
)

@Serializable
data class VerifyRequest(
    val publicKey: String,
    val signature: String,  // Base64 encoded
    val message: String
)

@Serializable
data class VerifyResponse(
    val token: String,
    val expiresAt: Long? = null
)

// MARK: - Payment Models

@Serializable
data class SolanaOffer(
    val id: String,
    val credits: Int,
    @SerialName("priceLamports")
    val priceLamports: Long? = null,
    @SerialName("priceRaw")
    val priceRaw: Long? = null,
    @SerialName("priceSol")
    val priceSol: String? = null,
    @SerialName("price")
    val price: String? = null,
    @SerialName("priceDisplay")
    val priceDisplay: Double? = null,
    @SerialName("pricePerCredit")
    val pricePerCredit: String? = null
) {
    // Helper to get lamports from either field
    fun getLamports(): Long = priceLamports ?: priceRaw ?: 0L

    // Helper to get display price
    fun getDisplayPrice(): String = price ?: priceSol ?: "${priceDisplay ?: 0.0} SOL"
}

@Serializable
data class PurchaseTransactionResponse(
    val transaction: String,  // Base64 encoded serialized transaction
    val offerId: String? = null,
    val credits: Int? = null,
    val expiresAt: Long? = null,
    val message: String? = null
)

@Serializable
data class SyncPurchasesResponse(
    val success: Boolean? = null,
    val newPurchases: Int? = null,
    val creditsAdded: Int? = null,
    val totalCredits: Int? = null,
    val credits: Long? = null
) {
    fun getTotalCredits(): Long = credits ?: totalCredits?.toLong() ?: 0L
}

@Serializable
data class PurchaseRecord(
    val id: String? = null,
    val transactionId: String? = null,
    val productId: String? = null,
    val offerId: String? = null,
    val credits: Int,
    val signature: String? = null,
    val currency: String? = null,
    val amountPaid: Long? = null,
    val createdAt: String? = null
)

@Serializable
data class CurrencyResponse(
    val id: String,
    val name: String,
    val decimals: Int? = null
)

// MARK: - User Models

@Serializable
data class SolanaUserResponse(
    val userId: String,
    val credits: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
