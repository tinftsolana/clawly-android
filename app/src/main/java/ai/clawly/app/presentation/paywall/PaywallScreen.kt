package ai.clawly.app.presentation.paywall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.clawly.app.R
import ai.clawly.app.ui.theme.ClawlyColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class PlanType {
    Monthly, Yearly
}

@Composable
fun PaywallScreen(
    monthlyPrice: String = "$9.99",
    yearlyPrice: String = "$49.99",
    isPurchasing: Boolean = false,
    isRestoring: Boolean = false,
    onSelectPlan: (PlanType) -> Unit = {},
    onSubscribe: () -> Unit = {},
    onRestore: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onTermsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp
    val isSmallScreen = screenHeight < 700

    var animateContent by remember { mutableStateOf(false) }
    var showCloseButton by remember { mutableStateOf(false) }
    var selectedPlan by remember { mutableStateOf(PlanType.Yearly) }
    var chipsFloating by remember { mutableStateOf(false) }

    // Sparkle rotation
    val infiniteTransition = rememberInfiniteTransition(label = "sparkle")
    val sparkleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkleRotation"
    )

    LaunchedEffect(Unit) {
        animateContent = true
        chipsFloating = true
        // Show close button after 2 seconds (ASO best practice)
        delay(2000)
        showCloseButton = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ClawlyColors.background, ClawlyColors.surface)
                )
            )
            .navigationBarsPadding()
    ) {
        // Accent glow at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ClawlyColors.accentPrimary.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close button row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .height(44.dp),
                horizontalArrangement = Arrangement.End
            ) {
                AnimatedVisibility(
                    visible = showCloseButton,
                    enter = fadeIn()
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        },
                        enabled = !isPurchasing && !isRestoring,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(ClawlyColors.surfaceElevated, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = ClawlyColors.secondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isSmallScreen) 4.dp else 8.dp))

            // Lobster header with sparkles
            LobsterHeader(
                animateContent = animateContent,
                sparkleRotation = sparkleRotation,
                modifier = Modifier.height(if (isSmallScreen) 80.dp else 100.dp)
            )

            Spacer(modifier = Modifier.height(if (isSmallScreen) 8.dp else 12.dp))

            // Title
            AnimatedVisibility(
                visible = animateContent,
                enter = fadeIn() + slideInVertically { 20 }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Unlock",
                        fontSize = if (isSmallScreen) 22.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClawlyColors.textPrimary
                    )
                    Text(
                        text = "Clawly Pro",
                        fontSize = if (isSmallScreen) 24.sp else 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ClawlyColors.accentPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isSmallScreen) 4.dp else 6.dp))

            // Subtitle
            AnimatedVisibility(
                visible = animateContent,
                enter = fadeIn() + slideInVertically { 10 }
            ) {
                Text(
                    text = "The most powerful AI assistant\nin your pocket.",
                    fontSize = if (isSmallScreen) 12.sp else 14.sp,
                    color = ClawlyColors.secondaryText,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(if (isSmallScreen) 12.dp else 20.dp))

            // Benefits chips - CENTERED
            BenefitsChips(
                animateContent = animateContent,
                chipsFloating = chipsFloating,
                isSmallScreen = isSmallScreen
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bottom box with plans, button, and links
            BottomBox(
                selectedPlan = selectedPlan,
                monthlyPrice = monthlyPrice,
                yearlyPrice = yearlyPrice,
                animateContent = animateContent,
                isPurchasing = isPurchasing,
                isRestoring = isRestoring,
                isSmallScreen = isSmallScreen,
                onSelectPlan = { plan ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedPlan = plan
                    onSelectPlan(plan)
                },
                onSubscribe = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSubscribe()
                },
                onRestore = onRestore,
                onTermsClick = onTermsClick,
                onPrivacyClick = onPrivacyClick
            )
        }
    }
}

@Composable
private fun LobsterHeader(
    animateContent: Boolean,
    sparkleRotation: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Radial glow
        Box(
            modifier = Modifier
                .size(160.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ClawlyColors.accentPrimary.copy(alpha = 0.2f),
                            ClawlyColors.accentPrimary.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Sparkles around the lobster
        repeat(6) { index ->
            val angle = (index * 60f + sparkleRotation) * PI / 180
            val offsetX = (cos(angle) * 50).toFloat()
            val offsetY = (sin(angle) * 50).toFloat()

            Text(
                text = "✦",
                fontSize = if (index % 2 == 0) 14.sp else 10.sp,
                color = ClawlyColors.accentPrimary.copy(alpha = 0.9f),
                modifier = Modifier.offset(x = offsetX.dp, y = offsetY.dp)
            )
        }

        // Clawly logo
        val scale by animateFloatAsState(
            targetValue = if (animateContent) 1f else 0.8f,
            animationSpec = spring(dampingRatio = 0.7f),
            label = "logoScale"
        )
        val alpha by animateFloatAsState(
            targetValue = if (animateContent) 1f else 0f,
            animationSpec = tween(600),
            label = "logoAlpha"
        )

        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.clawly),
            contentDescription = "Clawly",
            modifier = Modifier
                .size(80.dp)
                .shadow(
                    elevation = 12.dp,
                    spotColor = ClawlyColors.accentPrimary.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
        )
    }
}

