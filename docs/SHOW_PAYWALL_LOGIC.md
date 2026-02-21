# Paywall Logic Documentation

This document describes when and how paywalls are shown in the Clawly Android app.

## Build Flavors

The app has two build flavors with different paywall logic:

| Flavor | Auth Method | Paywall Type | Currency |
|--------|-------------|--------------|----------|
| **web2** | Device ID / RevenueCat subscription | `PaywallScreen` | USD (subscription) |
| **web3** | Solana Wallet | `Web3PaywallScreen` | SOL (credits) |

---

## Web3 Paywall Logic

### When Paywall is Shown

#### 1. Chat Screen (`ChatViewModel.validateSendMessage()`)

```
User tries to send message
        ↓
Check: BuildConfig.IS_WEB3?
        ↓ YES
Check: _web3Credits.value > 0?
        ↓ NO (0 credits)
Return: SendValidationResult.ShowPaywall
        ↓
ChatEvent.ShowPaywall emitted
        ↓
Navigate to Web3PaywallRoute
```

**File:** `presentation/chat/ChatViewModel.kt:265-279`

```kotlin
if (BuildConfig.IS_WEB3) {
    val credits = _web3Credits.value
    if (credits <= 0) {
        return SendValidationResult.ShowPaywall
    }
    return SendValidationResult.Allowed
}
```

#### 2. Settings Screen - Buy Credits (`FullSettingsScreen`)

```
User taps "Buy Credits" in WALLET section
        ↓
onNavigateToWeb3Paywall()
        ↓
Navigate to Web3PaywallRoute
```

**File:** `presentation/settings/FullSettingsScreen.kt`

#### 3. After Onboarding (`ClawlyNavHost`)

```
Onboarding complete
        ↓
Check: BuildConfig.IS_WEB3?
        ↓ YES
Navigate to Web3PaywallRoute
```

**File:** `navigation/ClawlyNavHost.kt:57-65`

---

## Web2 Paywall Logic

### When Paywall is Shown

#### 1. Chat Screen - Non-Premium User

```
User tries to send message
        ↓
Check: hasPremiumAccess?
        ↓ NO
Check: isConnected?
        ↓ NO → ShowPaywall
        ↓ YES
Check: userMessageCount >= freeMessageLimit (2)?
        ↓ YES → ShowPaywall
        ↓ NO → Allowed
```

**File:** `presentation/chat/ChatViewModel.kt:309-321`

#### 2. Chat Screen - Premium User (Config Issues)

```
User tries to send message
        ↓
Check: hasPremiumAccess?
        ↓ YES
Check: hasAuthProvider?
        ↓ NO → ShowConfigPrompt (not paywall)
Check: isProvisioning?
        ↓ YES → ShowConfigPrompt
Check: isConnected?
        ↓ NO → ShowConfigPrompt
        ↓ YES → Allowed
```

#### 3. Settings Screen - Set Up Clawly

```
User taps "Set Up Clawly"
        ↓
Check: isPremium?
        ↓ NO → Navigate to PaywallRoute
        ↓ YES → Navigate to AuthProviderRoute
```

**File:** `presentation/settings/FullSettingsScreen.kt:221-227`

---

## Navigation Routes

### Route Selection Logic (`ClawlyNavHost`)

All paywall navigation checks `BuildConfig.IS_WEB3`:

```kotlin
// In ChatScreen composable
onNavigateToPaywall = {
    if (BuildConfig.IS_WEB3) {
        navController.navigate(Web3PaywallRoute)
    } else {
        navController.navigate(PaywallRoute)
    }
}

// In FullSettingsScreen composable
onNavigateToPaywall = {
    if (BuildConfig.IS_WEB3) {
        navController.navigate(Web3PaywallRoute)
    } else {
        navController.navigate(PaywallRoute)
    }
}
```

**File:** `navigation/ClawlyNavHost.kt`

---

## Credits Management (Web3)

### Storage

Credits are stored in DataStore via `WalletRepository`:

```kotlin
// domain/repository/WalletRepository.kt
val creditsFlow: Flow<Int>
suspend fun setCredits(credits: Int)
suspend fun deductCredit()
```

### Credit Flow

```
1. User purchases credits on Web3PaywallScreen
        ↓
2. Web3PaywallViewModel.purchaseCredits()
        ↓
3. web3CreditsUseCase.addCredits(amount)
        ↓
4. WalletRepository.setCredits(newTotal)
        ↓
5. Credits saved to DataStore

When sending message:
1. ChatViewModel checks _web3Credits.value
2. If > 0, allows send
3. After send, deducts 1 credit via web3CreditsUseCase.deductCredit()
```

### Credit Packages

| Package | Credits | Price (SOL) |
|---------|---------|-------------|
| starter | 100 | 0.05 |
| standard | 500 | 0.20 |
| pro | 1000 | 0.35 |
| whale | 5000 | 1.50 |

---

## Key Files

