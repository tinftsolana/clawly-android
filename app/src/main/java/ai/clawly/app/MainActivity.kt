package ai.clawly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ai.clawly.app.theme.ClawlyTheme
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import dagger.hilt.android.AndroidEntryPoint

var activityResultSender: ActivityResultSender? = null

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.IS_WEB3) {
            activityResultSender = ActivityResultSender(this)
        }

        enableEdgeToEdge()
        setContent {
            ClawlyTheme {
                ClawlyApp()
            }
        }
    }
}
