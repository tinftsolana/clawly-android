package ai.clawly.app.domain.repository

import ai.clawly.app.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for chat message persistence
 */
interface ChatRepository {
    /** Flow of all persisted messages */
    val messages: Flow<List<ChatMessage>>

    /** Save messages to local storage */
    suspend fun saveMessages(messages: List<ChatMessage>)

    /** Load messages from local storage */
    suspend fun loadMessages(): List<ChatMessage>

    /** Clear all persisted messages */
    suspend fun clearMessages()

    /** Add a single message */
    suspend fun addMessage(message: ChatMessage)

    /** Remove the last message (for error recovery) */
    suspend fun removeLastMessage()
}
