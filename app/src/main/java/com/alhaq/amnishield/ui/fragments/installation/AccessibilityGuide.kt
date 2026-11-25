package com.alhaq.amnishield.ui.fragments.installation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.alhaq.amnishield.ui.activity.MainActivity
import com.alhaq.amnishield.ui.screens.OnboardingFlowHost

class AccessibilityGuide : Fragment() {
    companion object {
        const val FRAGMENT_ID = "accessibility_guide_fragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OnboardingFlowHost(
                    initialStep = 4,
                    onFinishOnboarding = {
                        val ctx = context ?: return@OnboardingFlowHost
                        ctx.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
                            .edit().putBoolean("isFirstLaunchComplete", true).apply()
                        val intent = Intent(ctx, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        activity?.finish()
                    }
                )
            }
        }
    }
}