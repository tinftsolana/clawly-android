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
    // reverseLayout = true means index 0 is at the bottom.
    // We feed items in reversed order so newest messages appear at the bottom.
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        reverseLayout = true
    ) {
        // Bottom spacer (appears at top visually in reverse layout, but is index 0)
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Typing indicator (index 1 when present)
        if (isAssistantTyping) {
            item(key = "typing_indicator") {
                TypingIndicator(streamingContent = streamingContent)
            }
        }

        // Messages in reverse order (newest first = bottom of screen)
        items(
            items = messages.asReversed(),
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
    }
}

/**
 * Auto-scroll to bottom (index 0 in reverse layout).
 * Since reverseLayout = true, the list naturally starts at the bottom.
 * We only need to scroll on new messages / typing changes.
 */
@Composable
fun rememberAutoScrollState(
    messages: List<ChatMessage>,
    isTyping: Boolean,
    imeVisible: Boolean = false
): LazyListState {
    val listState = rememberLazyListState()

    // Scroll to bottom (index 0) when new messages arrive or typing changes
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            delay(50)
            listState.animateScrollToItem(0)
        }
    }

    // Scroll when keyboard opens/closes
    LaunchedEffect(imeVisible) {
        if (messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(0)
        }
    }

    return listState
}
