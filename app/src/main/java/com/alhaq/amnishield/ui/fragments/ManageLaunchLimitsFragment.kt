package com.alhaq.amnishield.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.alhaq.amnishield.data.blockers.AppLaunchLimitRule
import com.alhaq.amnishield.databinding.FragmentManageLaunchLimitsBinding
import com.alhaq.amnishield.premium.PremiumManager
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.ui.adapters.LaunchLimitAdapter
import com.alhaq.amnishield.ui.dialogs.SetLaunchLimitDialog
import com.alhaq.amnishield.utils.SavedPreferencesLoader

/**
 * Fragment for viewing and managing all app launch limit rules.
 * Shows a list of apps with launch limits and allows editing/deleting.
 */
class ManageLaunchLimitsFragment : Fragment() {

    private var _binding: FragmentManageLaunchLimitsBinding? = null
    private val binding get() = _binding!!

    private var adapter: LaunchLimitAdapter? = null
    private lateinit var savedPrefs: SavedPreferencesLoader

    companion object {
        const val FRAGMENT_ID = "manage_launch_limits"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageLaunchLimitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val selectAppsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
            val firstPkg = selectedApps?.firstOrNull()
            if (!firstPkg.isNullOrEmpty()) {
                try {
                    val appInfo = requireContext().packageManager.getApplicationInfo(firstPkg, 0)
                    val appName = appInfo.loadLabel(requireContext().packageManager).toString()

                    val dialog = SetLaunchLimitDialog(
                        packageName = firstPkg,
                        appName = appName,
                        onSave = { rule ->
                            if (rule != null) {
                                savedPrefs.addAppLaunchLimitRule(rule)
                                Toast.makeText(
                                    requireContext(),
                                    "Launch limit saved: ${rule.getDescription()}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                sendRefreshRequest()
                                loadLaunchLimits()
                            }
                        }
                    )
                    dialog.show(childFragmentManager, "add_launch_limit_$firstPkg")
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Could not set limit for selected app", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        savedPrefs = SavedPreferencesLoader(requireContext())

        // Setup back button
        binding.btnBackArrow.setOnClickListener {
            if (!parentFragmentManager.popBackStackImmediate()) {
                requireActivity().finish()
            }
        }

        // Setup add limit actions
        val openAppPicker = {
            val intent = Intent(requireContext(), com.alhaq.amnishield.ui.activity.SelectAppsActivity::class.java).apply {
                putExtra("SINGLE_SELECTION", true)
            }
            selectAppsLauncher.launch(intent)
        }
        binding.fabAddLimit.setOnClickListener { openAppPicker() }
        binding.btnAddLimitEmpty.setOnClickListener { openAppPicker() }

        // Setup adapter
        adapter = LaunchLimitAdapter(
            context = requireContext(),
            onEdit = { rule -> editLaunchLimit(rule) },
            onDelete = { rule -> deleteLaunchLimit(rule) }
        )

        binding.launchLimitsList.adapter = adapter

        // Load and display limits
        loadLaunchLimits()
    }

    override fun onResume() {
        super.onResume()
        // Reload limits each time fragment becomes visible
        loadLaunchLimits()
    }

    private fun loadLaunchLimits() {
        val rules = savedPrefs.loadAppLaunchLimitRules()

        if (rules.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.launchLimitsList.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.launchLimitsList.visibility = View.VISIBLE
            adapter?.submitList(rules)
        }
    }

    private fun editLaunchLimit(rule: AppLaunchLimitRule) {
        try {
            val appInfo = requireContext().packageManager.getApplicationInfo(rule.packageName, 0)
            val appName = appInfo.loadLabel(requireContext().packageManager).toString()

            val dialog = SetLaunchLimitDialog(
                packageName = rule.packageName,
                appName = appName,
                onSave = { updatedRule ->
                    if (updatedRule != null) {
                        savedPrefs.addAppLaunchLimitRule(updatedRule)
                        Toast.makeText(
                            requireContext(),
                            "Launch limit updated: ${updatedRule.getDescription()}",
                            Toast.LENGTH_SHORT
                        ).show()
                        sendRefreshRequest()
                        loadLaunchLimits()
                    }
                }
            )
            dialog.show(childFragmentManager, "edit_launch_limit_${rule.id}")
        } catch (e: Exception) {
            android.util.Log.e("ManageLaunchLimits", "Error editing launch limit for ${rule.packageName}", e)
            Toast.makeText(requireContext(), "Could not edit limit — try again", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteLaunchLimit(rule: AppLaunchLimitRule) {
        try {
            val appInfo = requireContext().packageManager.getApplicationInfo(rule.packageName, 0)
            val appName = appInfo.loadLabel(requireContext().packageManager).toString()

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Launch Limit?")
                .setMessage("Remove launch limit for $appName?")
                .setPositiveButton("Delete") { _, _ ->
                    savedPrefs.removeAppLaunchLimitRule(rule.packageName)
                    Toast.makeText(
                        requireContext(),
                        "Launch limit removed",
                        Toast.LENGTH_SHORT
                    ).show()
                    sendRefreshRequest()
                    loadLaunchLimits()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            android.util.Log.e("ManageLaunchLimits", "Error deleting launch limit for ${rule.packageName}", e)
            Toast.makeText(requireContext(), "Could not delete limit — try again", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendRefreshRequest() {
        val intent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
        requireContext().sendBroadcast(intent.setPackage(requireContext().packageName))
        val unifiedIntent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES)
        requireContext().sendBroadcast(unifiedIntent.setPackage(requireContext().packageName))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
