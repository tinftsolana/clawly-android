package ai.clawly.app.presentation.chat

data class SolanaSignRequestUi(
    val requestId: String,
    val unsignedTxBase64: String,
    val walletAddress: String? = null,
    val fromWallet: String? = null,
    val toWallet: String? = null,
    val txHash: String? = null,
    val status: String? = null,
    val expiresAt: Long? = null
)

