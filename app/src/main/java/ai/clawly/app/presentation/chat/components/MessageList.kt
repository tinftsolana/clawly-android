package ai.clawly.app.presentation.chat.components

import ai.clawly.app.domain.model.ChatMessage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    isAssistantTyping: Boolean,
    streamingContent: String,
    onRetry: () -> Unit,
    onReconnect: () -> Unit,
    onTopUpCreditsClick: () -> Unit,
    onSignRequest: () -> Unit,
    onRejectSignRequest: () -> Unit,
    onOpenSolscan: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        reverseLayout = false
    ) {
        items(
            items = messages,
            key = { it.id }
        ) { message ->
            when {
                message.isSignRequest -> {
                    SignRequestBubble(
                        state = message.signRequestState!!,
                        onSign = onSignRequest,
                        onReject = onRejectSignRequest,
                        onOpenSolscan = onOpenSolscan
                    )
                }
                message.isError -> {
                    ErrorMessageBubble(
                        message = message,
                        onRetry = onRetry,
                        onReconnect = onReconnect
                    )
                }
                else -> {
                    MessageBubble(
                        message = message,
                        onTopUpCreditsClick = onTopUpCreditsClick
                    )
                }
            }
        }

        // Typing indicator
        if (isAssistantTyping) {
            item(key = "typing_indicator") {
                TypingIndicator(streamingContent = streamingContent)
            }
        }

        // Bottom spacer for better scroll experience
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun LazyListState.lastIndex(messagesSize: Int, isTyping: Boolean): Int {
    // messages + typing indicator (if present) + bottom spacer
    return messagesSize + (if (isTyping) 1 else 0) + 1 - 1
}

/**
 * Auto-scroll to bottom:
 * - On initial load (instant)
 * - When new messages arrive
 * - When typing state changes
 * - When keyboard opens (via imeVisible param)
 */
@Composable
fun rememberAutoScrollState(
    messages: List<ChatMessage>,
    isTyping: Boolean,
    imeVisible: Boolean = false
): LazyListState {
    val listState = rememberLazyListState()
    val lastIdx = listState.lastIndex(messages.size, isTyping)

    // Initial scroll - instant, no animation
    LaunchedEffect(Unit) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(lastIdx)
        }
    }

    // Scroll when messages change or typing changes
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            // Small delay to let layout settle
            delay(50)
            listState.animateScrollToItem(lastIdx)
        }
    }

    // Scroll when keyboard opens or closes
    LaunchedEffect(imeVisible) {
        if (messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(lastIdx)
        }
    }

    return listState
}
