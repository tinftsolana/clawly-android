package ai.clawly.app

import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.navigation.ClawlyNavHost
import ai.clawly.app.ui.theme.ClawlyColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUiState(
    val isLoading: Boolean = true,
    val showOnboarding: Boolean = false
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferences: GatewayPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        checkOnboardingStatus()
        observeDebugSettings()
    }

    private fun checkOnboardingStatus() {
        viewModelScope.launch {
            val alwaysShow = preferences.alwaysShowOnboarding.first()
            val completed = preferences.onboardingCompleted.first()

            _uiState.value = AppUiState(
                isLoading = false,
                showOnboarding = alwaysShow || !completed
            )
        }
    }

    private fun observeDebugSettings() {
        // Observe changes to "always show onboarding" debug setting
        viewModelScope.launch {
            preferences.alwaysShowOnboarding.collect { alwaysShow ->
                if (alwaysShow) {
                    // If debug setting enabled, show onboarding immediately
                    _uiState.value = _uiState.value.copy(showOnboarding = true)
                } else {
                    // If disabled, check if user completed onboarding
                    val completed = preferences.onboardingCompleted.first()
                    _uiState.value = _uiState.value.copy(showOnboarding = !completed)
                }
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
            _uiState.value = _uiState.value.copy(showOnboarding = false)
        }
    }
}

/**
 * Main entry point for the Clawly chat feature.
 * Can be used as a standalone app or embedded in the existing app.
 */
@Composable
fun ClawlyApp(
    viewModel: AppViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        // Show loading state while checking onboarding status
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ClawlyColors.background)
        )
    } else {
        ClawlyNavHost(
            modifier = Modifier
                .fillMaxSize()
                .background(ClawlyColors.background),
            showOnboarding = uiState.showOnboarding,
            onOnboardingComplete = { viewModel.completeOnboarding() }
        )
    }
}
