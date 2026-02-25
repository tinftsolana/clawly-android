package ai.clawly.app.data.remote

import com.google.firebase.remoteconfig.FirebaseRemoteConfig

object RemoteConfigFlags {
    const val KEY_SELF_HOSTED_WITHOUT_PREMIUM = "self_hosted_without_premium_enabled"

    fun isSelfHostedWithoutPremiumEnabled(): Boolean {
        return runCatching {
            FirebaseRemoteConfig.getInstance().getBoolean(KEY_SELF_HOSTED_WITHOUT_PREMIUM)
        }.getOrDefault(false)
    }
}

