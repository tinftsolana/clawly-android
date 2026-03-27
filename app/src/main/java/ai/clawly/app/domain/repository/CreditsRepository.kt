package ai.clawly.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for Web2 credits (nano-dollars from Control Plane).
 * Mirrors WalletRepository's credits API but for Web2 builds.
 */
interface CreditsRepository {
    /** Flow of current credit balance (nano-dollars) */
    val creditsFlow: Flow<Long>

    /** Set credits to a specific value (e.g. after sync from server) */
    suspend fun setCredits(credits: Long)

    /** Deduct a fixed amount per message */
    suspend fun deductCredit(amount: Long = DEFAULT_DEDUCTION)

    /** Clear credits (e.g. on logout) */
    suspend fun clearCredits()

    companion object {
        const val NANO_DOLLARS_PER_DOLLAR = 1_000_000_000L
        const val DEFAULT_DEDUCTION = 1_000_000L // ~0.001$ per message (placeholder)
    }
}
