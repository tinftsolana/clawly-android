package ai.clawly.app

import android.app.Application
import android.util.Log
import ai.clawly.app.data.remote.gateway.DeviceIdentityManager
import ai.clawly.app.data.remote.RemoteConfigFlags
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

private const val TAG = "ClawlyApplication"
private const val REVENUECAT_API_KEY = "goog_FhIygtOwDCQSDHJjafXckUjmJcA"
private const val REMOTE_CONFIG_FETCH_INTERVAL_DEBUG_SEC = 0L
private const val REMOTE_CONFIG_FETCH_INTERVAL_RELEASE_SEC = 60L

@HiltAndroidApp
class ClawlyApplication : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RevenueCatInitEntryPoint {
        fun deviceIdentityManager(): DeviceIdentityManager
    }

    override fun onCreate() {
        super.onCreate()
        initRevenueCat()
        initRemoteConfig()
    }

    private fun initRevenueCat() {
        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR

        val builder = PurchasesConfiguration.Builder(this, REVENUECAT_API_KEY)
        val revenueCatUserId = resolveRevenueCatUserIdFromDeviceIdentity()
        if (!revenueCatUserId.isNullOrEmpty()) {
            builder.appUserID(revenueCatUserId)
        }
        val configuration = builder.build()

        Purchases.configure(configuration)
        Log.d(TAG, "RevenueCat initialized (appUserId=${revenueCatUserId ?: "anonymous"})")
    }

    private fun resolveRevenueCatUserIdFromDeviceIdentity(): String? {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                this,
                RevenueCatInitEntryPoint::class.java
            )
            runBlocking {
                entryPoint.deviceIdentityManager()
                    .loadOrCreateIdentity()
                    ?.deviceId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve RevenueCat appUserId from device identity", e)
            null
        }
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
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
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
