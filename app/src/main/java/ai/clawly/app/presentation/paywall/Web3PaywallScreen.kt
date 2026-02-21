package ai.clawly.app.presentation.paywall

import ai.clawly.app.R
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
    selectedPackageId: String?,
    onConnectWallet: () -> Unit,
    onSelectPackage: (String) -> Unit,
    onPurchase: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    var animateContent by remember { mutableStateOf(false) }
    var showCloseButton by remember { mutableStateOf(false) }

    val creditPackages = remember {
        listOf(
            CreditPackage("starter", 100, "0.05", "0.0005"),
            CreditPackage("standard", 500, "0.20", "0.0004"),
            CreditPackage("pro", 1000, "0.35", "0.00035"),
            CreditPackage("whale", 5000, "1.50", "0.0003")
        )
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
    LaunchedEffect(Unit) {
        if (selectedPackageId == null) {
            onSelectPackage(creditPackages.first().id)
        }
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
                        enabled = !isPurchasing && !isConnecting,
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
                        text = "Get",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClawlyColors.textPrimary
                    )
                    Text(
                        text = "Clawly Credits",
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
                                isEnabled = isWalletConnected && !isPurchasing,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelectPackage(pkg.id)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    // Second row - 2 packages
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        creditPackages.drop(2).forEach { pkg ->
                            CreditPackageCard(
                                package_ = pkg,
                                isSelected = selectedPackageId == pkg.id,
                                isEnabled = isWalletConnected && !isPurchasing,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelectPackage(pkg.id)
                                },
                                modifier = Modifier.weight(1f)
                            )
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
                canPurchase = isWalletConnected && selectedPackageId != null,
                animateContent = animateContent,
                onConnectWallet = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConnectWallet()
                },
                onPurchase = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPurchase()
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
                Text(
                    text = "◎ ",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) Color(0xFF9945FF)
                           else Color(0xFF9945FF).copy(alpha = 0.5f)
                )
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

            // Price per credit
            Text(
                text = "◎${package_.pricePerCredit}/credit",
                fontSize = 10.sp,
                color = if (isEnabled) ClawlyColors.textTertiary
                       else ClawlyColors.textTertiary.copy(alpha = 0.5f)
            )
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
    canPurchase: Boolean,
    animateContent: Boolean,
    onConnectWallet: () -> Unit,
    onPurchase: () -> Unit
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
            .padding(bottom = 24.dp),
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
                enabled = if (isWalletConnected) canPurchase && !isPurchasing else !isConnecting,
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
                    if (isConnecting || isPurchasing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isWalletConnected) "Purchase Credits" else "Connect Wallet",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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
