package com.alhaq.amnishield.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.alhaq.amnishield.R
import com.alhaq.amnishield.ui.screens.NotificationsInboxScreen
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.utils.ThemeUtils

class NotificationsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EXPAND_ITEM_ID = "extra_expand_item_id"
    }

    private var expandItemIdState by mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        expandItemIdState = intent?.getLongExtra(EXTRA_EXPAND_ITEM_ID, -1L) ?: -1L

        setContent {
            AmniShieldTheme(appTheme = ThemeUtils.resolveAppTheme(this)) {
                NotificationsInboxScreen(
                    initialExpandedItemId = expandItemIdState,
                    onNavigateBack = { finish() },
                    onOpenSmartReminders = {
                        startActivity(Intent(this, RemindersActivity::class.java))
                    },
                    onQuickActionNavigate = { actionTarget ->
                        handleQuickActionTarget(actionTarget)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newExpandId = intent.getLongExtra(EXTRA_EXPAND_ITEM_ID, -1L)
        if (newExpandId != -1L) {
            expandItemIdState = newExpandId
        }
    }

    private fun handleQuickActionTarget(target: String) {
        when (target.uppercase()) {
            "OPEN_REMINDERS" -> {
                startActivity(Intent(this, RemindersActivity::class.java))
            }
            "OPEN_BLOCKERS" -> {
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("start_tab", R.id.navigation_blocks)
                }
                startActivity(mainIntent)
                finish()
            }
            "OPEN_FOCUS", "OPEN_STREAK" -> {
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("start_tab", R.id.navigation_focus)
                }
                startActivity(mainIntent)
                finish()
            }
            else -> {
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("start_tab", R.id.navigation_stats)
                }
                startActivity(mainIntent)
                finish()
            }
        }
    }
}
