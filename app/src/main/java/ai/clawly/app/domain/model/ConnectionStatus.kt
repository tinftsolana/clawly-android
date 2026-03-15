package ai.clawly.app.domain.model

/**
 * Represents the WebSocket connection status to the gateway
 */
sealed class ConnectionStatus {
    data object Online : ConnectionStatus()
    data object Offline : ConnectionStatus()
    data object Connecting : ConnectionStatus()
    data object Paused : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()

    val isOnline: Boolean get() = this is Online
    val isConnecting: Boolean get() = this is Connecting
    val isError: Boolean get() = this is Error
    val isPaused: Boolean get() = this is Paused
}
