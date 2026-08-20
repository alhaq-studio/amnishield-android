package com.alhaq.amnshield.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
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
import com.alhaq.amnshield.ui.components.AmnShieldButton
import com.alhaq.amnshield.ui.components.AmnShieldButtonStyle
import com.alhaq.amnshield.ui.components.AmnShieldInputField
import com.alhaq.amnshield.ui.state.AmnShieldState
import com.alhaq.amnshield.ui.viewmodel.AmnShieldViewModel
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
    state: AmnShieldState,
    viewModel: AmnShieldViewModel,
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
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
                                text = "AmnShield Guardian • Level 8",
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
                        AmnShieldInputField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = "e.g. Alhaq DST",
                            label = "Guardian Username",
                            leadingIcon = Icons.Default.Person
                        )

                        AmnShieldInputField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = "e.g. info@amnshield.com",
                            label = "Security Email Address",
                            leadingIcon = Icons.Default.Email
                        )

                        AmnShieldInputField(
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
                                    imageVector = Icons.Default.ExitToApp,
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

            // ACTION SAVE BUTTON
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AmnShieldButton(
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
                    style = AmnShieldButtonStyle.Primary,
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
}
