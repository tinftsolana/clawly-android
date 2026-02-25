package ai.clawly.app.data.remote.gateway

import ai.clawly.app.data.preferences.GatewayPreferences
import android.content.Context
import android.util.Base64
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device identity data for gateway authentication
 */
data class DeviceIdentity(
    val privateSeed: ByteArray,
    val publicKey: ByteArray,
    val deviceId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DeviceIdentity
        return deviceId == other.deviceId
    }

    override fun hashCode(): Int = deviceId.hashCode()
}

/**
 * Manages device identity for gateway authentication using Ed25519 signing
 */
@Singleton
class DeviceIdentityManager @Inject constructor(
    private val preferences: GatewayPreferences,
    @ApplicationContext private val context: Context
) {
    /**
     * Load or create device identity for gateway authentication
     */
    suspend fun loadOrCreateIdentity(): DeviceIdentity? {
        return try {
            val savedSeed = preferences.getDevicePrivateSeedSync()

            if (savedSeed != null) {
                val seed = base64UrlDecode(savedSeed)
                if (seed != null && seed.size == 32) {
                    val privateKey = Ed25519PrivateKeyParameters(seed, 0)
                    val publicKey = privateKey.generatePublicKey()
                    val publicKeyBytes = publicKey.encoded
                    val deviceId = sha256Hex(publicKeyBytes)
                    return DeviceIdentity(seed, publicKeyBytes, deviceId)
                }
            }

            // No saved seed: derive deterministic seed from ANDROID_ID so identity survives reinstall.
            val seed = deriveSeedFromAndroidId()
                ?: run {
                    // Fallback for rare cases where ANDROID_ID is unavailable.
                    val keyPairGenerator = Ed25519KeyPairGenerator()
                    keyPairGenerator.init(Ed25519KeyGenerationParameters(SecureRandom()))
                    val keyPair = keyPairGenerator.generateKeyPair()
                    (keyPair.private as Ed25519PrivateKeyParameters).encoded
                }
            val privateKey = Ed25519PrivateKeyParameters(seed, 0)
            val publicKey = privateKey.generatePublicKey()
            val publicKeyBytes = publicKey.encoded
            val deviceId = sha256Hex(publicKeyBytes)

            // Save the seed
            preferences.setDevicePrivateSeed(base64UrlEncode(seed))

            DeviceIdentity(seed, publicKeyBytes, deviceId)
        } catch (e: Exception) {
            android.util.Log.e("DeviceIdentityManager", "Failed to load/create device identity", e)
            null
        }
    }

    /**
     * Create signed device payload for gateway connection
     */
    suspend fun createSignedDevice(
        clientId: String,
        clientMode: String,
        role: String,
        scopes: List<String>,
        signedAtMs: Long,
        token: String?,
        nonce: String?
    ): Map<String, Any>? {
        val identity = loadOrCreateIdentity() ?: return null

        val version = if (nonce.isNullOrEmpty()) "v1" else "v2"
        val scopesCsv = scopes.joinToString(",")
        val tokenString = token ?: ""

        val parts = mutableListOf(
            version,
            identity.deviceId,
            clientId,
            clientMode,
            role,
            scopesCsv,
            signedAtMs.toString(),
            tokenString
        )
        if (version == "v2") {
            parts.add(nonce ?: "")
        }
        val signString = parts.joinToString("|")

        return try {
            val privateKey = Ed25519PrivateKeyParameters(identity.privateSeed, 0)
            val signer = Ed25519Signer()
            signer.init(true, privateKey)
            signer.update(signString.toByteArray(Charsets.UTF_8), 0, signString.length)
            val signature = signer.generateSignature()

            buildMap {
                put("id", identity.deviceId)
                put("publicKey", base64UrlEncode(identity.publicKey))
                put("signature", base64UrlEncode(signature))
                put("signedAt", signedAtMs)
                if (nonce != null) {
                    put("nonce", nonce)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DeviceIdentityManager", "Device signing failed", e)
            null
        }
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun deriveSeedFromAndroidId(): ByteArray? {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(androidId.toByteArray(Charsets.UTF_8))
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun base64UrlDecode(s: String): ByteArray? {
        return try {
            Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (e: Exception) {
            null
        }
    }
}
