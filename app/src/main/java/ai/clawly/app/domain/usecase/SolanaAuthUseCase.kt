package ai.clawly.app.domain.usecase

import android.util.Log
import ai.clawly.app.domain.model.UserWalletDetails
import ai.clawly.app.domain.repository.SolanaAuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SolanaAuthUseCase"

/**
 * UseCase for orchestrating Solana SIWS authentication.
 *
 * Use this to authenticate before accessing protected resources (chat, WebSocket).
 * JWT is NOT needed for purchases - those use wallet address directly.
 */
@Singleton
class SolanaAuthUseCase @Inject constructor(
    private val authRepository: SolanaAuthRepository,
    private val connectWalletUseCase: ConnectWalletUseCase,
    private val walletConnectionUseCase: WalletConnectionUseCase
) {
    /** Flow indicating if user is authenticated */
    val isAuthenticated: Flow<Boolean> = authRepository.isAuthenticatedFlow

    /** Flow of current JWT token */
    val jwtToken: Flow<String?> = authRepository.jwtTokenFlow

    /**
     * Authenticate if needed (token expired or missing).
     *
     * Call this before accessing chat or WebSocket.
     *
     * @return Result with Unit on success
     */
    suspend fun authenticateIfNeeded(): Result<Unit> {
        // Check if already authenticated with valid token
        val existingToken = authRepository.getValidToken()
        if (existingToken != null) {
            Log.d(TAG, "Already authenticated with valid token")
            return Result.success(Unit)
        }

        Log.d(TAG, "No valid token, need to authenticate")

        // Get wallet address
        val walletDetails = walletConnectionUseCase.walletDetails.first()
        if (walletDetails !is UserWalletDetails.Connected) {
            Log.e(TAG, "Wallet not connected")
            return Result.failure(Exception("Wallet not connected"))
        }

        // Authenticate using wallet signing
        return authRepository.authenticate(walletDetails.publicKey) { message ->
            Log.d(TAG, "Requesting wallet signature for SIWS message")
            connectWalletUseCase.signMessage(message)
        }.map { token ->
            Log.d(TAG, "Authentication successful")
        }
    }

    /**
     * Force authentication (ignores existing token).
     * Use when you need to re-authenticate.
     */
    suspend fun forceAuthenticate(): Result<String> {
        val walletDetails = walletConnectionUseCase.walletDetails.first()
        if (walletDetails !is UserWalletDetails.Connected) {
            return Result.failure(Exception("Wallet not connected"))
        }

        return authRepository.authenticate(walletDetails.publicKey) { message ->
            connectWalletUseCase.signMessage(message)
        }
    }

    /**
     * Get valid JWT token or authenticate if needed.
     *
     * @return JWT token or null if authentication failed
     */
    suspend fun getOrRefreshToken(): String? {
        // Try to get existing valid token
        val existingToken = authRepository.getValidToken()
        if (existingToken != null) {
            return existingToken
        }

        // Need to authenticate
        val result = authenticateIfNeeded()
        return if (result.isSuccess) {
            authRepository.getValidToken()
        } else {
            null
        }
    }

    /**
     * Logout and clear JWT token
     */
    suspend fun logout(): Result<Unit> {
        return authRepository.logout()
    }

    /**
     * Check if wallet is authenticated
     */
    suspend fun isWalletAuthenticated(walletAddress: String): Boolean {
        val authenticatedWallet = authRepository.authenticatedWalletFlow.first()
        val hasValidToken = authRepository.getValidToken() != null

        return authenticatedWallet == walletAddress && hasValidToken
    }
}
