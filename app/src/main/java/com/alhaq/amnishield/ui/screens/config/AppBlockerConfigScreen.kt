package com.alhaq.amnishield.ui.screens.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alhaq.amnishield.data.blockers.AppInterceptionLevel
import com.alhaq.amnishield.utils.SavedPreferencesLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBlockerConfigScreen(
    isServiceEnabled: Boolean,
    onEnableServiceClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefsLoader = remember { SavedPreferencesLoader(context) }

    var isAutoBlockEnabled by remember { mutableStateOf(prefsLoader.isAutoBlockEnabled()) }
    var autoBlockedCategories by remember { mutableStateOf(prefsLoader.getAutoBlockCategories()) }
    var showCategoriesDialog by remember { mutableStateOf(false) }

    val rules = remember { prefsLoader.loadUnifiedBlockRules() }
    val activeAppRules = remember(rules) {
        rules.filter { rule ->
            rule.isActiveOrScheduledForContentNow() &&
                rule.appTargets.values.any { it == AppInterceptionLevel.BLOCK_ENTIRE_APP }
        }
    }
    val activeAppBlockCardsCount = activeAppRules.size
    val totalBlockedPackagesCount = remember(activeAppRules) {
        activeAppRules
            .flatMap { rule ->
                rule.appTargets.filterValues { it == AppInterceptionLevel.BLOCK_ENTIRE_APP }.keys
            }
            .toSet()
            .size
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("App Blocker Behavior", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("app_config_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (!isServiceEnabled) {
                item {
                    ServiceRequiredCard(onEnableClick = onEnableServiceClick)
                }
            }

            // Engine Status Summary Card (Driven by All-in-One Block Cards)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (activeAppBlockCardsCount > 0) Icons.Default.CheckCircle else Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Managed by All-in-One Block Cards",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (activeAppBlockCardsCount > 0) {
                                        "$activeAppBlockCardsCount active Block Card(s) currently guarding $totalBlockedPackagesCount app(s)."
                                    } else {
                                        "No active Block Cards are currently blocking full app launches."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)
                        )

                        Text(
                            text = "App targets, schedules, usage limits, pause friction, and warning screen styles are configured per Block Card in the Blocks tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Global Install-Time Category Protection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Block Categories on Install",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Automatically block newly installed Play Store apps matching selected Android categories (Games, Social, Video).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isAutoBlockEnabled,
                                onCheckedChange = { enabled ->
                                    isAutoBlockEnabled = enabled
                                    prefsLoader.setAutoBlockEnabled(enabled)
                                },
                                modifier = Modifier.testTag("auto_block_categories_switch")
                            )
                        }

                        if (isAutoBlockEnabled) {
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { showCategoriesDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("configure_auto_block_categories_button")
                            ) {
                                Text("Configure Categories (${autoBlockedCategories.size} selected)")
                            }
                        }
                    }
                }
            }

            // Engine Behavior Explanation Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Per-Card Newly Installed Apps & Warning Styles",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Inside any Block Card, you can also enable \"Block newly installed apps\" to automatically append any new app installation directly to that specific Block Card, and customize whether blocked launches show a Warning Dialog, Mindful Breathing Pause, or Instant Exit to Home.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCategoriesDialog) {
        val categoryMap = mapOf(
            "0" to "Games",
            "4" to "Social Apps",
            "3" to "Video & Media Players",
            "2" to "Audio & Music",
            "1" to "Image & Photo",
            "5" to "News & Magazines",
            "6" to "Maps & Navigation",
            "7" to "Productivity"
        )

        AlertDialog(
            onDismissRequest = { showCategoriesDialog = false },
            title = { Text("Select Categories to Auto-Block") },
            text = {
                Column {
                    categoryMap.forEach { (id, label) ->
                        val isChecked = autoBlockedCategories.contains(id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    autoBlockedCategories = if (isChecked) {
                                        autoBlockedCategories - id
                                    } else {
                                        autoBlockedCategories + id
                                    }
                                    prefsLoader.setAutoBlockCategories(autoBlockedCategories)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    autoBlockedCategories = if (checked) {
                                        autoBlockedCategories + id
                                    } else {
                                        autoBlockedCategories - id
                                    }
                                    prefsLoader.setAutoBlockCategories(autoBlockedCategories)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoriesDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun ServiceRequiredCard(
    onEnableClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Accessibility Service Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "AmniShield needs Accessibility permission to detect and intercept blocked content.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onEnableClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Enable Service")
            }
        }
    }
}
