package com.alhaq.amnishield.ui.screens

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.alhaq.amnishield.data.blockers.AppInterceptionLevel
import com.alhaq.amnishield.data.blockers.PreloadedPack
import com.alhaq.amnishield.data.blockers.PreloadedPacks
import com.alhaq.amnishield.data.blockers.UnifiedBlockRule
import com.alhaq.amnishield.data.blockers.WebsiteGuardConfig
import com.alhaq.amnishield.security.SystemExclusionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledTargetApp(
    val packageName: String,
    val label: String,
    val category: String, // "Social", "Entertainment", "Games", "Other"
    val usageMinutesWeek: Long,
    val iconDrawable: Drawable?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SelectTargetsScreen(
    initialAppTargets: Map<String, AppInterceptionLevel>,
    initialBlockedDomains: List<String>,
    initialBlockedKeywords: List<String>,
    initialBlockNewlyInstalled: Boolean,
    initialIncludeInBrowserShortform: Boolean = false,
    initialTabIndex: Int = 0,
    onConfirm: (
        appTargets: Map<String, AppInterceptionLevel>,
        blockedDomains: List<String>,
        blockedKeywords: List<String>,
        blockNewlyInstalled: Boolean,
        includeInBrowserShortform: Boolean
    ) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTabIndex.coerceIn(0, 2)) }

    var appTargets by remember { mutableStateOf(initialAppTargets) }
    var blockedDomains by remember { mutableStateOf(initialBlockedDomains) }
    var blockedKeywords by remember { mutableStateOf(initialBlockedKeywords) }
    var blockNewlyInstalled by remember { mutableStateOf(initialBlockNewlyInstalled) }
    var includeInBrowserShortform by remember { mutableStateOf(initialIncludeInBrowserShortform) }

    var searchQuery by remember { mutableStateOf("") }
    var sortByMostUsed by remember { mutableStateOf(true) }

    var installedApps by remember { mutableStateOf<List<InstalledTargetApp>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }

    var modalTargetApp by remember { mutableStateOf<InstalledTargetApp?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loaded = loadCategorizedInstalledApps(context)
            withContext(Dispatchers.Main) {
                installedApps = loaded
                isLoadingApps = false
            }
        }
    }

    if (modalTargetApp != null) {
        val target = modalTargetApp!!
        val currentSelection = appTargets[target.packageName]
        val shortformLabel = UnifiedBlockRule.shortformLabelForPackage(target.packageName)
        SelectAppBlockModeDialog(
            app = target,
            currentLevel = currentSelection,
            shortformLabel = shortformLabel,
            onDismiss = { modalTargetApp = null },
            onSelectLevel = { level ->
                val mutable = appTargets.toMutableMap()
                if (level == null) {
                    mutable.remove(target.packageName)
                } else {
                    mutable[target.packageName] = level
                }
                appTargets = mutable
                modalTargetApp = null
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("select_targets_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TargetPickerTabItem(
                            title = "Apps",
                            count = appTargets.size + (if (includeInBrowserShortform) 1 else 0),
                            icon = Icons.Outlined.Apps,
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.testTag("tab_apps")
                        )
                        TargetPickerTabItem(
                            title = "Websites",
                            count = blockedDomains.size,
                            icon = Icons.Outlined.Language,
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.testTag("tab_websites")
                        )
                        TargetPickerTabItem(
                            title = "Keywords",
                            count = blockedKeywords.size,
                            icon = Icons.Outlined.Abc,
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            modifier = Modifier.testTag("tab_keywords")
                        )
                    }

                    IconButton(
                        onClick = {
                            onConfirm(
                                appTargets,
                                blockedDomains,
                                blockedKeywords,
                                blockNewlyInstalled,
                                includeInBrowserShortform
                            )
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("select_targets_confirm_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm selection",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> AppsTargetPickerTab(
                    installedApps = installedApps,
                    isLoading = isLoadingApps,
                    appTargets = appTargets,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    blockNewlyInstalled = blockNewlyInstalled,
                    onBlockNewlyInstalledChange = { blockNewlyInstalled = it },
                    includeInBrowserShortform = includeInBrowserShortform,
                    onIncludeInBrowserShortformChange = { includeInBrowserShortform = it },
                    sortByMostUsed = sortByMostUsed,
                    onSortModeChange = { sortByMostUsed = it },
                    onToggleApp = { app ->
                        if (UnifiedBlockRule.isShortformCapable(app.packageName)) {
                            modalTargetApp = app
                        } else {
                            val mutable = appTargets.toMutableMap()
                            if (mutable.containsKey(app.packageName)) {
                                mutable.remove(app.packageName)
                            } else {
                                mutable[app.packageName] = AppInterceptionLevel.BLOCK_ENTIRE_APP
                            }
                            appTargets = mutable
                        }
                    },
                    onSelectCategoryAll = { categoryApps ->
                        val mutable = appTargets.toMutableMap()
                        categoryApps.forEach { app ->
                            if (!mutable.containsKey(app.packageName)) {
                                mutable[app.packageName] = AppInterceptionLevel.BLOCK_ENTIRE_APP
                            }
                        }
                        appTargets = mutable
                    },
                    onClearCategory = { categoryApps ->
                        val mutable = appTargets.toMutableMap()
                        categoryApps.forEach { app ->
                            mutable.remove(app.packageName)
                        }
                        appTargets = mutable
                    }
                )
                1 -> WebsitesTargetPickerTab(
                    blockedDomains = blockedDomains,
                    onAddDomain = { rawDomain ->
                        val cleaned = sanitizeDomainInput(rawDomain)
                        if (cleaned.isNotBlank() && !blockedDomains.contains(cleaned)) {
                            blockedDomains = blockedDomains + cleaned
                        }
                    },
                    onAddDomainsBatch = { domains ->
                        val merged = (blockedDomains + domains.map { sanitizeDomainInput(it) }.filter { it.isNotBlank() }).distinct()
                        blockedDomains = merged
                    },
                    onRemoveDomainsBatch = { domains ->
                        val toRemove = domains.map { sanitizeDomainInput(it) }.toSet()
                        blockedDomains = blockedDomains.filterNot { toRemove.contains(it) }
                    },
                    onRemoveDomain = { domain ->
                        blockedDomains = blockedDomains - domain
                    }
                )
                2 -> KeywordsTargetPickerTab(
                    blockedKeywords = blockedKeywords,
                    onAddKeyword = { rawKeyword ->
                        val cleaned = rawKeyword.trim().lowercase()
                        if (cleaned.isNotBlank() && !blockedKeywords.contains(cleaned)) {
                            blockedKeywords = blockedKeywords + cleaned
                        }
                    },
                    onAddKeywordsBatch = { keywords ->
                        val merged = (blockedKeywords + keywords.map { it.trim().lowercase() }.filter { it.isNotBlank() }).distinct()
                        blockedKeywords = merged
                    },
                    onRemoveKeywordsBatch = { keywords ->
                        val toRemove = keywords.map { it.trim().lowercase() }.toSet()
                        blockedKeywords = blockedKeywords.filterNot { toRemove.contains(it) }
                    },
                    onRemoveKeyword = { keyword ->
                        blockedKeywords = blockedKeywords - keyword
                    }
                )
            }
        }
    }
}

@Composable
private fun TargetPickerTabItem(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) activeColor else inactiveColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (count > 0) "$title ($count)" else title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) activeColor else inactiveColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) activeColor else Color.Transparent)
        )
    }
}

