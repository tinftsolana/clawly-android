package ai.clawly.app.presentation.chat

/**
 * One-time events from ChatViewModel
 */
sealed class ChatEvent {
    data object ShowPaywall : ChatEvent()
    data object ShowLogin : ChatEvent()
    data object ShowConfigPrompt : ChatEvent()
    data object ShowProviderSetup : ChatEvent()
    data object ShowGatewayResolvingAlert : ChatEvent()
    data class ShowError(val message: String) : ChatEvent()
    data class ShowToast(val message: String) : ChatEvent()
    data class ShowSignSuccess(val signature: String, val status: String) : ChatEvent()
    data object ScrollToBottom : ChatEvent()
    data object MessageSent : ChatEvent()
    data class SpeakText(val text: String) : ChatEvent()
}
