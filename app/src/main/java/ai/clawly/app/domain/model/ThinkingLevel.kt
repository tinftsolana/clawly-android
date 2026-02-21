package ai.clawly.app.domain.model

/**
 * Thinking level for AI responses - affects response quality and latency
 */
enum class ThinkingLevel(
    val displayName: String,
    val iconName: String
) {
    Low("Quick", "hare"),
    Medium("Balanced", "brain"),
    High("Deep", "brain.head.profile");

    companion object {
        fun fromString(value: String): ThinkingLevel {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: Medium
        }
    }
}
