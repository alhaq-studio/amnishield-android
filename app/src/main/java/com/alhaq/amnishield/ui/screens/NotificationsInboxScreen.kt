package com.alhaq.amnishield.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.utils.NotificationHelper
import com.alhaq.amnishield.utils.NotificationInboxStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsInboxScreen(
    initialExpandedItemId: Long = -1L,
    onNavigateBack: () -> Unit,
    onOpenSmartReminders: () -> Unit,
    onQuickActionNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var items by remember { mutableStateOf(NotificationInboxStore.getAll(context)) }
    var selectedCategory by remember { mutableStateOf("all") }
    var expandedIds by remember {
        mutableStateOf(
            if (initialExpandedItemId != -1L) setOf(initialExpandedItemId) else emptySet()
        )
    }

    LaunchedEffect(initialExpandedItemId) {
        if (initialExpandedItemId != -1L) {
            NotificationInboxStore.markAsRead(context, initialExpandedItemId)
            items = NotificationInboxStore.getAll(context)
            expandedIds = expandedIds + initialExpandedItemId
        }
    }

    val filteredItems = remember(items, selectedCategory) {
        if (selectedCategory == "all") items
        else items.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }
    val unreadCount = remember(items) { items.count { !it.read } }

    fun handleDeleteItem(item: NotificationInboxStore.InboxItem) {
        val removed = NotificationInboxStore.removeById(context, item.id)
        items = NotificationInboxStore.getAll(context)
        expandedIds = expandedIds - item.id
        if (removed != null) {
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Removed \"${removed.title}\"",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    NotificationInboxStore.restoreItem(context, removed)
                    items = NotificationInboxStore.getAll(context)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Notification Center",
                            fontWeight = FontWeight.Bold
                        )
                        if (unreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(
                                    text = "$unreadCount new",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("inbox_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSmartReminders,
                        modifier = Modifier.testTag("inbox_open_reminders_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Smart Reminders Settings"
                        )
                    }
                    if (items.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                NotificationInboxStore.markAllRead(context)
                                items = NotificationInboxStore.getAll(context)
                            },
                            modifier = Modifier.testTag("inbox_mark_all_read_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DoneAll,
                                contentDescription = "Mark all read"
                            )
                        }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Cross-Navigation & Live Report Generator Header Card
            item {
                InboxCrossConnectBanner(
                    totalCount = items.size,
                    unreadCount = unreadCount,
                    onOpenSmartReminders = onOpenSmartReminders,
                    onGenerateLiveReport = {
                        val created = NotificationHelper.getInstance(context).showDailyReportNotification()
                        items = NotificationInboxStore.getAll(context)
                        expandedIds = setOf(created.id)
                    },
                    onClearAll = {
                        NotificationInboxStore.clearAll(context)
                        items = emptyList()
                        expandedIds = emptySet()
                    }
                )
            }

            // Category Filter Chips
            item {
                CategoryFilterRow(
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it }
                )
            }

            if (filteredItems.isEmpty()) {
                item {
                    EmptyInboxCard(
                        onGenerateReport = {
                            val created = NotificationHelper.getInstance(context).showDailyReportNotification()
                            items = NotificationInboxStore.getAll(context)
                            expandedIds = setOf(created.id)
                        },
                        onOpenSmartReminders = onOpenSmartReminders
                    )
                }
            } else {
                items(
                    items = filteredItems,
                    key = { it.id }
                ) { item ->
                    val isExpanded = expandedIds.contains(item.id)
                    SwipeableNotificationReportCard(
                        item = item,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            val willExpand = !isExpanded
                            expandedIds = if (willExpand) expandedIds + item.id else expandedIds - item.id
                            if (willExpand && !item.read) {
                                NotificationInboxStore.markAsRead(context, item.id)
                                items = NotificationInboxStore.getAll(context)
                            }
                        },
                        onDelete = { handleDeleteItem(item) },
                        onQuickAction = { target -> onQuickActionNavigate(target) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InboxCrossConnectBanner(
    totalCount: Int,
    unreadCount: Int,
    onOpenSmartReminders: () -> Unit,
    onGenerateLiveReport: () -> Unit,
    onClearAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Interactive Shield Reports",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap any notification to drop down its full report, or swipe sideways to remove.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenSmartReminders,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inbox_smart_settings_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Smart Reminders", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onGenerateLiveReport,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inbox_generate_report_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Assessment,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Live Snapshot", style = MaterialTheme.typography.labelMedium)
                }

                if (totalCount > 0) {
                    IconButton(
                        onClick = onClearAll,
                        modifier = Modifier.testTag("inbox_clear_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = "Clear All Notifications",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    val categories = listOf(
        "all" to "All",
        "report" to "Daily Reports",
        "smart" to "Smart Coach",
        "blocking" to "Shield Alerts",
        "achievement" to "Milestones",
        "streak" to "Streaks"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { (key, label) ->
            FilterChip(
                selected = selectedCategory == key,
                onClick = { onSelectCategory(key) },
                label = { Text(label) },
                leadingIcon = if (selectedCategory == key) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null,
                modifier = Modifier.testTag("inbox_filter_$key")
            )
        }
    }
}
