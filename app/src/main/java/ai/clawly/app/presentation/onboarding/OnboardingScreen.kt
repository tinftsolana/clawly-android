package ai.clawly.app.presentation.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.clawly.app.BuildConfig
import ai.clawly.app.R
import ai.clawly.app.ui.theme.ClawlyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3-page onboarding matching iOS OnboardingView.swift exactly
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Logo animations
    val floatOffset by rememberInfiniteTransition(label = "float").animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )

    val glowOpacity by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowOpacity"
    )

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
                .height(screenHeight * 0.6f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ClawlyColors.accentPrimary.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(screenWidth.value / 2, 0f),
                        radius = screenHeight.value * 0.6f
                    )
                )
        )

        // Clawly logo (pages 0-1 only)
        if (pagerState.currentPage < 2) {
            ClawlyLogo(
                currentPage = pagerState.currentPage,
                screenWidth = screenWidth.value,
                screenHeight = screenHeight.value,
                floatOffset = floatOffset,
                glowOpacity = glowOpacity
            )
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Page content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> OnboardingPage1()
                    1 -> OnboardingPage2()
                    2 -> OnboardingPage3()
                }
            }

            // Bottom section
            BottomSection(
                currentPage = pagerState.currentPage,
                totalPages = 3,
                onContinue = {
                    if (pagerState.currentPage < 2) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onComplete()
                    }
                }
            )
        }
    }
}

