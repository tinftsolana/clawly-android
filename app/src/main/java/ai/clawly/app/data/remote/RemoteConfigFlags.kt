package ai.clawly.app.data.remote

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import org.json.JSONObject

data class TestCredentials(
    val login: String,
    val password: String,
    val wss: String,
    val gateway: String
)

object RemoteConfigFlags {
    const val KEY_SELF_HOSTED_WITHOUT_PREMIUM = "self_hosted_without_premium_enabled"
    const val KEY_TEST_CREDENTIALS = "testCredentials"

    fun isSelfHostedWithoutPremiumEnabled(): Boolean {
        return runCatching {
            FirebaseRemoteConfig.getInstance().getBoolean(KEY_SELF_HOSTED_WITHOUT_PREMIUM)
        }.getOrDefault(false)
    }

    fun getTestCredentials(): TestCredentials? {
        return runCatching {
            val json = FirebaseRemoteConfig.getInstance().getString(KEY_TEST_CREDENTIALS)
            if (json.isBlank()) return null
            val obj = JSONObject(json)
            TestCredentials(
                login = obj.getString("login"),
                password = obj.getString("password"),
                wss = obj.getString("wss"),
                gateway = obj.getString("gateway")
            )
        }.getOrNull()
    }
}

