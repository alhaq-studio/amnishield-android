package com.alhaq.amnishield.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.ui.screens.AppUsageItem
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

val DONUT_PALETTE = listOf(
    Color(0xFF38BDF8), // Sky Blue
    Color(0xFFFF7675), // Coral Red
    Color(0xFF34D399), // Mint Green
    Color(0xFFFBBF24), // Amber Gold
    Color(0xFFC084FC), // Lavender
    Color(0xFF818CF8)  // Indigo
)

data class DonutSegment(
    val id: String,
    val label: String,
    val durationText: String,
    val percentage: Float,
    val color: Color
)

@Composable
fun InteractiveScreenTimeDonut(
    totalDurationFormatted: String,
    apps: List<AppUsageItem>,
    modifier: Modifier = Modifier,
    selectedAppPackage: String? = null,
    onAppSelected: (String?) -> Unit = {}
) {
    // Convert apps to donut segments (top 4 and remainder as other)
    val segments = remember(apps) {
        if (apps.isEmpty()) emptyList()
        else {
            val totalScore = apps.sumOf { it.progress.toDouble() }.toFloat().coerceAtLeast(0.001f)
            val top = apps.take(4)
            val remainderProgress = apps.drop(4).sumOf { it.progress.toDouble() }.toFloat()

            val list = top.mapIndexed { index, app ->
                DonutSegment(
                    id = app.packageName,
                    label = app.name,
                    durationText = app.timeFormatted,
                    percentage = (app.progress / totalScore).coerceIn(0.01f, 1f),
                    color = DONUT_PALETTE[index % DONUT_PALETTE.size]
                )
            }.toMutableList()

            if (remainderProgress > 0.05f) {
                list.add(
                    DonutSegment(
                        id = "other_apps",
                        label = "Other Apps",
                        durationText = "${(remainderProgress * 100).toInt()}%",
                        percentage = (remainderProgress / totalScore).coerceIn(0.01f, 1f),
                        color = Color(0xFF94A3B8)
                    )
                )
            }
            list
        }
    }

    var activeSelection by remember { mutableStateOf<String?>(selectedAppPackage) }

    LaunchedEffect(selectedAppPackage) {
        activeSelection = selectedAppPackage
    }

    val selectedSegment = segments.find { it.id == activeSelection }
    val displayLabel = selectedSegment?.label ?: "SCREENTIME"
    val displayTime = selectedSegment?.durationText ?: totalDurationFormatted
    val displaySub = selectedSegment?.let { "${(it.percentage * 100).toInt()}% of usage" } ?: "${apps.size} Apps Active"

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(segments) {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dx = offset.x - center.x
                            val dy = offset.y - center.y
                            // Angle in degrees from top (-90 deg offset)
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            angle = (angle + 90f + 360f) % 360f

                            var currentStart = 0f
                            var tappedSegment: String? = null

                            for (seg in segments) {
                                val sweep = seg.percentage * 360f
                                if (angle in currentStart..(currentStart + sweep)) {
                                    tappedSegment = seg.id
                                    break
                                }
                                currentStart += sweep
                            }

                            val newSel = if (activeSelection == tappedSegment) null else tappedSegment
                            activeSelection = newSel
                            onAppSelected(newSel)
                        }
                    }
            ) {
                val strokeWidth = 18.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                val topLeft = Offset((size.width - radius * 2) / 2f, (size.height - radius * 2) / 2f)
                val arcSize = Size(radius * 2, radius * 2)

                // Background Track
                drawArc(
                    color = Color(0xFF1E293B).copy(alpha = 0.5f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                var startAngle = -90f
                val gap = if (segments.size > 1) 4f else 0f

                segments.forEach { seg ->
                    val isSelected = seg.id == activeSelection
                    val sweepAngle = (seg.percentage * 360f - gap).coerceAtLeast(1f)
                    val arcStroke = if (isSelected) strokeWidth + 6.dp.toPx() else strokeWidth

                    drawArc(
                        color = seg.color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = arcStroke, cap = StrokeCap.Round)
                    )

                    startAngle += seg.percentage * 360f
                }
            }

            // Center Metric & Active Details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = displayLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayTime,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displaySub,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
