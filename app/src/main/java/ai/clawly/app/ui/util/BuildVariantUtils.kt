package ai.clawly.app.ui.util

import ai.clawly.app.BuildConfig
import androidx.compose.runtime.Composable

@Composable
fun DrawIfWeb3(content: @Composable () -> Unit) {
    if (BuildConfig.IS_WEB3) {
        content()
    }
}

@Composable
fun DrawIfWeb2(content: @Composable () -> Unit) {
    if (BuildConfig.IS_WEB2) {
        content()
    }
}
