package ai.clawly.app.domain.usecase

import ai.clawly.app.BuildConfig
import ai.clawly.app.domain.model.UserWalletDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed class UserIdentity {
    data class Authenticated(
        val userId: String,
        val isWeb3: Boolean
    ) : UserIdentity()

    data object NotAuthenticated : UserIdentity()
}

@Singleton
class GetUserIdentityUseCase @Inject constructor(
    private val walletConnectionUseCase: WalletConnectionUseCase
) {
    val identityFlow: Flow<UserIdentity> = walletConnectionUseCase.walletDetails.map { details ->
        when (details) {
            is UserWalletDetails.Connected -> UserIdentity.Authenticated(
                userId = details.publicKey,
                isWeb3 = BuildConfig.IS_WEB3
            )
            else -> UserIdentity.NotAuthenticated
        }
    }

    suspend fun getCurrentIdentity(): UserIdentity {
        return identityFlow.first()
    }

    suspend fun isAuthenticated(): Boolean {
        return getCurrentIdentity() is UserIdentity.Authenticated
    }

    suspend fun getUserIdOrNull(): String? {
        return (getCurrentIdentity() as? UserIdentity.Authenticated)?.userId
    }
}
