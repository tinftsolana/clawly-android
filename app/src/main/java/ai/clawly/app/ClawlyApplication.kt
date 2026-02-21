package ai.clawly.app

import android.app.Application
import android.util.Log
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import dagger.hilt.android.HiltAndroidApp

private const val TAG = "ClawlyApplication"
private const val REVENUECAT_API_KEY = "goog_FhIygtOwDCQSDHJjafXckUjmJcA"

@HiltAndroidApp
class ClawlyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initRevenueCat()
    }

    private fun initRevenueCat() {
        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR

        val configuration = PurchasesConfiguration.Builder(this, REVENUECAT_API_KEY)
            .build()

        Purchases.configure(configuration)
        Log.d(TAG, "RevenueCat initialized")
    }
}
