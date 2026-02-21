package ai.clawly.app.domain.usecase

import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.domain.model.UserWalletDetails
import ai.clawly.app.domain.model.UserWalletDetails.Connected
import ai.clawly.app.domain.model.UserWalletDetails.NotConnected
import ai.clawly.app.domain.repository.WalletRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletConnectionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val preferences: GatewayPreferences
) {
    val walletDetails: Flow<UserWalletDetails> = combine(
        walletRepository.publicKeyFlow,
        walletRepository.accountLabelFlow,
        walletRepository.authTokenFlow
    ) { pubKey, label, authToken ->
        if (pubKey.isEmpty() || authToken.isEmpty()) {
            FirebaseCrashlytics.getInstance().setUserId("")
            NotConnected
        } else {
            FirebaseCrashlytics.getInstance().setUserId(pubKey)
            FirebaseCrashlytics.getInstance().setCustomKey("account_label", label)

            Connected(
                publicKey = pubKey,
                accountLabel = label,
                authToken = authToken
            )
        }
    }

    suspend fun persistConnection(pubKey: String, accountLabel: String, token: String) {
        walletRepository.updateWalletDetails(pubKey, accountLabel, token)
    }

    suspend fun clearConnection() {
        walletRepository.clearWalletDetails()
    }
}
