package ai.clawly.app

import android.os.Bundle
import ai.clawly.app.notifications.NotificationPermissionHelper
import ai.clawly.app.notifications.PushTokenManager
import ai.clawly.app.theme.ClawlyTheme
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

var activityResultSender: ActivityResultSender? = null

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var notificationPermissionHelper: NotificationPermissionHelper

    @Inject
    lateinit var pushTokenManager: PushTokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.IS_WEB3) {
            activityResultSender = ActivityResultSender(this)
        }

        enableEdgeToEdge()

        // Attempt silent registration if permission was already granted
        pushTokenManager.registerIfNeeded()

        setContent {
            ClawlyTheme {
                ClawlyApp(notificationPermissionHelper = notificationPermissionHelper)
            }
        }
    }
}
