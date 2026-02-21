package ai.clawly.app.presentation.wallet

data class WalletUiState(
    val walletAddress: String = "",
    val accountLabel: String = "",
    val shortenedAddress: String = "",
    val isWalletConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isDisconnecting: Boolean = false,
    val errorMessage: String? = null,
    val credits: Int = 0
)