@Composable
private fun AppsTargetPickerTab(
    installedApps: List<InstalledTargetApp>,
    isLoading: Boolean,
    appTargets: Map<String, AppInterceptionLevel>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    blockNewlyInstalled: Boolean,
    onBlockNewlyInstalledChange: (Boolean) -> Unit,
    includeInBrowserShortform: Boolean,
    onIncludeInBrowserShortformChange: (Boolean) -> Unit,
    sortByMostUsed: Boolean,
    onSortModeChange: (Boolean) -> Unit,
    onToggleApp: (InstalledTargetApp) -> Unit,
    onSelectCategoryAll: (List<InstalledTargetApp>) -> Unit,
    onClearCategory: (List<InstalledTargetApp>) -> Unit
) {
    val filteredApps = remember(installedApps, searchQuery, sortByMostUsed) {
        val base = if (searchQuery.isBlank()) {
            installedApps
        } else {
            val q = searchQuery.trim().lowercase()
            installedApps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
        if (sortByMostUsed) {
            base.sortedWith(
                compareByDescending<InstalledTargetApp> { it.usageMinutesWeek }
                    .thenBy { it.label.lowercase() }
            )
        } else {
            base.sortedBy { it.label.lowercase() }
        }
    }

    val categoryOrder = listOf("Social", "Entertainment", "Games", "Other")
    val groupedApps = remember(filteredApps) {
        categoryOrder.mapNotNull { category ->
            val items = filteredApps.filter { it.category == category }
            if (items.isEmpty()) null else category to items
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search apps") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search apps"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_apps_input")
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onBlockNewlyInstalledChange(!blockNewlyInstalled) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Block newly installed apps",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Checkbox(
                        checked = blockNewlyInstalled,
                        onCheckedChange = onBlockNewlyInstalledChange,
                        modifier = Modifier.testTag("block_newly_installed_checkbox")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onIncludeInBrowserShortformChange(!includeInBrowserShortform) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Block in-browser short videos",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "YouTube Shorts, Instagram Reels & TikTok inside web browsers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Checkbox(
                        checked = includeInBrowserShortform,
                        onCheckedChange = onIncludeInBrowserShortformChange,
                        modifier = Modifier.testTag("block_browser_shortform_checkbox")
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = sortByMostUsed,
                        onClick = { onSortModeChange(true) },
                        label = { Text("Most used") },
                        leadingIcon = if (sortByMostUsed) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilterChip(
                        selected = !sortByMostUsed,
                        onClick = { onSortModeChange(false) },
                        label = { Text("Alphabetical") },
                        leadingIcon = if (!sortByMostUsed) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (groupedApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching apps found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            groupedApps.forEach { (categoryName, categoryApps) ->
                item(key = "cat_$categoryName") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = { onSelectCategoryAll(categoryApps) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Select all")
                                    }
                                    TextButton(
                                        onClick = { onClearCategory(categoryApps) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Clear")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val rows = categoryApps.chunked(4)
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                rows.forEach { rowApps ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        for (i in 0 until 4) {
                                            val app = rowApps.getOrNull(i)
                                            if (app != null) {
                                                AppGridTile(
                                                    app = app,
                                                    interceptionLevel = appTargets[app.packageName],
                                                    onClick = { onToggleApp(app) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppGridTile(
    app: InstalledTargetApp,
    interceptionLevel: AppInterceptionLevel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = interceptionLevel != null
    val isShortformOnly = interceptionLevel == AppInterceptionLevel.BLOCK_SHORTFORM_ONLY
    val shortformCapable = UnifiedBlockRule.isShortformCapable(app.packageName)
    val shortformLabel = UnifiedBlockRule.shortformLabelForPackage(app.packageName)

    val tileBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        Color.Transparent
    }
    val tileBorder = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = tileBg,
        border = tileBorder,
        modifier = modifier.heightIn(min = 96.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                AppDrawableIcon(
                    drawable = app.iconDrawable,
                    contentDescription = app.label,
                    modifier = Modifier.size(48.dp)
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isShortformOnly) Icons.Default.VideoLibrary else Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                } else if (shortformCapable) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Granular options available",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = app.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            if (isShortformOnly) {
                Text(
                    text = shortformLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun AppDrawableIcon(
    drawable: Drawable?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(drawable) {
        runCatching { drawable?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            modifier = modifier.clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Android,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SelectAppBlockModeDialog(
    app: InstalledTargetApp,
    currentLevel: AppInterceptionLevel?,
    shortformLabel: String,
    onDismiss: () -> Unit,
    onSelectLevel: (AppInterceptionLevel?) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select what to block",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Option 1: Block nothing
                val isNothingSelected = currentLevel == null
                GranularBlockOptionCard(
                    title = "Block nothing",
                    subtitle = null,
                    selected = isNothingSelected,
                    leadingContent = {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    },
                    onClick = { onSelectLevel(null) }
                )

                // Option 2: Block entire app
                val isEntireSelected = currentLevel == AppInterceptionLevel.BLOCK_ENTIRE_APP
                GranularBlockOptionCard(
                    title = "Block entire app",
                    subtitle = null,
                    selected = isEntireSelected,
                    leadingContent = {
                        AppDrawableIcon(
                            drawable = app.iconDrawable,
                            contentDescription = app.label,
                            modifier = Modifier.size(40.dp)
                        )
                    },
                    onClick = { onSelectLevel(AppInterceptionLevel.BLOCK_ENTIRE_APP) }
                )

                // Option 3: Short-form only (Reels / Shorts / Updates / Spotlight)
                val isShortformSelected = currentLevel == AppInterceptionLevel.BLOCK_SHORTFORM_ONLY
                GranularBlockOptionCard(
                    title = shortformLabel,
                    subtitle = "Beta",
                    selected = isShortformSelected,
                    leadingContent = {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AppDrawableIcon(
                                drawable = app.iconDrawable,
                                contentDescription = shortformLabel,
                                modifier = Modifier.size(40.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    },
                    onClick = { onSelectLevel(AppInterceptionLevel.BLOCK_SHORTFORM_ONLY) }
                )
            }
        }
    }
}

@Composable
private fun GranularBlockOptionCard(
    title: String,
    subtitle: String?,
    selected: Boolean,
    leadingContent: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = border,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            leadingContent()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebsitesTargetPickerTab(
    blockedDomains: List<String>,
    onAddDomain: (String) -> Unit,
    onAddDomainsBatch: (List<String>) -> Unit,
    onRemoveDomainsBatch: (List<String>) -> Unit,
    onRemoveDomain: (String) -> Unit
) {
    var domainInput by remember { mutableStateOf("") }
    var domainError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val submitDomain: () -> Unit = {
        val cleaned = WebsiteGuardConfig.normalizeDomainInput(domainInput)
        if (cleaned == null) {
            domainError = "Enter a valid domain (e.g. example.com)"
        } else {
            onAddDomain(cleaned)
            domainInput = ""
            domainError = null
            focusManager.clearFocus()
        }
    }
    val suggestedDomains = remember {
        listOf(
            "instagram.com",
            "youtube.com",
            "tiktok.com",
            "reddit.com",
            "x.com",
            "facebook.com",
            "twitch.tv",
            "netflix.com"
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Websites to block",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            OutlinedTextField(
                value = domainInput,
                onValueChange = {
                    domainInput = it
                    domainError = null
                },
                placeholder = { Text("Add website, e.g. google.com") },
                isError = domainError != null,
                supportingText = domainError?.let { err -> { Text(err) } },
                trailingIcon = {
                    IconButton(
                        onClick = submitDomain,
                        modifier = Modifier.testTag("add_website_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add website"
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { submitDomain() }
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("website_domain_input")
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Preloaded Website & Blocklist Packs",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "One-tap import built-in domain blocklists and category packs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PreloadedPacks.websitePacks.forEach { pack ->
                    PreloadedPackSelectableCard(
                        pack = pack,
                        currentItems = blockedDomains,
                        unitLabel = "domains",
                        onAddPack = { onAddDomainsBatch(pack.items) },
                        onRemovePack = { onRemoveDomainsBatch(pack.items) }
                    )
                }
            }
        }

        item {
            Text(
                text = "Quick add common distractions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestedDomains.filter { !blockedDomains.contains(it) }.forEach { preset ->
                    AssistChip(
                        onClick = { onAddDomain(preset) },
                        label = { Text(preset) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        if (blockedDomains.isNotEmpty()) {
            item {
                Text(
                    text = "Selected websites (${blockedDomains.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            items(blockedDomains, key = { it }) { domain ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = domain,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { onRemoveDomain(domain) }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Remove $domain",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordsTargetPickerTab(
    blockedKeywords: List<String>,
    onAddKeyword: (String) -> Unit,
    onAddKeywordsBatch: (List<String>) -> Unit,
    onRemoveKeywordsBatch: (List<String>) -> Unit,
    onRemoveKeyword: (String) -> Unit
) {
    var keywordInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Keywords to block",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Blocks any website or search query containing the keyword",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OutlinedTextField(
                value = keywordInput,
                onValueChange = { keywordInput = it },
                placeholder = { Text("Add keyword, e.g. game") },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (keywordInput.isNotBlank()) {
                                onAddKeyword(keywordInput)
                                keywordInput = ""
                                focusManager.clearFocus()
                            }
                        },
                        modifier = Modifier.testTag("add_keyword_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add keyword"
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (keywordInput.isNotBlank()) {
                            onAddKeyword(keywordInput)
                            keywordInput = ""
                            focusManager.clearFocus()
                        }
                    }
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_keyword_input")
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Preloaded Keyword Packs",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "One-tap import curated keyword packs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PreloadedPacks.keywordPacks.forEach { pack ->
                    PreloadedPackSelectableCard(
                        pack = pack,
                        currentItems = blockedKeywords,
                        unitLabel = "keywords",
                        onAddPack = { onAddKeywordsBatch(pack.items) },
                        onRemovePack = { onRemoveKeywordsBatch(pack.items) }
                    )
                }
            }
        }

        if (blockedKeywords.isNotEmpty()) {
            item {
                Text(
                    text = "Selected keywords (${blockedKeywords.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            items(blockedKeywords, key = { it }) { keyword ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(end = 2.dp)
                            ) {
                                Text(
                                    text = "ABC",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = keyword,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { onRemoveKeyword(keyword) }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Remove $keyword",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreloadedPackSelectableCard(
    pack: PreloadedPack,
    currentItems: List<String>,
    unitLabel: String,
    onAddPack: () -> Unit,
    onRemovePack: () -> Unit
) {
    val currentSet = remember(currentItems) { currentItems.toSet() }
    val countAdded = remember(currentSet, pack.items) { pack.items.count { currentSet.contains(it) } }
    val allAdded = countAdded == pack.items.size && pack.items.isNotEmpty()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (allAdded) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (allAdded) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preloaded_pack_${pack.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = pack.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = pack.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            allAdded -> "All ${pack.items.size} $unitLabel added"
                            countAdded > 0 -> "$countAdded of ${pack.items.size} $unitLabel added"
                            else -> pack.description
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (allAdded) {
                OutlinedButton(
                    onClick = onRemovePack,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Remove", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Button(
                    onClick = onAddPack,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Add (${pack.items.size})", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun sanitizeDomainInput(raw: String): String {
    return WebsiteGuardConfig.normalizeDomainInput(raw).orEmpty()
}

private fun loadCategorizedInstalledApps(context: Context): List<InstalledTargetApp> {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolveInfos = runCatching { pm.queryIntentActivities(mainIntent, 0) }.getOrDefault(emptyList())

    val now = System.currentTimeMillis()
    val weekAgo = now - 7L * 24L * 60L * 60L * 1000L
    val usageMap: Map<String, Long> = runCatching {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        usm?.queryAndAggregateUsageStats(weekAgo, now)?.mapValues { (_, stat) ->
            stat.totalTimeInForeground / 60_000L
        } ?: emptyMap()
    }.getOrDefault(emptyMap())

    val socialPackages = setOf(
        "com.instagram.android",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.snapchat.android",
        "com.twitter.android",
        "com.linkedin.android",
        "org.telegram.messenger",
        "com.discord",
        "com.reddit.frontpage",
        "com.pinterest"
    )
    val entertainmentPackages = setOf(
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.netflix.mediaclient",
        "com.spotify.music",
        "tv.twitch.android.app",
        "com.amazon.avod.thirdpartyclient",
        "com.disney.disneyplus"
    )

    val seen = mutableSetOf<String>()
    val result = mutableListOf<InstalledTargetApp>()

    for (ri in resolveInfos) {
        val pkg = ri.activityInfo?.packageName ?: continue
        if (pkg == context.packageName ||
            pkg == "com.alhaq.amnishield" ||
            pkg == "com.alhaq.deenshield" ||
            pkg.startsWith("com.alhaq.deenshield.") ||
            SystemExclusionManager.isExempt(pkg, context) ||
            !seen.add(pkg)
        ) continue

        val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
        val label = runCatching {
            ri.loadLabel(pm).toString().ifBlank { pkg }
        }.getOrDefault(pkg)
        val icon = runCatching { ri.loadIcon(pm) }.getOrNull()

        val category = when {
            socialPackages.contains(pkg) -> "Social"
            entertainmentPackages.contains(pkg) -> "Entertainment"
            appInfo != null && appInfo.category == ApplicationInfo.CATEGORY_GAME -> "Games"
            appInfo != null && appInfo.category == ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            appInfo != null && (appInfo.category == ApplicationInfo.CATEGORY_VIDEO ||
                appInfo.category == ApplicationInfo.CATEGORY_AUDIO) -> "Entertainment"
            else -> "Other"
        }

        result.add(
            InstalledTargetApp(
                packageName = pkg,
                label = label,
                category = category,
                usageMinutesWeek = usageMap[pkg] ?: 0L,
                iconDrawable = icon
            )
        )
    }

    return result
}
