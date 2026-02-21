package ai.clawly.app.data.service

import ai.clawly.app.data.preferences.GatewayPreferences
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TTSService"

/**
 * Service for text-to-speech functionality
 */
@Singleton
class TTSService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: GatewayPreferences
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    var isSpeaking: Boolean = false
        private set

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported")
            } else {
                isInitialized = true
                Log.d(TAG, "TTS initialized successfully")

                // Set up progress listener
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                    }

                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                    }
                })
            }
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    /**
     * Speak the given text if TTS is enabled
     */
    fun speak(text: String) {
        // Check if TTS is enabled
        val isEnabled = runBlocking { preferences.ttsEnabled.first() }
        if (!isEnabled) return

        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized")
            return
        }

        // Strip markdown before speaking
        val cleanText = stripMarkdown(text)
        if (cleanText.isBlank()) return

        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Stop current speech
     */
    fun stop() {
        tts?.stop()
        isSpeaking = false
    }

    /**
     * Clean up resources
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    /**
     * Strip markdown formatting from text for cleaner speech
     */
    private fun stripMarkdown(text: String): String {
        return text
            // Remove code blocks
            .replace(Regex("```[\\s\\S]*?```"), "code block")
            // Remove inline code
            .replace(Regex("`[^`]+`"), "code")
            // Remove bold/italic
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("__([^_]+)__"), "$1")
            .replace(Regex("_([^_]+)_"), "$1")
            // Remove headers
            .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
            // Remove links
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
            // Remove images
            .replace(Regex("!\\[([^]]*)]\\([^)]+\\)"), "image: $1")
            // Remove bullet points
            .replace(Regex("^[*-]\\s+", RegexOption.MULTILINE), "")
            // Clean up extra whitespace
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
