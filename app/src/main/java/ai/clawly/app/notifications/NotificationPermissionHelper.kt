package ai.clawly.app.notifications

import ai.clawly.app.data.preferences.GatewayPreferences
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationPermissionHelper @Inject constructor(
    private val gatewayPreferences: GatewayPreferences,
    private val pushTokenManager: PushTokenManager
) {
    fun needsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    }

    suspend fun shouldShowPermissionDialog(context: Context): Boolean {
        if (!needsPermission(context)) return false
        return !gatewayPreferences.isNotificationPermissionRequested()
    }

    suspend fun markPermissionRequested() {
        gatewayPreferences.setNotificationPermissionRequested(true)
    }

    fun registerPushTokenIfEligible() {
        pushTokenManager.registerIfNeeded(force = true)
    }
}

@Composable
fun RequestNotificationPermission(
    permissionHelper: NotificationPermissionHelper,
    context: Context,
    onPermissionResult: (Boolean) -> Unit = {}
) {
    var shouldRequest by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        coroutineScope.launch {
            permissionHelper.markPermissionRequested()
        }

        if (isGranted) {
            permissionHelper.registerPushTokenIfEligible()
        }

        onPermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        shouldRequest = true
    }

    if (shouldRequest && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        LaunchedEffect(shouldRequest) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            shouldRequest = false
        }
    }
}
