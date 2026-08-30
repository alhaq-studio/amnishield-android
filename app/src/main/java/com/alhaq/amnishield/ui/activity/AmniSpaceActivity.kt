package com.alhaq.amnishield.ui.activity

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.ui.components.AmniSpaceBreathingCircle
import com.alhaq.amnishield.ui.components.bounceClick
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import kotlinx.coroutines.delay

/**
 * Data representation for an essential app in AmniSpace.
 */
data class AmniSpaceAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
)

/**
 * AmniSpace (Mindful Focus Launcher Space) Fullscreen Activity.
 * 
 * Supports two distinct execution contexts:
 * 1. [Constants.AMNISPACE_MODE_FOCUS_LAUNCHER]: Minimalist focus launcher workspace.
 * 2. [Constants.AMNISPACE_MODE_MINDFUL_BREATHING]: Mindful breathing interstitial friction.
 */
class AmniSpaceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val mode = intent.getIntExtra(Constants.AMNISPACE_EXTRA_MODE, Constants.AMNISPACE_MODE_FOCUS_LAUNCHER)
        val triggerReason = intent.getStringExtra(Constants.AMNISPACE_EXTRA_TRIGGER_REASON) ?: "Focus Session"
        val triggerApp = intent.getStringExtra(Constants.AMNISPACE_EXTRA_TRIGGER_APP) ?: ""
        val customDurationSeconds = intent.getIntExtra(Constants.AMNISPACE_EXTRA_DURATION_SECONDS, 0)

        setContent {
            AmniShieldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (mode == Constants.AMNISPACE_MODE_MINDFUL_BREATHING) {
                        AmniSpaceBreathingScreen(
                            triggerReason = triggerReason,
                            triggerApp = triggerApp,
                            customDurationSeconds = if (customDurationSeconds > 0) customDurationSeconds else null,
                            onClose = {
                                navigateToAndroidHome()
                                finish()
                            }
                        )
                    } else {
                        AmniSpaceLauncherScreen(
                            onExitSession = {
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun navigateToAndroidHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }
}

/**
 * Mode 1: Minimalist Focus Launcher Space.
 */
@Composable
fun AmniSpaceLauncherScreen(
    onExitSession: () -> Unit
) {
    val context = LocalContext.current
    val loader = remember { SavedPreferencesLoader(context) }
    val packageManager = context.packageManager

    val essentialPackageNames = remember { loader.getAmniSpaceEssentialApps() }
    
    val essentialApps = remember(essentialPackageNames) {
        essentialPackageNames.mapNotNull { pkg ->
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                val label = packageManager.getApplicationLabel(appInfo).toString()
                val icon = packageManager.getApplicationIcon(appInfo)
                AmniSpaceAppItem(pkg, label, icon)
            } catch (e: Exception) {
                null
            }
        }
    }

    var showBreathingModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SelfImprovement,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AmniSpace Focus Mode",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Deep Focus in Progress",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Only essential allowed tools are accessible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Essential Apps Grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "ESSENTIAL APPS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.outline,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (essentialApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No essential apps configured.\nConfigure tools in Focus Mode settings.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(essentialApps) { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .bounceClick()
                                .clickable {
                                    val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    } else {
                                        Toast.makeText(context, "Cannot open app", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon.toBitmap().asImageBitmap(),
                                        contentDescription = app.appName,
                                        modifier = Modifier.size(36.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Bottom Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { showBreathingModal = true },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Air,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Take a Mindful Breath", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onExitSession,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Exit to Home", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }

    if (showBreathingModal) {
        AlertDialog(
            onDismissRequest = { showBreathingModal = false },
            confirmButton = {
                TextButton(onClick = { showBreathingModal = false }) {
                    Text("Done")
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    Text(
                        text = "Mindful Pause",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    AmniSpaceBreathingCircle(
                        size = 180.dp,
                        totalDurationSeconds = 8,
                        onComplete = { }
                    )
                }
            }
        )
    }
}

/**
 * Mode 2: Mindful Breathing Interstitial (OneSec-style Friction for Reels/Limits).
 */
@Composable
fun AmniSpaceBreathingScreen(
    triggerReason: String,
    triggerApp: String,
    customDurationSeconds: Int? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val loader = remember { SavedPreferencesLoader(context) }
    val durationSeconds = remember(customDurationSeconds) {
        customDurationSeconds ?: loader.getAmniSpaceBreathingDurationSeconds()
    }

    var isBreathingDone by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Air,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AmniSpace Mindful Pause",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Pause & Breathe",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = if (triggerReason.contains("Reel", ignoreCase = true)) {
                    "Short-form video feeds are designed to trap attention. Take a breath and choose intentionally."
                } else {
                    "Notice the impulse to open this app. Take a moment of calm."
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
            )
        }

        // Center Breathing Circle
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            AmniSpaceBreathingCircle(
                size = 230.dp,
                totalDurationSeconds = durationSeconds,
                onComplete = {
                    isBreathingDone = true
                }
            )
        }

        // Bottom Action Button
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onClose,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = if (isBreathingDone) "Return to Home" else "Skip & Return Home",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
