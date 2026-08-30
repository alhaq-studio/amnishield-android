package com.alhaq.amnishield.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * AmniSpace Guided Mindful Breathing Animation Component.
 * 
 * Renders an expanding/contracting breathing circle with 3 phases:
 * - Inhale (Expand - 4s)
 * - Hold (Pause - 2s)
 * - Exhale (Contract - 4s)
 */
@Composable
fun AmniSpaceBreathingCircle(
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    totalDurationSeconds: Int = 5,
    onComplete: () -> Unit = {}
) {
    var secondsRemaining by remember { mutableIntStateOf(totalDurationSeconds) }
    var breathingPhase by remember { mutableStateOf("Inhale deeply") }

    val infiniteTransition = rememberInfiniteTransition(label = "AmniSpaceBreathing")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 8000
                0.75f at 0 using FastOutSlowInEasing // Start Inhale
                1.15f at 3500 using LinearEasing    // Peak Inhale
                1.15f at 4500 using LinearEasing    // Hold
                0.75f at 8000 using FastOutSlowInEasing // Exhale
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "BreathingScale"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    // Timer countdown
    LaunchedEffect(totalDurationSeconds) {
        while (secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining--
            val elapsed = totalDurationSeconds - secondsRemaining
            val cycleTime = elapsed % 8
            breathingPhase = when {
                cycleTime < 3 -> "Inhale gently..."
                cycleTime < 4 -> "Hold..."
                else -> "Exhale slowly..."
            }
        }
        onComplete()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        // Outer Pulsing Glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.22f),
                            secondaryColor.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Middle Progress Ring
        Canvas(modifier = Modifier.size(size * 0.85f)) {
            drawCircle(
                color = primaryColor.copy(alpha = 0.35f),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Inner Core Circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size * 0.7f)
                .scale(scale * 0.95f)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.85f),
                            secondaryColor.copy(alpha = 0.95f)
                        )
                    )
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$secondsRemaining",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = breathingPhase,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                )
            }
        }
    }
}
