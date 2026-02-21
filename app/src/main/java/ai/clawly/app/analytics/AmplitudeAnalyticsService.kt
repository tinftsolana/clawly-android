package ai.clawly.app.analytics

import android.content.Context
import android.util.Log
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Analytics"
private const val AMPLITUDE_API_KEY = "9dc3bdf7d654b80a7ef7a98e36ea87b7"

/**
 * Amplitude Analytics Service implementation
 * Matches events from iOS AnalyticsService.swift
 */
@Singleton
class AmplitudeAnalyticsService @Inject constructor(
    context: Context
) : AnalyticsTracker {

    private val amplitude: Amplitude = Amplitude(
        Configuration(
            apiKey = AMPLITUDE_API_KEY,
            context = context,
            flushEventsOnClose = true
        )
    )

    init {
        Log.d(TAG, "Amplitude initialized")
    }

    override fun track(event: String, params: Map<String, String>) {
        Log.d(TAG, "Event: $event, params: $params")
        if (params.isEmpty()) {
            amplitude.track(event)
        } else {
            amplitude.track(event, params)
        }
    }

    fun setUserId(userId: String) {
        amplitude.setUserId(userId)
        Log.d(TAG, "Set userId: $userId")
    }

    fun setUserProperties(properties: Map<String, Any>) {
        val identify = com.amplitude.android.events.Identify()
        properties.forEach { (key, value) ->
            when (value) {
                is String -> identify.set(key, value)
                is Int -> identify.set(key, value)
                is Long -> identify.set(key, value)
                is Double -> identify.set(key, value)
                is Boolean -> identify.set(key, value)
            }
        }
        amplitude.identify(identify)
        Log.d(TAG, "Set user properties: $properties")
    }

    // MARK: - Screen Views

    fun trackOnboardingViewed() = track("onboarding_viewed")
    fun trackOnboardingPage(page: Int) = track("onboarding_page_$page")
    fun trackOnboardingCompleted() = track("onboarding_completed")
    fun trackChatScreen() = track("chat_screen")
    fun trackPaywallScreen() = track("paywall_screen")

    // MARK: - Chat Events

    fun trackChatConnect() = track("chat_connect")
    fun trackChatDisconnect() = track("chat_disconnect")

    fun trackChatMessageSent(
        length: Int,
        hasAttachments: Boolean,
        attachmentCount: Int,
        thinkingLevel: String
    ) = track(
        "chat_message_sent",
        mapOf(
            "length" to length.toString(),
            "has_attachments" to hasAttachments.toString(),
            "attachment_count" to attachmentCount.toString(),
            "thinking_level" to thinkingLevel
        )
    )

    fun trackChatMessageReceived(type: String) = track(
        "chat_message_received",
        mapOf("type" to type)
    )

    fun trackChatAbort() = track("chat_abort")
    fun trackChatHistoryCleared() = track("chat_history_cleared")

    fun trackChatRetryMessage(thinkingLevel: String) = track(
        "chat_retry_message",
        mapOf("thinking_level" to thinkingLevel)
    )

    fun trackChatReconnect() = track("chat_reconnect")

    fun trackChatThinkingLevelChanged(level: String) = track(
        "chat_thinking_level_changed",
        mapOf("level" to level)
    )

    // MARK: - Paywall Events

    fun trackPaywallPurchaseTapped(productId: String, planType: String) = track(
        "paywall_purchase_tapped",
        mapOf("product_id" to productId, "plan_type" to planType)
    )

    fun trackPaywallPurchaseSuccess(productId: String) = track(
        "paywall_purchase_success",
        mapOf("product_id" to productId)
    )

    fun trackPaywallPurchaseFailed(error: String) = track(
        "paywall_purchase_failed",
        mapOf("error" to error)
    )

    fun trackPaywallRestoreTapped() = track("paywall_restore_tapped")
    fun trackPaywallRestoreSuccess() = track("paywall_restore_success")

    fun trackPaywallRestoreFailed(error: String) = track(
        "paywall_restore_failed",
        mapOf("error" to error)
    )

    fun trackPaywallRestoreEmpty() = track("paywall_restore_empty")

    // MARK: - Settings Events

    fun trackSettingsReconnectTapped() = track("settings_reconnect_tapped")
    fun trackSettingsGatewayUpdated() = track("settings_gateway_updated")

    fun trackTtsEnabledChanged(enabled: Boolean) = track(
        "tts_enabled_changed",
        mapOf("enabled" to enabled.toString())
    )

    fun trackTtsVoiceChanged(voiceId: String) = track(
        "tts_voice_changed",
        mapOf("voice_id" to voiceId)
    )

    fun trackWakeWordEnabledChanged(enabled: Boolean) = track(
        "wake_word_enabled_changed",
        mapOf("enabled" to enabled.toString())
    )

    fun trackWakeWordPhraseChanged(phrase: String) = track(
        "wake_word_phrase_changed",
        mapOf("phrase" to phrase)
    )

    // MARK: - Debug Events

    fun trackDebugPremiumOverrideChanged(value: Boolean?) = track(
        "debug_premium_override_changed",
        mapOf("value" to (value?.toString() ?: "null"))
    )
}
