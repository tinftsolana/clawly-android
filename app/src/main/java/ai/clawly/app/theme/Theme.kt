package ai.clawly.app.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClawlyTheme(
    content: @Composable () -> Unit,
) {
    val isDarkTheme: Boolean = true

    val colors = remember {
        if (isDarkTheme) {
            darkColors()
        } else {
            lightColors()
        }
    }

    val typographyInter = remember {
        clawlyTypography()
    }

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(12.dp), // Used by DropdownMenu in Material 3
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp)
    )

    CompositionLocalProvider(
        LocalBreakoutTypographyInter provides typographyInter,
        LocalColors provides colors,
    ) {
        MaterialTheme(
            shapes = shapes,
            content = content
        )
    }
}

object ClawlyTheme {
    val TypographyInter: BreakoutTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalBreakoutTypographyInter.current
    val Colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalColors.current
}
