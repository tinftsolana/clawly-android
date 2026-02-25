package ai.clawly.app

import android.app.Application
import android.util.Log
import ai.clawly.app.data.remote.RemoteConfigFlags
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import dagger.hilt.android.HiltAndroidApp

private const val TAG = "ClawlyApplication"
private const val REVENUECAT_API_KEY = "goog_FhIygtOwDCQSDHJjafXckUjmJcA"
private const val REMOTE_CONFIG_FETCH_INTERVAL_DEBUG_SEC = 0L
private const val REMOTE_CONFIG_FETCH_INTERVAL_RELEASE_SEC = 60L

@HiltAndroidApp
class ClawlyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initRevenueCat()
        initRemoteConfig()
    }

    private fun initRevenueCat() {
        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR

        val configuration = PurchasesConfiguration.Builder(this, REVENUECAT_API_KEY)
            .build()

        Purchases.configure(configuration)
        Log.d(TAG, "RevenueCat initialized")
    }

    private fun initRemoteConfig() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(
                if (BuildConfig.DEBUG) REMOTE_CONFIG_FETCH_INTERVAL_DEBUG_SEC
                else REMOTE_CONFIG_FETCH_INTERVAL_RELEASE_SEC
            )
            .build()

        remoteConfig.setConfigSettingsAsync(settings)
        remoteConfig.setDefaultsAsync(
            mapOf(
                RemoteConfigFlags.KEY_SELF_HOSTED_WITHOUT_PREMIUM to false
            )
        )
        remoteConfig.fetchAndActivate()
            .addOnSuccessListener { activated ->
                val selfHostedNoPremium = remoteConfig.getBoolean(RemoteConfigFlags.KEY_SELF_HOSTED_WITHOUT_PREMIUM)
                Log.d(TAG, "Firebase Remote Config ready (activated=$activated, selfHostedNoPremium=$selfHostedNoPremium)")
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Firebase Remote Config fetch failed", error)
            }
    }
}
