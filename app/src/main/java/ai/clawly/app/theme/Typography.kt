package ai.clawly.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class BreakoutTypography(
    val header: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val body1: TextStyle,
    val body2: TextStyle,
    val caption: TextStyle,
    val onboardingTitle: TextStyle,
    val onboardingBody: TextStyle
) {

    companion object {
        fun default() = BreakoutTypography(
            header = TextStyle.Default,
            title1 = TextStyle.Default,
            title2 = TextStyle.Default,
            title3 = TextStyle.Default,
            body1 = TextStyle.Default,
            body2 = TextStyle.Default,
            caption = TextStyle.Default,
            onboardingTitle = TextStyle.Default,
            onboardingBody = TextStyle.Default
        )
    }
}

val LocalBreakoutTypographyInter = staticCompositionLocalOf {
    BreakoutTypography.default()
}

// Use default system font for now - can be replaced with custom fonts later
val InterFamily = FontFamily.SansSerif

// Quicksand font family for clawly design system
// Using SansSerif until custom fonts are added
val QuicksandFamily = FontFamily.SansSerif

fun breakoutTypography(
    fontFamily: FontFamily
): BreakoutTypography {
    val baseStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal
    )
    return BreakoutTypography(
        header = baseStyle.copy(
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.SemiBold
        ),
        title1 = baseStyle.copy(
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold
        ),
        title2 = baseStyle.copy(
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold
        ),
        title3 = baseStyle.copy(
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium
        ),
        body1 = baseStyle.copy(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium
        ),
        body2 = baseStyle.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal
        ),
        caption = baseStyle.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal
        ),
        onboardingTitle = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 30.sp
        ),
        onboardingBody = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.sp
        )
    )
}

// clawly typography with Quicksand font - use this for clawly design system
fun clawlyTypography(): BreakoutTypography = breakoutTypography(QuicksandFamily)