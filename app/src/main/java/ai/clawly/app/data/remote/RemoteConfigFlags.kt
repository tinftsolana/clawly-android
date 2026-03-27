package ai.clawly.app.data.remote

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import org.json.JSONObject

data class TestCredentials(
    val login: String,
    val password: String,
    val wss: String,
    val gateway: String
)

data class CreditPackConfig(
    val creditsAmount: Int,
    val packId: String,
    val priceLabel: String?
)

object RemoteConfigFlags {
    const val KEY_SELF_HOSTED_WITHOUT_PREMIUM = "self_hosted_without_premium_enabled"
    const val KEY_TEST_CREDENTIALS = "testCredentials"
    const val KEY_CREDITS_PACKS = "credits_packs"
    const val KEY_SHOW_RATE_DIALOG_IMMEDIATELY = "show_rate_dialog_immediately"

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

    /**
     * Credit packs from Remote Config. Format:
     * [{"creditsAmount":30000,"packId":"clawly.credits.pack.one","priceLabel":"$2.99"}, ...]
     */
    fun isShowRateDialogImmediately(): Boolean {
        return runCatching {
            FirebaseRemoteConfig.getInstance().getBoolean(KEY_SHOW_RATE_DIALOG_IMMEDIATELY)
        }.getOrDefault(false)
    }

    fun getCreditPacks(): List<CreditPackConfig> {
        return runCatching {
            val json = FirebaseRemoteConfig.getInstance().getString(KEY_CREDITS_PACKS)
            if (json.isBlank()) return emptyList()
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CreditPackConfig(
                    creditsAmount = obj.getInt("creditsAmount"),
                    packId = obj.getString("packId"),
                    priceLabel = obj.optString("priceLabel", null)
                )
            }
        }.getOrDefault(emptyList())
    }
}

