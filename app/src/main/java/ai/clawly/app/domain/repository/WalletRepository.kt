package ai.clawly.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface WalletRepository {
    val publicKeyFlow: Flow<String>
    val accountLabelFlow: Flow<String>
    val authTokenFlow: Flow<String>
    val creditsFlow: Flow<Int>

    suspend fun updateWalletDetails(pubKey: String, accountLabel: String, token: String)
    suspend fun clearWalletDetails()
    suspend fun setCredits(credits: Int)
    suspend fun deductCredit()
}
