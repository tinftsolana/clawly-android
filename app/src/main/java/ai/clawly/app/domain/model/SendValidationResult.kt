package ai.clawly.app.domain.model

/**
 * Result of pre-send validation for chat messages
 */
sealed class SendValidationResult {
    /** User is allowed to send a message */
    data object Allowed : SendValidationResult()

    /** Non-premium user hit limit or no connection - show paywall */
    data object ShowPaywall : SendValidationResult()

    /** Web2 user not signed in - show login */
    data object ShowLogin : SendValidationResult()

    /** Premium user but no connection configured - show config prompt */
    data object ShowConfigPrompt : SendValidationResult()

    /** Premium user with managed hosting but no AI provider connected - show provider setup */
    data object ShowProviderSetup : SendValidationResult()
}
