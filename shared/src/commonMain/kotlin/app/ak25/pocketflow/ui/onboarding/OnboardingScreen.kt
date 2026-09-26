package app.ak25.pocketflow.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ak25.pocketflow.ui.editor.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import pocketflow.shared.generated.resources.Res
import pocketflow.shared.generated.resources.pocketflow_logo
import pocketflow.shared.generated.resources.EduAUVICWANTHand
import pocketflow.shared.generated.resources.cat
import kotlin.math.absoluteValue

data class TemplateItem(
    val icon: String,
    val title: String,
    val description: String,
    val color: Color
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color(0xFFFAFAFA) // Pure minimalist neutral background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header: Logo and Skip Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minimal branding
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(Res.drawable.pocketflow_logo),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "pocketflow",
                        fontFamily = FontFamily(org.jetbrains.compose.resources.Font(Res.font.EduAUVICWANTHand)),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.W600,
                        color = Color(0xFF1E293B)
                    )
                }

                // Skip button always visible on all slides (including last slide)
                TextButton(
                    onClick = onFinished,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
                ) {
                    Text(
                        text = "Skip",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Sliders Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> OnboardingSlide1()
                        1 -> OnboardingSlide2()
                        2 -> OnboardingSlide3()
                        3 -> OnboardingSlide4()
                    }
                }
            }

            // Bottom controls: Page Indicators and Continue button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
            ) {
                // Page Indicator Dots (Sky Blue accent color for selected slide marker)
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    repeat(4) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 20.dp else 6.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        )
                        val color = if (isSelected) Color(0xFF0EA5E9) else Color(0xFFE2E8F0)
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(6.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                // Premium Dark Sky Blue Gradient Button of Theme Accent
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                if (pagerState.currentPage < 3) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                } else {
                                    onFinished()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        Text(
                            text = if (pagerState.currentPage == 3) "Get Started" else "Continue",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingSlide1() {
    val infiniteTransition = rememberInfiniteTransition()

    // Smooth flow animations for y-offset, x-offset, and rotation fanning
    val floatY1 by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        )
    )
    val floatX1 by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )
    val rotation1 by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            contentAlignment = Alignment.Center
        ) {
            // Stack Card 3 (Back Card, Square: 160x160 dp)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = (floatY1 * 1.2f) - 30f
                        translationX = (floatX1 * -1f) - 20f
                        rotationZ = (rotation1 * -1.5f) - 4f
                    }
                    .scale(0.85f)
                    .size(160.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0EA5E9).copy(alpha = 0.04f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.NavAssets, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Prompt Template", color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Color.White))
                }
            }

            // Stack Card 2 (Middle Card, Square: 160x160 dp)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = (floatY1 * -0.8f) - 15f
                        translationX = (floatX1 * 1.1f) + 15f
                        rotationZ = (rotation1 * 1.2f) + 3f
                    }
                    .scale(0.92f)
                    .size(160.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0EA5E9).copy(alpha = 0.06f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.NavAssets, contentDescription = null, tint = Color(0xFF0EA5E9).copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Prompt Card", color = Color(0xFF0EA5E9).copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Color.White))
                }
            }

            // Stack Card 1 (Front main card, Square: 160x160 dp)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = floatY1 * 0.4f
                        translationX = floatX1 * 0.3f
                        rotationZ = rotation1 * 0.5f
                    }
                    .size(160.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0EA5E9).copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.NavAssets, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Prompt", color = Color(0xFF0EA5E9), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color(0xFF0EA5E9).copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", color = Color(0xFF0EA5E9), fontSize = 10.sp)
                        }
                    }
                    
                    Column(
                        modifier = Modifier
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text("Prompt Input", color = Color(0xFF334155), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("\"A Brown Cat Sitting\"", color = Color(0xFFAAAAAA), fontSize = 10.sp, maxLines = 3)
                    }
                }

                // Output Port on edge
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 6.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Design Workflows",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Connect triggers, models, and actions visually to map your AI chain setup.",
            fontSize = 14.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
