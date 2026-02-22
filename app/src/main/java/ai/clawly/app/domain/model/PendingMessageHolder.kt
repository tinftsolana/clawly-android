package ai.clawly.app.domain.model

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple holder for pending messages that need to be sent when navigating to chat.
 * Used by Skills screen to send skill creation requests.
 */
@Singleton
class PendingMessageHolder @Inject constructor() {
    private var pendingMessage: String? = null

    fun setPendingMessage(message: String) {
        pendingMessage = message
    }

    fun consumePendingMessage(): String? {
        val message = pendingMessage
        pendingMessage = null
        return message
    }

    fun hasPendingMessage(): Boolean = pendingMessage != null
}
