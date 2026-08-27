package com.alhaq.amnishield.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alhaq.amnishield.ui.screens.RemindersScreen
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.utils.ThemeUtils

class RemindersActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AmniShieldTheme {
                RemindersScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}