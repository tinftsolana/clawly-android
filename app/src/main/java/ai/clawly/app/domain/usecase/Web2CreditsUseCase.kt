package ai.clawly.app.domain.usecase

import ai.clawly.app.domain.repository.CreditsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for Web2 credit operations.
 * Credits are in nano-dollars (1,000,000,000 = $1.00).
 */
@Singleton
class Web2CreditsUseCase @Inject constructor(
    private val creditsRepository: CreditsRepository
) {
    /** Raw nano-dollar balance */
    val creditsFlow: Flow<Long> = creditsRepository.creditsFlow

    /** Whether user has any credits */
    val hasCreditsFlow: Flow<Boolean> = creditsFlow.map { it > 0 }

    /** Display string (e.g. "$1.23", "<$0.01", "No credits") */
    val displayStringFlow: Flow<String> = creditsFlow.map { formatCredits(it) }

    suspend fun getCredits(): Long = creditsRepository.creditsFlow.first()

    suspend fun hasCredits(): Boolean = getCredits() > 0

    suspend fun setCredits(credits: Long) {
        creditsRepository.setCredits(credits)
    }

    suspend fun deductCredit() {
        creditsRepository.deductCredit()
    }

    suspend fun clearCredits() {
        creditsRepository.clearCredits()
    }

    companion object {
        private const val NANO_PER_DOLLAR = 1_000_000_000.0

        fun formatCredits(nanoDollars: Long): String {
            val dollars = nanoDollars / NANO_PER_DOLLAR
            return when {
                dollars >= 0.01 -> String.format("$%.2f", dollars)
                nanoDollars > 0 -> "<$0.01"
                else -> "No credits"
            }
        }

        fun formatCreditsShort(nanoDollars: Long): String {
            val dollars = nanoDollars / NANO_PER_DOLLAR
            return when {
                dollars >= 1.0 -> String.format("$%.0f", dollars)
                dollars >= 0.01 -> String.format("$%.2f", dollars)
                nanoDollars > 0 -> "<$0.01"
                else -> "$0"
            }
        }
    }
}
