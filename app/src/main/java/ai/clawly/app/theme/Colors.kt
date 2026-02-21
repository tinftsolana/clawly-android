package ai.clawly.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object Colors {

    // clawly Design System Colors
    object clawly {
        val primary = Color(0xFFFF86DF)           // Pink - main action color
        val secondary = Color(0xFF9159EC)         // Purple
        val background = Color(0xFF17171F)        // Dark background
        val backgroundDark = Color(0xFF0A0019)    // Darker background
        val textWhite = Color(0xFFFFFFFF)
        val textGrey = Color(0xFF8792A7)
        val textGreySecondary = Color(0xFF9C8F99)
        val success = Color(0xFF7ABC27)           // Green
        val error = Color(0xFFF01456)             // Red
    }

    object Light {
        val white = Color(0xFFFFFFFF)
        val black = Color(0xFF000000)
        val label = Color(0xFF16161A)
        val labelSecondary = Color(0xFF737376)
        val labelTertiary = Color(0xFFA2A2A3)
        val labelInverse = Color(0xFFFFFFFF)
        val divider = Color(0x1416161A)
        val fill = Color(0x0A16161A)
        val base = Color(0xFFFFFFFF)
        val baseSecondary = Color(0xFFF2F2F7)
        val baseInverse = Color(0xFF16161A)
        val nav = Color(0x66D1D1D6)
        val fog = Color(0x6616161A)
        val fullscreen = Color(0xCC16161A)
        val action = Color(0xFFFF86DF)            // Updated to clawly pink
        val actionSecondary = Color(0x1AFF86DF)
        val brand = Color(0xFFFF86DF)             // Updated to clawly pink
        val success = Color(0xFF7ABC27)           // Updated to clawly green
        val error = Color(0xFFF01456)             // Updated to clawly red
    }

    object Dark {
        val white = Color(0xFFFFFFFF)
        val black = Color(0xFF000000)
        val label = Color(0xFFFFFFFF)
        val labelSecondary = Color(0xFF8792A7)    // Updated to clawly text grey
        val labelTertiary = Color(0xFF9C8F99)     // Updated to clawly grey
        val labelInverse = Color(0xFF0A0019)      // Updated to clawly dark
        val divider = Color(0x14FFFFFF)
        val divider2 = Color(0xFF1B1C1E)
        val fill = Color(0x0AFFFFFF)
        val base = Color(0xFF17171F)              // Updated to clawly background
        val baseSecondary = Color(0xFF0A0019)     // Updated to clawly dark
        val baseInverse = Color(0xFFFFFFFF)
        val nav = Color(0x66575767)
        val fog = Color(0x66151619)
        val fullscreen = Color(0xCC0A0019)        // Updated to clawly dark
        val action = Color(0xFFFF86DF)            // Updated to clawly pink
        val actionSecondary = Color(0x1AFF86DF)
        val brand = Color(0xFFFF86DF)             // Updated to clawly pink
        val success = Color(0xFF7ABC27)           // Updated to clawly green
        val error = Color(0xFFF01456)             // Updated to clawly red
    }

    val Background = Color(0xFF17171F)            // Updated to clawly background
    val Surface = Color(0xFF17171F)               // Updated to clawly background
    val SurfaceVariant = Color(0xFF0A0019)        // Updated to clawly dark
    val OnBackground = Color.White
    val OnSurfaceInactive = Color(0xFF8792A7)     // clawly text grey
    val OnSurface = Color.White
    var ActionColorApp by mutableStateOf(Color(0xFFFF86DF))  // Updated to clawly pink
        private set

    fun updateActionColor(color: Color) {
        ActionColorApp = color
    }
}

@Composable
fun Colors.ActionColorApp() = ActionColorApp

data class AppColors(
    val white: Color,
    val black: Color,
    val label: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val labelInverse: Color,
    val divider: Color,
    val divider2: Color,
    val fill: Color,
    val base: Color,
    val baseSecondary: Color,
    val baseInverse: Color,
    val nav: Color,
    val fog: Color,
    val fullscreen: Color,
    val action: Color,
    val actionSecondary: Color,
    val brand: Color,
    val success: Color,
    val error: Color
) {

    companion object {
        fun unspecified() = AppColors(
            white = Color.Unspecified,
            black = Color.Unspecified,
            label = Color.Unspecified,
            labelSecondary = Color.Unspecified,
            labelTertiary = Color.Unspecified,
            labelInverse = Color.Unspecified,
            divider = Color.Unspecified,
            fill = Color.Unspecified,
            base = Color.Unspecified,
            baseSecondary = Color.Unspecified,
            baseInverse = Color.Unspecified,
            nav = Color.Unspecified,
            fog = Color.Unspecified,
            fullscreen = Color.Unspecified,
            action = Color.Unspecified,
            actionSecondary = Color.Unspecified,
            brand = Color.Unspecified,
            success = Color.Unspecified,
            error = Color.Unspecified,
            divider2 = Color.Unspecified,
        )
    }
}

val LocalColors = staticCompositionLocalOf {
    AppColors.unspecified()
}

fun lightColors() = AppColors(
    white = Colors.Light.white,
    black = Colors.Light.black,
    label = Colors.Light.label,
    labelSecondary = Colors.Light.labelSecondary,
    labelTertiary = Colors.Light.labelTertiary,
    labelInverse = Colors.Light.labelInverse,
    divider = Colors.Light.divider,
    divider2 = Colors.Light.divider,
    fill = Colors.Light.fill,
    base = Colors.Light.base,
    baseSecondary = Colors.Light.baseSecondary,
    baseInverse = Colors.Light.baseInverse,
    nav = Colors.Light.nav,
    fog = Colors.Light.fog,
    fullscreen = Colors.Light.fullscreen,
    action = Colors.Light.action,
    actionSecondary = Colors.Light.actionSecondary,
    brand = Colors.Light.brand,
    success = Colors.Light.success,
    error = Colors.Light.error
)

fun darkColors() = AppColors(
    white = Colors.Dark.white,
    black = Colors.Dark.black,
    label = Colors.Dark.label,
    labelSecondary = Colors.Dark.labelSecondary,
    labelTertiary = Colors.Dark.labelTertiary,
    labelInverse = Colors.Dark.labelInverse,
    divider = Colors.Dark.divider,
    divider2 = Colors.Dark.divider2,
    fill = Colors.Dark.fill,
    base = Colors.Dark.base,
    baseSecondary = Colors.Dark.baseSecondary,
    baseInverse = Colors.Dark.baseInverse,
    nav = Colors.Dark.nav,
    fog = Colors.Dark.fog,
    fullscreen = Colors.Dark.fullscreen,
    action = Colors.Dark.action,
    actionSecondary = Colors.Dark.actionSecondary,
    brand = Colors.Dark.brand,
    success = Colors.Dark.success,
    error = Colors.Dark.error
)
