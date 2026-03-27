package ai.clawly.app.navigation

import ai.clawly.app.BuildConfig
import ai.clawly.app.presentation.apikeys.ApiKeysScreen
import ai.clawly.app.presentation.chat.ChatScreen
import ai.clawly.app.presentation.gateway.GatewayConfigScreen
import ai.clawly.app.presentation.login.LoginScreen
import ai.clawly.app.presentation.onboarding.OnboardingScreen
import ai.clawly.app.presentation.paywall.PaywallScreen
import ai.clawly.app.presentation.paywall.PaywallViewModel
import ai.clawly.app.presentation.paywall.Web3PaywallScreen
import ai.clawly.app.presentation.paywall.Web3PaywallViewModel
import ai.clawly.app.presentation.settings.AuthProviderScreen
import ai.clawly.app.presentation.settings.FullSettingsScreen
import ai.clawly.app.presentation.settings.InstanceSetupScreen
import ai.clawly.app.presentation.setupwizard.SetupWizardScreen
import ai.clawly.app.presentation.skills.SkillsScreen
import androidx.navigation.toRoute
import ai.clawly.app.ui.theme.ClawlyColors
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun ClawlyNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Any = ChatRoute,
    showOnboarding: Boolean = false,
    isFirebaseSignedIn: Boolean = true,
    onOnboardingComplete: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    navHostViewModel: NavHostViewModel = hiltViewModel()
) {
    // Remember the initial start destination so recompositions don't reset the NavHost
    val resolvedStart = remember {
        when {
            showOnboarding -> OnboardingRoute
            else -> startDestination
        }
    }

    NavHost(
        navController = navController,
        startDestination = resolvedStart,
        modifier = modifier
    ) {
        // Onboarding
        composable<OnboardingRoute> {
            OnboardingScreen(
                onComplete = {
                    // Mark onboarding as completed so it won't show again
                    onOnboardingComplete()
                    // After onboarding, route based on build flavor
                    if (BuildConfig.IS_WEB3) {
                        navController.navigate(Web3PaywallRoute) {
                            popUpTo(OnboardingRoute) { inclusive = true }
                        }
                    } else {
                        // Web2: Paywall first, then Login after
                        navController.navigate(PaywallRoute) {
                            popUpTo(OnboardingRoute) { inclusive = true }
                        }
                    }
                }
            )
        }

        // Login (Web2 only — Google Sign-In)
        composable<LoginRoute> {
            LoginScreen(
                onSignedIn = {
                    // Pop back to wherever we came from (Settings or start)
                    if (!navController.popBackStack()) {
                        navController.navigate(ChatRoute) {
                            popUpTo(LoginRoute) { inclusive = true }
                        }
                    }
                },
                onDismiss = {
                    if (!navController.popBackStack()) {
                        navController.navigate(ChatRoute) {
                            popUpTo(LoginRoute) { inclusive = true }
                        }
                    }
                }
            )
        }

        // Chat
        composable<ChatRoute> {
            ChatScreen(
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
                },
                onNavigateToPaywall = {
                    // Navigate to appropriate paywall based on build flavor
                    if (BuildConfig.IS_WEB3) {
                        navController.navigate(Web3PaywallRoute)
                    } else {
                        navController.navigate(PaywallRoute)
                    }
                },
                onNavigateToProviderSetup = {
                    navController.navigate(ProviderSetupRoute)
                },
                onNavigateToLogin = {
                    navController.navigate(LoginRoute)
                },
                onNavigateToSetupWizard = {
                    navController.navigate(SetupWizardRoute())
                }
            )
        }

        // Settings (Full version with all sections)
        composable<SettingsRoute> {
            FullSettingsScreen(
                onBackClick = {
                    if (navController.currentBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
                onSignedOut = {
                    // Navigate to login, clearing entire backstack
                    navController.navigate(LoginRoute) {
                        popUpTo(0) { inclusive = true }
                    }
                    onSignedOut()
                },
                onNavigateToLogin = {
                    navController.navigate(LoginRoute)
                },
                onNavigateToSkills = {
                    navController.navigate(SkillsRoute)
                },
                onNavigateToApiKeys = {
                    navController.navigate(ApiKeysRoute)
                },
                onNavigateToPaywall = {
                    // Navigate to appropriate paywall based on build flavor
                    if (BuildConfig.IS_WEB3) {
                        navController.navigate(Web3PaywallRoute)
                    } else {
                        navController.navigate(PaywallRoute)
                    }
                },
                onNavigateToAuthProvider = {
                    navController.navigate(AuthProviderRoute)
                },
                onNavigateToInstanceSetup = {
                    navController.navigate(InstanceSetupRoute)
                },
                onNavigateToGatewayConfig = {
                    navController.navigate(GatewayConfigRoute)
                },
                onNavigateToWeb3Paywall = {
                    navController.navigate(Web3PaywallRoute)
                },
                onNavigateToSetupWizard = { initialPrompt ->
                    navController.navigate(SetupWizardRoute(initialPrompt = initialPrompt))
                }
            )
        }

        // Setup Wizard
        composable<SetupWizardRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SetupWizardRoute>()
            SetupWizardScreen(
                onBackClick = {
                    if (navController.currentBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
                initialPrompt = route.initialPrompt
            )
        }

        // Paywall
        composable<PaywallRoute> {
            val viewModel: PaywallViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val context = LocalContext.current
            val activity = context as? Activity

            LaunchedEffect(uiState.purchaseCompleted) {
                if (uiState.purchaseCompleted) {
                    navController.navigate(ChatRoute) {
                        popUpTo(PaywallRoute) { inclusive = true }
                    }
                }
            }

            PaywallScreen(
                monthlyPrice = uiState.monthlyPrice,
                yearlyPrice = uiState.yearlyPrice,
                isPurchasing = uiState.isPurchasing,
                isRestoring = uiState.isRestoring,
                onSelectPlan = { viewModel.selectPlan(it) },
                onSubscribe = { activity?.let { viewModel.purchaseSelectedProduct(it) } },
                onRestore = { viewModel.restorePurchases() },
                onDismiss = {
                    // Go to chat after paywall (whether purchased or dismissed)
                    navController.navigate(ChatRoute) {
                        popUpTo(PaywallRoute) { inclusive = true }
                    }
                },
                onTermsClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/document/d/1NUAcle14HNFpF8-JsKhEKdVnuXcNRg9uFlWV9uFbIUM/edit?usp=sharing"))
                    context.startActivity(intent)
                },
                onPrivacyClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/document/d/1s6ijRCCVNSvLnlC4andYPRJUUxveskF6c-2Km0sZdYU/edit?usp=sharing"))
                    context.startActivity(intent)
                }
            )
        }

        // Provider Setup (placeholder for AI provider setup)
        composable<ProviderSetupRoute> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ClawlyColors.background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Provider Setup - Coming Soon",
                    color = Color.White
                )
            }
        }

        // Gateway Config
        composable<GatewayConfigRoute> {
            GatewayConfigScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Auth Provider - Hosting type selection
        composable<AuthProviderRoute> {
            AuthProviderScreen(
                onNavigateBack = { navController.popBackStack() },
                onConfigured = {
                    // Self-hosted is configured, go back to settings
                    navController.popBackStack()
                },
                onNavigateToInstanceSetup = {
                    // Managed hosting is ready, navigate to instance setup
                    navController.navigate(InstanceSetupRoute) {
                        // Don't pop auth provider so user can go back
                    }
                },
                onNavigateToPaywall = {
                    if (BuildConfig.IS_WEB3) {
                        navController.navigate(Web3PaywallRoute)
                    } else {
                        navController.navigate(PaywallRoute)
                    }
                }
            )
        }

        // Instance Setup - AI Provider selection
        composable<InstanceSetupRoute> {
            InstanceSetupScreen(
                onNavigateBack = {
                    // Pop back to settings (through auth provider)
                    navController.popBackStack(SettingsRoute, false)
                }
            )
        }

        // Skills
        composable<SkillsRoute> {
            SkillsScreen(
                onBackClick = {
                    if (navController.currentBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
                onNavigateToChat = { message ->
                    // Set pending message before navigating
                    navHostViewModel.setPendingMessage(message)
                    navController.navigate(ChatRoute) {
                        popUpTo(ChatRoute) { inclusive = true }
                    }
                }
            )
        }

        // API Keys
        composable<ApiKeysRoute> {
            ApiKeysScreen(
                onBackClick = {
                    if (navController.currentBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        // Web3 Paywall (credit-based, wallet connection)
        composable<Web3PaywallRoute> {
            val viewModel: Web3PaywallViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val offers by viewModel.offers.collectAsState()
            val isLoadingOffers by viewModel.isLoadingOffers.collectAsState()

            Web3PaywallScreen(
                walletAddress = uiState.walletAddress,
                isWalletConnected = uiState.isWalletConnected,
                isConnecting = uiState.isConnecting,
                isPurchasing = uiState.isPurchasing,
                isLoadingOffers = isLoadingOffers,
                isWaitingForConfirmation = uiState.isWaitingForConfirmation,
                isRestoringCredits = uiState.isRestoringCredits,
                offers = offers,
                selectedPackageId = uiState.selectedPackageId,
                currentCredits = uiState.currentCredits,
                purchaseSuccess = uiState.purchaseSuccess,
                creditsReceived = uiState.creditsReceived,
                onConnectWallet = { viewModel.connectWallet() },
                onSelectPackage = { viewModel.selectPackage(it) },
                onPurchase = { viewModel.purchaseCredits() },
                onRestoreCredits = { viewModel.restoreCredits() },
                onRetryLoadOffers = { viewModel.refreshOffers() },
                onDismiss = {
                    // Go to chat after dismissing
                    navController.navigate(ChatRoute) {
                        popUpTo(Web3PaywallRoute) { inclusive = true }
                    }
                },
                onSuccessDismiss = {
                    // Reset state and navigate to chat after success
                    viewModel.resetPurchaseState()
                    navController.navigate(ChatRoute) {
                        popUpTo(Web3PaywallRoute) { inclusive = true }
                    }
                }
            )
        }
    }
}
