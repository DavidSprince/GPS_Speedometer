package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DDriveLoadingScreen(
    onFinished: () -> Unit
) {
    // Progress animating from 0% to 100% (0.0f to 1.0f) in 3.0 seconds
    val progressAnim = remember { Animatable(0f) }
    
    // Pulse animation for the "LOADING..." label
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_loading")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_pulse"
    )

    LaunchedEffect(Unit) {
        // Animate up to 1.0f (representing 100%)
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3200, easing = FastOutSlowInEasing)
        )
        delay(300) // Small luxury pause at 100%
        onFinished()
    }

    val currentProgressPercent = (progressAnim.value * 100).toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF04060C)), // Rich Space Dark Deep Blue
        contentAlignment = Alignment.Center
    ) {
        // Decorative background subtle starglow effects
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00FAF2).copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        radius = 800f
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 44.dp, horizontal = 24.dp)
        ) {
            
            // 1. HEADER TEXTS (D-DRIVE SMART MOBILITY)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "D-DRIVE",
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "SMART MOBILITY",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF00FAF2),
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center
                )
            }

            // 2. D-SPEEDOMETER CENTERPIECE GAUGE
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(280.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2.2f
                    
                    // A. Draw background circular track / tick guidelines
                    drawArc(
                        color = Color(0xFF1E293B).copy(alpha = 0.6f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // B. Draw many high-tech tick ticks on the outer perimeter
                    for (i in 0..60) {
                        val angleDegrees = 135f + (i / 60f) * 270f
                        val angleRadians = Math.toRadians(angleDegrees.toDouble())
                        val tickLength = if (i % 6 == 0) 10.dp.toPx() else 4.dp.toPx()
                        val tickWidth = if (i % 6 == 0) 2.dp.toPx() else 1.dp.toPx()
                        val tickColor = if (i % 6 == 0) Color(0xFF00FAF2).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)

                        val startX = center.x + ((radius - tickLength) * cos(angleRadians)).toFloat()
                        val startY = center.y + ((radius - tickLength) * sin(angleRadians)).toFloat()
                        val endX = center.x + (radius * cos(angleRadians)).toFloat()
                        val endY = center.y + (radius * sin(angleRadians)).toFloat()

                        drawLine(
                            color = tickColor,
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = tickWidth
                        )
                    }

                    // C. Draw glowing active progress sweep arc
                    val sweepDegrees = progressAnim.value * 270f
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF0F172A),
                                Color(0xFF0044FF),
                                Color(0xFF00FAF2)
                            ),
                            center = center
                        ),
                        startAngle = 135f,
                        sweepAngle = sweepDegrees,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw a futuristic pointer dot at the leading edge
                    val needleDegrees = 135f + sweepDegrees
                    val needleRad = Math.toRadians(needleDegrees.toDouble())
                    val dotX = center.x + (radius * cos(needleRad)).toFloat()
                    val dotY = center.y + (radius * sin(needleRad)).toFloat()

                    // Pointer Outer glow
                    drawCircle(
                        color = Color(0xFF00FAF2).copy(alpha = 0.4f),
                        radius = 12.dp.toPx(),
                        center = Offset(dotX, dotY)
                    )
                    // Pointer Inner core
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = Offset(dotX, dotY)
                    )
                }

                // Inner core card for the brand Letter 'D' and live percent progress label
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Percent counter
                    Text(
                        text = "$currentProgressPercent%",
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FAF2),
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Massive stylized Holographic 'D' inside a container
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .background(Color(0xFF00152F).copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                            .border(1.5.dp, Color(0xFF00FAF2).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    ) {
                        Text(
                            text = "D",
                            fontSize = 44.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // "LOADING..." pulser label
                    Text(
                        text = "LOADING...",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = alphaPulse),
                        letterSpacing = 3.sp
                    )
                }
            }

            // 3. MECHANICAL STYLE ROLLING ODOMETER CYLINDERS AT THE BOTTOM
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0xFF0C1322), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Digits displayed: 6, 8, 4, 3, 2, 1 (exactly like mockup imagery)
                val digits = listOf("6", "8", "4", "3", "2", "1")
                val activeDigitIndex = (progressAnim.value * 5.99f).toInt().coerceIn(0, 5)

                digits.forEachIndexed { index, char ->
                    val isChanging = index == activeDigitIndex
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 24.dp, height = 34.dp)
                            .background(
                                if (isChanging) Color(0xFF00FAF2).copy(alpha = 0.15f) else Color(0xFF080D18),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isChanging) Color(0xFF00FAF2) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(4.dp)
                            )
                    ) {
                        Text(
                            text = char,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isChanging) Color(0xFF00FAF2) else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
