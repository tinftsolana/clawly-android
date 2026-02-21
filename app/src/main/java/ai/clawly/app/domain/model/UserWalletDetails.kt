package ai.clawly.app.domain.model

sealed class UserWalletDetails {
    object NotConnected : UserWalletDetails()

    data class Connected(
        val publicKey: String,
        val accountLabel: String,
        val authToken: String
    ) : UserWalletDetails()
}
