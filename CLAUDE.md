# Clawly Android - Agent Instructions

This file contains important context and rules for AI agents working on this codebase.

## Project Overview

Clawly is an AI chat assistant Android app with two build flavors:
- **web2**: Traditional subscription-based (RevenueCat)
- **web3**: Solana wallet-based with credits

## Build Commands

```bash
# Web2 build (subscription auth)
./gradlew :app:assembleWeb2Debug

# Web3 build (wallet auth)
./gradlew :app:assembleWeb3Debug

# Install to device
adb install -r app/build/outputs/apk/web3/debug/app-web3-debug.apk
```

## Key Architecture Decisions

### Build Flavor Checks
Always use `BuildConfig.IS_WEB3` or `BuildConfig.IS_WEB2` for flavor-specific code:
```kotlin
if (BuildConfig.IS_WEB3) {
    // Web3 specific code
} else {
    // Web2 specific code
}
```

### Conditional UI (Compose)
Use utility functions in `ui/util/BuildVariantUtils.kt`:
```kotlin
DrawIfWeb3 {
    // Only rendered in web3 builds
}
DrawIfWeb2 {
    // Only rendered in web2 builds
}
```

### User Identity (Backend userId)
The userId sent to backend differs by build flavor:
- **web3**: Wallet address (publicKey) from `WalletRepository.publicKeyFlow`
- **web2**: Device ID from `DeviceIdentityManager.loadOrCreateIdentity()`

Logic is in `AuthProviderRepositoryImpl.currentUserId`. See `docs/SHOW_PAYWALL_LOGIC.md` for details.

## Important Documentation

- **Paywall Logic**: See `docs/SHOW_PAYWALL_LOGIC.md` for complete paywall flow documentation
- **Gateway Connection**: `GatewayServiceImpl.kt` handles WebSocket connection to gateway

## Key Files by Feature

### Wallet/Credits (Web3)
| File | Purpose |
|------|---------|
| `domain/repository/WalletRepository.kt` | Wallet data interface |
| `data/repository/WalletRepositoryImpl.kt` | DataStore persistence |
| `domain/usecase/WalletConnectionUseCase.kt` | Wallet state management |
| `domain/usecase/ConnectWalletUseCase.kt` | MWA wallet operations |
| `domain/usecase/Web3CreditsUseCase.kt` | Credit management |
| `presentation/wallet/WalletViewModel.kt` | Wallet UI state |

### Paywall
| File | Purpose |
|------|---------|
| `presentation/paywall/PaywallScreen.kt` | Web2 paywall (subscription) |
| `presentation/paywall/Web3PaywallScreen.kt` | Web3 paywall (credits) |
| `presentation/paywall/Web3PaywallViewModel.kt` | Credit purchase logic |

### Chat
| File | Purpose |
|------|---------|
| `presentation/chat/ChatViewModel.kt` | Chat logic + paywall validation |
| `presentation/chat/ChatScreen.kt` | Chat UI |
| `data/remote/gateway/GatewayServiceImpl.kt` | WebSocket gateway |

### Settings
| File | Purpose |
|------|---------|
| `presentation/settings/FullSettingsScreen.kt` | Main settings screen |
| `presentation/settings/SettingsViewModel.kt` | Settings state |

### Navigation
| File | Purpose |
|------|---------|
| `navigation/ClawlyNavHost.kt` | All navigation routes |
| `navigation/ClawlyDestinations.kt` | Route definitions |

## Rules for Modifications

### When Modifying Paywall Logic
1. Update `docs/SHOW_PAYWALL_LOGIC.md` to reflect changes
2. Test both web2 and web3 builds
3. Verify navigation routes in `ClawlyNavHost.kt`

### When Adding Web3-Only Features
1. Wrap with `DrawIfWeb3 { }` or check `BuildConfig.IS_WEB3`
2. Ensure web2 build still compiles and works
3. Add any new wallet-related code to `WalletModule.kt` for DI

### When Modifying Credits
1. Credits are stored via `WalletRepository.creditsFlow`
2. Use `Web3CreditsUseCase` for all credit operations
3. Credits are deducted per message in `ChatViewModel.sendMessage()`

### When Modifying Gateway Connection
1. See `GatewayServiceImpl.kt` for protocol details
2. Protocol version is 3
3. Ping interval is 30s (Ktor built-in)
4. Connection flow: connect → challenge (optional) → auth → online

## Common Patterns

### Hilt Injection
All ViewModels use `@HiltViewModel` with constructor injection:
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val someUseCase: SomeUseCase
) : ViewModel()
```

### State Management
Use `StateFlow` for UI state:
```kotlin
private val _uiState = MutableStateFlow(MyUiState())
val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()
```

### Navigation
Use type-safe navigation with `@Serializable` objects:
```kotlin
@Serializable
object MyRoute

// In NavHost
composable<MyRoute> { MyScreen() }

// Navigate
navController.navigate(MyRoute)
```

## Testing Checklist

When making changes, verify:
- [ ] Web2Debug builds successfully
- [ ] Web3Debug builds successfully
- [ ] Paywall shows correctly for each flavor
- [ ] Credits work in web3 (purchase, deduct, display)
- [ ] Settings shows correct sections per flavor
- [ ] Gateway connection works
