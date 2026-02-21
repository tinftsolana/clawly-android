package ai.clawly.app.data.local

import ai.clawly.app.domain.model.ChatMessage
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chatDataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_persistence")

/**
 * Service for persisting chat messages to DataStore
 */
@Singleton
class ChatPersistenceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.chatDataStore
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val KEY_MESSAGES = stringPreferencesKey("chat_messages")
        private const val MAX_MESSAGES = 100
    }

    /**
     * Flow of all persisted messages
     */
    val messages: Flow<List<ChatMessage>> = dataStore.data.map { prefs ->
        val messagesJson = prefs[KEY_MESSAGES] ?: "[]"
        try {
            json.decodeFromString<List<ChatMessage>>(messagesJson)
        } catch (e: Exception) {
            android.util.Log.e("ChatPersistence", "Failed to load messages", e)
            emptyList()
        }
    }

    /**
     * Save messages to local storage
     * Filters out typing indicators and limits to MAX_MESSAGES
     */
    suspend fun saveMessages(messages: List<ChatMessage>) {
        val messagesToSave = messages
            .filter { !it.isTyping }
            .takeLast(MAX_MESSAGES)

        try {
            val messagesJson = json.encodeToString(messagesToSave)
            dataStore.edit { prefs ->
                prefs[KEY_MESSAGES] = messagesJson
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatPersistence", "Failed to save messages", e)
        }
    }

    /**
     * Load messages from local storage
     */
    suspend fun loadMessages(): List<ChatMessage> {
        return messages.first()
    }

    /**
     * Clear all persisted messages
     */
    suspend fun clearMessages() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_MESSAGES)
        }
    }

    /**
     * Add a single message and persist
     */
    suspend fun addMessage(message: ChatMessage) {
        val current = loadMessages().toMutableList()
        current.add(message)
        saveMessages(current)
    }

    /**
     * Remove the last message (for error recovery)
     */
    suspend fun removeLastMessage() {
        val current = loadMessages().toMutableList()
        if (current.isNotEmpty()) {
            current.removeAt(current.lastIndex)
            saveMessages(current)
        }
    }
}
