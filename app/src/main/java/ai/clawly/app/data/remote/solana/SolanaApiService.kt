package ai.clawly.app.data.remote.solana

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
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SolanaApiService"

@Singleton
class SolanaApiService @Inject constructor() {

    companion object {
        const val BASE_URL = "http://157.245.185.252:3004"
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
            level = LogLevel.BODY
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
    }

    // MARK: - Auth Endpoints

    /**
     * Get nonce for SIWS authentication
     * GET /api/auth/nonce?publicKey={wallet}
     */
    suspend fun getNonce(publicKey: String): Result<NonceResponse> {
        return try {
            Log.d(TAG, "Getting nonce for publicKey: ${publicKey.take(8)}...")

            val response = client.get("$BASE_URL/api/auth/nonce") {
                parameter("publicKey", publicKey)
            }

            if (response.status.isSuccess()) {
                val nonceResponse = response.body<NonceResponse>()
                Log.d(TAG, "Nonce received: ${nonceResponse.nonce.take(16)}...")
                Result.success(nonceResponse)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Get nonce failed: ${response.status} - $errorBody")
                Result.failure(SolanaApiException.ServerError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get nonce error", e)
            Result.failure(e)
        }
    }

    /**
     * Verify signature and get JWT token
     * POST /api/auth/verify
     */
    suspend fun verifySignature(request: VerifyRequest): Result<VerifyResponse> {
        return try {
            Log.d(TAG, "Verifying signature for publicKey: ${request.publicKey.take(8)}...")

            val response = client.post("$BASE_URL/api/auth/verify") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val verifyResponse = response.body<VerifyResponse>()
                Log.d(TAG, "Signature verified, token received")
                Result.success(verifyResponse)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Verify signature failed: ${response.status} - $errorBody")
                Result.failure(SolanaApiException.ServerError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Verify signature error", e)
            Result.failure(e)
        }
    }

    /**
     * Logout and revoke JWT token
     * POST /api/auth/logout
     */
    suspend fun logout(token: String): Result<Unit> {
        return try {
            Log.d(TAG, "Logging out...")

            val response = client.post("$BASE_URL/api/auth/logout") {
                header("Authorization", "Bearer $token")
            }

            if (response.status.isSuccess()) {
                Log.d(TAG, "Logout successful")
                Result.success(Unit)
            } else {
                // Ignore logout errors - clear locally anyway
                Log.w(TAG, "Logout request failed, ignoring")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Logout error, ignoring", e)
            Result.success(Unit)
        }
    }

    // MARK: - Payment Endpoints (NO JWT NEEDED)

    /**
     * Get available credit offers
     * GET /solana/offers?currency=SOL
     */
    suspend fun getOffers(currency: String = "SOL"): Result<List<SolanaOffer>> {
        return try {
            Log.d(TAG, "Fetching offers for currency: $currency")

            val response = client.get("$BASE_URL/solana/offers") {
                parameter("currency", currency)
            }

            if (response.status.isSuccess()) {
                val offers = response.body<List<SolanaOffer>>()
                Log.d(TAG, "Received ${offers.size} offers")
                Result.success(offers)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Get offers failed: ${response.status} - $errorBody")
                Result.failure(SolanaApiException.ServerError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get offers error", e)
            Result.failure(e)
        }
    }

    /**
     * Get unsigned purchase transaction
     * GET /solana/getPurchaseTransaction?walletAddress={}&offerId={}
     */
    suspend fun getPurchaseTransaction(
        walletAddress: String,
        offerId: String
    ): Result<PurchaseTransactionResponse> {
        return try {
            Log.d(TAG, "Getting purchase transaction for offer: $offerId")

            val response = client.get("$BASE_URL/solana/getPurchaseTransaction") {
                parameter("walletAddress", walletAddress)
                parameter("offerId", offerId)
            }

            if (response.status.isSuccess()) {
                val txResponse = response.body<PurchaseTransactionResponse>()
                Log.d(TAG, "Purchase transaction received")
                Result.success(txResponse)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Get purchase transaction failed: ${response.status} - $errorBody")
                Result.failure(SolanaApiException.ServerError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get purchase transaction error", e)
            Result.failure(e)
        }
    }

    /**
     * Sync purchases from blockchain and update credits
     * GET /solana/sync/purchases?walletAddress={}
     */
    suspend fun syncPurchases(walletAddress: String): Result<SyncPurchasesResponse> {
        return try {
            Log.d(TAG, "Syncing purchases for wallet: ${walletAddress.take(8)}...")

            val response = client.get("$BASE_URL/solana/sync/purchases") {
                parameter("walletAddress", walletAddress)
            }

            if (response.status.isSuccess()) {
                val syncResponse = response.body<SyncPurchasesResponse>()
                Log.d(TAG, "Sync complete, total credits: ${syncResponse.getTotalCredits()}")
                Result.success(syncResponse)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Sync purchases failed: ${response.status} - $errorBody")
                Result.failure(SolanaApiException.ServerError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync purchases error", e)
            Result.failure(e)
        }
    }

    /**
     * Get purchase history
     * GET /solana/purchases?walletAddress={}
     */
    suspend fun getPurchaseHistory(walletAddress: String): Result<List<PurchaseRecord>> {
        return try {
            Log.d(TAG, "Getting purchase history for wallet: ${walletAddress.take(8)}...")

            val response = client.get("$BASE_URL/solana/purchases") {
                parameter("walletAddress", walletAddress)
            }

            if (response.status.isSuccess()) {
                val purchases = response.body<List<PurchaseRecord>>()
                Log.d(TAG, "Received ${purchases.size} purchases")
                Result.success(purchases)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Get purchase history failed: ${response.status} - $errorBody")
                Result.failure(SolanaApiException.ServerError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get purchase history error", e)
            Result.failure(e)
        }
    }

    /**
     * Get user info including credits
     * GET /users/{walletAddress}
     */
    suspend fun getUser(walletAddress: String): Result<SolanaUserResponse> {
        return try {
            Log.d(TAG, "Getting user info for: ${walletAddress.take(8)}...")

            val response = client.get("$BASE_URL/users/$walletAddress")

            if (response.status.isSuccess()) {
                val userResponse = response.body<SolanaUserResponse>()
                Log.d(TAG, "User info received, credits: ${userResponse.credits}")
                Result.success(userResponse)
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Get user failed: ${response.status} - $errorBody")
                Result.failure(SolanaApiException.ServerError(response.status.value, errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get user error", e)
            Result.failure(e)
        }
    }
}

// MARK: - Exceptions

sealed class SolanaApiException : Exception() {
    data class ServerError(val statusCode: Int, override val message: String) : SolanaApiException()
    data class NonceExpired(override val message: String = "Nonce expired") : SolanaApiException()
    data class InvalidSignature(override val message: String = "Invalid signature") : SolanaApiException()
    data class OfferNotFound(override val message: String = "Offer not found") : SolanaApiException()
}