| File | Purpose |
|------|---------|
| `ChatViewModel.kt` | `validateSendMessage()` - main paywall trigger logic |
| `SendValidationResult.kt` | Enum for validation outcomes |
| `ClawlyNavHost.kt` | Route selection (Web3Paywall vs Paywall) |
| `Web3PaywallScreen.kt` | Web3 paywall UI |
| `Web3PaywallViewModel.kt` | Purchase logic, credit addition |
| `Web3CreditsUseCase.kt` | Credit management |
| `WalletRepository.kt` | Credit persistence |
| `FullSettingsScreen.kt` | Settings paywall triggers |

---

## State Flows

### ChatViewModel States Affecting Paywall

```kotlin
data class ChatUiState(
    val hasPremiumAccess: Boolean,      // Web2: subscription status
    val hasAuthProvider: Boolean,        // Is Clawly configured
    val isProvisioning: Boolean,         // Instance still setting up
    val isConnected: Boolean,            // Gateway connection status
    val userMessageCount: Int,           // For free message limit
    // ...
)

// Separate flow for web3
val web3Credits: StateFlow<Int>
```

### WalletUiState (Web3)

```kotlin
data class WalletUiState(
    val walletAddress: String,
    val isWalletConnected: Boolean,
    val credits: Int,                    // Current credit balance
    // ...
)
```

---

## Decision Tree Summary

```
┌─────────────────────────────────────────────────────────────┐
│                    USER SENDS MESSAGE                        │
└─────────────────────────────────────────────────────────────┘
                            │
                 ┌──────────┴──────────┐
                 │  IS_WEB3 build?     │
                 └──────────┬──────────┘
            YES             │             NO
             │              │              │
    ┌────────┴────────┐    │    ┌────────┴────────┐
    │ credits > 0?    │    │    │ hasPremium?     │
    └────────┬────────┘    │    └────────┬────────┘
        YES  │  NO         │        YES  │  NO
         │   │   │         │         │   │   │
    ALLOW│   │PAYWALL      │    ┌────┴───┘   │
         │   │             │    │            │
         │   │             │    │  ┌─────────┴─────────┐
         │   │             │    │  │ connected?        │
         │   │             │    │  └─────────┬─────────┘
         │   │             │    │       YES  │  NO
         │   │             │    │        │   │   │
         │   │             │    │   ┌────┴───┘ PAYWALL
         │   │             │    │   │
         │   │             │    │   │  ┌─────────────────┐
         │   │             │    │   │  │ msgCount < 2?   │
         │   │             │    │   │  └────────┬────────┘
         │   │             │    │   │      YES  │  NO
         │   │             │    │   │       │   │   │
         │   │             │    │   │  ALLOW│   │PAYWALL
         │   │             │    │   │       │   │
         │   │             │    │   │       │   │
    ┌────┴───┴─────────────┴───┴───┴───────┴───┴────┐
    │              CHECK CONFIG (premium)            │
    │  - hasAuthProvider? → ShowConfigPrompt         │
    │  - isProvisioning?  → ShowConfigPrompt         │
    │  - isConnected?     → ShowConfigPrompt         │
    │  - else             → ALLOW                    │
    └────────────────────────────────────────────────┘
```

---

## User Identity (Backend userId)

### How userId is Determined

The `AuthProviderRepositoryImpl.currentUserId` property determines what identifier is sent to the backend (ControlPlaneService):

**File:** `data/repository/AuthProviderRepositoryImpl.kt:44-63`

```
┌─────────────────────────────────────────────────────────┐
│               GET USER ID FOR BACKEND                    │
└─────────────────────────────────────────────────────────┘
                         │
              ┌──────────┴──────────┐
              │  Debug override?    │
              └──────────┬──────────┘
                   YES   │   NO
                    │    │    │
            DEBUG_ID│    │    │
                    │    │    │
              ┌─────┴────┴────┴─────┐
              │    IS_WEB3 build?   │
              └─────────┬───────────┘
                 YES    │    NO
                  │     │     │
         ┌────────┴─────┘     │
         │                    │
   ┌─────┴─────┐        ┌─────┴─────┐
   │  Wallet   │        │  Device   │
   │ connected?│        │ Identity  │
   └─────┬─────┘        └───────────┘
    YES  │  NO
     │   │   │
     │   │   └─── Fallback to Device Identity
     │   │
     └───┴─── Wallet Public Key (address)
```

### Identity by Build Flavor

| Flavor | Primary Identity | Fallback |
|--------|------------------|----------|
| **web2** | Device ID (`DeviceIdentityManager`) | Generated ID |
| **web3** | Wallet Address (`WalletRepository.publicKeyFlow`) | Device ID |

### Key Files

| File | Purpose |
|------|---------|
| `AuthProviderRepositoryImpl.kt` | `currentUserId` property - main identity logic |
| `WalletRepository.kt` | `publicKeyFlow` - wallet address storage |
| `DeviceIdentityManager.kt` | `loadOrCreateIdentity()` - device ID generation |
| `GetUserIdentityUseCase.kt` | Reactive identity flow for UI state |
