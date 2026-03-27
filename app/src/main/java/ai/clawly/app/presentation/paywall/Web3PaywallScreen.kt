package ai.clawly.app.presentation.paywall

import ai.clawly.app.R
import ai.clawly.app.data.remote.solana.SolanaOffer
import ai.clawly.app.ui.theme.ClawlyColors
import ai.clawly.app.ui.theme.ClawlyRadius
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class CreditPackage(
    val id: String,
    val credits: Int,
    val priceSol: String,
    val pricePerCredit: String
)

private val ScrimColor = Color(0xFF141419)

private val Perks = listOf(
    "Ready-to-use OpenClaw agent",
    "Thousands of skills via ClawHub",
    "MCP integrations built-in",
    "Top AI models, no setup needed",
    "Voice & image capabilities"
)

@Composable
fun Web3PaywallScreen(
    walletAddress: String?,
    isWalletConnected: Boolean,
    isConnecting: Boolean,
    isPurchasing: Boolean,
    isLoadingOffers: Boolean = false,
    isWaitingForConfirmation: Boolean = false,
    isRestoringCredits: Boolean = false,
    offers: List<SolanaOffer> = emptyList(),
    selectedPackageId: String?,
    currentCredits: Int = 0,
    purchaseSuccess: Boolean = false,
    creditsReceived: Int = 0,
    onConnectWallet: () -> Unit,
    onSelectPackage: (String) -> Unit,
    onPurchase: () -> Unit,
    onRestoreCredits: () -> Unit,
    onRetryLoadOffers: () -> Unit = {},
    onDismiss: () -> Unit,
    onSuccessDismiss: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var animateContent by remember { mutableStateOf(false) }
    var showCloseButton by remember { mutableStateOf(false) }

    val creditPackages = remember(offers) {
        offers.map { offer ->
            CreditPackage(
                id = offer.id,
                credits = offer.credits,
                priceSol = offer.getDisplayPrice().replace(" SOL", ""),
                pricePerCredit = offer.pricePerCredit ?: String.format("%.6f", offer.getLamports().toDouble() / 1_000_000_000 / offer.credits)
            )
        }
    }

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

    // Select first package by default
    LaunchedEffect(creditPackages) {
        if (selectedPackageId == null && creditPackages.isNotEmpty()) {
            onSelectPackage(creditPackages.first().id)
        }
    }

    LaunchedEffect(Unit) {
        animateContent = true
        delay(2000)
        showCloseButton = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClawlyColors.background)
    ) {
        // ── Giant Clawly (background, upper area) ──
        GiantClawlyBackground(
            animateContent = animateContent,
            sparkleRotation = sparkleRotation,
            screenHeight = screenHeight
        )

        // ── Gradient scrim ──
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(screenHeight * 0.12f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight * 0.30f)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Transparent,
                                0.35f to ScrimColor.copy(alpha = 0.60f),
                                0.60f to ScrimColor.copy(alpha = 0.90f),
                                1.00f to ScrimColor
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(ScrimColor)
            )
        }

        // ── Foreground content — bottom-anchored ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close button (top-right, fixed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .height(44.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
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
                        enabled = !isPurchasing && !isConnecting && !isWaitingForConfirmation,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color.Black.copy(alpha = 0.40f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.80f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Scrollable middle content (fills remaining space above bottom)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                // Title — "Join" / "Clawly Pro"
                AnimatedVisibility(
                    visible = animateContent,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 20 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Join",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClawlyColors.textPrimary
                        )
                        Text(
                            text = "Clawly Pro",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = ClawlyColors.accentPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Perks checklist
                AnimatedVisibility(
                    visible = animateContent,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 20 }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Perks.forEach { perk ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ClawlyColors.accentPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = perk,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.90f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Credit packages grid
                AnimatedVisibility(
                    visible = animateContent,
                    enter = fadeIn(tween(500, delayMillis = 100)) + slideInVertically(tween(500, delayMillis = 100)) { 20 }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { clip = false }
                            .animateContentSize(animationSpec = tween(300))
                    ) {
                        if (isLoadingOffers) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = ClawlyColors.accentPrimary,
                                    strokeWidth = 3.dp
                                )
                            }
                        } else if (creditPackages.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Failed to load credit packages",
                                    fontSize = 14.sp,
                                    color = ClawlyColors.secondaryText
                                )
                                Button(
                                    onClick = onRetryLoadOffers,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ClawlyColors.accentPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Try Again", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 10.dp)
                                .graphicsLayer { clip = false },
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // First row - 2 packages
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { clip = false },
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                creditPackages.take(2).forEachIndexed { index, pkg ->
                                    CreditPackageCard(
                                        package_ = pkg,
                                        isSelected = selectedPackageId == pkg.id,
                                        isEnabled = !isPurchasing,
                                        showBestOffer = index == 1,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSelectPackage(pkg.id)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            // Second row
                            if (creditPackages.size > 2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    creditPackages.drop(2).take(2).forEach { pkg ->
                                        CreditPackageCard(
                                            package_ = pkg,
                                            isSelected = selectedPackageId == pkg.id,
                                            isEnabled = !isPurchasing,
                                            showBestOffer = false,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onSelectPackage(pkg.id)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (creditPackages.drop(2).size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Bottom pinned section ──
            Web3PaywallBottomSection(
                walletAddress = walletAddress,
                isWalletConnected = isWalletConnected,
                isConnecting = isConnecting,
                isPurchasing = isPurchasing,
                isWaitingForConfirmation = isWaitingForConfirmation,
                isRestoringCredits = isRestoringCredits,
                canPurchase = isWalletConnected && selectedPackageId != null,
                currentCredits = currentCredits,
                animateContent = animateContent,
                onConnectWallet = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConnectWallet()
                },
                onPurchase = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPurchase()
                },
                onRestoreCredits = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRestoreCredits()
                }
            )
        }

        // Success overlay
        if (purchaseSuccess) {
            PurchaseSuccessOverlay(
                creditsReceived = creditsReceived,
                onDismiss = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSuccessDismiss()
                }
            )
        }
    }
}

// ── Giant Clawly background ──

@Composable
private fun GiantClawlyBackground(
    animateContent: Boolean,
    sparkleRotation: Float,
    screenHeight: androidx.compose.ui.unit.Dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "crabFloat")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "crabFloat"
    )

    val crabScale by animateFloatAsState(
        targetValue = if (animateContent) 1f else 0.6f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "crabScale"
    )
    val crabAlpha by animateFloatAsState(
        targetValue = if (animateContent) 1f else 0f,
        animationSpec = tween(700),
        label = "crabAlpha"
    )

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset(
                    x = screenWidth * 0.15f - 225.dp,
                    y = screenHeight * 0.10f - 225.dp + floatOffset.dp
                )
                .size(600.dp),
            contentAlignment = Alignment.Center
        ) {
            // Radial glow
            Box(
                modifier = Modifier
                    .size(600.dp)
                    .background(
                        Brush.radialGradient(
                            colorStops = arrayOf(
                                0.13f to ClawlyColors.accentPrimary.copy(alpha = 0.25f),
                                0.33f to ClawlyColors.accentPrimary.copy(alpha = 0.10f),
                                0.66f to ClawlyColors.accentPrimary.copy(alpha = 0.03f),
                                1.00f to Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Sparkles orbiting
            repeat(6) { index ->
                val angle = (index * 60f + sparkleRotation) * PI / 180
                val offsetX = (cos(angle) * 160).toFloat()
                val offsetY = (sin(angle) * 160).toFloat()

                Text(
                    text = "✦",
                    fontSize = if (index % 2 == 0) 20.sp else 14.sp,
                    color = ClawlyColors.accentPrimary.copy(alpha = 0.60f),
                    modifier = Modifier.offset(x = offsetX.dp, y = offsetY.dp)
                )
            }

            // Crab image
            Image(
                painter = painterResource(id = R.drawable.clawly),
                contentDescription = "Clawly",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(450.dp)
                    .shadow(
                        elevation = 50.dp,
                        spotColor = ClawlyColors.accentPrimary.copy(alpha = 0.40f),
                        shape = CircleShape
                    )
                    .graphicsLayer {
                        scaleX = crabScale
                        scaleY = crabScale
                        alpha = crabAlpha
                        rotationZ = 15f
                        translationY = floatOffset * 3
                    }
            )
        }
    }
}

// ── Credit Package Card ──

@Composable
private fun CreditPackageCard(
    package_: CreditPackage,
    isSelected: Boolean,
    isEnabled: Boolean,
    showBestOffer: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "cardScale"
    )

    val cardAlpha = if (isSelected) 1f else 0.85f

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
                clip = false
            }
            .padding(top = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) Modifier.shadow(
                        elevation = 12.dp,
                        spotColor = ClawlyColors.accentPrimary.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(ClawlyRadius.md.dp)
                    ) else Modifier
                )
                .clip(RoundedCornerShape(ClawlyRadius.md.dp))
                .background(ClawlyColors.surface)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) ClawlyColors.accentPrimary
                    else ClawlyColors.surfaceBorder,
                    shape = RoundedCornerShape(ClawlyRadius.md.dp)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = isEnabled
                ) { onClick() }
                .graphicsLayer { alpha = if (isEnabled) cardAlpha else 0.5f }
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // "10 credits"
            Text(
                text = "${package_.credits} credits",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ClawlyColors.textPrimary
            )

            // icon + 0.001 SOL
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_solana),
                    contentDescription = "SOL",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${package_.priceSol} SOL",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClawlyColors.secondaryText
                )
            }
        }

        // "BEST OFFER" badge
        if (showBestOffer) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-20).dp)
                    .shadow(
                        elevation = 4.dp,
                        spotColor = ClawlyColors.accentPrimary.copy(alpha = 0.40f),
                        shape = RoundedCornerShape(50)
                    )
                    .background(
                        ClawlyColors.accentPrimary,
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "BEST OFFER",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// ── Bottom Section (pinned) ──

@Composable
private fun ColumnScope.Web3PaywallBottomSection(
    walletAddress: String?,
    isWalletConnected: Boolean,
    isConnecting: Boolean,
    isPurchasing: Boolean,
    isWaitingForConfirmation: Boolean,
    isRestoringCredits: Boolean,
    canPurchase: Boolean,
    currentCredits: Int,
    animateContent: Boolean,
    onConnectWallet: () -> Unit,
    onPurchase: () -> Unit,
    onRestoreCredits: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Connected wallet info
        if (isWalletConnected && walletAddress != null) {
            AnimatedVisibility(
                visible = animateContent,
                enter = fadeIn()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ClawlyRadius.md.dp))
                        .background(ClawlyColors.surfaceElevated)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ClawlyColors.success, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = shortenAddress(walletAddress),
                        fontSize = 14.sp,
                        color = ClawlyColors.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    if (currentCredits > 0) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "✦ ${formatCredits(currentCredits)}",
                            fontSize = 13.sp,
                            color = ClawlyColors.accentPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // CTA Button
        AnimatedVisibility(
            visible = animateContent,
            enter = fadeIn(tween(500, delayMillis = 200)) + slideInVertically(tween(500, delayMillis = 200)) { 20 }
        ) {
            Button(
                onClick = if (isWalletConnected) onPurchase else onConnectWallet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = if (isWalletConnected) canPurchase && !isPurchasing && !isWaitingForConfirmation else !isConnecting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClawlyColors.accentPrimary,
                    disabledContainerColor = ClawlyColors.accentPrimary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(ClawlyRadius.md.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isConnecting || isPurchasing -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                        isWaitingForConfirmation -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Confirming...",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = if (isWalletConnected) "Purchase Credits" else "Connect Wallet",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // "No commitment" text
        Spacer(modifier = Modifier.height(6.dp))
        AnimatedVisibility(
            visible = animateContent,
            enter = fadeIn()
        ) {
            Text(
                text = "Pay only for what you use \u00B7 No subscriptions",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ClawlyColors.secondaryText
            )
        }

        // Helper text when wallet not connected
        if (!isWalletConnected) {
            Spacer(modifier = Modifier.height(4.dp))
            AnimatedVisibility(
                visible = animateContent,
                enter = fadeIn()
            ) {
                Text(
                    text = "Connect your Solana wallet to purchase credits",
                    fontSize = 12.sp,
                    color = ClawlyColors.textTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Footer: Restore Credits (very bottom)
        AnimatedVisibility(
            visible = animateContent,
            enter = fadeIn()
        ) {
            TextButton(
                onClick = onRestoreCredits,
                enabled = !isPurchasing && !isWaitingForConfirmation && !isRestoringCredits,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                if (isRestoringCredits) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = ClawlyColors.textTertiary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (isRestoringCredits) "Restoring..." else "Restore Credits",
                    fontSize = 11.sp,
                    color = ClawlyColors.textTertiary
                )
            }
        }
    }
}

// ── Success Overlay ──

@Composable
private fun PurchaseSuccessOverlay(
    creditsReceived: Int,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClawlyColors.background.copy(alpha = 0.95f))
            .clickable(enabled = false) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ClawlyColors.success.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(ClawlyColors.success, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Text(
                text = "Purchase Successful!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = ClawlyColors.textPrimary
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "+$creditsReceived",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ClawlyColors.accentPrimary
                )
                Text(
                    text = "credits added",
                    fontSize = 16.sp,
                    color = ClawlyColors.secondaryText
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClawlyColors.accentPrimary
                ),
                shape = RoundedCornerShape(ClawlyRadius.md.dp)
            ) {
                Text(
                    text = "Continue",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Utilities ──

private fun shortenAddress(address: String): String {
    return if (address.length > 10) {
        "${address.take(6)}...${address.takeLast(4)}"
    } else {
        address
    }
}

private fun formatCredits(credits: Int): String {
    return when {
        credits >= 1_000_000 -> String.format("%.1fM", credits / 1_000_000.0)
        credits >= 1_000 -> String.format("%.1fK", credits / 1_000.0)
        else -> credits.toString()
    }
}
