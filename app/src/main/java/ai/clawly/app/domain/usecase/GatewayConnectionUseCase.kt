package ai.clawly.app.domain.usecase

import ai.clawly.app.data.remote.gateway.GatewayService
import ai.clawly.app.domain.model.ConnectionStatus
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GatewayConnection"
private const val WATCHDOG_INTERVAL_MS = 10_000L

/**
 * Single shared component for gateway connection management.
 * Auto-connects on creation, auto-reconnects on foreground,
 * and polls every 10s to recover from unexpected offline state.
 */
@Singleton
class GatewayConnectionUseCase @Inject constructor(
    private val gatewayService: GatewayService
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isAppForeground = true

    /** Single source of truth for connection status */
    val connectionStatus: StateFlow<ConnectionStatus> = gatewayService.connectionStatus

    init {
        // Auto-connect on app start
        scope.launch {
            Log.d(TAG, "Auto-connecting on init")
            gatewayService.connect()
        }
        // Observe app lifecycle
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        // Start watchdog
        startWatchdog()
    }

    override fun onStart(owner: LifecycleOwner) {
        isAppForeground = true
        val current = connectionStatus.value
        if (current !is ConnectionStatus.Online && current !is ConnectionStatus.Connecting) {
            Log.d(TAG, "App foregrounded, auto-reconnecting (was: $current)")
            scope.launch {
                gatewayService.connect()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppForeground = false
    }

    /**
     * Polls connection status every 10s.
     * If offline/error and app is in foreground, triggers reconnect.
     */
    private fun startWatchdog() {
        scope.launch {
            while (true) {
                delay(WATCHDOG_INTERVAL_MS)
                if (!isAppForeground) continue

                val current = connectionStatus.value
                if (current is ConnectionStatus.Offline || current is ConnectionStatus.Error) {
                    Log.d(TAG, "Watchdog: status is $current, triggering reconnect")
                    gatewayService.connect()
                }
            }
        }
    }

    suspend fun connect() = gatewayService.connect()

    suspend fun disconnect() = gatewayService.disconnect()

    suspend fun reconnect() = gatewayService.reconnect()
}
