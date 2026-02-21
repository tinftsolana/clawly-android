# Web3 Wallet Integration - Complete Guide

A comprehensive guide to implementing Solana wallet connection, signing, authentication, and state management in Android apps using Jetpack Compose and Hilt DI.

---

## Table of Contents

1. [Dependencies](#1-dependencies)
2. [Architecture Overview](#2-architecture-overview)
3. [Data Models](#3-data-models)
4. [Repository Layer](#4-repository-layer)
5. [Use Cases](#5-use-cases)
   - [Wallet Connection State](#51-wallet-connection-state)
   - [Connect Wallet](#52-connect-wallet)
   - [Sign Message & Authentication](#53-sign-message--authentication)
   - [Authenticated API Calls](#54-authenticated-api-calls)
   - [User Identity Abstraction](#55-user-identity-abstraction)
6. [ViewModel Integration Patterns](#6-viewmodel-integration-patterns)
7. [DI Configuration](#7-di-configuration)
8. [Complete Flow Diagrams](#8-complete-flow-diagrams)
9. [Error Handling](#9-error-handling)
10. [Web2/Web3 Build Variants](#10-web2web3-build-variants)

---

## 1. Dependencies

Add these to your `build.gradle.kts`:

```kotlin
// Solana Mobile Wallet Adapter
implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.1.0")

// Solana Web3 & RPC
implementation("com.solanamobile:web3-solana:0.2.5")
implementation("com.solanamobile:rpc-core:0.2.7")

// Base58 encoding (for signature encoding)
implementation("org.sol4k:sol4k:0.5.3")

// DataStore for wallet persistence
implementation("androidx.datastore:datastore-preferences:1.1.4")

// Hilt DI
implementation("com.google.dagger:hilt-android:2.57.2")
kapt("com.google.dagger:hilt-android-compiler:2.57.2")
implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

// Lifecycle & Coroutines
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
```

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         PRESENTATION                            │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────┐  │
│  │ WalletScreen│    │  FeedScreen │    │ Other Screens...    │  │
│  └──────┬──────┘    └──────┬──────┘    └──────────┬──────────┘  │
│         │                  │                       │            │
│  ┌──────▼──────┐    ┌──────▼──────┐    ┌──────────▼──────────┐  │
│  │WalletViewModel   │FeedViewModel │    │  Other ViewModels   │  │
│  └──────┬──────┘    └──────┬──────┘    └──────────┬──────────┘  │
└─────────┼──────────────────┼─────────────────────┼──────────────┘
          │                  │                     │
┌─────────▼──────────────────▼─────────────────────▼──────────────┐
│                         DOMAIN                                   │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              MobileWalletAdapterWalletConnectionUseCase    │  │
│  │                    (Central wallet state)                   │  │
│  └─────────────────────────────┬──────────────────────────────┘  │
│                                │                                 │
│  ┌──────────────┐   ┌─────────▼────────┐   ┌─────────────────┐  │
│  │ConnectWallet │   │SignMessageUseCase│   │AuthenticatedAPI │  │
│  │   UseCase    │   │                  │   │  CallUseCase    │  │
│  └──────┬───────┘   └────────┬─────────┘   └────────┬────────┘  │
└─────────┼────────────────────┼──────────────────────┼────────────┘
          │                    │                      │
┌─────────▼────────────────────▼──────────────────────▼────────────┐
│                           DATA                                    │
│  ┌────────────────────┐    ┌──────────────────────────────────┐  │
│  │  WalletRepository  │    │       ApiRepository              │  │
│  │   (DataStore)      │    │  (Backend API calls)             │  │
│  └────────────────────┘    └──────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. Data Models

### UserWalletDetails.kt

```kotlin
package io.breakout.hackathon.app.domain.model

sealed class UserWalletDetails {
    object NotConnected : UserWalletDetails()

    data class Connected(
        val publicKey: String,
        val accountLabel: String,
        val authToken: String,
    ) : UserWalletDetails()
}
```

**Purpose**: Sealed class representing wallet connection state. Used throughout the app to reactively respond to wallet connection changes.

---

## 4. Repository Layer

### WalletRepository.kt (Interface)

```kotlin
package io.breakout.hackathon.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface WalletRepository {
    val publicKeyFlow: Flow<String>
    val accountLabelFlow: Flow<String>
    val authTokenFlow: Flow<String>

    suspend fun updateWalletDetails(pubKey: String, accountLabel: String, token: String)
    suspend fun clearWalletDetails()
}
```

### WalletRepositoryImpl.kt (DataStore Implementation)

```kotlin
package io.breakout.hackathon.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.breakout.hackathon.app.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wallet_prefs")

@Singleton
class WalletRepositoryImpl @Inject constructor(
    private val context: Context
) : WalletRepository {

    private object PreferencesKeys {
        val PUBLIC_KEY = stringPreferencesKey("public_key")
        val ACCOUNT_LABEL = stringPreferencesKey("account_label")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
    }

    override val publicKeyFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.PUBLIC_KEY] ?: "" }

    override val accountLabelFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.ACCOUNT_LABEL] ?: "" }

    override val authTokenFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.AUTH_TOKEN] ?: "" }

    override suspend fun updateWalletDetails(pubKey: String, accountLabel: String, token: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PUBLIC_KEY] = pubKey
            preferences[PreferencesKeys.ACCOUNT_LABEL] = accountLabel
            preferences[PreferencesKeys.AUTH_TOKEN] = token
        }
    }

    override suspend fun clearWalletDetails() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
```

**Key Points**:
- Uses DataStore for persistent wallet storage
- Exposes Flow-based state for reactive updates
- Stores public key, account label, and MWA auth token (not JWT)

---

## 5. Use Cases

### 5.1 Wallet Connection State

**MobileWalletAdapterWalletConnectionUseCase.kt**

```kotlin
package io.breakout.hackathon.app.domain.usecase

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.breakout.hackathon.app.BuildConfig
import io.breakout.hackathon.app.auth.GoogleUserSessionManager
import io.breakout.hackathon.app.domain.model.UserWalletDetails
import io.breakout.hackathon.app.domain.model.UserWalletDetails.Connected
import io.breakout.hackathon.app.domain.model.UserWalletDetails.NotConnected
import io.breakout.hackathon.app.domain.repository.WalletRepository
import io.breakout.hackathon.app.helper.PreferenceHelper
import io.breakout.hackathon.app.notifications.PushTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MobileWalletAdapterWalletConnectionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val preferenceHelper: PreferenceHelper,
    private val pushTokenManager: PushTokenManager,
    private val googleUserSessionManager: GoogleUserSessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val walletDetails: Flow<UserWalletDetails> = combine(
        walletRepository.publicKeyFlow,
        walletRepository.accountLabelFlow,
        walletRepository.authTokenFlow
    ) { pubKey, label, authToken ->
        if (pubKey.isEmpty() || label.isEmpty() || authToken.isEmpty()) {
            // Clear wallet address when disconnected
            preferenceHelper.walletAddress = ""
            FirebaseCrashlytics.getInstance().setUserId("")

            // For Web2 builds, reset everything and force logout
            if (BuildConfig.IS_WEB2) {
                scope.launch { resetWeb2Session() }
            }

            NotConnected
        } else {
            // Save wallet address and register push token when connected
            preferenceHelper.walletAddress = pubKey
            pushTokenManager.registerCurrentToken(pubKey)

            // Set user identifier for Crashlytics
            FirebaseCrashlytics.getInstance().setUserId(pubKey)
            FirebaseCrashlytics.getInstance().setCustomKey("account_label", label)

            Connected(
                publicKey = pubKey,
                accountLabel = label,
                authToken = authToken
            )
        }
    }

    suspend fun persistConnection(pubKey: String, accountLabel: String, token: String) {
        walletRepository.updateWalletDetails(pubKey, accountLabel, token)
    }

    suspend fun clearConnection() {
        // Clear both wallet auth token and JWT token
        preferenceHelper.jwtToken = ""
        preferenceHelper.isAuthBannerDismissed = false
        preferenceHelper.walletAddress = ""
        walletRepository.clearWalletDetails()

        // For Web2 builds, reset everything and force logout
        if (BuildConfig.IS_WEB2) {
            resetWeb2Session()
        }
    }

    private suspend fun resetWeb2Session() {
        googleUserSessionManager.clearSession()
        preferenceHelper.isOnboardingCompleted = false
        preferenceHelper.isPaywallCompleted = false
    }
}
```

**Key Points**:
- Singleton use case that provides the central wallet state as a `Flow<UserWalletDetails>`
- Combines three DataStore flows into a single reactive state
- Automatically registers push tokens and sets Crashlytics user ID on connection
- Handles cleanup for both Web2 and Web3 variants

---

### 5.2 Connect Wallet

**ConnectWalletUseCase.kt**

```kotlin
package io.breakout.hackathon.app.domain.usecase

import android.net.Uri
import androidx.core.net.toUri
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionParams
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.successPayload
import com.solana.publickey.SolanaPublicKey
import io.breakout.hackathon.app.activityResultSender
import io.breakout.hackathon.app.domain.model.UserWalletDetails.Connected
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import org.sol4k.Base58

const val IDENTITY_URI = "https://candysol.io"
const val IDENTITY_NAME = "Candy AI"
const val ICON_URI = "candy_logo.png"

class ConnectWalletUseCase @Inject constructor(
    private val walletConnectionUseCase: MobileWalletAdapterWalletConnectionUseCase,
) {

    private val walletAdapter = MobileWalletAdapter(
        ConnectionIdentity(
            identityUri = IDENTITY_URI.toUri(),
            iconUri = ICON_URI.toUri(),
            identityName = IDENTITY_NAME
        )
    )

    init {
        walletAdapter.blockchain = Solana.Mainnet
    }

    /**
     * Attempts to restore the auth token from persistent storage
     */
    suspend fun tryToRestoreAuthToken(): Boolean {
        return try {
            val wallet = walletConnectionUseCase.walletDetails.firstOrNull() as? Connected
            val authToken = wallet?.authToken

            if (!authToken.isNullOrEmpty()) {
                walletAdapter.authToken = authToken
                println("Auth token restored successfully: ${authToken.take(10)}...")
                true
            } else {
                println("No auth token to restore")
                walletAdapter.authToken = null
                false
            }
        } catch (e: Exception) {
            println("Failed to restore auth token: ${e.message}")
            walletAdapter.authToken = null
            walletConnectionUseCase.clearConnection()
            false
        }
    }

    /**
     * Signs a message using the connected wallet
     */
    suspend fun signMessage(message: String): ByteArray? {
        val activityResultSender = activityResultSender ?: return null

        // Try to restore auth token, but continue even if it fails
        val tokenRestored = tryToRestoreAuthToken()
        println("Auth token restoration: ${if (tokenRestored) "success" else "failed"}")

        return (walletConnectionUseCase.walletDetails.firstOrNull() as? Connected)?.let { details ->
            val txn = walletAdapter.transact(activityResultSender) { authResult ->
                // Update stored connection details with new auth info
                persistConnection(
                    authResult.accounts.first().publicKey,
                    authResult.accounts.first().accountLabel.orEmpty(),
                    authResult.authToken
                )

                // Sign the message
                signMessages(
                    arrayOf(message.toByteArray()),
                    arrayOf(authResult.accounts.first().publicKey)
                )
            }

            when (txn) {
                is TransactionResult.Success -> {
                    println("Message signed successfully")
                    txn.payload.signedPayloads.firstOrNull()
                }
                is TransactionResult.Failure -> {
                    println("SignMessage failed: ${txn.message}")
                    null
                }
                is TransactionResult.NoWalletFound -> {
                    println("No wallet found for signing")
                    null
                }
            }
        }
    }

    /**
     * Initiates wallet connection flow
     */
    suspend fun connect(): Boolean {
        val activityResultSender = activityResultSender ?: return false

        // Reset adapter state before attempting to connect
        try {
            walletAdapter.authToken = null
            println("Reset wallet adapter state before connection")
        } catch (e: Exception) {
            println("Warning: Could not reset adapter state: ${e.message}")
        }

        return when (val result = walletAdapter.connect(activityResultSender)) {
            is TransactionResult.Success -> {
                val accounts = result.authResult.accounts.firstOrNull() ?: return false
                val authToken = result.authResult.authToken
                val address = accounts.publicKey
                val accountLabel = accounts.accountLabel ?: ""

                // Store the auth token in the adapter for future use
                walletAdapter.authToken = authToken

                walletConnectionUseCase.persistConnection(
                    SolanaPublicKey(bytes = address).base58(),
                    accountLabel,
                    authToken
                )

                true
            }
            is TransactionResult.Failure -> {
                println("Wallet connection failed: ${result.message}")
                walletConnectionUseCase.clearConnection()
                false
            }
            is TransactionResult.NoWalletFound -> {
                println("No wallet found")
                walletConnectionUseCase.clearConnection()
                false
            }
        }
    }

    /**
     * Executes a single transaction
     */
    suspend fun executeTransaction(data: ByteArray): String? {
        val activityResultSender = activityResultSender ?: return null

        return (walletConnectionUseCase.walletDetails.firstOrNull() as? Connected)?.let { details ->
            val txn = walletAdapter.transact(activityResultSender) { authResult ->
                persistConnection(
                    authResult.accounts.first().publicKey,
                    authResult.accounts.first().accountLabel.orEmpty(),
                    authResult.authToken
                )

                signAndSendTransactions(
                    arrayOf(data),
                    TransactionParams(
                        minContextSlot = 10,
                        commitment = null,
                        skipPreflight = null,
                        maxRetries = null,
                        waitForCommitmentToSendNextTransaction = null
                    )
                )
            }

            when (txn) {
                is TransactionResult.Success -> {
                    val txSignatureBytes = txn.successPayload?.signatures?.first()
                    txSignatureBytes?.let { Base58.encode(it) }
                }
                else -> null
            }
        }
    }

    /**
     * Executes multiple transactions
     */
    suspend fun executeTransactions(data: List<IntArray>): Boolean {
        val activityResultSender = activityResultSender ?: return false

        return (walletConnectionUseCase.walletDetails.firstOrNull() as? Connected)?.let { details ->
            val txn = walletAdapter.transact(activityResultSender) { authResult ->
                persistConnection(
                    authResult.accounts.first().publicKey,
                    authResult.accounts.first().accountLabel.orEmpty(),
                    authResult.authToken
                )

                val trxs = data.map { it.map { it.toByte() }.toByteArray() }.toTypedArray()
                signAndSendTransactions(trxs)
            }

            when (txn) {
                is TransactionResult.Success -> true
                else -> false
            }
        } ?: false
    }

    suspend fun persistConnection(pubKey: ByteArray, accountLabel: String, token: String) {
        persistConnection(SolanaPublicKey(pubKey), accountLabel, token)
    }

    private suspend fun persistConnection(
        pubKey: SolanaPublicKey,
        accountLabel: String,
        token: String
    ) {
        walletConnectionUseCase.persistConnection(pubKey.base58(), accountLabel, token)
    }

    /**
     * Clears local state without interacting with the wallet app
     */
    suspend fun clear(disconnected: () -> Unit = {}) {
        walletAdapter.authToken = null
        walletConnectionUseCase.clearConnection()
        delay(500L)
        disconnected()
    }

    /**
     * Disconnects from the wallet app and clears local state
     */
    suspend fun disconnect(disconnected: () -> Unit = {}) {
        val activityResultSender = activityResultSender
        val connection = walletConnectionUseCase.walletDetails.firstOrNull()

        if (connection is Connected && activityResultSender != null) {
            try {
                println("Attempting to disconnect wallet adapter")
                walletAdapter.disconnect(activityResultSender)
                walletAdapter.authToken = null
                walletConnectionUseCase.clearConnection()
                println("Wallet disconnected successfully")
            } catch (e: Exception) {
                println("Error during wallet disconnect: ${e.message}")
                walletAdapter.authToken = null
                walletConnectionUseCase.clearConnection()
            }
        } else {
            println("No active connection to disconnect, clearing local state")
            walletAdapter.authToken = null
            walletConnectionUseCase.clearConnection()
        }

        delay(1000L)
        disconnected()
    }

    /**
     * Force resets the adapter state
     */
    suspend fun resetAdapter() {
        try {
            walletAdapter.authToken = null
            walletConnectionUseCase.clearConnection()
            println("Wallet adapter reset successfully")
        } catch (e: Exception) {
            println("Error resetting wallet adapter: ${e.message}")
        }
    }
}
```

**Key Points**:
- Uses `MobileWalletAdapter` with `ConnectionIdentity` for app identification
- `activityResultSender` is a global variable set in `MainActivity` (see Section 8)
- Auth token restoration enables session persistence across app restarts
- All wallet operations return `TransactionResult` sealed class for handling success/failure

---

### 5.3 Sign Message & Authentication

**SignMessageUseCase.kt**

```kotlin
package io.breakout.hackathon.app.domain.usecase

import android.content.Context
import io.breakout.hackathon.app.data.model.SignMessageRequest
import io.breakout.hackathon.app.data.model.VerifySignatureRequest
import io.breakout.hackathon.app.data.model.AuthResponse
import io.breakout.hackathon.app.data.repository.ApiRepository
import io.breakout.hackathon.app.helper.PreferenceHelper
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import org.sol4k.Base58

@Singleton
class SignMessageUseCase @Inject constructor(
    private val apiRepository: ApiRepository,
    private val preferenceHelper: PreferenceHelper,
    private val connectWalletUseCase: ConnectWalletUseCase
) {

    /**
     * Complete 3-step authentication flow:
     * 1. Request message from backend (with nonce)
     * 2. Sign message using wallet
     * 3. Verify signature and get JWT token
     */
    suspend fun signMessage(walletAddress: String, context: Context): AuthResponse {
        // Step 1: Get message to sign from backend
        val signMessageResponse = apiRepository.signMessage(
            SignMessageRequest(
                walletAddress = walletAddress,
                domain = "https://candysol.io/"
            )
        )

        // Step 2: Sign the message using Mobile Wallet Adapter
        val signature = signMessageWithWallet(signMessageResponse.message, context)
        delay(300L)

        // Step 3: Verify signature and get JWT token
        val authResponse = apiRepository.verifySignature(
            VerifySignatureRequest(
                walletAddress = walletAddress,
                signature = signature,
                nonce = signMessageResponse.nonce
            )
        )

        // Store JWT token for authenticated API calls
        preferenceHelper.jwtToken = authResponse.token

        return authResponse
    }

    private suspend fun signMessageWithWallet(message: String, context: Context): String {
        val byteArray = connectWalletUseCase.signMessage(message)
            ?: throw IllegalStateException("Failed to sign message")

        return Base58.encode(byteArray)
    }

    fun clearAuthToken() {
        preferenceHelper.jwtToken = ""
        preferenceHelper.isAuthBannerDismissed = false
    }

    fun hasValidToken(): Boolean {
        return preferenceHelper.jwtToken.isNotEmpty()
    }

    fun getToken(): String {
        return preferenceHelper.jwtToken
    }
}
```

**Authentication Flow**:
```
┌─────────┐     ┌─────────┐     ┌────────────┐     ┌─────────┐
│  App    │     │ Backend │     │   Wallet   │     │ Backend │
└────┬────┘     └────┬────┘     └─────┬──────┘     └────┬────┘
     │               │                │                 │
     │ 1. Request    │                │                 │
     │   message     │                │                 │
     ├──────────────►│                │                 │
     │               │                │                 │
     │ 2. Message +  │                │                 │
     │    nonce      │                │                 │
     │◄──────────────┤                │                 │
     │               │                │                 │
     │ 3. Sign       │                │                 │
     │   message     │                │                 │
     ├──────────────────────────────►│                 │
     │               │                │                 │
     │ 4. Signature  │                │                 │
     │◄──────────────────────────────┤                 │
     │               │                │                 │
     │ 5. Verify signature                              │
     │    (wallet + signature + nonce)                  │
     ├─────────────────────────────────────────────────►│
     │               │                │                 │
     │ 6. JWT token                                     │
     │◄─────────────────────────────────────────────────┤
     │               │                │                 │
```

---

### 5.4 Authenticated API Calls

**AuthenticatedApiCallUseCase.kt**

```kotlin
package io.breakout.hackathon.app.domain.usecase

import android.content.Context
import io.breakout.hackathon.app.data.repository.MintTokenException
import io.breakout.hackathon.app.helper.PreferenceHelper
import io.breakout.hackathon.app.helper.WalletUtils
import javax.inject.Inject

private const val ERROR = "invalidTokenOrWalletMismatch"

/**
 * Use case to handle authenticated API calls with automatic token refresh on 401 errors
 *
 * This handles the common pattern of:
 * 1. Checking if JWT token exists
 * 2. Signing message if needed
 * 3. Making the API call
 * 4. Catching token validation errors and retrying with fresh signature
 *
 * Usage:
 * ```
 * val result = authenticatedApiCallUseCase(
 *     context = context,
 *     walletAddress = walletAddress
 * ) {
 *     apiRepository.mintVideo(request)
 * }
 * ```
 */
class AuthenticatedApiCallUseCase @Inject constructor(
    private val signMessageUseCase: SignMessageUseCase,
    private val preferenceHelper: PreferenceHelper
) {
    /**
     * Executes an authenticated API call with automatic token refresh
     *
     * @param context Android context for signing message
     * @param walletAddress User's wallet address
     * @param apiCall Suspend lambda that performs the actual API call
     * @return Result of the API call
     * @throws Exception if the API call fails after retry
     */
    suspend operator fun <T> invoke(
        context: Context,
        walletAddress: String,
        apiCall: suspend () -> T
    ): T {
        val isSolana = WalletUtils.isSolanaAddress(walletAddress)

        // Check if JWT token exists, if not, authenticate first
        if (!signMessageUseCase.hasValidToken() && isSolana) {
            signMessageUseCase.signMessage(walletAddress, context)
        }

        return try {
            // First attempt
            apiCall()
        } catch (e: MintTokenException) {
            // Handle invalid token error
            if (e.reason == ERROR) {
                // Clear the token and retry with fresh signature
                preferenceHelper.jwtToken = ""
                signMessageUseCase.signMessage(walletAddress, context)

                // Retry the API call
                apiCall()
            } else {
                // Re-throw if it's a different error
                throw e
            }
        }
    }
}
```

**Key Points**:
- Wraps any API call with automatic JWT token handling
- Auto-signs if no token exists
- Auto-refreshes on token expiration (401 errors)
- Uses Kotlin's `operator fun invoke` for clean call syntax

---

### 5.5 User Identity Abstraction

**GetUserIdentityUseCase.kt**

```kotlin
package io.breakout.hackathon.app.domain.usecase

import io.breakout.hackathon.app.BuildConfig
import io.breakout.hackathon.app.domain.model.UserWalletDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * UseCase for getting the current user's identity.
 *
 * For Web3: Returns the connected wallet address
 * For Web2: Returns the Google Sign-In user ID (if available)
 *
 * Usage:
 * ```
 * // As Flow
 * getUserIdentityUseCase.identityFlow.collect { identity ->
 *     when (identity) {
 *         is UserIdentity.Authenticated -> // use identity.userId
 *         is UserIdentity.NotAuthenticated -> // prompt login
 *     }
 * }
 *
 * // One-shot
 * val identity = getUserIdentityUseCase.getCurrentIdentity()
 * ```
 */
class GetUserIdentityUseCase @Inject constructor(
    private val walletConnectionUseCase: MobileWalletAdapterWalletConnectionUseCase
) {

    /**
     * Flow of current user identity
     */
    val identityFlow: Flow<UserIdentity> = walletConnectionUseCase.walletDetails.map { details ->
        when (details) {
            is UserWalletDetails.Connected -> UserIdentity.Authenticated(
                userId = details.publicKey,
                isWeb3 = !BuildConfig.IS_WEB2
            )
            else -> UserIdentity.NotAuthenticated
        }
    }

    /**
     * Get current identity (one-shot, suspending)
     */
    suspend fun getCurrentIdentity(): UserIdentity {
        return identityFlow.first()
    }

    /**
     * Check if user is authenticated
     */
    suspend fun isAuthenticated(): Boolean {
        return getCurrentIdentity() is UserIdentity.Authenticated
    }

    /**
     * Get user ID or null if not authenticated
     */
    suspend fun getUserIdOrNull(): String? {
        return (getCurrentIdentity() as? UserIdentity.Authenticated)?.userId
    }
}

sealed class UserIdentity {
    data class Authenticated(
        val userId: String,
        val isWeb3: Boolean
    ) : UserIdentity()

    data object NotAuthenticated : UserIdentity()
}
```

**Purpose**: Provides a unified identity abstraction that works for both Web2 (Google auth) and Web3 (wallet) variants.

---

## 6. ViewModel Integration Patterns

### Coroutine Extensions

**CoroutineExtensions.kt**

```kotlin
package io.breakout.hackathon.app.helper

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

val defaultCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    print(throwable)
}

/**
 * Safe launch that automatically handles exceptions
 */
inline fun CoroutineScope.safeLaunch(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    coroutineExceptionHandler: CoroutineExceptionHandler = defaultCoroutineExceptionHandler,
    crossinline suspendedBlock: suspend CoroutineScope.() -> Unit
): Job {
    val launchContext = coroutineContext.plus(coroutineExceptionHandler)
    return this.launch(launchContext) {
        suspendedBlock()
    }
}

/**
 * Safe flow collection in a coroutine scope
 */
inline fun <T> Flow<T>.safeLaunchIn(
    scope: CoroutineScope,
    crossinline action: (T) -> Unit = {}
): Job {
    return safeLaunchIn(scope, defaultCoroutineExceptionHandler, action)
}

inline fun <T> Flow<T>.safeLaunchIn(
    scope: CoroutineScope,
    coroutineExceptionHandler: CoroutineExceptionHandler,
    crossinline action: (T) -> Unit = {}
): Job {
    return scope.safeLaunch(coroutineExceptionHandler = coroutineExceptionHandler) {
        this@safeLaunchIn.collect { value -> action(value) }
    }
}
```

### ViewModel Example: WalletViewModel

```kotlin
@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletConnectionUseCase: MobileWalletAdapterWalletConnectionUseCase,
    private val connectWalletUseCase: ConnectWalletUseCase,
    private val signMessageUseCase: SignMessageUseCase,
    private val apiRepository: ApiRepository,
    private val analyticsTracker: AnalyticsTracker,
    val preferenceHelper: PreferenceHelper,
) : ViewModel() {

    private val _state = MutableStateFlow(WalletState())
    val state: StateFlow<WalletState> = _state.asStateFlow()

    init {
        observeWalletConnection()
    }

    /**
     * Pattern: Observe wallet state using safeLaunchIn
     */
    private fun observeWalletConnection() {
        walletConnectionUseCase.walletDetails
            .onEach { walletDetails ->
                when (walletDetails) {
                    is UserWalletDetails.Connected -> {
                        _state.update { state ->
                            state.copy(
                                walletAddress = walletDetails.publicKey,
                                isWalletConnected = true,
                                shortenAddress = walletDetails.publicKey.take(6) +
                                    "..." + walletDetails.publicKey.takeLast(4)
                            )
                        }
                        fetchUserInfo()
                    }
                    is UserWalletDetails.NotConnected -> {
                        _state.update { state ->
                            state.copy(
                                walletAddress = "",
                                isWalletConnected = false,
                            )
                        }
                    }
                }
            }
            .safeLaunchIn(viewModelScope)
    }

    /**
     * Pattern: Connect wallet
     */
    fun connectWallet() {
        _state.update { it.copy(isConnecting = true, errorMessage = null) }
        viewModelScope.safeLaunch {
            connectWalletUseCase.connect()
        }
    }

    /**
     * Pattern: Disconnect wallet
     */
    fun disconnectWallet() {
        viewModelScope.launch {
            connectWalletUseCase.clear()
        }
    }

    /**
     * Pattern: Authenticate wallet (sign message for JWT)
     */
    fun authenticateWallet(context: Context) {
        if (state.value.walletAddress.isEmpty()) return

        _state.update { it.copy(isAuthenticating = true) }

        viewModelScope.launch {
            try {
                signMessageUseCase.signMessage(state.value.walletAddress, context)
                _state.update {
                    it.copy(
                        isAuthenticating = false,
                        isAuthenticated = true
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isAuthenticating = false,
                        errorMessage = "Authentication failed: ${e.message}"
                    )
                }
            }
        }
    }
}
```

### ViewModel Example: FeedViewModel (with AuthenticatedApiCallUseCase)

```kotlin
@HiltViewModel
class FeedViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiRepository: ApiRepository,
    private val walletConnectionUseCase: MobileWalletAdapterWalletConnectionUseCase,
    private val authenticatedApiCallUseCase: AuthenticatedApiCallUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    init {
        observeWalletConnection()
    }

    /**
     * Pattern: Using authenticatedApiCallUseCase for protected endpoints
     */
    fun mintFromFeed(creationId: String) {
        if (!_state.value.isWalletConnected) {
            _state.update { it.copy(showWalletConnectionDialog = true) }
            return
        }

        _state.update { it.copy(isMinting = true) }

        viewModelScope.safeLaunch {
            try {
                val wallet = (walletConnectionUseCase.walletDetails.firstOrNull()
                    as? UserWalletDetails.Connected)
                    ?: throw IllegalStateException("Wallet not connected")

                // Automatically handles JWT token acquisition/refresh
                authenticatedApiCallUseCase(
                    context = context,
                    walletAddress = wallet.publicKey
                ) {
                    apiRepository.mintFromFeed(creationId, wallet.publicKey)
                }

                _state.update { it.copy(isMinting = false, mintSuccess = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isMinting = false, errorMessage = "Failed to mint") }
            }
        }
    }

    /**
     * Pattern: One-time wallet access using firstOrNull()
     */
    fun loadFeed() {
        viewModelScope.safeLaunch {
            try {
                val currentWallet = (walletConnectionUseCase.walletDetails.firstOrNull()
                    as? UserWalletDetails.Connected)?.publicKey

                val response = apiRepository.getFeed(
                    currentWallet = currentWallet,
                    limit = 25
                )

                _state.update { it.copy(feedItems = response.items) }
            } catch (e: Exception) {
                _state.update { it.copy(isError = true) }
            }
        }
    }
}
```

---

## 7. DI Configuration

### RepositoryModule.kt

```kotlin
package io.breakout.hackathon.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.breakout.hackathon.app.data.repository.WalletRepositoryImpl
import io.breakout.hackathon.app.domain.repository.WalletRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindWalletRepository(
        walletRepositoryImpl: WalletRepositoryImpl
    ): WalletRepository
}
```

### AppModule.kt (Context Provider)

```kotlin
package io.breakout.hackathon.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context
}
```

---

## 8. Complete Flow Diagrams

### ActivityResultSender Setup (MainActivity.kt)

```kotlin
package io.breakout.hackathon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import dagger.hilt.android.AndroidEntryPoint

// Global variable for wallet adapter access
var activityResultSender: ActivityResultSender? = null

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ActivityResultSender for Mobile Wallet Adapter
        activityResultSender = ActivityResultSender(this)

        // ... rest of setup
    }
}
```

**Why Global Variable?**
- `ActivityResultSender` requires an `Activity` reference
- Use cases are singletons injected by Hilt (no Activity access)
- Global variable bridges the gap between DI and Activity lifecycle

### Complete Connection Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER TAPS CONNECT                        │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      ViewModel.connectWallet()                  │
│  viewModelScope.safeLaunch {                                    │
│      connectWalletUseCase.connect()                             │
│  }                                                              │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   ConnectWalletUseCase.connect()                │
│  1. Reset adapter state (walletAdapter.authToken = null)        │
│  2. Call walletAdapter.connect(activityResultSender)            │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Mobile Wallet Adapter                        │
│  - Opens wallet app (Phantom, Solflare, etc.)                   │
│  - User approves connection                                     │
│  - Returns TransactionResult.Success with:                      │
│    - accounts[0].publicKey (ByteArray)                          │
│    - accounts[0].accountLabel (String?)                         │
│    - authToken (String - MWA session token)                     │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                 ConnectWalletUseCase (continued)                │
│  3. Convert publicKey to Base58: SolanaPublicKey(bytes).base58()│
│  4. Store authToken in adapter: walletAdapter.authToken = token │
│  5. Persist: walletConnectionUseCase.persistConnection(...)     │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│           MobileWalletAdapterWalletConnectionUseCase            │
│  persistConnection() -> walletRepository.updateWalletDetails()  │
│                                                                 │
│  DataStore update triggers Flow emission                        │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│              walletDetails Flow emits Connected(...)            │
│                                                                 │
│  All observing ViewModels receive the update:                   │
│  - WalletViewModel                                              │
│  - FeedViewModel                                                │
│  - etc.                                                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 9. Error Handling

### TransactionResult Handling

```kotlin
when (val result = walletAdapter.connect(activityResultSender)) {
    is TransactionResult.Success -> {
        // Connection successful
        // Access: result.authResult.accounts, result.authResult.authToken
    }
    is TransactionResult.Failure -> {
        // User cancelled or error occurred
        // Access: result.message
        println("Wallet connection failed: ${result.message}")
    }
    is TransactionResult.NoWalletFound -> {
        // No compatible wallet app installed
        // Show install wallet prompt
    }
}
```

### JWT Token Expiration

```kotlin
class AuthenticatedApiCallUseCase @Inject constructor(
    private val signMessageUseCase: SignMessageUseCase,
    private val preferenceHelper: PreferenceHelper
) {
    suspend operator fun <T> invoke(
        context: Context,
        walletAddress: String,
        apiCall: suspend () -> T
    ): T {
        // Ensure token exists
        if (!signMessageUseCase.hasValidToken()) {
            signMessageUseCase.signMessage(walletAddress, context)
        }

        return try {
            apiCall()
        } catch (e: MintTokenException) {
            if (e.reason == "invalidTokenOrWalletMismatch") {
                // Token expired - refresh and retry
                preferenceHelper.jwtToken = ""
                signMessageUseCase.signMessage(walletAddress, context)
                apiCall()
            } else {
                throw e
            }
        }
    }
}
```

---

## 10. Web2/Web3 Build Variants

### Build Configuration (build.gradle.kts)

```kotlin
android {
    flavorDimensions += "platform"
    productFlavors {
        create("web2") {
            dimension = "platform"
            isDefault = true
            buildConfigField("Boolean", "IS_WEB2", "true")
            buildConfigField("Boolean", "IS_WEB3", "false")
        }
        create("web3") {
            dimension = "platform"
            buildConfigField("Boolean", "IS_WEB2", "false")
            buildConfigField("Boolean", "IS_WEB3", "true")
        }
    }
}
```

### Conditional Rendering in Compose

```kotlin
// DrawIfWeb3 / DrawIfWeb2 utilities
@Composable
fun DrawIfWeb3(content: @Composable () -> Unit) {
    if (!BuildConfig.IS_WEB2) {
        content()
    }
}

@Composable
fun DrawIfWeb2(content: @Composable () -> Unit) {
    if (BuildConfig.IS_WEB2) {
        content()
    }
}

// Usage
DrawIfWeb3 {
    ConnectWalletButton(onClick = { viewModel.connectWallet() })
}

DrawIfWeb2 {
    GoogleSignInButton(onClick = { viewModel.signInWithGoogle() })
}
```

### Build Commands

```bash
# Web3 build (wallet-based auth)
./gradlew :app:assembleWeb3Debug
./gradlew :app:compileWeb3DebugKotlin

# Web2 build (Google auth)
./gradlew :app:assembleWeb2Debug
./gradlew :app:compileWeb2DebugKotlin
```

---

## Quick Reference

### Essential Imports

```kotlin
// Mobile Wallet Adapter
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult

// Solana Public Key
import com.solana.publickey.SolanaPublicKey

// Base58 encoding
import org.sol4k.Base58
```

### Common Patterns Cheat Sheet

```kotlin
// 1. Observe wallet state
walletConnectionUseCase.walletDetails
    .onEach { details -> /* handle Connected/NotConnected */ }
    .safeLaunchIn(viewModelScope)

// 2. Check if connected
val isConnected = walletConnectionUseCase.walletDetails.firstOrNull() is Connected

// 3. Get wallet address
val address = (walletConnectionUseCase.walletDetails.firstOrNull() as? Connected)?.publicKey

// 4. Connect wallet
connectWalletUseCase.connect()

// 5. Disconnect wallet
connectWalletUseCase.disconnect { /* callback */ }

// 6. Authenticated API call
authenticatedApiCallUseCase(context, walletAddress) {
    apiRepository.protectedEndpoint(request)
}

// 7. Manual sign (for custom auth flows)
signMessageUseCase.signMessage(walletAddress, context)
```

---

## Summary

This guide covers the complete Web3 wallet infrastructure:

1. **Dependencies**: MWA, Solana Web3, DataStore, Hilt
2. **Data Layer**: `WalletRepository` with DataStore persistence
3. **Domain Layer**: Use cases for connection, signing, and auth
4. **Presentation Layer**: ViewModel patterns with reactive state
5. **Build Variants**: Web2/Web3 conditional features

The architecture ensures:
- Reactive wallet state across all screens
- Automatic session restoration on app restart
- Automatic JWT token refresh on 401 errors
- Clean separation between wallet (MWA) and API (JWT) authentication
