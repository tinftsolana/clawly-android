package ai.clawly.app.data.repository

import android.util.Base64
import android.util.Log
import ai.clawly.app.data.preferences.SolanaAuthPreferences
import ai.clawly.app.data.remote.solana.SolanaApiService
import ai.clawly.app.data.remote.solana.VerifyRequest
import ai.clawly.app.domain.repository.SolanaAuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SolanaAuthRepository"

@Singleton
class SolanaAuthRepositoryImpl @Inject constructor(
    private val apiService: SolanaApiService,
    private val preferences: SolanaAuthPreferences
) : SolanaAuthRepository {

    override val isAuthenticatedFlow: Flow<Boolean> = preferences.isAuthenticated

    override val jwtTokenFlow: Flow<String?> = preferences.jwtToken

    override val authenticatedWalletFlow: Flow<String?> = preferences.authenticatedWallet

    override suspend fun authenticate(
        walletAddress: String,
        signMessage: suspend (String) -> ByteArray?
    ): Result<String> {
        Log.d(TAG, "Starting SIWS authentication for wallet: ${walletAddress.take(8)}...")

        // 1. Get nonce from server
        val nonceResult = apiService.getNonce(walletAddress)
        val nonceResponse = nonceResult.getOrElse { error ->
            Log.e(TAG, "Failed to get nonce: ${error.message}")
            return Result.failure(error)
        }

        // 2. Build SIWS message
        val message = nonceResponse.message ?: buildSiwsMessage(walletAddress, nonceResponse.nonce)
        Log.d(TAG, "SIWS message built, requesting wallet signature...")

        // 3. Sign message using wallet
        val signature = signMessage(message)
        if (signature == null) {
            Log.e(TAG, "User rejected signing request")
            return Result.failure(Exception("User rejected signing request"))
        }

        // 4. Encode signature to base64
        val signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP)
        Log.d(TAG, "Message signed, verifying with server...")

        // 5. Verify signature with server
        val verifyResult = apiService.verifySignature(
            VerifyRequest(
                publicKey = walletAddress,
                signature = signatureBase64,
                message = message
            )
        )

        return verifyResult.map { response ->
            Log.d(TAG, "Signature verified, JWT received")

            // 6. Store JWT token
            preferences.setJwtToken(
                token = response.token,
                expiresAt = response.expiresAt,
                walletAddress = walletAddress
            )

            response.token
        }.onFailure { error ->
            Log.e(TAG, "Signature verification failed: ${error.message}")
        }
    }

    override suspend fun logout(): Result<Unit> {
        Log.d(TAG, "Logging out...")

        val token = preferences.getJwtTokenSync()

        // Try to revoke on server (ignore errors)
        if (!token.isNullOrEmpty()) {
            apiService.logout(token)
        }

        // Clear local storage
        preferences.clearToken()
        Log.d(TAG, "Logout complete")

        return Result.success(Unit)
    }

    override suspend fun getValidToken(): String? {
        return preferences.getValidToken()
    }

    override suspend fun shouldRefreshToken(): Boolean {
        return preferences.shouldRefreshToken()
    }

    override suspend fun refreshTokenIfNeeded(
        walletAddress: String,
        signMessage: suspend (String) -> ByteArray?
    ): Result<String?> {
        // Check if refresh is needed
        if (!shouldRefreshToken()) {
            val existingToken = getValidToken()
            return Result.success(existingToken)
        }

        Log.d(TAG, "Token needs refresh, re-authenticating...")

        // Re-authenticate
        return authenticate(walletAddress, signMessage).map { it }
    }

    private fun buildSiwsMessage(walletAddress: String, nonce: String): String {
        // Standard SIWS message format
        return """
            clawlyai.io wants you to sign in with your Solana account:
            $walletAddress

            Sign in to Clawly

            Nonce: $nonce
            Issued At: ${java.time.Instant.now()}
        """.trimIndent()
    }
}
