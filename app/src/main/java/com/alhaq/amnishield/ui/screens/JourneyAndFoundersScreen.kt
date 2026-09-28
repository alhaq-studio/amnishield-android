package com.alhaq.amnishield.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.premium.PremiumManager
import com.alhaq.amnishield.ui.components.FounderClaimBottomSheet
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

data class FoundingSupporter(
    val id: Int,
    val handle: String,
    val joinedDate: String,
    val tier: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyAndFoundersScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val preferencesLoader = remember { SavedPreferencesLoader(context.applicationContext) }
    val premiumManager = remember { PremiumManager.getInstance(context.applicationContext) }

    var isFounderActive by remember { mutableStateOf(preferencesLoader.isFounderPassActive()) }
    var supporterName by remember { mutableStateOf(preferencesLoader.getFounderSupporterName()) }
    var showClaimSheet by remember { mutableStateOf(false) }

    val supporters = remember { loadFoundingSupportersFromAssets(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Our Journey & Founders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                JourneyHeroCard()
            }

            // User's Founder Status Card
            item {
                if (isFounderActive) {
                    PersonalFounderCard(
                        supporterName = supporterName.ifBlank { "Anonymous Supporter" },
                        expiryTimeMs = preferencesLoader.getFounderPassExpiry()
                    )
                } else {
                    ClaimFounderInvitationCard(
                        onClaimClick = { showClaimSheet = true }
                    )
                }
            }

            // Our Core Pillars / Journey Narrative
            item {
                Text(
                    text = "THE AMNISHIELD MISSION",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.2.sp
                )
            }

            item {
                JourneyPillarCard(
                    icon = Icons.Outlined.VisibilityOff,
                    title = "Zero Telemetry, Complete Sovereignty",
                    description = "AmniShield is built on a zero-knowledge principle. All screen and application inspections execute 100% locally on your device without sending private data anywhere."
                )
            }

            item {
                JourneyPillarCard(
                    icon = Icons.Outlined.Key,
                    title = "Offline Cryptographic Verification",
                    description = "We reject intrusive account registration. Supporter entitlements and features are validated offline using cryptographic NIST P-256 ECDSA signatures."
                )
            }

            item {
                JourneyPillarCard(
                    icon = Icons.Outlined.Shield,
                    title = "Unyielding Digital Discipline",
                    description = "Engineered with triple-PIN barriers, device-admin defense, and uninstallation deterrence to protect you when willpower fluctuates."
                )
            }

            // Hall of Fame Header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FOUNDING SUPPORTERS WALL",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5B842),
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "${supporters.size} Pioneers",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Hall of Fame Items
            items(supporters) { supporter ->
                SupporterWallItem(supporter = supporter)
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showClaimSheet) {
        FounderClaimBottomSheet(
            onConfirm = { handle, optIn ->
                preferencesLoader.grantFounderPass(
                    supporterName = handle,
                    optInPublic = optIn,
                    durationDays = 90
                )
                isFounderActive = true
                supporterName = handle
                showClaimSheet = false
            },
            onDismiss = { showClaimSheet = false }
        )
    }
}

@Composable
private fun JourneyHeroCard() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Our Story & Vision",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "From open-source idea to distraction shield",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Modern tech products are deliberately designed to capture attention and monetize human vulnerability. AmniShield was created as an antidote: a transparent, offline-first shield that restores autonomy, focus, and digital peace.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PersonalFounderCard(
    supporterName: String,
    expiryTimeMs: Long
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val expiryText = if (expiryTimeMs > 0) dateFormat.format(Date(expiryTimeMs)) else "90 Days"

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFE5B842).copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color(0xFFE5B842).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.WorkspacePremium,
                        contentDescription = "Founder",
                        tint = Color(0xFFE5B842),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FOUNDING SUPPORTER",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5B842),
                        letterSpacing = 1.1.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFE5B842).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE5B842),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = supporterName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Thank you for supporting AmniShield. Your Founder Pass unlocks all PIN barriers, strict anti-uninstall protection, and the exclusive Founder Obsidian Gold theme until $expiryText.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ClaimFounderInvitationCard(
    onClaimClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.MilitaryTech,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Early Supporter Privilege",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Pre-registered users and early adopters can claim 3 months of Full Premium protection and an exclusive Founder badge.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClaimClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Claim 3-Month Founder Pass", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun JourneyPillarCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SupporterWallItem(supporter: FoundingSupporter) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE5B842).copy(alpha = 0.15f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "#${supporter.id}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE5B842)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = supporter.handle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Joined ${supporter.joinedDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = supporter.tier,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

private fun loadFoundingSupportersFromAssets(context: Context): List<FoundingSupporter> {
    return try {
        val jsonString = context.assets.open("founders.json").bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<FoundingSupporter>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                FoundingSupporter(
                    id = obj.optInt("id", i + 1),
                    handle = obj.optString("handle", "Anonymous Supporter"),
                    joinedDate = obj.optString("joined_date", "2026-08"),
                    tier = obj.optString("tier", "Supporter")
                )
            )
        }
        list
    } catch (e: Exception) {
        listOf(
            FoundingSupporter(1, "Al-Haq Team", "2026-08-01", "Founder"),
            FoundingSupporter(2, "Habibur Rahman", "2026-08-10", "Core Architect")
        )
    }
}
