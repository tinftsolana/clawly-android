package ai.clawly.app.navigation

import ai.clawly.app.domain.model.PendingMessageHolder
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavHostViewModel @Inject constructor(
    private val pendingMessageHolder: PendingMessageHolder
) : ViewModel() {

    fun setPendingMessage(message: String) {
        pendingMessageHolder.setPendingMessage(message)
    }
}
