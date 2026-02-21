package ai.clawly.app.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Error types for chat messages
 */
@Serializable
enum class ChatErrorType {
    None,
    SendFailed,
    Timeout,
    ConnectionError
}

/**
 * Represents a single chat message
 */
@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val errorType: ChatErrorType = ChatErrorType.None,
    val isTyping: Boolean = false,
    val attachments: List<MessageAttachment> = emptyList()
) {
    val isAssistant: Boolean get() = !isUser && !isError
    val hasAttachments: Boolean get() = attachments.isNotEmpty()

    companion object {
        fun userMessage(content: String, attachments: List<MessageAttachment> = emptyList()) = ChatMessage(
            content = content,
            isUser = true,
            attachments = attachments
        )

        fun assistantMessage(content: String) = ChatMessage(
            content = content,
            isUser = false
        )

        fun errorMessage(content: String, errorType: ChatErrorType = ChatErrorType.SendFailed) = ChatMessage(
            content = content,
            isUser = false,
            isError = true,
            errorType = errorType
        )

        fun typingIndicator() = ChatMessage(
            content = "",
            isUser = false,
            isTyping = true
        )
    }
}
