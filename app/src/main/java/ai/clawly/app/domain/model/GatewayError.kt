package ai.clawly.app.domain.model

/**
 * Errors that can occur during gateway operations
 */
sealed class GatewayError : Exception() {
    data object NotConnected : GatewayError() {
        override val message = "Not connected to gateway"
    }

    data object EncodingFailed : GatewayError() {
        override val message = "Failed to encode message"
    }

    data class RequestFailed(override val message: String) : GatewayError()

    data class NotPaired(val requestId: String?) : GatewayError() {
        override val message: String
            get() = if (!requestId.isNullOrEmpty()) {
                "Pairing required (requestId: $requestId)"
            } else {
                "Pairing required"
            }
    }

    data object DeviceIdentityRequired : GatewayError() {
        override val message = "Device identity required"
    }

    data object Timeout : GatewayError() {
        override val message = "Request timed out"
    }
}
