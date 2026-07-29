package com.alhaq.amnshield.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.alhaq.amnshield.notifications.SmartNotificationScheduler
import com.alhaq.amnshield.utils.NotificationHelper
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)
    }

    var dailyReportEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("daily_report_enabled", false))
    }
    var reportHour by remember {
        mutableIntStateOf(sharedPreferences.getInt("daily_report_hour", 20))
    }
    var reportMinute by remember {
        mutableIntStateOf(sharedPreferences.getInt("daily_report_minute", 0))
    }

    var doomscrollAlertsEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("doomscroll_alerts_enabled", true))
    }
    var preBlockWarningsEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("pre_block_warnings_enabled", true))
    }
    var focusReminderEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("focus_reminder_enabled", true))
    }

    val scheduler = remember { SmartNotificationScheduler(context) }
    var hasNotifPermission by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var hasExactAlarmPermission by remember {
        mutableStateOf(scheduler.canScheduleExactAlarms())
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Notifications & Brain", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Dashboard Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🛡️ OS Permission Status Dashboard", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    PermissionStatusRow(
                        title = "Push Notifications",
                        granted = hasNotifPermission,
                        icon = Icons.Default.Notifications,
                        onFix = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    )

                    PermissionStatusRow(
                        title = "Exact Alarms Scheduling",
                        granted = hasExactAlarmPermission,
                        icon = Icons.Default.Alarm,
                        onFix = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }

            // Smart Behavioral Engine Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🧠 Smart Behavioral Brain Alerts", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    SwitchSettingRow(
                        title = "Doomscroll Spike Alerts",
                        subtitle = "Notify when Reels/Shorts scrolling exceeds 25 mins with 1-tap Focus action",
                        checked = doomscrollAlertsEnabled,
                        onCheckedChange = { checked ->
                            doomscrollAlertsEnabled = checked
                            sharedPreferences.edit().putBoolean("doomscroll_alerts_enabled", checked).apply()
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SwitchSettingRow(
                        title = "5-Minute Pre-Block Warnings",
                        subtitle = "Receive an alert 5 minutes before scheduled block rules take effect",
                        checked = preBlockWarningsEnabled,
                        onCheckedChange = { checked ->
                            preBlockWarningsEnabled = checked
                            sharedPreferences.edit().putBoolean("pre_block_warnings_enabled", checked).apply()
                        }
                    )
                }
            }

            // Daily Report Scheduler Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("📊 Scheduled Daily Report", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    SwitchSettingRow(
                        title = "Daily Summary Notification",
                        subtitle = "Receive exact daily report of time saved, reels scrolled & focus score",
                        checked = dailyReportEnabled,
                        onCheckedChange = { checked ->
                            dailyReportEnabled = checked
                            sharedPreferences.edit().putBoolean("daily_report_enabled", checked).apply()
                            if (checked) {
                                scheduler.scheduleDailyReport(reportHour, reportMinute)
                            } else {
                                scheduler.cancelDailyReport()
                            }
                        }
                    )

                    AnimatedVisibility(visible = dailyReportEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Scheduled Report Time:", fontSize = 14.sp)
                            Button(
                                onClick = {
                                    TimePickerDialog(context, { _, selectedHour, selectedMinute ->
                                        reportHour = selectedHour
                                        reportMinute = selectedMinute
                                        sharedPreferences.edit()
                                            .putInt("daily_report_hour", selectedHour)
                                            .putInt("daily_report_minute", selectedMinute)
                                            .apply()

                                        scheduler.scheduleDailyReport(selectedHour, selectedMinute)
                                        Toast.makeText(context, "Scheduled for ${formatTime(selectedHour, selectedMinute)}", Toast.LENGTH_SHORT).show()
                                    }, reportHour, reportMinute, false).show()
                                }
                            ) {
                                Text(formatTime(reportHour, reportMinute))
                            }
                        }
                    }
                }
            }

            // Focus Mode Reminders Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🎯 Focus Reminders", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    SwitchSettingRow(
                        title = "Focus Reminders",
                        subtitle = "Gentle nudges to stay focused during work windows",
                        checked = focusReminderEnabled,
                        onCheckedChange = { checked ->
                            focusReminderEnabled = checked
                            sharedPreferences.edit().putBoolean("focus_reminder_enabled", checked).apply()
                        }
                    )
                }
            }

            // Test Notification Button
            Button(
                onClick = {
                    val notificationHelper = NotificationHelper.getInstance(context)
                    notificationHelper.showFocusReminder(
                        "🚨 Test Doomscroll Alert",
                        "You've scrolled 42 Reels in 25 mins. Tap below to start 25m Focus Session!"
                    )
                    Toast.makeText(context, "Test notification sent!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🚀 Send Test Smart Notification", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    granted: Boolean,
    icon: ImageVector,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (granted) Color(0xFF4CAF50) else Color(0xFFFF9800),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, fontSize = 13.sp)
        }

        if (granted) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
        } else {
            TextButton(onClick = onFix) {
                Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
            }
        }
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    val hourOfDay = calendar.get(Calendar.HOUR)
    val displayHour = if (hourOfDay == 0) 12 else hourOfDay
    val amPm = if (calendar.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"
    return String.format("%02d:%02d (%d:%02d %s)", hour, minute, displayHour, minute, amPm)
}
