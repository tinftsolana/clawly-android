package ai.clawly.app.presentation.login

import ai.clawly.app.R
import ai.clawly.app.ui.theme.ClawlyColors
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Navigate on sign-in success
    LaunchedEffect(uiState.isSignedIn) {
        if (uiState.isSignedIn) {
            onSignedIn()
        }
    }

    // Glow animation
    val glowOpacity by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowOpacity"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClawlyColors.background)
    ) {
        // Top radial glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ClawlyColors.accentPrimary.copy(alpha = glowOpacity),
                            ClawlyColors.accentPrimary.copy(alpha = 0.1f),
                            Color.Transparent
                        ),
                        center = Offset(Float.POSITIVE_INFINITY / 2, 0f),
                        radius = 800f
                    )
                )
        )

        // Close button (top-right)
        if (onDismiss != null) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = ClawlyColors.textPrimary
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Logo
            Image(
                painter = painterResource(id = R.drawable.clawly),
                contentDescription = "Clawly",
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = "Welcome to Clawly",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ClawlyColors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle
            Text(
                text = "Sign in to sync your data across devices",
                fontSize = 16.sp,
                color = ClawlyColors.secondaryText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Google Sign-In button
            Button(
                onClick = { viewModel.signInWithGoogle(context) },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.5f)
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Signing in...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "Continue with Google",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Error message
            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = uiState.error ?: "",
                    fontSize = 14.sp,
                    color = ClawlyColors.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0.9f)
                )
            }

            Spacer(modifier = Modifier.weight(1.5f))

            // Footer with clickable links
            @Suppress("DEPRECATION")
            val annotatedText = buildAnnotatedString {
                withStyle(SpanStyle(color = ClawlyColors.textMuted)) {
                    append("By continuing, you agree to our\n")
                }
                pushStringAnnotation(tag = "URL", annotation = "https://docs.google.com/document/d/1NUAcle14HNFpF8-JsKhEKdVnuXcNRg9uFlWV9uFbIUM/edit?usp=sharing")
                withStyle(SpanStyle(color = ClawlyColors.accentPrimary, textDecoration = TextDecoration.Underline)) {
                    append("Terms of Service")
                }
                pop()
                withStyle(SpanStyle(color = ClawlyColors.textMuted)) {
                    append(" and ")
                }
                pushStringAnnotation(tag = "URL", annotation = "https://docs.google.com/document/d/1s6ijRCCVNSvLnlC4andYPRJUUxveskF6c-2Km0sZdYU/edit?usp=sharing")
                withStyle(SpanStyle(color = ClawlyColors.accentPrimary, textDecoration = TextDecoration.Underline)) {
                    append("Privacy Policy")
                }
                pop()
            }
            ClickableText(
                text = annotatedText,
                style = LocalTextStyle.current.copy(
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(bottom = 32.dp),
                onClick = { offset ->
                    annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)))
                        }
                }
            )
        }
    }
}
