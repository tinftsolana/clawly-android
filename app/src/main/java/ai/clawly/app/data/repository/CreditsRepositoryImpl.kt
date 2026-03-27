package ai.clawly.app.data.repository

import ai.clawly.app.domain.repository.CreditsRepository
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.creditsDataStore: DataStore<Preferences> by preferencesDataStore(name = "web2_credits")

@Singleton
class CreditsRepositoryImpl @Inject constructor(
    private val context: Context
) : CreditsRepository {

    private val dataStore = context.creditsDataStore

    private object Keys {
        val CREDITS = longPreferencesKey("credits_nano_dollars")
    }

    override val creditsFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.CREDITS] ?: 0L
    }

    override suspend fun setCredits(credits: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.CREDITS] = credits
        }
    }

    override suspend fun deductCredit(amount: Long) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.CREDITS] ?: 0L
            prefs[Keys.CREDITS] = maxOf(0L, current - amount)
        }
    }

    override suspend fun clearCredits() {
        dataStore.edit { prefs ->
            prefs[Keys.CREDITS] = 0L
        }
    }
}
