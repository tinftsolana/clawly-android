package ai.clawly.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SignRequestBubbleState {
    abstract val requestId: String
    abstract val fromWallet: String?
    abstract val toWallet: String?
    abstract val txHash: String?

    @Serializable
    @SerialName("ready_to_sign")
    data class ReadyToSign(
        override val requestId: String,
        override val fromWallet: String? = null,
        override val toWallet: String? = null,
        override val txHash: String? = null
    ) : SignRequestBubbleState()

    @Serializable
    @SerialName("signing")
    data class Signing(
        override val requestId: String,
        override val fromWallet: String? = null,
        override val toWallet: String? = null,
        override val txHash: String? = null
    ) : SignRequestBubbleState()

    @Serializable
    @SerialName("success")
    data class Success(
        override val requestId: String,
        override val fromWallet: String? = null,
        override val toWallet: String? = null,
        override val txHash: String? = null,
        val signature: String,
        val status: String
    ) : SignRequestBubbleState()

    @Serializable
    @SerialName("rejected")
    data class Rejected(
        override val requestId: String,
        override val fromWallet: String? = null,
        override val toWallet: String? = null,
        override val txHash: String? = null,
        val reason: String = "Transaction rejected"
    ) : SignRequestBubbleState()
}
