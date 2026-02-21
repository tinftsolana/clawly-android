package ai.clawly.app.domain.model

/**
 * Wire types for gateway messages
 */
enum class GatewayWireType(val value: String) {
    Req("req"),
    Res("res"),
    Event("event");

    companion object {
        fun fromString(value: String): GatewayWireType? {
            return entries.find { it.value == value }
        }
    }
}
