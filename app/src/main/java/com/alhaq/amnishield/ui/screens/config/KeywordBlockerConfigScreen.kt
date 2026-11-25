package com.alhaq.amnishield.ui.screens.config

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.ui.components.ShieldIgnoredAppsSheet
import com.alhaq.amnishield.utils.SavedPreferencesLoader

/**
 * Engine behavior & sensitivity screen for Keyword Blocker.
 *
 * Keyword lists, curated keyword packs (26 Adult Keywords, 12 Social, 11 Gambling, 7 Gaming),
 * schedules, pause friction, and warning styles are configured per Block Card.
 * This screen manages engine-wide text matching sensitivity, URL/screen scanning scope,
 * safe redirect URL, and whitelisted apps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordBlockerConfigScreen(
    isServiceEnabled: Boolean,
    onEnableServiceClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loader = remember { SavedPreferencesLoader(context) }
    val keywordConfigsSp = remember { context.getSharedPreferences("keyword_blocker_configs", Context.MODE_PRIVATE) }

    var alwaysOnConfig by remember { mutableStateOf(loader.loadAlwaysOnShieldsConfig()) }
    var searchAllTextFields by remember {
        mutableStateOf(
            if (alwaysOnConfig.keyword.enabled) alwaysOnConfig.keyword.scanAllTextFields
            else keywordConfigsSp.getBoolean("search_all_text_fields", false)
        )
    }
    var blockUnsupportedBrowsers by remember {
        mutableStateOf(keywordConfigsSp.getBoolean("block_all_except_supported", false))
    }
    var wholeWordOnly by remember {
        mutableStateOf(
            if (alwaysOnConfig.keyword.enabled) alwaysOnConfig.keyword.wholeWordOnly
            else keywordConfigsSp.getBoolean("whole_word_only", true)
        )
    }
    var redirectUrl by remember {
        mutableStateOf(
            keywordConfigsSp.getString("redirect_url", "https://www.google.com/?safe=active")
                ?: "https://www.google.com/?safe=active"
        )
    }
    var showRedirectDialog by remember { mutableStateOf(false) }
    var showIgnoredAppsSheet by remember { mutableStateOf(false) }
    var ignoredPackages by remember {
        mutableStateOf(
            (loader.getKeywordBlockerIgnoredApps() + alwaysOnConfig.keyword.ignoredPackages).toSet()
        )
    }

    val rules = remember { loader.loadUnifiedBlockRules() }
    val activeKeywordRules = remember(rules) {
        rules.filter { it.isActiveOrScheduledForContentNow() && it.blockedKeywords.isNotEmpty() }
    }
    val alwaysOnKeywords = remember(alwaysOnConfig) {
        if (alwaysOnConfig.keyword.enabled) alwaysOnConfig.keyword.effectiveKeywords() else emptySet()
    }
    val activeKeywordsTotal = remember(activeKeywordRules, alwaysOnKeywords) {
        (activeKeywordRules.flatMap { it.blockedKeywords }.map { it.lowercase() } + alwaysOnKeywords).toSet().size
    }
    val hasAnyKeywordProtection = activeKeywordRules.isNotEmpty() || alwaysOnConfig.keyword.enabled
    val ignoredAppsCount = ignoredPackages.size

    fun broadcastKeywordRefresh() {
        val refreshIntent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(refreshIntent)
    }

    fun syncAlwaysOnKeywordSettings(
        nextWholeWord: Boolean = wholeWordOnly,
        nextScanAllText: Boolean = searchAllTextFields,
        nextIgnored: Set<String> = ignoredPackages
    ) {
        val current = loader.loadAlwaysOnShieldsConfig()
        val updated = current.copy(
            keyword = current.keyword.copy(
                wholeWordOnly = nextWholeWord,
                scanAllTextFields = nextScanAllText,
                ignoredPackages = nextIgnored
            )
        )
        alwaysOnConfig = updated
        loader.saveAlwaysOnShieldsConfig(updated, syncToEngines = false)
        context.sendBroadcast(
            Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_ALWAYS_ON_SHIELDS).apply {
                setPackage(context.packageName)
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(
                            text = "Keyword Detection Behavior",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Matching sensitivity, scan scope & safe redirect",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("keyword_config_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isServiceEnabled) {
                ServiceRequiredCard(onEnableClick = onEnableServiceClick)
            }

            // Engine Status Banner
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
                            imageVector = if (hasAnyKeywordProtection) Icons.Default.CheckCircle else Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Managed by Always-On Shields & Block Cards",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (hasAnyKeywordProtection) {
                                    val guardPrefix = if (alwaysOnConfig.keyword.enabled) "Always-On Keyword Filter + " else ""
                                    "${guardPrefix}${activeKeywordRules.size} active Block Card(s) enforcing $activeKeywordsTotal unique keyword(s)."
                                } else {
                                    "No Always-On Keyword Filter or Block Cards are currently enforcing keyword triggers."
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
                        text = "Custom keywords, curated Keyword Packs (26 Adult, 12 Social, 11 Gambling, 7 Gaming terms), and warning screen styles are configured per Block Card.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // Matching & Scan Scope Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Matching & Scan Scope",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Control how the Accessibility engine inspects text and browser search queries.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Whole-Word Matching Only",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Match complete words only so short keywords do not trigger inside unrelated longer words.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = wholeWordOnly,
                            onCheckedChange = { enabled ->
                                wholeWordOnly = enabled
                                keywordConfigsSp.edit().putBoolean("whole_word_only", enabled).apply()
                                syncAlwaysOnKeywordSettings(nextWholeWord = enabled)
                                broadcastKeywordRefresh()
                            },
                            modifier = Modifier.testTag("keyword_whole_word_switch")
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Scan All On-Screen Text Nodes",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "When enabled, scans all visible text fields on screen instead of only browser URL and search bars.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = searchAllTextFields,
                            onCheckedChange = { enabled ->
                                searchAllTextFields = enabled
                                keywordConfigsSp.edit().putBoolean("search_all_text_fields", enabled).apply()
                                syncAlwaysOnKeywordSettings(nextScanAllText = enabled)
                                broadcastKeywordRefresh()
                            },
                            modifier = Modifier.testTag("keyword_scan_all_text_switch")
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Block Unsupported Browsers",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Prevent bypassing keyword/URL inspection through obscure browsers that hide their address bar from Accessibility.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = blockUnsupportedBrowsers,
                            onCheckedChange = { enabled ->
                                blockUnsupportedBrowsers = enabled
                                keywordConfigsSp.edit().putBoolean("block_all_except_supported", enabled).apply()
                                broadcastKeywordRefresh()
                            },
                            modifier = Modifier.testTag("keyword_block_unsupported_browsers_switch")
                        )
                    }
                }
            }

            // Safe Redirect & Whitelisted Apps Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRedirectDialog = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Safe Search Redirect URL",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = redirectUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Edit Redirect URL",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showIgnoredAppsSheet = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Whitelisted Apps (Ignored from Keyword Scan)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$ignoredAppsCount whitelisted app(s) excluded from keyword scanning",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Configure Whitelisted Apps",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Info Card
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
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "How Keyword Interception Works",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "When an active Block Card contains keywords, AmniShield checks browser search queries and text nodes in real time, redirects browser tabs to your Safe Search URL, and displays the Block Card's configured Warning Dialog, Mindful Breathing Pause, or Instant Exit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showRedirectDialog) {
        var draftUrl by remember(redirectUrl) { mutableStateOf(redirectUrl) }
        AlertDialog(
            onDismissRequest = { showRedirectDialog = false },
            title = { Text("Safe Search Redirect URL", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter the URL opened in the browser when a blocked keyword query is intercepted:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = draftUrl,
                        onValueChange = { draftUrl = it },
                        singleLine = true,
                        label = { Text("Redirect URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = draftUrl.trim().ifEmpty { "https://www.google.com/?safe=active" }
                        redirectUrl = cleaned
                        keywordConfigsSp.edit().putString("redirect_url", cleaned).apply()
                        broadcastKeywordRefresh()
                        showRedirectDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRedirectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showIgnoredAppsSheet) {
        ShieldIgnoredAppsSheet(
            ignoredPackages = ignoredPackages,
            onDismiss = { showIgnoredAppsSheet = false },
            onToggleIgnoredApp = { pkg, nowIgnored ->
                val updatedSet = if (nowIgnored) ignoredPackages + pkg else ignoredPackages - pkg
                ignoredPackages = updatedSet
                loader.saveKeywordBlockerIgnoredApps(updatedSet.toList())
                syncAlwaysOnKeywordSettings(nextIgnored = updatedSet)
                broadcastKeywordRefresh()
            }
        )
    }
}
