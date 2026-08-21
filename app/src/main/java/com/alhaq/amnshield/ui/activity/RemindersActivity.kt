package com.alhaq.amnshield.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alhaq.amnshield.ui.screens.RemindersScreen
import com.alhaq.amnshield.ui.theme.AmnShieldTheme
import com.alhaq.amnshield.utils.ThemeUtils

class RemindersActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AmnShieldTheme {
                RemindersScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}