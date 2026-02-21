package ai.clawly.app.domain.model

/**
 * Represents the streaming state of an assistant response
 */
sealed class StreamingState {
    data object Idle : StreamingState()
    data class Streaming(val partialContent: String) : StreamingState()
    data object Complete : StreamingState()

    val isStreaming: Boolean get() = this is Streaming
    val isComplete: Boolean get() = this is Complete
    val isIdle: Boolean get() = this is Idle
}
