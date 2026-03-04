package ai.clawly.app.presentation.paywall

import ai.clawly.app.R
import ai.clawly.app.data.remote.solana.SolanaOffer
import ai.clawly.app.ui.theme.ClawlyColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
    onDismiss: () -> Unit,
    onSuccessDismiss: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    var animateContent by remember { mutableStateOf(false) }
    var showCloseButton by remember { mutableStateOf(false) }

    // Convert SolanaOffer to CreditPackage for display
    // Use API offers if available, fallback to hardcoded for backward compatibility
    val creditPackages = remember(offers) {
        if (offers.isNotEmpty()) {
            offers.map { offer ->
                CreditPackage(
                    id = offer.id,
                    credits = offer.credits,
                    priceSol = offer.getDisplayPrice().replace(" SOL", ""),
                    pricePerCredit = offer.pricePerCredit ?: String.format("%.6f", offer.getLamports().toDouble() / 1_000_000_000 / offer.credits)
                )
            }
        } else {
            // Fallback to hardcoded packages
            listOf(
                CreditPackage("pack_test", 10, "0.001", "0.0001"),
                CreditPackage("pack_1", 2000, "0.267", "0.000134"),
                CreditPackage("pack_2", 5000, "0.533", "0.000107"),
                CreditPackage("pack_3", 8000, "0.800", "0.0001")
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(ClawlyColors.background, ClawlyColors.surface)
                )
            )
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
            // Top row with credits and close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .height(44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Credits display (left side)
                AnimatedVisibility(
                    visible = animateContent && isWalletConnected,
                    enter = fadeIn()
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(ClawlyColors.surfaceElevated)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "✦",
                            fontSize = 14.sp,
                            color = ClawlyColors.accentPrimary
                        )
                        Text(
                            text = formatCredits(currentCredits),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ClawlyColors.textPrimary
                        )
                        Text(
                            text = "credits",
                            fontSize = 12.sp,
                            color = ClawlyColors.secondaryText
                        )
                    }
                }

                // Spacer when credits not visible
                if (!isWalletConnected) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Close button (right side)
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

            Spacer(modifier = Modifier.height(8.dp))

            // Logo with sparkles
            Web3PaywallHeader(
                animateContent = animateContent,
                sparkleRotation = sparkleRotation
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            AnimatedVisibility(
                visible = animateContent,
                enter = fadeIn() + slideInVertically { 20 }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Join",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClawlyColors.textPrimary
                    )
                    Text(
                        text = "Clawly AI",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ClawlyColors.accentPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle
            AnimatedVisibility(
                visible = animateContent,
                enter = fadeIn() + slideInVertically { 10 }
            ) {
                Text(
                    text = "Pay only for what you use.\nNo subscriptions.",
                    fontSize = 14.sp,
                    color = ClawlyColors.secondaryText,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Credit packages grid
            AnimatedVisibility(
                visible = animateContent,
                enter = fadeIn() + slideInVertically { 30 }
            ) {
                if (isLoadingOffers) {
                    // Loading state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = ClawlyColors.accentPrimary,
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "Loading offers...",
                                fontSize = 14.sp,
                                color = ClawlyColors.secondaryText
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // First row - 2 packages
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            creditPackages.take(2).forEach { pkg ->
                                CreditPackageCard(
                                    package_ = pkg,
                                    isSelected = selectedPackageId == pkg.id,
                                    isEnabled = !isPurchasing,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSelectPackage(pkg.id)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        // Second row - remaining packages
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
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSelectPackage(pkg.id)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // Add empty space if odd number of packages in second row
                                if (creditPackages.drop(2).size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom section
            Web3PaywallBottomSection(
                walletAddress = walletAddress,
                isWalletConnected = isWalletConnected,
                isConnecting = isConnecting,
                isPurchasing = isPurchasing,
                isWaitingForConfirmation = isWaitingForConfirmation,
                isRestoringCredits = isRestoringCredits,
                canPurchase = isWalletConnected && selectedPackageId != null,
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

@Composable
private fun Web3PaywallHeader(
    animateContent: Boolean,
    sparkleRotation: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
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

        // Sparkles
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

        // Logo
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

        Image(
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
private fun CreditPackageCard(
    package_: CreditPackage,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isSelected) ClawlyColors.accentPrimary.copy(alpha = 0.1f)
                    else ClawlyColors.surface
                )
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) ClawlyColors.accentPrimary
                           else if (isEnabled) ClawlyColors.surfaceBorder
                           else ClawlyColors.surfaceBorder.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(enabled = isEnabled) { onClick() }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Credits amount
            Text(
                text = "${package_.credits}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) ClawlyColors.textPrimary
                       else ClawlyColors.textPrimary.copy(alpha = 0.5f)
            )
            Text(
                text = "credits",
                fontSize = 12.sp,
                color = if (isEnabled) ClawlyColors.secondaryText
                       else ClawlyColors.secondaryText.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Price in SOL
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_solana),
                    contentDescription = "SOL",
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            alpha = if (isEnabled) 1f else 0.5f
                        }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = package_.priceSol,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) ClawlyColors.textPrimary
                           else ClawlyColors.textPrimary.copy(alpha = 0.5f)
                )
                Text(
                    text = " SOL",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isEnabled) ClawlyColors.secondaryText
                           else ClawlyColors.secondaryText.copy(alpha = 0.5f)
                )
            }
        }

        // Selection indicator - X in top right with offset
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(22.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .background(
                        ClawlyColors.accentPrimary,
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun Web3PaywallBottomSection(
    walletAddress: String?,
    isWalletConnected: Boolean,
    isConnecting: Boolean,
    isPurchasing: Boolean,
    isWaitingForConfirmation: Boolean,
    isRestoringCredits: Boolean,
    canPurchase: Boolean,
    animateContent: Boolean,
    onConnectWallet: () -> Unit,
    onPurchase: () -> Unit,
    onRestoreCredits: () -> Unit
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
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(ClawlyColors.surfaceElevated)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = shortenAddress(walletAddress),
                        fontSize = 14.sp,
                        color = ClawlyColors.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Main CTA Button
        AnimatedVisibility(
            visible = animateContent,
            enter = fadeIn() + slideInVertically { 20 }
        ) {
            Button(
                onClick = if (isWalletConnected) onPurchase else onConnectWallet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = if (isWalletConnected) canPurchase && !isPurchasing && !isWaitingForConfirmation else !isConnecting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClawlyColors.accentPrimary,
                    disabledContainerColor = ClawlyColors.accentPrimary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(14.dp)
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

        // Restore Credits button (when wallet connected)
        if (isWalletConnected) {
            Spacer(modifier = Modifier.height(2.dp))
            AnimatedVisibility(
                visible = animateContent,
                enter = fadeIn()
            ) {
                TextButton(
                    onClick = onRestoreCredits,
                    enabled = !isPurchasing && !isWaitingForConfirmation && !isRestoringCredits
                ) {
                    if (isRestoringCredits) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = ClawlyColors.accentPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isRestoringCredits) "Restoring..." else "Restore Credits",
                        fontSize = 14.sp,
                        color = ClawlyColors.accentPrimary
                    )
                }
            }
        }

        // Helper text
        if (!isWalletConnected) {
            Spacer(modifier = Modifier.height(12.dp))
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
    }
}

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
            // Success icon with glow
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Glow
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF4CAF50).copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                // Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF4CAF50), CircleShape),
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

            // Success text
            Text(
                text = "Purchase Successful!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = ClawlyColors.textPrimary
            )

            // Credits received
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

            // Continue button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClawlyColors.accentPrimary
                ),
                shape = RoundedCornerShape(14.dp)
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
