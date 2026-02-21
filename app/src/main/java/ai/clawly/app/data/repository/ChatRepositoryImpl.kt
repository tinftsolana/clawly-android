package ai.clawly.app.data.repository

import ai.clawly.app.data.local.ChatPersistenceService
import ai.clawly.app.domain.model.ChatMessage
import ai.clawly.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ChatRepository using DataStore persistence
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val persistenceService: ChatPersistenceService
) : ChatRepository {

    override val messages: Flow<List<ChatMessage>> = persistenceService.messages

    override suspend fun saveMessages(messages: List<ChatMessage>) {
        persistenceService.saveMessages(messages)
    }

    override suspend fun loadMessages(): List<ChatMessage> {
        return persistenceService.loadMessages()
    }

    override suspend fun clearMessages() {
        persistenceService.clearMessages()
    }

    override suspend fun addMessage(message: ChatMessage) {
        persistenceService.addMessage(message)
    }

    override suspend fun removeLastMessage() {
        persistenceService.removeLastMessage()
    }
}
