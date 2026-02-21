package ai.clawly.app.domain.usecase

import ai.clawly.app.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Web3CreditsUseCase @Inject constructor(
    private val walletRepository: WalletRepository
) {
    val creditsFlow: Flow<Int> = walletRepository.creditsFlow

    suspend fun getCredits(): Int = walletRepository.creditsFlow.first()

    suspend fun hasCredits(): Boolean = getCredits() > 0

    suspend fun setCredits(credits: Int) {
        walletRepository.setCredits(credits)
    }

    suspend fun addCredits(amount: Int) {
        val current = getCredits()
        walletRepository.setCredits(current + amount)
    }

    suspend fun deductCredit() {
        walletRepository.deductCredit()
    }
}
