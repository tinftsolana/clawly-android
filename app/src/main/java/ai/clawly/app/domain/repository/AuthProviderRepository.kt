package ai.clawly.app.domain.repository

import ai.clawly.app.domain.model.AuthProviderConfig
import ai.clawly.app.domain.model.ManagedInstanceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for auth provider management
 */
interface AuthProviderRepository {
    /** Current auth provider configuration */
    val currentConfig: StateFlow<AuthProviderConfig>

    /** Whether a sync operation is in progress */
    val isSyncing: StateFlow<Boolean>

    /** Configure self-hosted gateway */
    suspend fun configureSelfHosted(url: String, token: String?)

    /** Configure managed hosting */
    suspend fun configureManaged(tenantId: String)

    /** Update managed instance info */
    suspend fun updateManagedInstance(info: ManagedInstanceInfo)

    /** Clear the current configuration */
    suspend fun clearConfig()

    /** Get the selected AI provider (for managed hosting) */
    fun getSelectedAiProvider(): String?

    /** Set the selected AI provider (for managed hosting) */
    suspend fun setSelectedAiProvider(provider: String)

    /**
     * Fallback pairing recovery: list pending pairing requests and auto-approve the first pending one.
     * Returns true when approval call was attempted.
     */
    suspend fun tryApprovePendingPairingFromList(source: String, reconnectAfterApproval: Boolean = true): Boolean

    /** Refresh managed instance status from server (call when screen appears) */
    suspend fun refreshManagedInstanceStatus()
}
