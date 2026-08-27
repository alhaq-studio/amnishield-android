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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.POWER_SERVICE
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.alhaq.amnishield.R
import com.alhaq.amnishield.databinding.FragmentPermissionsBinding
import com.alhaq.amnishield.permissions.PermissionsManager
import com.alhaq.amnishield.utils.PermissionGuideHelper
import com.alhaq.amnishield.utils.ZipUtils
import com.alhaq.amnishield.utils.ZipUtils.unzipSharedPreferencesFromUri

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
            val sharedPreferences =
                requireContext().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            sharedPreferences.edit().putBoolean("isFirstLaunchComplete", true).apply()

            requireActivity().supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_holder,
                    AccessibilityGuide()
                )
                .addToBackStack(null)
                .commit()
        }

        // Accessibility click
        binding.accessPermRoot.setOnClickListener {
            if (isAccessibilityPermissionGiven()) return@setOnClickListener
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                accessibilityLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
        }

        // Overlay click
        binding.overlayPermRoot.setOnClickListener {
            if (isOverlayPermissionGiven()) return@setOnClickListener
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
                overlayLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(intent)
            }
        }

        // Usage Stats click
        binding.usagePermRoot.setOnClickListener {
            if (isUsageStatsPermissionGiven()) return@setOnClickListener
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                usageStatsLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
            }
        }

        // Background / Battery Optimization click
        binding.bgPermRoot.setOnClickListener {
            if (isBackgroundPermissionGiven()) return@setOnClickListener
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            batteryOptimizationLauncher.launch(intent)
        }

        // Notifications click
        binding.notifPermRoot.setOnClickListener {
            if (isNotificationPermissionGiven()) return@setOnClickListener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Restore click
        binding.restoreRoot.setOnClickListener {
            ZipUtils.showRestorePicker(restorePicker)
        }
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
        val isAccessOk = isAccessibilityPermissionGiven()
        val isOverlayOk = isOverlayPermissionGiven()
        val isUsageOk = isUsageStatsPermissionGiven()
        val isBgOk = isBackgroundPermissionGiven()
        val isNotifOk = isNotificationPermissionGiven()

        setPermissionIconState(isAccessOk, binding.accessPermIcon)
        setPermissionIconState(isOverlayOk, binding.overlayPermIcon)
        setPermissionIconState(isUsageOk, binding.usagePermIcon)
        setPermissionIconState(isBgOk, binding.bgPermIcon)
        setPermissionIconState(isNotifOk, binding.notifPermIcon)

        updateNextButtonState()
    }

    private fun updateNextButtonState() {
        val isAccessOk = isAccessibilityPermissionGiven()
        val isOverlayOk = isOverlayPermissionGiven()
        val isUsageOk = isUsageStatsPermissionGiven()
        val isBgOk = isBackgroundPermissionGiven()

        // Enable next button when key permissions are configured
        binding.btnNext.isEnabled = isAccessOk || (isBgOk && (isOverlayOk || isUsageOk))
    }

    private fun setPermissionIconState(isEnabled: Boolean, icon: ImageView) {
        if (isEnabled) {
            icon.setImageResource(R.drawable.baseline_done_24)
            icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.md_theme_onSurface))
        } else {
            icon.setImageResource(R.drawable.baseline_close_24)
            icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.error_color))
        }
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