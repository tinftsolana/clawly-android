package ai.clawly.app.analytics

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.amplitude.android.Amplitude
import com.amplitude.core.events.Identify
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AmplitudeAnalyticsTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val amplitude: Amplitude,
    private val firebaseAnalytics: FirebaseAnalytics
) : AnalyticsTracker {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun track(event: String, params: Map<String, String>) {
        try {
            Log.d("Analytics", "Event: $event, Params: $params")

            scope.launch {
                // Build enhanced params with metadata
                val enhancedParams = buildMap {
                    putAll(params)

                    // Add device metadata
                    put("os_version", Build.VERSION.RELEASE)
                    put("sdk_int", Build.VERSION.SDK_INT.toString())
                    put("device_manufacturer", Build.MANUFACTURER)
                    put("device_model", Build.MODEL)
                }

                // Track event in Amplitude
                amplitude.track(event, enhancedParams)

                // Track event in Firebase Analytics
                val firebaseBundle = Bundle().apply {
                    enhancedParams.forEach { (key, value) ->
                        putString(key, value)
                    }
                }
                firebaseAnalytics.logEvent(event, firebaseBundle)
            }
        } catch (t: Throwable) {
            Log.e("Analytics", "Failed to log event: $event", t)
            val errorParams = mapOf(
                "failed_event" to event,
                "error" to (t.message ?: "unknown")
            )
            amplitude.track("Failed_To_Log_Event", errorParams)
        }
    }

    fun setUserId(userId: String) {
        amplitude.setUserId(userId)
    }

    fun identify(properties: Map<String, Any>) {
        val identify = Identify()
        properties.forEach { (key, value) ->
            when (value) {
                is String -> identify.set(key, value)
                is Boolean -> identify.set(key, value)
                is Int -> identify.set(key, value)
                is Long -> identify.set(key, value)
                is Double -> identify.set(key, value)
            }
        }
        amplitude.identify(identify)
    }
}
