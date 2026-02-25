package ai.clawly.app.data.preferences

import ai.clawly.app.BuildConfig
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.gatewayDataStore: DataStore<Preferences> by preferencesDataStore(name = "gateway_preferences")

/**
 * DataStore-based preferences for gateway configuration
 */
@Singleton
class GatewayPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.gatewayDataStore

    companion object {
        // Default values (DEBUG builds only)
        const val DEFAULT_URL = "wss://167.172.219.64"
        const val DEFAULT_TOKEN = "ba861d953af9137b46e6d8c2de3965aaba9608857559249c21d8b401076bddab"
        const val DEFAULT_SESSION_KEY = "agent:main:main"

        // Preference keys
        private val KEY_GATEWAY_URL = stringPreferencesKey("gateway_url")
        private val KEY_GATEWAY_TOKEN = stringPreferencesKey("gateway_token")
        private val KEY_SESSION_KEY = stringPreferencesKey("gateway_session_key")
        private val KEY_DEVICE_TOKEN = stringPreferencesKey("gateway_device_token")
        private val KEY_DEVICE_PRIVATE_SEED = stringPreferencesKey("gateway_device_private_seed")
        private val KEY_HOSTING_TYPE = stringPreferencesKey("hosting_type")
        private val KEY_MANAGED_TENANT_ID = stringPreferencesKey("managed_tenant_id")
        private val KEY_MANAGED_STATUS = stringPreferencesKey("managed_status")
        private val KEY_MANAGED_GATEWAY_URL = stringPreferencesKey("managed_gateway_url")
        private val KEY_MANAGED_GATEWAY_TOKEN = stringPreferencesKey("managed_gateway_token")
        private val KEY_MANAGED_SESSION_KEY = stringPreferencesKey("managed_session_key")
        private val KEY_SELECTED_AI_PROVIDER = stringPreferencesKey("selected_ai_provider")
        private val KEY_THINKING_LEVEL = stringPreferencesKey("thinking_level")
        private val KEY_USE_DEBUG_DEFAULTS = booleanPreferencesKey("use_debug_defaults")
        private val KEY_TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        private val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
        private val KEY_GATEWAY_SKILLS_ENABLED = booleanPreferencesKey("gateway_skills_enabled")
        private val KEY_ALWAYS_SHOW_ONBOARDING = booleanPreferencesKey("always_show_onboarding")
        private val KEY_USE_DEBUG_USER_ID = booleanPreferencesKey("use_debug_user_id")
        private val KEY_USE_BYPASS_TOKEN = booleanPreferencesKey("use_bypass_token")
        private val KEY_BYPASS_TOKEN = stringPreferencesKey("bypass_token")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_DEBUG_PREMIUM_OVERRIDE = booleanPreferencesKey("debug_premium_override")
        private val KEY_DEBUG_PREMIUM_ACTIVE = booleanPreferencesKey("debug_premium_active")
        private val KEY_BACKEND_USER_ID = stringPreferencesKey("backend_user_id")
        private val KEY_DEVICE_INSTANCE_ID = stringPreferencesKey("device_instance_id")
    }

    // Gateway URL
    val gatewayUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_GATEWAY_URL] ?: ""
    }

    suspend fun setGatewayUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[KEY_GATEWAY_URL] = url
        }
    }

    suspend fun getGatewayUrlSync(): String {
        return dataStore.data.first()[KEY_GATEWAY_URL] ?: ""
    }

    // Gateway Token
    val gatewayToken: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_GATEWAY_TOKEN] ?: ""
    }

    suspend fun setGatewayToken(token: String) {
        dataStore.edit { prefs ->
            prefs[KEY_GATEWAY_TOKEN] = token
        }
    }

    suspend fun getGatewayTokenSync(): String {
        return dataStore.data.first()[KEY_GATEWAY_TOKEN] ?: ""
    }

    // Session Key
    val sessionKey: Flow<String> = dataStore.data.map { prefs ->
        val saved = prefs[KEY_SESSION_KEY]?.trim()
        if (saved.isNullOrEmpty()) DEFAULT_SESSION_KEY else saved
    }

    suspend fun setSessionKey(key: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SESSION_KEY] = key
        }
    }

    suspend fun getSessionKeySync(): String {
        val saved = dataStore.data.first()[KEY_SESSION_KEY]?.trim()
        return if (saved.isNullOrEmpty()) DEFAULT_SESSION_KEY else saved
    }

    // Device Token (issued by gateway after pairing)
    val deviceToken: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_TOKEN]?.trim()?.ifEmpty { null }
    }

    suspend fun setDeviceToken(token: String?) {
        dataStore.edit { prefs ->
            if (token.isNullOrEmpty()) {
                prefs.remove(KEY_DEVICE_TOKEN)
            } else {
                prefs[KEY_DEVICE_TOKEN] = token
            }
        }
    }

    suspend fun getDeviceTokenSync(): String? {
        return dataStore.data.first()[KEY_DEVICE_TOKEN]?.trim()?.ifEmpty { null }
    }

    // Device Private Seed (for Curve25519 signing)
    val devicePrivateSeed: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_PRIVATE_SEED]
    }

    suspend fun setDevicePrivateSeed(seed: String) {
        dataStore.edit { prefs ->
            prefs[KEY_DEVICE_PRIVATE_SEED] = seed
        }
    }

    suspend fun getDevicePrivateSeedSync(): String? {
        return dataStore.data.first()[KEY_DEVICE_PRIVATE_SEED]
    }

    // Hosting Type
    val hostingType: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_HOSTING_TYPE]
    }

    suspend fun setHostingType(type: String?) {
        dataStore.edit { prefs ->
            if (type == null) {
                prefs.remove(KEY_HOSTING_TYPE)
            } else {
                prefs[KEY_HOSTING_TYPE] = type
            }
        }
    }

    suspend fun getHostingTypeSync(): String? {
        return dataStore.data.first()[KEY_HOSTING_TYPE]
    }

    // Managed Instance Info
    val managedTenantId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_MANAGED_TENANT_ID]
    }

    suspend fun setManagedTenantId(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(KEY_MANAGED_TENANT_ID)
            } else {
                prefs[KEY_MANAGED_TENANT_ID] = id
            }
        }
    }

    suspend fun getTenantIdSync(): String? {
        return dataStore.data.first()[KEY_MANAGED_TENANT_ID]
    }

    val managedStatus: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_MANAGED_STATUS]
    }

    suspend fun setManagedStatus(status: String?) {
        dataStore.edit { prefs ->
            if (status == null) {
                prefs.remove(KEY_MANAGED_STATUS)
            } else {
                prefs[KEY_MANAGED_STATUS] = status
            }
        }
    }

    val managedGatewayUrl: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_MANAGED_GATEWAY_URL]
    }

    suspend fun setManagedGatewayUrl(url: String?) {
        dataStore.edit { prefs ->
            if (url == null) {
                prefs.remove(KEY_MANAGED_GATEWAY_URL)
            } else {
                prefs[KEY_MANAGED_GATEWAY_URL] = url
            }
        }
    }

    val managedGatewayToken: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_MANAGED_GATEWAY_TOKEN]
    }

    suspend fun setManagedGatewayToken(token: String?) {
        dataStore.edit { prefs ->
            if (token == null) {
                prefs.remove(KEY_MANAGED_GATEWAY_TOKEN)
            } else {
                prefs[KEY_MANAGED_GATEWAY_TOKEN] = token
            }
        }
    }

    // Selected AI Provider (for managed hosting)
    val selectedAiProvider: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_AI_PROVIDER]
    }

    suspend fun setSelectedAiProvider(provider: String?) {
        dataStore.edit { prefs ->
            if (provider == null) {
                prefs.remove(KEY_SELECTED_AI_PROVIDER)
            } else {
                prefs[KEY_SELECTED_AI_PROVIDER] = provider
            }
        }
    }

    suspend fun getSelectedAiProviderSync(): String? {
        return dataStore.data.first()[KEY_SELECTED_AI_PROVIDER]
    }

    // Thinking Level
    val thinkingLevel: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_THINKING_LEVEL] ?: "medium"
    }

    suspend fun setThinkingLevel(level: String) {
        dataStore.edit { prefs ->
            prefs[KEY_THINKING_LEVEL] = level
        }
    }

    suspend fun getThinkingLevelSync(): String {
        return dataStore.data.first()[KEY_THINKING_LEVEL] ?: "medium"
    }

    // TTS Enabled
    val ttsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_TTS_ENABLED] ?: false
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_TTS_ENABLED] = enabled
        }
    }

    // Speech Rate
    val speechRate: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_SPEECH_RATE] ?: 1.0f
    }

    suspend fun setSpeechRate(rate: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_SPEECH_RATE] = rate
        }
    }

    // Use Debug Defaults
    val useDebugDefaults: Flow<Boolean> = dataStore.data.map { prefs ->
        if (BuildConfig.DEBUG) prefs[KEY_USE_DEBUG_DEFAULTS] ?: false else false
    }

    suspend fun setUseDebugDefaults(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        dataStore.edit { prefs ->
            prefs[KEY_USE_DEBUG_DEFAULTS] = enabled
        }
    }

    suspend fun getUseDebugDefaultsSync(): Boolean {
        if (!BuildConfig.DEBUG) return false
        return dataStore.data.first()[KEY_USE_DEBUG_DEFAULTS] ?: false
    }

    /**
     * Get effective gateway URL based on hosting type:
     * 1. Managed hosting -> use managed gateway URL
     * 2. Self-hosted -> use self-hosted gateway URL
     * 3. Debug defaults -> use default URL if enabled
     */
    suspend fun getEffectiveGatewayUrl(): String {
        val prefs = dataStore.data.first()
        val hostingType = prefs[KEY_HOSTING_TYPE]

        // For managed hosting, use managed gateway URL
        if (hostingType == "managed") {
            val managedUrl = prefs[KEY_MANAGED_GATEWAY_URL] ?: ""
            android.util.Log.d("GatewayPreferences", "getEffectiveGatewayUrl: managed, url=$managedUrl")
            if (managedUrl.isNotEmpty()) return managedUrl
        }

        // For self-hosted or no hosting type, use regular gateway URL
        val saved = prefs[KEY_GATEWAY_URL] ?: ""
        val useDefaults = if (BuildConfig.DEBUG) (prefs[KEY_USE_DEBUG_DEFAULTS] ?: false) else false
        android.util.Log.d("GatewayPreferences", "getEffectiveGatewayUrl: hostingType=$hostingType, saved=$saved, useDefaults=$useDefaults")

        if (saved.isNotEmpty()) return saved

        // Fallback to debug defaults
        return if (useDefaults) DEFAULT_URL else ""
    }

    /**
     * Get effective gateway token based on hosting type:
     * 1. Managed hosting -> use managed gateway token
     * 2. Self-hosted -> use self-hosted gateway token
     * 3. Debug defaults -> use default token if enabled
     */
    suspend fun getEffectiveGatewayToken(): String {
        val prefs = dataStore.data.first()
        val hostingType = prefs[KEY_HOSTING_TYPE]

        // For managed hosting, use managed gateway token
        if (hostingType == "managed") {
            val managedToken = prefs[KEY_MANAGED_GATEWAY_TOKEN] ?: ""
            if (managedToken.isNotEmpty()) return managedToken
        }

        // For self-hosted or no hosting type, use regular gateway token
        val saved = prefs[KEY_GATEWAY_TOKEN] ?: ""
        if (saved.isNotEmpty()) return saved

        // Fallback to debug defaults
        val useDefaults = if (BuildConfig.DEBUG) (prefs[KEY_USE_DEBUG_DEFAULTS] ?: false) else false
        return if (useDefaults) DEFAULT_TOKEN else ""
    }

    // Gateway Skills Enabled
    val gatewaySkillsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_GATEWAY_SKILLS_ENABLED] ?: false
    }

    suspend fun setGatewaySkillsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_GATEWAY_SKILLS_ENABLED] = enabled
        }
    }

    // Always Show Onboarding
    val alwaysShowOnboarding: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ALWAYS_SHOW_ONBOARDING] ?: false
    }

    suspend fun setAlwaysShowOnboarding(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ALWAYS_SHOW_ONBOARDING] = enabled
        }
    }

    // Use Debug User ID
    val useDebugUserId: Flow<Boolean> = dataStore.data.map { prefs ->
        if (BuildConfig.DEBUG) prefs[KEY_USE_DEBUG_USER_ID] ?: false else false
    }

    suspend fun setUseDebugUserId(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        dataStore.edit { prefs ->
            prefs[KEY_USE_DEBUG_USER_ID] = enabled
        }
    }

    suspend fun getUseDebugUserIdSync(): Boolean {
        if (!BuildConfig.DEBUG) return false
        return dataStore.data.first()[KEY_USE_DEBUG_USER_ID] ?: false
    }

    // Use Bypass Token
    val useBypassToken: Flow<Boolean> = dataStore.data.map { prefs ->
        if (BuildConfig.DEBUG) prefs[KEY_USE_BYPASS_TOKEN] ?: false else false
    }

    suspend fun setUseBypassToken(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        dataStore.edit { prefs ->
            prefs[KEY_USE_BYPASS_TOKEN] = enabled
        }
    }

    suspend fun getUseBypassTokenSync(): Boolean {
        if (!BuildConfig.DEBUG) return false
        return dataStore.data.first()[KEY_USE_BYPASS_TOKEN] ?: false
    }

    // Bypass Token
    val bypassToken: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_BYPASS_TOKEN] ?: ""
    }

    suspend fun setBypassToken(token: String) {
        if (!BuildConfig.DEBUG) return
        dataStore.edit { prefs ->
            prefs[KEY_BYPASS_TOKEN] = token
        }
    }

    suspend fun getBypassTokenSync(): String {
        return dataStore.data.first()[KEY_BYPASS_TOKEN] ?: ""
    }

    // Onboarding Completed
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun getOnboardingCompletedSync(): Boolean {
        return dataStore.data.first()[KEY_ONBOARDING_COMPLETED] ?: false
    }

    // Debug Premium Override - when active, use debugPremiumOverride value
    val debugPremiumActive: Flow<Boolean> = dataStore.data.map { prefs ->
        if (BuildConfig.DEBUG) prefs[KEY_DEBUG_PREMIUM_ACTIVE] ?: false else false
    }

    val debugPremiumOverride: Flow<Boolean> = dataStore.data.map { prefs ->
        if (BuildConfig.DEBUG) prefs[KEY_DEBUG_PREMIUM_OVERRIDE] ?: false else false
    }

    suspend fun setDebugPremiumOverride(active: Boolean, value: Boolean) {
        if (!BuildConfig.DEBUG) return
        dataStore.edit { prefs ->
            prefs[KEY_DEBUG_PREMIUM_ACTIVE] = active
            prefs[KEY_DEBUG_PREMIUM_OVERRIDE] = value
        }
    }

    suspend fun getDebugPremiumSync(): Pair<Boolean, Boolean> {
        if (!BuildConfig.DEBUG) return Pair(false, false)
        val prefs = dataStore.data.first()
        val active = prefs[KEY_DEBUG_PREMIUM_ACTIVE] ?: false
        val value = prefs[KEY_DEBUG_PREMIUM_OVERRIDE] ?: false
        return Pair(active, value)
    }

    // Clear all preferences
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    // Clear managed hosting info
    suspend fun clearManagedInfo() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_MANAGED_TENANT_ID)
            prefs.remove(KEY_MANAGED_STATUS)
            prefs.remove(KEY_MANAGED_GATEWAY_URL)
            prefs.remove(KEY_MANAGED_GATEWAY_TOKEN)
            prefs.remove(KEY_MANAGED_SESSION_KEY)
        }
    }

    suspend fun clearManagedSessionKey() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_MANAGED_SESSION_KEY)
        }
    }

    suspend fun getOrCreateManagedSessionKey(): String {
        val existing = dataStore.data.first()[KEY_MANAGED_SESSION_KEY]
        if (!existing.isNullOrEmpty()) return existing
        val instanceId = getOrCreateInstanceId()
        val suffixRaw = instanceId.replace("-", "")
        val suffix = suffixRaw.takeLast(12).ifEmpty {
            System.currentTimeMillis().toString(16).takeLast(12)
        }
        val sessionKey = "agent:main:android-$suffix"
        dataStore.edit { prefs ->
            prefs[KEY_MANAGED_SESSION_KEY] = sessionKey
        }
        return sessionKey
    }

    // Clear self-hosted info
    suspend fun clearSelfHostedInfo() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_GATEWAY_URL)
            prefs.remove(KEY_GATEWAY_TOKEN)
        }
    }

    // Backend User ID (returned from POST /auth/login)
    suspend fun setBackendUserId(userId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_BACKEND_USER_ID] = userId
        }
    }

    suspend fun getBackendUserIdSync(): String? {
        return dataStore.data.first()[KEY_BACKEND_USER_ID]
    }

    suspend fun clearBackendUserId() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_BACKEND_USER_ID)
        }
    }

    // Device Instance ID (stable UUID for managed hosting pairing)
    suspend fun getOrCreateInstanceId(): String {
        val existing = dataStore.data.first()[KEY_DEVICE_INSTANCE_ID]
        if (!existing.isNullOrEmpty()) return existing

        val newId = java.util.UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            prefs[KEY_DEVICE_INSTANCE_ID] = newId
        }
        return newId
    }

    suspend fun getInstanceIdSync(): String? {
        return dataStore.data.first()[KEY_DEVICE_INSTANCE_ID]
    }
}