fun OnboardingSlide2() {
    val infiniteTransition = rememberInfiniteTransition()

    // Smooth flow animations for y-offset, x-offset, and rotation fanning
    val floatY2 by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        )
    )
    val floatX2 by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )
    val rotation2 by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            // Stack Card 3 (Back Card, scaled down)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = (floatY2 * 1.2f) - 30f
                        translationX = (floatX2 * -1f) - 20f
                        rotationZ = (rotation2 * -1.5f) - 4f
                    }
                    .scale(0.85f)
                    .width(180.dp)
                    .height(200.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF3B82F6).copy(alpha = 0.04f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.NavSettings, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Image Template", color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Color.White))
                }
            }

            // Stack Card 2 (Middle Card, scaled down)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = (floatY2 * -0.8f) - 15f
                        translationX = (floatX2 * 1.1f) + 15f
                        rotationZ = (rotation2 * 1.2f) + 3f
                    }
                    .scale(0.92f)
                    .width(180.dp)
                    .height(200.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF3B82F6).copy(alpha = 0.06f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.NavSettings, contentDescription = null, tint = Color(0xFF3B82F6).copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Image Card", color = Color(0xFF3B82F6).copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Color.White))
                }
            }

            // Stack Card 1 (Front main Card with image - Bigger: 180.dp)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = floatY2 * 0.4f
                        translationX = floatX2 * 0.3f
                        rotationZ = rotation2 * 0.5f
                    }
                    .width(180.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
            ) {
                // Input Port on Left
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-6).dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3B82F6))
                )

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF3B82F6).copy(alpha = 0.08f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.NavSettings, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Image Card", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFF3B82F6).copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", color = Color(0xFF3B82F6), fontSize = 11.sp)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .background(Color.White)
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                    ) {
                        Text("veo3.1_fast", color = Color(0xFFAAAAAA), fontSize = 10.sp, maxLines = 1)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Displaying cat.jpg, fitted cleanly inside the card with ContentScale.Crop
                        Image(
                            painter = painterResource(Res.drawable.cat),
                            contentDescription = "A Brown Cat Sitting",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(0.5.dp, Color(0xFFE5E5EA), RoundedCornerShape(8.dp))
                        )
                    }
                }

                // Output Port on Right
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 6.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "AI Generation",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Process triggers through state-of-the-art AI models to compile media assets.",
            fontSize = 14.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
fun OnboardingSlide3() {
    val templates = remember {
        listOf(
            TemplateItem("🌐", "Localize", "Translate ad image language", Color(0xFFEFF6FF)),
            TemplateItem("👤", "Product UGC", "AI influencer product ad", Color(0xFFECFDF5)),
            TemplateItem("🛍️", "Product Ad", "High-conversion product video", Color(0xFFFDF2F8)),
            TemplateItem("🔄", "Product Swap", "Replace products in video", Color(0xFFFFF7ED)),
            TemplateItem("🎥", "Multishot", "Multi-angle AI generation", Color(0xFFF5F3FF)),
            TemplateItem("📈", "Marketing", "High-quality stock variants", Color(0xFFF8FAFC))
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Static scrollable Row of Node cards
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                templates.forEach { item ->
                    // Real Node Card Style: Square (160x160 dp)
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        // Input Port (Blue Port) on Left edge
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (-6).dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3B82F6))
                        )

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Node Header (matching real NodeUI card)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(item.color.copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.icon, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("▶", color = Color(0xFF1E293B), fontSize = 8.sp)
                                }
                            }

                            // Card Body description
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = item.description,
                                    fontSize = 9.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 12.sp
                                )
                            }
                        }

                        // Output Port (Green Port) on Right edge
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = 6.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Pre-built Templates",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select one of our popular preconfigured blueprints or build custom automation chains from scratch.",
            fontSize = 14.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 28.dp)
        )
    }
}

@Composable
fun OnboardingSlide4() {
    val infiniteTransition = rememberInfiniteTransition()

    // Cursors relative to Center alignment: keep coordinates within safe bounds (width:300, height:200)
    val c1X by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val c1Y by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val c2X by infiniteTransition.animateFloat(
        initialValue = 100f,
        targetValue = -90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val c2Y by infiniteTransition.animateFloat(
        initialValue = 50f,
        targetValue = -60f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            // Real Node Card Style: Square (160x160 dp) matching Slide 3
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .shadow(4.dp, RoundedCornerShape(16.dp))
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
            ) {
                // Input Port (Blue Port) on Left edge
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-6).dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3B82F6))
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    // Node Header (matching real NodeUI card)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF7ED).copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔄", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Product Swap",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF1E293B)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", color = Color(0xFF1E293B), fontSize = 8.sp)
                        }
                    }

                    // Card Body description
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Replace products in video automatically using AI video swapping models.",
                            fontSize = 9.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 12.sp
                        )
                    }
                }

                // Output Port (Green Port) on Right edge
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 6.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
            }

            // Cursor 1: Emily (Coral)
            Box(
                modifier = Modifier
                    .offset(x = c1X.dp, y = c1Y.dp)
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Canvas(modifier = Modifier.size(14.dp)) {
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, size.height * 0.7f)
                            lineTo(size.width * 0.4f, size.height * 0.7f)
                            lineTo(size.width * 0.2f, size.height)
                            close()
                        }
                        drawPath(path = path, color = Color(0xFFFF5A5F))
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFF5A5F), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Emily", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Cursor 2: Jordan (Sky Blue)
            Box(
                modifier = Modifier
                    .offset(x = c2X.dp, y = c2Y.dp)
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Canvas(modifier = Modifier.size(14.dp)) {
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, size.height * 0.7f)
                            lineTo(size.width * 0.4f, size.height * 0.7f)
                            lineTo(size.width * 0.2f, size.height)
                            close()
                        }
                        drawPath(path = path, color = Color(0xFF0EA5E9))
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0EA5E9), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Jordan", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Realtime Collaboration",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Collaborate on workflow schemas, test prompt ideas, and share runtime video feeds instantly with colleagues.",
            fontSize = 14.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 28.dp)
        )
    }
}