@Composable
private fun BenefitsChips(
    animateContent: Boolean,
    chipsFloating: Boolean,
    isSmallScreen: Boolean
) {
    val chips = listOf(
        listOf("\uD83D\uDCBB" to "Code", "\uD83D\uDCE7" to "Emails"),
        listOf("\uD83D\uDCC5" to "Planning", "✈\uFE0F" to "Travel"),
        listOf("\uD83D\uDCDD" to "Writing", "\uD83E\uDDEE" to "Math"),
        listOf("\uD83C\uDFA8" to "Creative", "\uD83D\uDCCA" to "Research"),
        listOf("\uD83D\uDCA1" to "Ideas", "✨" to "Life Stuff")
    )

    AnimatedVisibility(
        visible = animateContent,
        enter = fadeIn() + slideInVertically { 20 }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 6.dp else 8.dp)
        ) {
            chips.forEachIndexed { rowIndex, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    row.forEachIndexed { colIndex, (emoji, text) ->
                        val delayOffset = (rowIndex * 0.2 + colIndex * 0.3)
                        BenefitChip(
                            emoji = emoji,
                            text = text,
                            floatOffset = delayOffset,
                            isFloating = chipsFloating,
                            isSmallScreen = isSmallScreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BenefitChip(
    emoji: String,
    text: String,
    floatOffset: Double,
    isFloating: Boolean,
    isSmallScreen: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "benefitChip_$text")
    val offset by infiniteTransition.animateFloat(
        initialValue = 3f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOut, delayMillis = (floatOffset * 1000).toInt()),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset_$text"
    )

    Row(
        modifier = Modifier
            .offset(y = if (isFloating) offset.dp else 0.dp)
            .background(
                ClawlyColors.surfaceElevated,
                shape = RoundedCornerShape(50)
            )
            .padding(
                horizontal = if (isSmallScreen) 12.dp else 16.dp,
                vertical = if (isSmallScreen) 8.dp else 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = emoji, fontSize = if (isSmallScreen) 14.sp else 16.sp)
        Text(
            text = text,
            fontSize = if (isSmallScreen) 12.sp else 14.sp,
            fontWeight = FontWeight.Medium,
            color = ClawlyColors.textPrimary
        )
    }
}

@Composable
private fun BottomBox(
    selectedPlan: PlanType,
    monthlyPrice: String,
    yearlyPrice: String,
    animateContent: Boolean,
    isPurchasing: Boolean,
    isRestoring: Boolean,
    isSmallScreen: Boolean,
    onSelectPlan: (PlanType) -> Unit,
    onSubscribe: () -> Unit,
    onRestore: () -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        ClawlyColors.surface.copy(alpha = 0.8f),
                        ClawlyColors.surface
                    )
                )
            )
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Plan selector
        AnimatedVisibility(
            visible = animateContent,
            enter = fadeIn() + slideInVertically { 20 }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Monthly plan
                PlanCard(
                    title = "MONTHLY",
                    price = monthlyPrice,
                    period = "/month",
                    isSelected = selectedPlan == PlanType.Monthly,
                    showBadge = false,
                    onClick = { onSelectPlan(PlanType.Monthly) },
                    modifier = Modifier.weight(1f)
                )

                // Yearly plan
                PlanCard(
                    title = "YEARLY",
                    price = yearlyPrice,
                    period = "/year",
                    isSelected = selectedPlan == PlanType.Yearly,
                    showBadge = true,
                    onClick = { onSelectPlan(PlanType.Yearly) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isSmallScreen) 12.dp else 16.dp))

        // CTA Button
        AnimatedVisibility(
            visible = animateContent,
            enter = fadeIn() + slideInVertically { 20 }
        ) {
            Button(
                onClick = onSubscribe,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = !isPurchasing && !isRestoring,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClawlyColors.accentPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPurchasing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Subscribe",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Sub-CTA links
        AnimatedVisibility(
            visible = animateContent,
            enter = fadeIn()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onRestore,
                    enabled = !isPurchasing && !isRestoring,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = ClawlyColors.textTertiary,
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "Restore",
                        fontSize = 12.sp,
                        color = ClawlyColors.textTertiary
                    )
                }

                Text(
                    text = "•",
                    fontSize = 12.sp,
                    color = ClawlyColors.textTertiary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                TextButton(
                    onClick = onTermsClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Terms",
                        fontSize = 12.sp,
                        color = ClawlyColors.textTertiary
                    )
                }

                Text(
                    text = "•",
                    fontSize = 12.sp,
                    color = ClawlyColors.textTertiary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                TextButton(
                    onClick = onPrivacyClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Privacy",
                        fontSize = 12.sp,
                        color = ClawlyColors.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    period: String,
    isSelected: Boolean,
    showBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "cardScale"
    )

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ClawlyColors.surface)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) ClawlyColors.accentPrimary else ClawlyColors.surfaceBorder,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { onClick() }
                .padding(vertical = 20.dp, horizontal = 12.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) ClawlyColors.textPrimary else ClawlyColors.secondaryText,
                letterSpacing = 1.5.sp
            )

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = price,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClawlyColors.textPrimary
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = period,
                    fontSize = 12.sp,
                    color = ClawlyColors.textTertiary
                )
            }
        }

        // "Most Popular" badge
        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-10).dp)
                    .shadow(
                        elevation = 4.dp,
                        spotColor = ClawlyColors.accentPrimary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(50)
                    )
                    .background(
                        ClawlyColors.accentPrimary,
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "BEST VALUE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
