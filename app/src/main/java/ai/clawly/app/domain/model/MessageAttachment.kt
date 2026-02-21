package ai.clawly.app.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents an image attachment in a chat message
 */
@Serializable
data class MessageAttachment(
    val id: String = UUID.randomUUID().toString(),
    @Serializable(with = ByteArraySerializer::class)
    val imageData: ByteArray,
    val mimeType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MessageAttachment
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Custom serializer for ByteArray to Base64 string
 */
object ByteArraySerializer : kotlinx.serialization.KSerializer<ByteArray> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "ByteArray",
        kotlinx.serialization.descriptors.PrimitiveKind.STRING
    )

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: ByteArray) {
        encoder.encodeString(android.util.Base64.encodeToString(value, android.util.Base64.NO_WRAP))
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): ByteArray {
        return android.util.Base64.decode(decoder.decodeString(), android.util.Base64.NO_WRAP)
    }
}