@Composable
private fun ClawlyLogo(
    currentPage: Int,
    screenWidth: Float,
    screenHeight: Float,
    floatOffset: Float,
    glowOpacity: Float
) {
    val logoSize = screenWidth * 0.75f

    // Position: page 0 from right, page 1 from left
    val xOffset by animateFloatAsState(
        targetValue = if (currentPage == 0) {
            screenWidth - logoSize * 0.35f - screenWidth / 2
        } else {
            logoSize * 0.35f - screenWidth * 0.05f - screenWidth / 2
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "xOffset"
    )

    val rotation by animateFloatAsState(
        targetValue = if (currentPage == 0) -8f else 8f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = xOffset
                translationY = screenHeight * 0.55f - screenHeight / 2 + 40 + floatOffset
            },
        contentAlignment = Alignment.Center
    ) {
        // Glow
        Box(
            modifier = Modifier
                .size(500.dp)
                .blur(60.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ClawlyColors.accentPrimary.copy(alpha = glowOpacity),
                            ClawlyColors.accentPrimary.copy(alpha = glowOpacity * 0.4f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Logo image
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.clawly),
            contentDescription = "Clawly",
            modifier = Modifier
                .size(logoSize.dp)
                .rotate(rotation)
                .shadow(
                    elevation = 40.dp,
                    spotColor = ClawlyColors.accentPrimary.copy(alpha = 0.6f),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun OnboardingPage1() {
    var showTitle by remember { mutableStateOf(false) }
    var showClawlyText by remember { mutableStateOf(false) }
    var showSwipe by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        showTitle = true
        delay(300)
        showClawlyText = true
        delay(500)
        showSwipe = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        // Subtitle
        AnimatedVisibility(
            visible = showTitle,
            enter = fadeIn() + slideInVertically { 20 }
        ) {
            Text(
                text = "Meet your AI Assistant",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = ClawlyColors.secondaryText
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // CLAWLY - THE HERO with shimmer
        AnimatedVisibility(
            visible = showClawlyText,
            enter = fadeIn()
        ) {
            ShimmerText(
                text = "Clawly",
                modifier = Modifier.scale(if (showClawlyText) 1f else 0.8f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Swipe hint
        AnimatedVisibility(
            visible = showSwipe,
            enter = fadeIn()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Swipe to continue",
                    fontSize = 14.sp,
                    color = ClawlyColors.textMuted
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = ClawlyColors.textMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun ShimmerText(
    text: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(300)
        ),
        label = "shimmerPhase"
    )

    Box(modifier = modifier) {
        // Base text
        Text(
            text = text,
            fontSize = 64.sp,
            fontWeight = FontWeight.SemiBold,
            color = ClawlyColors.accentPrimary
        )

        // Shimmer overlay
        Text(
            text = text,
            fontSize = 64.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Transparent,
            modifier = Modifier.graphicsLayer {
                // Apply shimmer gradient
                alpha = 0.8f
            },
            style = LocalTextStyle.current.copy(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        (shimmerPhase - 0.3f).coerceIn(0f, 1f) to Color.Transparent,
                        shimmerPhase.coerceIn(0f, 1f) to Color.White.copy(alpha = 0.8f),
                        (shimmerPhase + 0.3f).coerceIn(0f, 1f) to Color.Transparent,
                        1f to Color.Transparent
                    )
                )
            )
        )
    }
}

@Composable
private fun OnboardingPage2() {
    var showContent by remember { mutableStateOf(false) }
    var chipsFloating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showContent = true
        delay(100)
        chipsFloating = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        // Title
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn() + slideInVertically { 20 }
        ) {
            Text(
                text = "Clawly can",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ClawlyColors.textPrimary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Floating chips
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn()
        ) {
            if (BuildConfig.IS_WEB3) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FloatingChip("", "Send SOL", 0.0, chipsFloating, iconRes = R.drawable.ic_solana)
                        FloatingChip("\uD83D\uDD04", "Swap", 0.3, chipsFloating)
                        FloatingChip("\uD83D\uDCB0", "Lend USDT", 0.6, chipsFloating)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FloatingChip("\uD83C\uDFE6", "Borrow", 0.8, chipsFloating)
                        FloatingChip("\uD83E\uDD16", "Auto Trade", 0.2, chipsFloating)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FloatingChip("\uD83D\uDCCA", "Portfolio", 0.5, chipsFloating)
                        FloatingChip("\uD83D\uDD25", "DeFi", 0.4, chipsFloating)
                        FloatingChip("\uD83D\uDCB8", "Stake", 0.7, chipsFloating)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FloatingChip("\uD83D\uDCC8", "Analytics", 0.1, chipsFloating)
                        FloatingChip("✨", "& More", 0.9, chipsFloating)
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FloatingChip("\uD83D\uDCBB", "Code", 0.0, chipsFloating)
                        FloatingChip("\uD83D\uDCE7", "Emails", 0.3, chipsFloating)
                        FloatingChip("\uD83D\uDCC5", "Calendar", 0.6, chipsFloating)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FloatingChip("✈\uFE0F", "Travel", 0.8, chipsFloating)
                        FloatingChip("\uD83D\uDCDD", "Writing", 0.2, chipsFloating)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FloatingChip("\uD83C\uDF99\uFE0F", "Voice", 0.5, chipsFloating)
                        FloatingChip("\uD83E\uDDEE", "Math", 0.4, chipsFloating)
                        FloatingChip("\uD83C\uDFA8", "Creative", 0.7, chipsFloating)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FloatingChip("\uD83D\uDCCA", "Research", 0.1, chipsFloating)
                        FloatingChip("✨", "& More", 0.9, chipsFloating)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FloatingChip(
    emoji: String,
    text: String,
    delayFraction: Double,
    isFloating: Boolean,
    iconRes: Int? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "chip_$text")
    val offset by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOut, delayMillis = (delayFraction * 1000).toInt()),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset_$text"
    )

    Row(
        modifier = Modifier
            .offset(y = if (isFloating) offset.dp else 0.dp)
            .background(
                ClawlyColors.surfaceElevated,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(text = emoji, fontSize = 16.sp)
        }
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = ClawlyColors.textPrimary
        )
    }
}

@Composable
private fun OnboardingPage3() {
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        showContent = true
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Title - with horizontal padding
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Use Clawly for",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClawlyColors.secondaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                ChatDemo()
            }
        }

        // Gap between title/chat and features
        Spacer(modifier = Modifier.height(40.dp))

        // "..and more!" text
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn()
        ) {
            Text(
                text = "..and more!",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = ClawlyColors.secondaryText
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Auto-scrolling capabilities row - NO horizontal padding, full width
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn()
        ) {
            InfiniteScrollRow()
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ChatDemo() {
    val chatCases = if (BuildConfig.IS_WEB3) listOf(
        Triple("Send", "Send 2 SOL to Alex", "Sent 2 SOL to Alex.sol \u2705"),
        Triple("Swap", "Swap 10 USDC to SOL", "Swapped 10 USDC \u2192 0.058 SOL on Jupiter."),
        Triple("Lend", "Lend 500 USDT on Kamino", "Deposited 500 USDT at 8.2% APY."),
        Triple("Borrow", "Borrow SOL against USDC", "Borrowed 5 SOL, collateral: 800 USDC."),
        Triple("Auto Trade", "DCA $50 into SOL weekly", "Set up: $50 \u2192 SOL every Monday."),
        Triple("Stake", "Stake my SOL", "Staked 20 SOL with Marinade, 7.1% APY."),
        Triple("Portfolio", "Show my portfolio", "SOL: $2,340 \u2022 USDC: $500 \u2022 JUP: $120"),
        Triple("DeFi", "Best yield on USDT?", "Kamino 8.2% \u2022 MarginFi 7.8% \u2022 Drift 7.5%")
    ) else listOf(
        Triple("Emails", "Check my emails", "Found 3 unread from your boss about Q4."),
        Triple("Calendar", "What's on today?", "Standup at 10am, design review at 3pm."),
        Triple("Writing", "Write a tweet", "\"Excited to share our new feature! \uD83D\uDE80\""),
        Triple("Travel", "Book Paris flight", "Air France $450, departs 8:30am."),
        Triple("Coding", "Debug this code", "Bug on line 42 - null pointer fixed!"),
        Triple("Research", "Summarize article", "AI trends 2026: personal assistants."),
        Triple("Planning", "Plan weekend", "Sat: brunch 11am. Sun: relax day."),
        Triple("Shopping", "Best headphones?", "Sony WH-1000XM5, $349. Top rated!")
    )

    var currentCaseIndex by remember { mutableIntStateOf(0) }
    var usecaseText by remember { mutableStateOf("") }
    var userText by remember { mutableStateOf("") }
    var assistantText by remember { mutableStateOf("") }
    var showUserBubble by remember { mutableStateOf(false) }
    var showAssistantBubble by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(true) }

    LaunchedEffect(currentCaseIndex, isRunning) {
        if (!isRunning) return@LaunchedEffect

        val currentCase = chatCases[currentCaseIndex]

        // Reset
        showUserBubble = false
        showAssistantBubble = false
        userText = ""
        assistantText = ""
        usecaseText = ""

        delay(100)

        // Type usecase
        for (char in currentCase.first) {
            if (!isRunning) return@LaunchedEffect
            usecaseText += char
            delay(60)
        }

        delay(200)

        // Show user bubble and type
        showUserBubble = true
        for (char in currentCase.second) {
            if (!isRunning) return@LaunchedEffect
            userText += char
            delay(40)
        }

        delay(400)

        // Show assistant bubble and type
        showAssistantBubble = true
        for (char in currentCase.third) {
            if (!isRunning) return@LaunchedEffect
            assistantText += char
            delay(25)
        }

        delay(1500)

        // Next case
        currentCaseIndex = (currentCaseIndex + 1) % chatCases.size
    }

    DisposableEffect(Unit) {
        onDispose { isRunning = false }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Usecase title - 1.5x bigger
        Text(
            text = usecaseText.ifEmpty { " " },
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = ClawlyColors.accentPrimary
        )

        Spacer(modifier = Modifier.height(60.dp))

        // Chat window
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    ClawlyColors.surface,
                    shape = RoundedCornerShape(16.dp)
                )
                .shadow(
                    elevation = 12.dp,
                    spotColor = Color.Black.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ClawlyColors.surfaceElevated)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.clawly),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clawly",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ClawlyColors.textPrimary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ClawlyColors.terminalGreen, CircleShape)
                    )
                }

                // Messages area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(ClawlyColors.surface)
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        // User bubble
                        if (showUserBubble && userText.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = userText,
                                    fontSize = 14.sp,
                                    color = ClawlyColors.textPrimary,
                                    modifier = Modifier
                                        .background(
                                            ClawlyColors.bubbleUser,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Assistant bubble
                        if (showAssistantBubble) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.Top
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(id = R.drawable.clawly),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = assistantText.ifEmpty { "..." },
                                    fontSize = 14.sp,
                                    color = ClawlyColors.textPrimary,
                                    modifier = Modifier
                                        .background(
                                            ClawlyColors.bubbleAssistant,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Input area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ClawlyColors.surface)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Message Clawly...",
                        fontSize = 13.sp,
                        color = ClawlyColors.textMuted,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                ClawlyColors.surfaceElevated,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                ClawlyColors.accentPrimary.copy(alpha = 0.4f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(-90f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfiniteScrollRow() {
    val items = if (BuildConfig.IS_WEB3) listOf(
        "◎ Send SOL",
        "\uD83D\uDD04 Token Swap",
        "\uD83D\uDCB0 Lend USDT",
        "\uD83C\uDFE6 Borrow",
        "\uD83E\uDD16 Auto Trade",
        "\uD83D\uDCC8 DCA",
        "\uD83D\uDD25 DeFi Yield",
        "\uD83D\uDCB8 Stake SOL",
        "\uD83D\uDCCA Portfolio",
        "\uD83D\uDCC9 Analytics",
        "\uD83D\uDD0D Token Info",
        "\uD83D\uDCB1 Price Alerts",
        "\uD83E\uDE99 NFTs",
        "⚡ Jupiter",
        "\uD83C\uDF0A Marinade",
        "\uD83D\uDEE1\uFE0F MarginFi",
        "\uD83C\uDFAF Drift",
        "\uD83D\uDD11 Wallet",
        "\uD83D\uDCDC TX History",
        "\uD83D\uDCC0 Airdrop Check",
        "\uD83E\uDDD1\u200D\uD83D\uDCBB Sniper",
        "\uD83C\uDF1F Memecoins",
        "\uD83D\uDD17 On-Chain",
        "\uD83E\uDDE0 AI Signals"
    ) else listOf(
        "⚡ Skills",
        "\uD83D\uDD27 MCP Tools",
        "\uD83D\uDCC5 Meetings",
        "\uD83D\uDCDD Notes",
        "⏰ Reminders",
        "\uD83C\uDF10 Translate",
        "\uD83D\uDCCB Summarize",
        "\uD83D\uDD0D Analyze",
        "\uD83D\uDCC6 Schedule",
        "\uD83D\uDD0E Search",
        "\uD83E\uDDEE Calculate",
        "\uD83D\uDD04 Convert",
        "⛅ Weather",
        "\uD83D\uDCF0 News",
        "\uD83D\uDCC8 Stocks",
        "\uD83D\uDCB0 Crypto",
        "\uD83C\uDF73 Recipes",
        "\uD83D\uDCAA Workouts",
        "\uD83E\uDDD8 Meditate",
        "\uD83D\uDCDA Learn",
        "❓ Quiz",
        "\uD83D\uDDC2\uFE0F Flashcards",
        "\uD83D\uDCA1 Brainstorm",
        "✏\uFE0F Draft",
        "✅ Proofread",
        "\uD83D\uDCD0 Format",
        "\uD83D\uDCE4 Export",
        "\uD83D\uDD17 Share",
        "\uD83E\uDD16 Automate",
        "\uD83D\uDD0C Integrate",
        "\uD83D\uDCBC Business",
        "\uD83C\uDFAF Goals",
        "\uD83D\uDCC9 Analytics",
        "\uD83D\uDCE9 Inbox",
        "\uD83D\uDCF1 Apps",
        "\uD83D\uDEE0\uFE0F Settings",
        "\uD83C\uDFA4 Podcast",
        "\uD83C\uDFB5 Music",
        "\uD83C\uDFAC Video",
        "\uD83D\uDCF8 Photos"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "scroll")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -4000f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scrollOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clipToBounds()
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth(unbounded = true)
                .graphicsLayer { translationX = offset },
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Repeat items multiple times for infinite effect
            repeat(5) {
                items.forEach { item ->
                    Text(
                        text = item,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier
                            .background(
                                Color(0xFF333340),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomSection(
    currentPage: Int,
    totalPages: Int,
    onContinue: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Animate button appearance smoothly
    val buttonAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(300, easing = EaseInOut),
        label = "buttonAlpha"
    )

    val buttonScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(300, easing = EaseInOut),
        label = "buttonScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Page indicators (always visible, on top)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(totalPages) { index ->
                val width by animateDpAsState(
                    targetValue = if (currentPage == index) 24.dp else 8.dp,
                    animationSpec = spring(dampingRatio = 0.7f),
                    label = "indicator"
                )
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (currentPage == index) ClawlyColors.accentPrimary
                            else ClawlyColors.textMuted.copy(alpha = 0.4f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Button container with fixed height - no layout shift
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onContinue()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = buttonAlpha
                        scaleX = buttonScale
                        scaleY = buttonScale
                    },
                enabled = true,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClawlyColors.accentPrimary,
                    disabledContainerColor = ClawlyColors.accentPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (currentPage < totalPages - 1) "Continue" else "Get Started",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
