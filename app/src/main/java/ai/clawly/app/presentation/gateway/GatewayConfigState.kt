package ai.clawly.app.presentation.gateway

import ai.clawly.app.domain.model.ConnectionStatus

/**
 * UI State for the GatewayConfigScreen
 */
data class GatewayConfigState(
    // Input fields
    val gatewayUrl: String = "",
    val gatewayToken: String = "",

    // UI states
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUsingDebugDefaults: Boolean = false,

    // Connection status
    val connectionStatus: ConnectionStatus = ConnectionStatus.Offline,

    // Validation
    val urlError: String? = null,

    // Error handling
    val showError: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Events emitted by the ViewModel for one-time UI actions
 */
sealed interface GatewayConfigEvent {
    data object NavigateBack : GatewayConfigEvent
    data object SaveSuccess : GatewayConfigEvent
    data class ShowError(val message: String) : GatewayConfigEvent
}
