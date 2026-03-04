package ai.clawly.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.solanaAuthDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "solana_auth_preferences"
)

/**
 * DataStore-based preferences for Solana JWT token storage
 */
@Singleton
class SolanaAuthPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.solanaAuthDataStore

    companion object {
        private val KEY_JWT_TOKEN = stringPreferencesKey("solana_jwt_token")
        private val KEY_JWT_EXPIRES_AT = longPreferencesKey("solana_jwt_expires_at")
        private val KEY_AUTHENTICATED_WALLET = stringPreferencesKey("solana_authenticated_wallet")

        // Buffer time before expiry (5 minutes) to refresh proactively
        private const val EXPIRY_BUFFER_MS = 5 * 60 * 1000L
    }

    // JWT Token Flow
    val jwtToken: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_JWT_TOKEN]
    }

    // Expiry timestamp Flow
    val jwtExpiresAt: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[KEY_JWT_EXPIRES_AT]
    }

    // Authenticated wallet address Flow
    val authenticatedWallet: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_AUTHENTICATED_WALLET]
    }

    // Combined flow to check if authenticated
    val isAuthenticated: Flow<Boolean> = dataStore.data.map { prefs ->
        val token = prefs[KEY_JWT_TOKEN]
        val expiresAt = prefs[KEY_JWT_EXPIRES_AT]

        if (token.isNullOrEmpty() || expiresAt == null) {
            false
        } else {
            System.currentTimeMillis() < expiresAt
        }
    }

    /**
     * Save JWT token with expiry
     */
    suspend fun setJwtToken(token: String, expiresAt: Long?, walletAddress: String) {
        dataStore.edit { prefs ->
            prefs[KEY_JWT_TOKEN] = token
            prefs[KEY_JWT_EXPIRES_AT] = expiresAt ?: (System.currentTimeMillis() + 24 * 60 * 60 * 1000) // Default 24h
            prefs[KEY_AUTHENTICATED_WALLET] = walletAddress
        }
    }

    /**
     * Get JWT token synchronously
     */
    suspend fun getJwtTokenSync(): String? {
        return dataStore.data.first()[KEY_JWT_TOKEN]
    }

    /**
     * Get expiry timestamp synchronously
     */
    suspend fun getJwtExpiresAtSync(): Long? {
        return dataStore.data.first()[KEY_JWT_EXPIRES_AT]
    }

    /**
     * Get authenticated wallet address synchronously
     */
    suspend fun getAuthenticatedWalletSync(): String? {
        return dataStore.data.first()[KEY_AUTHENTICATED_WALLET]
    }

    /**
     * Check if token is valid (not expired)
     */
    suspend fun isTokenValid(): Boolean {
        val token = getJwtTokenSync()
        val expiresAt = getJwtExpiresAtSync()

        if (token.isNullOrEmpty() || expiresAt == null) {
            return false
        }

        return System.currentTimeMillis() < expiresAt
    }

    /**
     * Check if token needs refresh (within buffer of expiry)
     */
    suspend fun shouldRefreshToken(): Boolean {
        val expiresAt = getJwtExpiresAtSync() ?: return true
        return System.currentTimeMillis() > (expiresAt - EXPIRY_BUFFER_MS)
    }

    /**
     * Get valid token or null if expired
     */
    suspend fun getValidToken(): String? {
        return if (isTokenValid()) {
            getJwtTokenSync()
        } else {
            null
        }
    }

    /**
     * Clear JWT token (logout)
     */
    suspend fun clearToken() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_JWT_TOKEN)
            prefs.remove(KEY_JWT_EXPIRES_AT)
            prefs.remove(KEY_AUTHENTICATED_WALLET)
        }
    }

    /**
     * Clear all preferences
     */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
