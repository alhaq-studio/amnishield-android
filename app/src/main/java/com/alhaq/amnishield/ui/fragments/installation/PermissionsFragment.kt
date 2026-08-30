package com.alhaq.amnishield.ui.fragments.installation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.POWER_SERVICE
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.alhaq.amnishield.R
import com.alhaq.amnishield.databinding.DialogPermissionInfoBinding
import com.alhaq.amnishield.databinding.FragmentPermissionsBinding
import com.alhaq.amnishield.permissions.PermissionsManager
import com.alhaq.amnishield.utils.PermissionGuideHelper
import com.alhaq.amnishield.utils.ZipUtils
import com.alhaq.amnishield.utils.ZipUtils.unzipSharedPreferencesFromUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PermissionsFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "permission_fragment"
    }

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var permissionGuideHelper: PermissionGuideHelper
    private lateinit var permissionsManager: PermissionsManager

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            setPermissionIconState(isGranted, binding.notifPermIcon)
            updateNextButtonState()
        }

    private val batteryOptimizationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            setPermissionIconState(isBackgroundPermissionGiven(), binding.bgPermIcon)
            updateNextButtonState()
        }

    private val accessibilityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            setPermissionIconState(isAccessibilityPermissionGiven(), binding.accessPermIcon)
            updateNextButtonState()
        }

    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            setPermissionIconState(isOverlayPermissionGiven(), binding.overlayPermIcon)
            updateNextButtonState()
        }

    private val usageStatsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            setPermissionIconState(isUsageStatsPermissionGiven(), binding.usagePermIcon)
            updateNextButtonState()
        }

    private val restorePicker: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.data?.let { uri ->
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                activity?.contentResolver?.takePersistableUriPermission(uri, takeFlags)
                unzipSharedPreferencesFromUri(requireContext(), uri)
                refreshPermissions()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X,
            /* forward = */ true
        )
        returnTransition = com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X,
            /* forward = */ false
        )
        exitTransition = com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X,
            /* forward = */ true
        )
        reenterTransition = com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X,
            /* forward = */ false
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("BatteryLife")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        permissionGuideHelper = PermissionGuideHelper(requireActivity())
        permissionsManager = PermissionsManager(requireContext())

        binding.btnNext.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_holder,
                    SupportAlHaqFragment()
                )
                .addToBackStack(null)
                .commit()
        }

        // 1. Accessibility click (Google Play Prominent Disclosure Enforcement - LOCKED)
        val accessClickListener = View.OnClickListener {
            if (isAccessibilityPermissionGiven()) return@OnClickListener
            com.alhaq.amnishield.utils.AccessibilityDisclosureDialog.show(
                context = requireContext(),
                onAgree = {
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        accessibilityLauncher.launch(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                    }
                },
                onDecline = {
                    // Explicit denial by user - do not navigate to settings
                }
            )
        }
        binding.cardAccessibility.setOnClickListener(accessClickListener)
        binding.accessPermRoot.setOnClickListener(accessClickListener)

        // 2. Display Over Other Apps (Overlay) click
        val overlayClickListener = View.OnClickListener {
            if (isOverlayPermissionGiven()) return@OnClickListener
            showOverlayDisclosureDialog()
        }
        binding.cardOverlay.setOnClickListener(overlayClickListener)
        binding.overlayPermRoot.setOnClickListener(overlayClickListener)

        // 3. App Usage Access (Stats) click
        val usageClickListener = View.OnClickListener {
            if (isUsageStatsPermissionGiven()) return@OnClickListener
            showUsageStatsDisclosureDialog()
        }
        binding.cardUsage.setOnClickListener(usageClickListener)
        binding.usagePermRoot.setOnClickListener(usageClickListener)

        // 4. Background / Battery Optimization click
        val bgClickListener = View.OnClickListener {
            if (isBackgroundPermissionGiven()) return@OnClickListener
            showBatteryOptimizationDisclosureDialog()
        }
        binding.cardBg.setOnClickListener(bgClickListener)
        binding.bgPermRoot.setOnClickListener(bgClickListener)

        // 5. Notifications click
        val notifClickListener = View.OnClickListener {
            if (isNotificationPermissionGiven()) return@OnClickListener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.cardNotif.setOnClickListener(notifClickListener)
        binding.notifPermRoot.setOnClickListener(notifClickListener)

        // Restore click
        binding.restoreRoot.setOnClickListener {
            ZipUtils.showRestorePicker(restorePicker)
        }
    }

    /**
     * Shows existing prominent disclosure dialog overlay for App Usage Data permission.
     */
    private fun showUsageStatsDisclosureDialog() {
        val dialogBinding = DialogPermissionInfoBinding.inflate(layoutInflater)
        dialogBinding.title.text = getString(R.string.enable_2, getString(R.string.usage_stats))
        dialogBinding.desc.text = "AmniShield requires App Usage Access to monitor foreground app usage, detect doomscrolling habits, and enforce launch limits. All usage data is processed 100% locally on your device and is never shared."
        dialogBinding.point1.text = "Track app screen time and daily launch counts"
        dialogBinding.point2.text = "Enforce scheduled blocks and launch limits"
        dialogBinding.point3.visibility = View.GONE
        dialogBinding.point4.visibility = View.GONE
        dialogBinding.btnGuide.visibility = View.GONE

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.btnReject.setOnClickListener {
            dialog.dismiss()
        }
        dialogBinding.btnAccept.setOnClickListener {
            dialog.dismiss()
            try {
                val intent = Intent(
                    Settings.ACTION_USAGE_ACCESS_SETTINGS,
                    Uri.parse("package:${requireContext().packageName}")
                )
                usageStatsLauncher.launch(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    usageStatsLauncher.launch(intent)
                } catch (e2: Exception) {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        }

        dialog.show()
    }

    /**
     * Shows existing prominent disclosure dialog overlay for Display Over Other Apps (Overlay) permission.
     */
    private fun showOverlayDisclosureDialog() {
        val dialogBinding = DialogPermissionInfoBinding.inflate(layoutInflater)
        dialogBinding.title.text = getString(R.string.enable_2, getString(R.string.display_over_other_apps))
        dialogBinding.desc.text = getString(R.string.device_perm_draw_over_other_apps)
        dialogBinding.point1.text = getString(R.string.show_time_elapsed_on_phone)
        dialogBinding.point2.text = getString(R.string.calculate_how_many_reels_tiktok_short_videos_you_scroll_per_day)
        dialogBinding.point3.visibility = View.GONE
        dialogBinding.point4.visibility = View.GONE
        dialogBinding.btnGuide.visibility = View.GONE

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.btnReject.setOnClickListener {
            dialog.dismiss()
        }
        dialogBinding.btnAccept.setOnClickListener {
            dialog.dismiss()
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
                overlayLauncher.launch(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    overlayLauncher.launch(intent)
                } catch (e2: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
            }
        }

        dialog.show()
    }

    /**
     * Shows existing prominent disclosure dialog overlay for Battery Optimization permission.
     */
    private fun showBatteryOptimizationDisclosureDialog() {
        val dialogBinding = DialogPermissionInfoBinding.inflate(layoutInflater)
        dialogBinding.title.text = getString(R.string.enable_2, "Unrestricted Background")
        dialogBinding.desc.text = "AmniShield requires unrestricted background access so that your scheduled blocks, focus mode sessions, and screen-time monitoring continue operating reliably even when battery saver is active."
        dialogBinding.point1.text = "Prevent Android battery optimizations from closing protection services"
        dialogBinding.point2.text = "Ensure timely scheduled block enforcement and timers"
        dialogBinding.point3.visibility = View.GONE
        dialogBinding.point4.visibility = View.GONE
        dialogBinding.btnGuide.visibility = View.GONE

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.btnReject.setOnClickListener {
            dialog.dismiss()
        }
        dialogBinding.btnAccept.setOnClickListener {
            dialog.dismiss()
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${requireContext().packageName}")
                )
                batteryOptimizationLauncher.launch(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (e2: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refreshPermissions() {
        if (_binding == null) return
        val isAccessOk = isAccessibilityPermissionGiven()
        val isOverlayOk = isOverlayPermissionGiven()
        val isUsageOk = isUsageStatsPermissionGiven()
        val isBgOk = isBackgroundPermissionGiven()
        val isNotifOk = isNotificationPermissionGiven()

        setPermissionCardState(isAccessOk, binding.cardAccessibility, binding.accessPermIcon, binding.accessPermBadge, "Required")
        setPermissionCardState(isOverlayOk, binding.cardOverlay, binding.overlayPermIcon, binding.overlayPermBadge, "Required")
        setPermissionCardState(isUsageOk, binding.cardUsage, binding.usagePermIcon, binding.usagePermBadge, "Recommended")
        setPermissionCardState(isBgOk, binding.cardBg, binding.bgPermIcon, binding.bgPermBadge, "Recommended")
        setPermissionCardState(isNotifOk, binding.cardNotif, binding.notifPermIcon, binding.notifPermBadge, "Optional")

        updateNextButtonState()
    }

    private fun updateNextButtonState() {
        if (_binding == null) return
        val isAccessOk = isAccessibilityPermissionGiven()
        val isOverlayOk = isOverlayPermissionGiven()
        val isUsageOk = isUsageStatsPermissionGiven()
        val isBgOk = isBackgroundPermissionGiven()

        // Enable next button when key permissions are configured
        val canProceed = isAccessOk || (isBgOk && (isOverlayOk || isUsageOk))
        binding.btnNext.isEnabled = canProceed

        var grantedCount = 0
        if (isAccessOk) grantedCount++
        if (isOverlayOk) grantedCount++
        if (isUsageOk) grantedCount++
        if (isBgOk) grantedCount++

        binding.btnNext.text = if (grantedCount >= 3) {
            "Continue"
        } else {
            "Continue ($grantedCount/4 Ready)"
        }
    }

    private fun setPermissionCardState(
        isEnabled: Boolean,
        card: com.google.android.material.card.MaterialCardView,
        icon: ImageView,
        badge: TextView? = null,
        defaultBadgeText: String = "Required"
    ) {
        val primaryColor = com.google.android.material.color.MaterialColors.getColor(
            card,
            androidx.appcompat.R.attr.colorPrimary,
            android.graphics.Color.BLUE
        )
        val outlineColor = com.google.android.material.color.MaterialColors.getColor(
            card,
            com.google.android.material.R.attr.colorOutlineVariant,
            android.graphics.Color.GRAY
        )
        val onSurfaceVariant = com.google.android.material.color.MaterialColors.getColor(
            card,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            android.graphics.Color.GRAY
        )
        val errorColor = com.google.android.material.color.MaterialColors.getColor(
            card,
            androidx.appcompat.R.attr.colorError,
            android.graphics.Color.RED
        )

        if (isEnabled) {
            icon.setImageResource(R.drawable.baseline_done_24)
            icon.setColorFilter(primaryColor)
            card.strokeColor = primaryColor
            card.strokeWidth = 2
            badge?.text = "Active"
            badge?.setTextColor(primaryColor)
        } else {
            icon.setImageResource(R.drawable.baseline_chevron_right_24)
            icon.setColorFilter(onSurfaceVariant)
            card.strokeColor = outlineColor
            card.strokeWidth = 1
            badge?.text = defaultBadgeText
            if (defaultBadgeText == "Required") {
                badge?.setTextColor(errorColor)
            } else {
                badge?.setTextColor(onSurfaceVariant)
            }
        }
    }

    private fun setPermissionIconState(isEnabled: Boolean, icon: ImageView) {
        refreshPermissions()
    }

    private fun isAccessibilityPermissionGiven(): Boolean {
        if (!::permissionGuideHelper.isInitialized) {
            permissionGuideHelper = PermissionGuideHelper(requireActivity())
        }
        return permissionGuideHelper.isAccessibilityEnabled(com.alhaq.amnishield.services.AmniShieldAccessibilityService::class.java)
    }

    private fun isOverlayPermissionGiven(): Boolean {
        return Settings.canDrawOverlays(requireContext())
    }

    private fun isUsageStatsPermissionGiven(): Boolean {
        if (!::permissionsManager.isInitialized) {
            permissionsManager = PermissionsManager(requireContext())
        }
        return permissionsManager.isUsageStatsPermissionGranted()
    }

    private fun isBackgroundPermissionGiven(): Boolean {
        val powerManager =
            requireContext().getSystemService(POWER_SERVICE) as PowerManager
        val packageName = requireContext().packageName
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isNotificationPermissionGiven(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
}