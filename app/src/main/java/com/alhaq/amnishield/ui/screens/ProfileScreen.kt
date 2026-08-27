package com.alhaq.amnishield.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import com.alhaq.amnishield.Constants
import org.json.JSONObject
import org.json.JSONArray
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.ui.components.AmniShieldButton
import com.alhaq.amnishield.ui.components.AmniShieldButtonStyle
import com.alhaq.amnishield.ui.components.AmniShieldInputField
import com.alhaq.amnishield.ui.state.AmniShieldState
import com.alhaq.amnishield.ui.viewmodel.AmniShieldViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PresetAvatar(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val gradientColors: List<Color>
)

val PRESET_AVATARS = listOf(
    PresetAvatar("avatar_shield", "Shield Master", Icons.Default.Shield, listOf(Color(0xFF1E88E5), Color(0xFF00ACC1))),
    PresetAvatar("avatar_focus", "Zen Monk", Icons.Default.SelfImprovement, listOf(Color(0xFF8E24AA), Color(0xFF5E35B1))),
    PresetAvatar("avatar_cyber", "Cyber Guard", Icons.Default.Security, listOf(Color(0xFF00897B), Color(0xFF43A047))),
    PresetAvatar("avatar_serene", "Serene Soul", Icons.Default.Spa, listOf(Color(0xFF3949AB), Color(0xFF1E88E5))),
    PresetAvatar("avatar_gold", "Golden Sentry", Icons.Default.WorkspacePremium, listOf(Color(0xFFF4511E), Color(0xFFFB8C00))),
    PresetAvatar("avatar_sage", "Cosmic Mind", Icons.Default.AutoAwesome, listOf(Color(0xFFD81B60), Color(0xFF8E24AA)))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: AmniShieldState,
    viewModel: AmniShieldViewModel,
    isGoogleSignedIn: Boolean,
    onGoogleSignIn: () -> Unit,
    onGoogleSignOut: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var name by remember(state.userName) { mutableStateOf(state.userName) }
    var email by remember(state.userEmail) { mutableStateOf(state.userEmail) }
    var bio by remember(state.userBio) { mutableStateOf(state.userBio) }
    var goalMinutes by remember(state.userGoalMinutes) { mutableStateOf(state.userGoalMinutes) }
    var profileType by remember(state.focusProfileType) { mutableStateOf(state.focusProfileType) }
    var pinProtectionEnabled by remember(state.isPinProtectionEnabled) { mutableStateOf(state.isPinProtectionEnabled) }
    var profilePin by remember(state.profilePin) { mutableStateOf(state.profilePin) }

    var showSuccessMessage by remember { mutableStateOf(false) }
    var showAvatarPickerSheet by remember { mutableStateOf(false) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var pairingPinInput by remember { mutableStateOf("") }
    var isPairingInProgress by remember { mutableStateOf(false) }
    var pairingStatusMsg by remember { mutableStateOf<String?>(null) }
    var isPairingSuccess by remember { mutableStateOf(false) }
    var isPairedWithConsole by remember { mutableStateOf(context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE).getBoolean("is_paired_with_console", false)) }
    var pairedDeviceId by remember { mutableStateOf(context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE).getString("paired_device_id", null)) }
    val coroutineScope = rememberCoroutineScope()

    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { scanResult ->
        val rawContents = scanResult.contents
        if (!rawContents.isNullOrBlank()) {
            val extractedPin = extractPinFromQr(rawContents)
            if (extractedPin.isNotBlank()) {
                pairingPinInput = extractedPin
                isPairingInProgress = true
                pairingStatusMsg = "Linking device with PIN: $extractedPin..."
                showPairingDialog = true
                val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                coroutineScope.launch(Dispatchers.IO) {
                    val rest = com.alhaq.amnishield.data.sync.SupabaseRest()
                    val result = rest.claimPairingToken(extractedPin, deviceName, "android")
                    withContext(Dispatchers.Main) {
                        isPairingInProgress = false
                        if (result.success) {
                            isPairingSuccess = true
                            pairingStatusMsg = "🎉 Linked to Cloud Sync Hub successfully!"
                            val savedPrefs = com.alhaq.amnishield.utils.SavedPreferencesLoader(context)
                            savedPrefs.setConsoleManaged(true, result.deviceId, result.ownerId)
                            if (result.policyPayload != null) {
                                savedPrefs.saveCachedPolicyPayload(result.policyPayload.toString())
                                com.alhaq.amnishield.data.sync.PolicySyncManager.applyPolicyPayload(context, result.policyPayload)
                            }
                            isPairedWithConsole = true
                            pairedDeviceId = result.deviceId
                            Toast.makeText(context, "🎉 Device Linked Successfully!", Toast.LENGTH_LONG).show()
                            coroutineScope.launch(Dispatchers.IO) {
                                com.alhaq.amnishield.data.sync.PolicySyncManager.syncNow(context)
                            }
                            delay(1200)
                            showPairingDialog = false
                        } else {
                            isPairingSuccess = false
                            pairingStatusMsg = result.message
                            Toast.makeText(context, "Pairing Failed: ${result.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                Toast.makeText(context, "Could not find a valid pairing code in QR", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Decode custom profile image if present
    val profileBitmap = remember(state.profileImageUri) {
        state.profileImageUri?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else {
                    val uri = Uri.parse(path)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    // Photo picker launcher (Android Photo Picker API)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                val destDir = File(context.filesDir, "profile_pictures").apply { mkdirs() }
                val destFile = File(destDir, "profile_photo.jpg")
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val savedPath = destFile.absolutePath
                val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("profile_image_uri", savedPath)
                    .putString("profile_avatar_id", "custom_photo")
                    .apply()
                viewModel.updateProfilePicture(imageUri = savedPath, avatarId = "custom_photo")
                showAvatarPickerSheet = false
            } catch (e: Exception) {
                android.util.Log.e("ProfileScreen", "Failed to save profile picture", e)
            }
        }
    }

    // Fallback document picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                val destDir = File(context.filesDir, "profile_pictures").apply { mkdirs() }
                val destFile = File(destDir, "profile_photo.jpg")
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val savedPath = destFile.absolutePath
                val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("profile_image_uri", savedPath)
                    .putString("profile_avatar_id", "custom_photo")
                    .apply()
                viewModel.updateProfilePicture(imageUri = savedPath, avatarId = "custom_photo")
                showAvatarPickerSheet = false
            } catch (e: Exception) {
                android.util.Log.e("ProfileScreen", "Failed to save profile picture", e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Identity", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp)
        ) {
            // SUCCESS BANNER
            if (showSuccessMessage) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Profile Saved Successfully!",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Your identity and wellbeing settings have been saved locally.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // INTERACTIVE HEADER AVATAR
            item {
                val currentAvatar = PRESET_AVATARS.find { it.id == state.profileAvatarId } ?: PRESET_AVATARS.first()

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .clip(CircleShape)
                                .border(
                                    BorderStroke(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                                    CircleShape
                                )
                                .clickable { showAvatarPickerSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (profileBitmap != null) {
                                Image(
                                    bitmap = profileBitmap,
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (state.profileAvatarId != "initials" && state.profileAvatarId.startsWith("avatar_")) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(currentAvatar.gradientColors)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = currentAvatar.icon,
                                        contentDescription = currentAvatar.name,
                                        tint = Color.White,
                                        modifier = Modifier.size(52.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.tertiary
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name.take(2).uppercase().ifEmpty { "ME" },
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }

                            // Edit Badge Pill
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Change picture",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = name.ifEmpty { "Anonymous Guardian" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "AmniShield Guardian • Level 8",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // GUARDIAN STATS BADGES (STREAK, SCORE, THREATS)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Streak Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = Color(0xFFFF6D00),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${state.focusStreakDays} Days",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Focus Streak",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Score Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${state.focusShieldScore}%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Shield Score",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Threats Blocked Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${state.totalThreatsBlocked}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Filtered Hits",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // FOCUS TARGET & WELLBEING GOALS
            item {
                Text(
                    text = "WELLBEING & FOCUS MODE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Focus Mode Tone Selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Guardian Protection Profile",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Deep Focus", "Balanced Guard", "Strict Monk").forEach { mode ->
                                    val isSelected = profileType == mode
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { profileType = mode },
                                        label = {
                                            Text(
                                                text = mode,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // Daily Screen Time Limit Target
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Daily Screen Time Target",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${goalMinutes / 60}h ${goalMinutes % 60}m",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = goalMinutes.toFloat(),
                                onValueChange = { goalMinutes = it.toInt() },
                                valueRange = 30f..360f,
                                steps = 10,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }

            // PERSONAL DETAILS SECTION
            item {
                Text(
                    text = "PERSONAL INFORMATION",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AmniShieldInputField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = "e.g. Alhaq DST",
                            label = "Guardian Username",
                            leadingIcon = Icons.Default.Person
                        )

                        AmniShieldInputField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = "e.g. info@amnishield.com",
                            label = "Security Email Address",
                            leadingIcon = Icons.Default.Email
                        )

                        AmniShieldInputField(
                            value = bio,
                            onValueChange = { bio = it },
                            placeholder = "Write your focus motto...",
                            label = "Focus Biography",
                            leadingIcon = Icons.Default.Description
                        )
                    }
                }
            }

            // LINKED GOOGLE ACCOUNT SECTION
            item {
                Text(
                    text = "LINKED ACCOUNT",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    if (isGoogleSignedIn) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF4285F4), Color(0xFF34A853))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = state.userName.take(1).uppercase().ifEmpty { "G" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.userName.ifEmpty { "Google Account" },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = state.userEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Linked",
                                    tint = Color(0xFF34A853),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onGoogleSignOut() }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = "Sign Out",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sign Out",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Link Google Account",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Sign in to backup settings and enable cross-device synchronization.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )

                            Button(
                                onClick = onGoogleSignIn,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4285F4)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sign in with Google",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // ACCOUNT & CLOUD SYNC CARD
            item {
                Text(
                    text = "ACCOUNT & CLOUD SYNC",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Account & Cloud Sync",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isPairedWithConsole) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF059669).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "CONNECTED",
                                                color = Color(0xFF059669),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "AmniShield Sync Hub",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = if (isPairedWithConsole)
                                "This phone is linked to your AmniShield Cloud Sync Hub. Focus rules, schedules, and blocklists automatically synchronize across all your connected devices."
                            else
                                "Link this phone to your AmniShield Cloud Sync Hub using a 6-digit PIN or QR code to sync focus rules and blocklists across your devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isPairedWithConsole) {
                            // Paired Controls: Sync Now + Unlink
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        Toast.makeText(context, "Syncing latest policies...", Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val ok = com.alhaq.amnishield.data.sync.PolicySyncManager.syncNow(context)
                                            withContext(Dispatchers.Main) {
                                                if (ok) {
                                                    Toast.makeText(context, "✅ Policies Synchronized!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "⚠️ Sync offline. Locally cached policy active.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sync Now",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val savedPrefs = com.alhaq.amnishield.utils.SavedPreferencesLoader(context)
                                        savedPrefs.setConsoleManaged(false)
                                        isPairedWithConsole = false
                                        pairedDeviceId = null
                                        Toast.makeText(context, "Device Unlinked from Cloud Sync Hub", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LinkOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Unlink",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        } else {
                            // Unpaired Controls: Scan QR (Primary) + Enter PIN (Outlined)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val options = ScanOptions().apply {
                                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                            setPrompt("Align QR code from app.amnishield.com inside the frame")
                                            setBeepEnabled(true)
                                            setBarcodeImageEnabled(false)
                                            setOrientationLocked(true)
                                        }
                                        qrScanLauncher.launch(options)
                                    },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Scan QR Code",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        pairingPinInput = ""
                                        pairingStatusMsg = null
                                        isPairingSuccess = false
                                        showPairingDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Enter PIN",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SYNC & DATA PRIVACY TOGGLES
            item {
                Text(
                    text = "SYNC & DATA PRIVACY",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Sync Rules Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sync Rules & Blocklists",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (state.syncRulesEnabled) "Cloud sync active for app & domain rules" else "Local rules only (cloud sync off)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.syncRulesEnabled,
                                onCheckedChange = { viewModel.toggleSyncRules() }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // Smart AI Recommendations Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Smart AI Recommendations",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (state.smartRecommendationsEnabled) "AI focus insights & recommendations active" else "AI insights disabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.smartRecommendationsEnabled,
                                onCheckedChange = { viewModel.toggleSmartRecommendations() }
                            )
                        }
                    }
                }
            }

            // DATA SOVEREIGNTY & ACCOUNT CONTROL
            item {
                Text(
                    text = "DATA SOVEREIGNTY & ACCOUNT RIGHTS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Export Data JSON Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Export Account & Rule Data (JSON)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Download / Share a structured snapshot of your active rules, telemetry, and profile.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { exportUserData(context, state, name, email, bio, goalMinutes, profileType) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Outlined.FileDownload, contentDescription = "Export Data")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // Delete Account & Purge Data (Danger Zone)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Delete Account & Purge Data",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Unlink this device from cloud sync, wipe local profile data, and reset tokens.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { showDeleteAccountDialog = true },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Outlined.DeleteForever, contentDescription = "Delete Account")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // Legal & Privacy Portal Links
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { openUrl(context, Constants.PRIVACY_POLICY_URL) }
                            ) {
                                Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Privacy Policy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(
                                onClick = { openUrl(context, Constants.DATA_DELETION_URL) }
                            ) {
                                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Deletion Portal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ACTION SAVE BUTTON
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AmniShieldButton(
                    text = "Save Profile Changes",
                    onClick = {
                        viewModel.updateProfile(
                            name = name,
                            email = email,
                            bio = bio,
                            goalMinutes = goalMinutes,
                            profileType = profileType,
                            pinEnabled = pinProtectionEnabled,
                            pin = profilePin
                        )
                        // Persist to SharedPreferences
                        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("profile_name", name)
                            .putString("profile_email", email)
                            .putString("profile_bio", bio)
                            .putString("profile_type", profileType)
                            .putInt("profile_goal_minutes", goalMinutes)
                            .putBoolean("profile_pin_enabled", pinProtectionEnabled)
                            .putString("profile_pin", profilePin)
                            .apply()
                        showSuccessMessage = true
                    },
                    style = AmniShieldButtonStyle.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // MODAL BOTTOM SHEET FOR CHANGING PROFILE PICTURE / AVATAR
    if (showAvatarPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarPickerSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Choose Profile Avatar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Option 1: Pick Custom Photo from Gallery
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            try {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } catch (e: Exception) {
                                galleryLauncher.launch("image/*")
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Gallery",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Column {
                            Text(
                                text = "Upload Photo from Device",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Select a picture from your camera roll or gallery",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 2: Curated Guardian Avatars
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Or Select Guardian Avatar",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(PRESET_AVATARS) { avatar ->
                            val isSelected = state.profileAvatarId == avatar.id && state.profileImageUri == null
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
                                        prefs.edit()
                                            .remove("profile_image_uri")
                                            .putString("profile_avatar_id", avatar.id)
                                            .apply()
                                        viewModel.updateProfilePicture(imageUri = null, avatarId = avatar.id)
                                        showAvatarPickerSheet = false
                                    }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(avatar.gradientColors))
                                        .border(
                                            if (isSelected) 3.dp else 0.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = avatar.icon,
                                        contentDescription = avatar.name,
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = avatar.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Option 3: Reset / Remove Photo
                if (state.profileImageUri != null || state.profileAvatarId != "avatar_shield") {
                    TextButton(
                        onClick = {
                            val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
                            prefs.edit()
                                .remove("profile_image_uri")
                                .putString("profile_avatar_id", "avatar_shield")
                                .apply()
                            viewModel.updateProfilePicture(imageUri = null, avatarId = "avatar_shield")
                            showAvatarPickerSheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reset to Default Avatar",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // PAIRING PIN DIALOG
    if (showPairingDialog) {
        AlertDialog(
            onDismissRequest = { if (!isPairingInProgress) showPairingDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.PhonelinkSetup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Link with Cloud Sync Hub", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter the 6-digit sync PIN from your AmniShield account or scan the QR code to link this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = pairingPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pairingPinInput = it },
                        label = { Text("6-Digit PIN") },
                        placeholder = { Text("e.g. 482910") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            letterSpacing = 4.sp
                        )
                    )

                    if (pairingStatusMsg != null) {
                        Text(
                            text = pairingStatusMsg.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPairingSuccess) Color(0xFF059669) else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            showPairingDialog = false
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Align QR code from AmniShield Sync Hub inside the frame")
                                setBeepEnabled(true)
                                setBarcodeImageEnabled(false)
                                setOrientationLocked(true)
                            }
                            qrScanLauncher.launch(options)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Or Scan QR Code with Camera", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pairingPinInput.length >= 6) {
                            isPairingInProgress = true
                            pairingStatusMsg = "Connecting to Supabase cloud..."
                            val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val rest = com.alhaq.amnishield.data.sync.SupabaseRest()
                                val result = rest.claimPairingToken(pairingPinInput, deviceName, "android")
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    isPairingInProgress = false
                                    if (result.success) {
                                        isPairingSuccess = true
                                        pairingStatusMsg = "🎉 Linked to Cloud Sync Hub successfully!"
                                        val savedPrefs = com.alhaq.amnishield.utils.SavedPreferencesLoader(context)
                                        savedPrefs.setConsoleManaged(true, result.deviceId, result.ownerId)
                                        if (result.policyPayload != null) {
                                            savedPrefs.saveCachedPolicyPayload(result.policyPayload.toString())
                                            com.alhaq.amnishield.data.sync.PolicySyncManager.applyPolicyPayload(context, result.policyPayload)
                                        }
                                        isPairedWithConsole = true
                                        pairedDeviceId = result.deviceId
                                        Toast.makeText(context, "🎉 Device Linked Successfully!", Toast.LENGTH_LONG).show()
                                        coroutineScope.launch(Dispatchers.IO) {
                                            com.alhaq.amnishield.data.sync.PolicySyncManager.syncNow(context)
                                        }
                                        kotlinx.coroutines.delay(1200)
                                        showPairingDialog = false
                                    } else {
                                        isPairingSuccess = false
                                        pairingStatusMsg = result.message
                                    }
                                }
                            }
                        }
                    },
                    enabled = pairingPinInput.length >= 6 && !isPairingInProgress,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isPairingInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Connect", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPairingDialog = false },
                    enabled = !isPairingInProgress
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // DELETE ACCOUNT & PURGE DATA CONFIRMATION DIALOG
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Account & Purge Data?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This will immediately unlink this device from the AmniShield Sync Hub, purge your cloud-paired tokens, clear local focus profile settings, and sign you out.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "For full account deletion from our active cloud databases, visit the Deletion Portal or contact support@alhaq.uk.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        // Reset local profile
                        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("profile_name", "AmniShield User")
                            .putString("profile_email", "")
                            .putString("profile_bio", "")
                            .putBoolean("is_paired_with_console", false)
                            .remove("paired_device_id")
                            .apply()

                        name = "AmniShield User"
                        email = ""
                        bio = ""
                        isPairedWithConsole = false
                        pairedDeviceId = null

                        val savedPrefs = com.alhaq.amnishield.utils.SavedPreferencesLoader(context)
                        savedPrefs.setConsoleManaged(false)

                        onGoogleSignOut()
                        Toast.makeText(context, "Account unlinked and local profile data purged.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Purge & Unlink", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun exportUserData(
    context: Context,
    state: AmniShieldState,
    name: String,
    email: String,
    bio: String,
    goalMinutes: Int,
    profileType: String
) {
    try {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("export_version", "2.0")
            put("studio", "Al-Haq Studio (alhaq.uk)")
            put("export_timestamp", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date()))
            put("user_profile", JSONObject().apply {
                put("display_name", name)
                put("email", email)
                put("bio", bio)
                put("goal_minutes", goalMinutes)
                put("profile_type", profileType)
                put("is_google_signed_in", prefs.getBoolean("is_google_signed_in", false))
                put("is_paired_with_console", prefs.getBoolean("is_paired_with_console", false))
                put("paired_device_id", prefs.getString("paired_device_id", null))
            })
            put("blocking_rules", JSONObject().apply {
                put("web_filter_enabled", state.isWebFilterEnabled)
                put("usage_limit_enabled", state.isUsageLimitEnabled)
                put("sync_rules_enabled", state.syncRulesEnabled)
                put("smart_recommendations_enabled", state.smartRecommendationsEnabled)
                put("anti_uninstall_active", state.isAntiUninstallEnabled)
                put("blocked_apps_count", state.appsUsage.size)
                put("blocked_domains_count", state.customBlockedWebsites.size)
                put("blocked_keywords_count", state.keywords.size)
            })
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AmniShield_User_Data_Export.json")
            putExtra(Intent.EXTRA_TEXT, json.toString(2))
        }
        context.startActivity(Intent.createChooser(sendIntent, "Export AmniShield Data"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open browser for URL: $url", Toast.LENGTH_SHORT).show()
    }
}

private fun extractPinFromQr(content: String): String {
    val trimmed = content.trim()
    if (trimmed.length in 6..10 && trimmed.all { it.isDigit() }) return trimmed

    // JSON payload check (e.g. {"token":"48139226", ...})
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
        try {
            val jsonObj = com.google.gson.JsonParser.parseString(trimmed).asJsonObject
            if (jsonObj.has("token")) return jsonObj.get("token").asString.trim()
            if (jsonObj.has("pin")) return jsonObj.get("pin").asString.trim()
            if (jsonObj.has("code")) return jsonObj.get("code").asString.trim()
            if (jsonObj.has("pairing_token")) return jsonObj.get("pairing_token").asString.trim()
        } catch (_: Exception) {}
    }

    // URI / URL check
    try {
        val uri = Uri.parse(trimmed)
        val token = uri.getQueryParameter("token")
            ?: uri.getQueryParameter("pin")
            ?: uri.getQueryParameter("code")
            ?: uri.getQueryParameter("pairing_token")
            ?: uri.getQueryParameter("key")
        if (!token.isNullOrBlank()) return token.trim()
    } catch (_: Exception) {}

    // Regex fallback for any 6-10 digit number in the string
    val regexMatch = Regex("\\b\\d{6,10}\\b").find(trimmed)
    return regexMatch?.value ?: trimmed
}
