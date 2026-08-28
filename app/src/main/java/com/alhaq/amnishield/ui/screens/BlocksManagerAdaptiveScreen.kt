package com.alhaq.amnishield.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.*
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.ui.state.AmniShieldState
import com.alhaq.amnishield.ui.state.ScheduleRule
import com.alhaq.amnishield.ui.viewmodel.AmniShieldViewModel

enum class BlockerCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val ruleType: String
) {
    APPS("App Blocker", "Restrict distracting apps & games", Icons.Outlined.Apps, "App Blocker"),
    KEYWORDS("Keyword Filter", "Block adult & explicit keywords", Icons.Outlined.Search, "Keyword Blocker"),
    WEBSITES("Website Blocker", "Filter adult sites & custom URLs", Icons.Outlined.Language, "Website Blocker"),
    REELS("Reels & Shorts", "Interrupt infinite doomscrolling", Icons.Outlined.SmartDisplay, "Reels Blocker"),
    FOCUS("Focus Sessions", "Deep focus mode with custom timers", Icons.Outlined.Timer, "Focus Mode"),
    LIMITS("Launch Limits", "Cap daily app open frequency", Icons.Outlined.LockClock, "Launch Limit")
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BlocksManagerAdaptiveScreen(
    state: AmniShieldState,
    viewModel: AmniShieldViewModel,
    onBack: () -> Unit = {}
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<BlockerCategory>()
    var selectedCategory by remember { mutableStateOf<BlockerCategory?>(BlockerCategory.APPS) }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Protection Rules", fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                    ) {
                        items(BlockerCategory.entries) { category ->
                            val isSelected = selectedCategory == category
                            val isEnabled = when (category) {
                                BlockerCategory.APPS -> state.isAppBlockerEnabled
                                BlockerCategory.KEYWORDS -> state.isKeywordBlockerEnabled
                                BlockerCategory.WEBSITES -> state.isWebFilterEnabled
                                BlockerCategory.REELS -> state.isReelsBlockerEnabled
                                BlockerCategory.FOCUS -> state.isFocusModeActive
                                BlockerCategory.LIMITS -> state.isUsageLimitEnabled
                            }

                            OutlinedCard(
                                onClick = {
                                    selectedCategory = category
                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, category)
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            category.icon,
                                            contentDescription = null,
                                            tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            category.title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            category.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val currentCat = selectedCategory ?: BlockerCategory.APPS
                when (currentCat) {
                    BlockerCategory.APPS -> {
                        CreateRuleScreen(
                            state = state,
                            onSaveRule = { rule ->
                                viewModel.addScheduleRule(rule)
                                if (navigator.canNavigateBack()) navigator.navigateBack()
                            },
                            onBack = {
                                if (navigator.canNavigateBack()) navigator.navigateBack() else onBack()
                            }
                        )
                    }
                    BlockerCategory.KEYWORDS -> {
                        CreateKeywordBlockerRuleScreen(
                            state = state,
                            viewModel = viewModel,
                            onSaveRule = { rule ->
                                viewModel.addScheduleRule(rule)
                                if (navigator.canNavigateBack()) navigator.navigateBack()
                            },
                            onBack = {
                                if (navigator.canNavigateBack()) navigator.navigateBack() else onBack()
                            }
                        )
                    }
                    BlockerCategory.WEBSITES -> {
                        CreateWebsiteBlockerRuleScreen(
                            state = state,
                            viewModel = viewModel,
                            onSaveRule = { rule ->
                                viewModel.addScheduleRule(rule)
                                if (navigator.canNavigateBack()) navigator.navigateBack()
                            },
                            onBack = {
                                if (navigator.canNavigateBack()) navigator.navigateBack() else onBack()
                            }
                        )
                    }
                    BlockerCategory.REELS -> {
                        CreateReelsBlockerRuleScreen(
                            state = state,
                            viewModel = viewModel,
                            onSaveRule = { rule ->
                                viewModel.addScheduleRule(rule)
                                if (navigator.canNavigateBack()) navigator.navigateBack()
                            },
                            onBack = {
                                if (navigator.canNavigateBack()) navigator.navigateBack() else onBack()
                            }
                        )
                    }
                    BlockerCategory.FOCUS -> {
                        CreateFocusModeRuleScreen(
                            state = state,
                            onSaveRule = { rule ->
                                viewModel.addScheduleRule(rule)
                                if (navigator.canNavigateBack()) navigator.navigateBack()
                            },
                            onBack = {
                                if (navigator.canNavigateBack()) navigator.navigateBack() else onBack()
                            }
                        )
                    }
                    BlockerCategory.LIMITS -> {
                        CreateRuleScreen(
                            state = state,
                            prefillTarget = "LAUNCH_LIMIT",
                            onSaveRule = { rule ->
                                viewModel.addScheduleRule(rule)
                                if (navigator.canNavigateBack()) navigator.navigateBack()
                            },
                            onBack = {
                                if (navigator.canNavigateBack()) navigator.navigateBack() else onBack()
                            }
                        )
                    }
                }
            }
        }
    )
}
