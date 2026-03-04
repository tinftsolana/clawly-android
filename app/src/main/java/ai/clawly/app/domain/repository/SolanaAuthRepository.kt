package ai.clawly.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for Solana SIWS (Sign-In with Solana) authentication
 */
interface SolanaAuthRepository {

    /** Flow indicating if user is authenticated with valid JWT */
    val isAuthenticatedFlow: Flow<Boolean>

    /** Flow of current JWT token (null if not authenticated) */
    val jwtTokenFlow: Flow<String?>

    /** Flow of authenticated wallet address */
    val authenticatedWalletFlow: Flow<String?>

    /**
     * Authenticate using SIWS flow:
     * 1. Get nonce from server
     * 2. Build SIWS message
     * 3. Sign message with wallet
     * 4. Verify signature with server
     * 5. Store JWT token
     *
     * @param walletAddress Solana wallet address (base58)
     * @param signMessage Lambda to sign message using wallet
     * @return JWT token on success
     */
    suspend fun authenticate(
        walletAddress: String,
        signMessage: suspend (String) -> ByteArray?
    ): Result<String>

    /**
     * Logout and revoke JWT token
     */
    suspend fun logout(): Result<Unit>

    /**
     * Get valid JWT token (null if expired)
     */
    suspend fun getValidToken(): String?

    /**
     * Check if token needs refresh
     */
    suspend fun shouldRefreshToken(): Boolean

    /**
     * Refresh token if needed
     * @param walletAddress Solana wallet address
     * @param signMessage Lambda to sign message using wallet
     * @return New JWT token or null if refresh not needed
     */
    suspend fun refreshTokenIfNeeded(
        walletAddress: String,
        signMessage: suspend (String) -> ByteArray?
    ): Result<String?>
}
