package ai.clawly.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ai.clawly.app.domain.repository.WalletRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.walletDataStore: DataStore<Preferences> by preferencesDataStore(name = "wallet_preferences")

@Singleton
class WalletRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WalletRepository {

    private object PreferencesKeys {
        val PUBLIC_KEY = stringPreferencesKey("wallet_public_key")
        val ACCOUNT_LABEL = stringPreferencesKey("wallet_account_label")
        val AUTH_TOKEN = stringPreferencesKey("wallet_auth_token")
        val CREDITS = intPreferencesKey("wallet_credits")
    }

    override val publicKeyFlow: Flow<String> = context.walletDataStore.data
        .map { preferences -> preferences[PreferencesKeys.PUBLIC_KEY] ?: "" }

    override val accountLabelFlow: Flow<String> = context.walletDataStore.data
        .map { preferences -> preferences[PreferencesKeys.ACCOUNT_LABEL] ?: "" }

    override val authTokenFlow: Flow<String> = context.walletDataStore.data
        .map { preferences -> preferences[PreferencesKeys.AUTH_TOKEN] ?: "" }

    override val creditsFlow: Flow<Int> = context.walletDataStore.data
        .map { preferences -> preferences[PreferencesKeys.CREDITS] ?: 0 }

    override suspend fun updateWalletDetails(pubKey: String, accountLabel: String, token: String) {
        context.walletDataStore.edit { preferences ->
            preferences[PreferencesKeys.PUBLIC_KEY] = pubKey
            preferences[PreferencesKeys.ACCOUNT_LABEL] = accountLabel
            preferences[PreferencesKeys.AUTH_TOKEN] = token
        }
    }

    override suspend fun clearWalletDetails() {
        context.walletDataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.PUBLIC_KEY)
            preferences.remove(PreferencesKeys.ACCOUNT_LABEL)
            preferences.remove(PreferencesKeys.AUTH_TOKEN)
            preferences.remove(PreferencesKeys.CREDITS)
        }
    }

    override suspend fun setCredits(credits: Int) {
        context.walletDataStore.edit { preferences ->
            preferences[PreferencesKeys.CREDITS] = credits
        }
    }

    override suspend fun deductCredit() {
        context.walletDataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.CREDITS] ?: 0
            if (current > 0) {
                preferences[PreferencesKeys.CREDITS] = current - 1
            }
        }
    }
}
