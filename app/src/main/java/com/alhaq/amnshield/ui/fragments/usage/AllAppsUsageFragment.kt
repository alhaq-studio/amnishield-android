package com.alhaq.amnshield.ui.fragments.usage

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.icu.util.Calendar
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.alhaq.amnshield.R
import com.alhaq.amnshield.ui.components.InteractiveScreenTimeDonut
import com.alhaq.amnshield.ui.screens.AppUsageItem
import com.alhaq.amnshield.ui.theme.AmnShieldTheme
import com.alhaq.amnshield.utils.ThemeUtils
import com.alhaq.amnshield.databinding.AppUsageItemBinding
import com.alhaq.amnshield.databinding.DialogPermissionInfoBinding
import com.alhaq.amnshield.databinding.FragmentAllAppUsageBinding
import com.alhaq.amnshield.ui.activity.FragmentActivity
import com.alhaq.amnshield.ui.activity.SelectAppsActivity
import com.alhaq.amnshield.ui.fragments.BlocksManagerFragment
import com.alhaq.amnshield.utils.SavedPreferencesLoader
import com.alhaq.amnshield.utils.TimeTools
import com.alhaq.amnshield.utils.UsageStatsHelper
import com.alhaq.amnshield.utils.getDefaultLauncherPackageName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class AllAppsUsageFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "all_app_usage"
    }

    private var selectedDate:Long = System.currentTimeMillis()
    private var currentDate:Long = selectedDate
    private var earliestDate:Long = selectedDate

    private var _binding: FragmentAllAppUsageBinding? = null
    private val binding get() = _binding!!

    private var ignoredPackages: MutableSet<String> = mutableSetOf()
    private lateinit var savedPreferencesLoader: SavedPreferencesLoader

    private var selectedPackageForDonut: String? = null
    private var isWebsiteModeActive: Boolean = false

    val selectIgnoredAppsLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
            selectedApps?.let {
                savedPreferencesLoader.saveIgnoredAppUsageTracker(it.toSet())
                reloadIgnoredPackages()
                lifecycleScope.launch(Dispatchers.IO) {
                    val localDate = Instant.ofEpochMilli(selectedDate)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    setUsageStats(localDate)
                }
            }
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllAppUsageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        savedPreferencesLoader = SavedPreferencesLoader(requireContext())

        if (!hasUsageStatsPermission(requireContext())) {
            makeUsageStatsPermissoinDialog()
        }

        val adapter = AppUsageAdapter(emptyList())
        binding.appUsageRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.appUsageRecyclerView.adapter = adapter

        lifecycleScope.launch(Dispatchers.IO) {

            reloadIgnoredPackages()

            setUsageStats()

            findDataAvailabilityRange()
        }
        binding.openMenu.setOnClickListener {
            val popupMenu = PopupMenu(requireContext(), binding.openMenu)
            popupMenu.menuInflater.inflate(R.menu.usage_tracker_options, popupMenu.menu)

            // Handle menu item clicks
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.select_ignored -> {

                        val intent = Intent(requireContext(), SelectAppsActivity::class.java)
                        intent.putStringArrayListExtra(
                            "PRE_SELECTED_APPS",
                            ArrayList(savedPreferencesLoader.loadIgnoredAppUsageTracker())
                        )
                        selectIgnoredAppsLauncher.launch(
                            intent,
                            ActivityOptionsCompat.makeCustomAnimation(
                                requireContext(),
                                R.anim.fade_in,
                                R.anim.fade_out
                            )
                        )
                        true
                    }

                    R.id.view_recommendations -> {
                        binding.main.smoothScrollTo(0, binding.recommendationCard.top)
                        true
                    }

                    R.id.add_shortcut_usage_tracker -> {

                        val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                            action = Intent.ACTION_CREATE_SHORTCUT
                        }

                        intent.putExtra("fragment", FRAGMENT_ID)
                        val shortcutInfo =
                            ShortcutInfoCompat.Builder(requireContext(), "amnshield_usage_tracker")
                                .setShortLabel("Usage Stats")
                                .setLongLabel("Usage Stats")
                                .setIntent(intent)
                                .setIcon(
                                    IconCompat.createWithResource(
                                        requireContext(),
                                        R.drawable.baseline_query_stats_24
                                    )
                                )
                                .build()


                        val supported =
                            ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())
                        val dynamicShortcuts =
                            ShortcutManagerCompat.getDynamicShortcuts(requireContext())

                        if (supported) {
                            if (dynamicShortcuts.contains(shortcutInfo)) {
                                return@setOnMenuItemClickListener false
                            }
                        }
                        val pinnedShortcutCallbackIntent =
                            Intent("example.intent.action.SHORTCUT_CREATED")

                        val successCallback = PendingIntent.getBroadcast(
                            requireContext(),
                            3000,
                            pinnedShortcutCallbackIntent,
                            FLAG_IMMUTABLE
                        )

                        ShortcutManagerCompat.requestPinShortcut(
                            requireContext(),
                            shortcutInfo,
                            successCallback.intentSender
                        )

                        true
                    }

                    else -> false
                }
            }

            popupMenu.show()

        }
        binding.selectDate.setOnClickListener {
            showDatePickerDialog(selectedDate, earliestDate, currentDate) { newDate ->
                selectedDate = newDate
                binding.selectDate.text = TimeTools.formatDate(newDate)
                val localDate = Instant.ofEpochMilli(newDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                lifecycleScope.launch(Dispatchers.IO) {
                    setUsageStats(localDate)
                }

            }
        }

    }

    fun findDataAvailabilityRange() {

        val usageStatsManager = requireContext().getSystemService(UsageStatsManager::class.java)
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            0, System.currentTimeMillis()
        )

        // Calculate earliest available date
        earliestDate = stats.minByOrNull { it.firstTimeStamp }?.firstTimeStamp ?: System.currentTimeMillis()
        currentDate = System.currentTimeMillis()
        selectedDate = currentDate.coerceAtLeast(earliestDate) // Ensure valid range

    }

    private fun reloadIgnoredPackages() {
        ignoredPackages.clear()
        getDefaultLauncherPackageName(requireContext().packageManager)?.let {
            ignoredPackages.add(it)
        }
        ignoredPackages.addAll(savedPreferencesLoader.loadIgnoredAppUsageTracker())
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch(Dispatchers.IO) {
            val localDate = Instant.ofEpochMilli(selectedDate)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            setUsageStats(localDate)
            findDataAvailabilityRange()
        }
    }
    private fun makeUsageStatsPermissoinDialog() {
        val dialogBinding =
            DialogPermissionInfoBinding.inflate(layoutInflater)
        dialogBinding.title.text =
            getString(R.string.enable_2, "Device Usage Access")

        dialogBinding.desc.text =
            "AmnShield requires device usage access to monitor apps, helping you manage screen time effectively and stay focused on your goals. Rest assured, all data stays securely on your device and is never shared with anyone, ensuring your privacy is fully protected."

        dialogBinding.point1.text = "Track what apps you use"
        dialogBinding.point2.visibility = View.GONE
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .show()

        dialogBinding.btnReject.setOnClickListener {
            dialog.dismiss()
            activity?.finish()
        }
        dialogBinding.btnAccept.setOnClickListener {
            Toast.makeText(requireContext(), "Find 'AmnShield' and press enable", Toast.LENGTH_LONG)
                .show()
            requestUsageStatsPermission(requireContext())
            dialog.dismiss()
        }
    }

    private var currentLoadedStats: List<Stat> = emptyList()

    private suspend fun setUsageStats(date : LocalDate = LocalDate.now()) {
        val usageStatsHelper = UsageStatsHelper(requireContext())
        val list = usageStatsHelper.getForegroundStatsByDay(date).filter {
            it.totalTime >= 180_000 && it.packageName !in ignoredPackages
        }
        val totalTime = TimeTools.formatTime(calculateTotalScreenTimeInHours(list),false)

        withContext(Dispatchers.Main) {
            try {
                currentLoadedStats = list
                if(list.isEmpty()){
                    Toast.makeText(requireContext(),"No data available",Toast.LENGTH_SHORT).show()
                }
                updateDonutChart(list)
                updateRecommendations(list, isWebsiteModeActive, selectedPackageForDonut)
                updateUsageList(isWebsiteModeActive, list)

                val targetPkg = activity?.intent?.getStringExtra("target_package_name")
                if (!targetPkg.isNullOrEmpty()) {
                    activity?.intent?.removeExtra("target_package_name")
                    var matchedStat = list.find { it.packageName == targetPkg }
                    if (matchedStat == null) {
                        matchedStat = usageStatsHelper.getForegroundStatsByDay(date).find { it.packageName == targetPkg }
                    }
                    if (matchedStat != null) {
                        activity?.supportFragmentManager?.beginTransaction()
                            ?.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                            ?.replace(R.id.fragment_holder, AppUsageBreakdown(matchedStat))
                            ?.addToBackStack(null)
                            ?.commit()
                    }
                }
            } catch (e: Exception) {
                Log.e("AppUsageFragment", "Error updating UI with stats", e)
            }
        }
    }

    private fun updateUsageList(isWebMode: Boolean, appStats: List<Stat> = currentLoadedStats) {
        val adapter = binding.appUsageRecyclerView.adapter as? AppUsageAdapter ?: return
        if (!isWebMode) {
            val appItems = appStats.map { stat ->
                val pm = requireContext().packageManager
                val appLabel = try {
                    val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                    appInfo.loadLabel(pm).toString()
                } catch (e: Exception) {
                    stat.packageName.substringAfterLast('.')
                }
                DisplayUsageItem(
                    id = stat.packageName,
                    title = appLabel,
                    durationMillis = stat.totalTime,
                    isWebsite = false,
                    originalStat = stat
                )
            }
            adapter.updateData(appItems)
        } else {
            val domainStatsMap = savedPreferencesLoader.loadWebsiteUsageStats()
            val ignoredDomains = savedPreferencesLoader.loadIgnoredWebDomains()
            val webItems = domainStatsMap.entries
                .filter { it.key !in ignoredDomains }
                .sortedByDescending { it.value }
                .map { entry ->
                    DisplayUsageItem(
                        id = entry.key,
                        title = entry.key,
                        durationMillis = entry.value,
                        isWebsite = true,
                        originalStat = null
                    )
                }
            adapter.updateData(webItems)
        }
    }

    private fun calculateTotalScreenTimeInHours(stats: List<Stat>): Long {
        val totalTimeInMillis = stats.sumOf { it.totalTime }

        return totalTimeInMillis
    }

    private fun updateRecommendations(
        statsList: List<Stat>,
        isWebMode: Boolean = isWebsiteModeActive,
        selectedItem: String? = null
    ) {
        if (!isWebMode) {
            val sorted = statsList.sortedByDescending { it.totalTime }
            val topApps = if (selectedItem != null && selectedItem != "other_apps") {
                val selectedStat = statsList.find { it.packageName == selectedItem }
                if (selectedStat != null) {
                    listOf(selectedStat) + sorted.filter { it.packageName != selectedItem }.take(2)
                } else {
                    sorted.take(3)
                }
            } else {
                sorted.take(3)
            }

            binding.recommendationTitle.text = "Recommended apps to control"
            if (topApps.isEmpty()) {
                binding.recommendationSubtitle.text = "No usage data available for recommendations on this date."
                setRecommendationRow(0, null)
                setRecommendationRow(1, null)
                setRecommendationRow(2, null)
                return
            }

            binding.recommendationSubtitle.text = if (selectedItem != null && selectedItem != "other_apps") {
                "Selected app highlighted from screen time chart"
            } else {
                "Based on your highest usage apps for this day"
            }

            setRecommendationRow(0, topApps.getOrNull(0))
            setRecommendationRow(1, topApps.getOrNull(1))
            setRecommendationRow(2, topApps.getOrNull(2))
        } else {
            val domainStatsMap = savedPreferencesLoader.loadWebsiteUsageStats()
            val sortedWebsites = domainStatsMap.entries.sortedByDescending { it.value }
            val topWebsites = if (selectedItem != null && selectedItem != "other_sites") {
                val matched = sortedWebsites.find { it.key == selectedItem }
                if (matched != null) {
                    listOf(matched) + sortedWebsites.filter { it.key != selectedItem }.take(2)
                } else {
                    sortedWebsites.take(3)
                }
            } else {
                sortedWebsites.take(3)
            }

            binding.recommendationTitle.text = "Recommended websites to control"
            if (topWebsites.isEmpty()) {
                binding.recommendationSubtitle.text = "No website usage recorded yet on this date."
                setRecommendationWebRow(0, null)
                setRecommendationWebRow(1, null)
                setRecommendationWebRow(2, null)
                return
            }

            binding.recommendationSubtitle.text = if (selectedItem != null && selectedItem != "other_sites") {
                "Selected web domain highlighted from browsing chart"
            } else {
                "Based on your highest visited web domains"
            }

            setRecommendationWebRow(0, topWebsites.getOrNull(0))
            setRecommendationWebRow(1, topWebsites.getOrNull(1))
            setRecommendationWebRow(2, topWebsites.getOrNull(2))
        }
    }

    private fun setRecommendationRow(index: Int, stat: Stat?) {
        val row = when (index) {
            0 -> binding.recommendationItem1
            1 -> binding.recommendationItem2
            else -> binding.recommendationItem3
        }
        val iconView: ImageView = when (index) {
            0 -> binding.recommendationIcon1
            1 -> binding.recommendationIcon2
            else -> binding.recommendationIcon3
        }
        val textView: TextView = when (index) {
            0 -> binding.recommendationText1
            1 -> binding.recommendationText2
            else -> binding.recommendationText3
        }

        if (stat == null) {
            row.visibility = View.GONE
            return
        }

        val pm = requireContext().packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(stat.packageName, 0)
            iconView.setImageDrawable(pm.getApplicationIcon(appInfo))
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            iconView.setImageResource(R.drawable.baseline_android_24)
            stat.packageName.substringAfterLast('.')
        }

        val minutes = (stat.totalTime / 60000L).toInt()
        val riskText = when {
            minutes >= 240 || stat.sessions.size >= 35 -> "High"
            minutes >= 120 || stat.sessions.size >= 20 -> "Moderate"
            else -> "Elevated"
        }

        textView.text = "$appName • ${TimeTools.formatTime(stat.totalTime, false)} • $riskText risk"
        row.visibility = View.VISIBLE
        row.setOnClickListener {
            activity?.supportFragmentManager?.beginTransaction()
                ?.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                ?.replace(R.id.fragment_holder, AppUsageBreakdown(stat))
                ?.addToBackStack(null)
                ?.commit()
        }
    }

    private fun setRecommendationWebRow(index: Int, entry: Map.Entry<String, Long>?) {
        val row = when (index) {
            0 -> binding.recommendationItem1
            1 -> binding.recommendationItem2
            else -> binding.recommendationItem3
        }
        val iconView: ImageView = when (index) {
            0 -> binding.recommendationIcon1
            1 -> binding.recommendationIcon2
            else -> binding.recommendationIcon3
        }
        val textView: TextView = when (index) {
            0 -> binding.recommendationText1
            1 -> binding.recommendationText2
            else -> binding.recommendationText3
        }

        if (entry == null) {
            row.visibility = View.GONE
            return
        }

        iconView.setImageResource(R.drawable.baseline_language_24)
        val domain = entry.key
        val minutes = (entry.value / 60000L).toInt()
        val riskText = when {
            minutes >= 120 -> "High"
            minutes >= 45 -> "Moderate"
            else -> "Standard"
        }

        textView.text = "$domain • ${TimeTools.formatTime(entry.value, false)} • $riskText risk"
        row.visibility = View.VISIBLE
        row.setOnClickListener {
            showWebsiteControlDialog(domain, entry.value)
        }
    }

    private fun showWebsiteControlDialog(domain: String, durationMillis: Long = 0L) {
        val blockedWebsites = savedPreferencesLoader.loadBlockedWebsites()
        val isAlreadyBlocked = blockedWebsites.contains(domain)
        val hasExistingWebsiteRules = blockedWebsites.isNotEmpty() ||
                savedPreferencesLoader.loadAppBlockerScheduleRules().any { it.packageName == "website_blocker" }

        val timeSnippet = if (durationMillis > 0) {
            "Browsing time today: ${TimeTools.formatTime(durationMillis, false)}\n\n"
        } else ""

        val dialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Control Website: $domain")

        if (hasExistingWebsiteRules) {
            if (isAlreadyBlocked) {
                dialogBuilder.setMessage("${timeSnippet}This domain is currently in your active website blocklist.\n\nChoose an action:")
                    .setPositiveButton("Create New Rule") { _, _ ->
                        openCreateWebsiteRule(domain)
                    }
                    .setNeutralButton("Remove from Blocklist") { _, _ ->
                        val updated = blockedWebsites.toMutableSet()
                        updated.remove(domain)
                        savedPreferencesLoader.saveBlockedWebsites(updated)
                        Toast.makeText(requireContext(), "Removed $domain from website blocklist", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
            } else {
                dialogBuilder.setMessage("${timeSnippet}Choose how you would like to restrict $domain:")
                    .setPositiveButton("Add to Existing Rule") { _, _ ->
                        val updated = blockedWebsites.toMutableSet()
                        updated.add(domain)
                        savedPreferencesLoader.saveBlockedWebsites(updated)
                        Toast.makeText(requireContext(), "Added $domain to active website blocklist", Toast.LENGTH_LONG).show()
                    }
                    .setNeutralButton("Create New Rule") { _, _ ->
                        openCreateWebsiteRule(domain)
                    }
                    .setNegativeButton("Cancel", null)
            }
        } else {
            dialogBuilder.setMessage("${timeSnippet}No website blocking rules are currently active.\n\nCreate a new schedule or always-block rule with $domain in the blocklist:")
                .setPositiveButton("Create New Rule") { _, _ ->
                    openCreateWebsiteRule(domain)
                }
                .setNegativeButton("Cancel", null)
        }

        dialogBuilder.show()
    }

    private fun openCreateWebsiteRule(domain: String) {
        val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
            putExtra("fragment", BlocksManagerFragment.FRAGMENT_ID)
            putExtra("action", "create")
            putExtra("prefill_target", "WEBSITE_BLOCKER")
            putExtra("prefill_website", domain)
            putExtra("preset_keyword", domain)
        }
        val options = ActivityOptionsCompat.makeCustomAnimation(
            requireContext(),
            R.anim.fade_in,
            R.anim.fade_out
        )
        startActivity(intent, options.toBundle())
    }

    private fun showDatePickerDialog(
        selectedDate: Long,
        startDate: Long,
        endDate: Long,
        onDateSelected: (Long) -> Unit
    ) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectedDate

        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val pickedCalendar = Calendar.getInstance()
                pickedCalendar.set(year, month, dayOfMonth)
                onDateSelected(pickedCalendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Restrict the selectable date range
        datePicker.datePicker.minDate = startDate
        datePicker.datePicker.maxDate = endDate
        datePicker.show()
    }

    private fun updateDonutChart(statsList: List<Stat>) {
        val totalMillis = calculateTotalScreenTimeInHours(statsList)
        val totalFormatted = TimeTools.formatTime(totalMillis, false)
        val sortedStats = statsList.sortedByDescending { it.totalTime }
        val pm = requireContext().packageManager

        val topApps = sortedStats.map { stat ->
            val label = try {
                val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                appInfo.loadLabel(pm).toString()
            } catch (e: Exception) {
                stat.packageName.substringAfterLast('.')
            }
            val progressRatio = if (totalMillis > 0) stat.totalTime.toFloat() / totalMillis.toFloat() else 0f
            AppUsageItem(
                name = label,
                packageName = stat.packageName,
                timeFormatted = TimeTools.formatTime(stat.totalTime, false),
                progress = progressRatio
            )
        }

        val domainStatsMap = savedPreferencesLoader.loadWebsiteUsageStats()
        val totalWebMillis = domainStatsMap.values.sum().coerceAtLeast(1L)
        val topWebsites = domainStatsMap.entries.sortedByDescending { it.value }.map { entry ->
            AppUsageItem(
                name = entry.key,
                packageName = entry.key,
                timeFormatted = TimeTools.formatTime(entry.value, false),
                progress = (entry.value.toFloat() / totalWebMillis.toFloat())
            )
        }
        val totalWebFormatted = TimeTools.formatTime(totalWebMillis, false)

        binding.composeDonutChart.setContent {
            val activeTheme = ThemeUtils.resolveAppTheme(requireContext())
            var currentMode by remember { mutableStateOf(if (isWebsiteModeActive) "websites" else "apps") }
            val isAppTrackingOn = savedPreferencesLoader.isAppUsageTrackingEnabled()
            val isWebTrackingOn = savedPreferencesLoader.isWebsiteUsageTrackingEnabled()

            AmnShieldTheme(appTheme = activeTheme) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Category Selector Pills: Apps vs Websites
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = currentMode == "apps",
                            onClick = {
                                currentMode = "apps"
                                isWebsiteModeActive = false
                                selectedPackageForDonut = null
                                updateRecommendations(statsList, false, null)
                                updateUsageList(false, statsList)
                            },
                            label = { Text("📱 Apps", fontSize = 13.sp) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        FilterChip(
                            selected = currentMode == "websites",
                            onClick = {
                                currentMode = "websites"
                                isWebsiteModeActive = true
                                selectedPackageForDonut = null
                                updateRecommendations(statsList, true, null)
                                updateUsageList(true, statsList)
                            },
                            label = { Text("🌐 Websites", fontSize = 13.sp) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (currentMode == "apps") {
                        if (!isAppTrackingOn) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "🔒 App Usage Tracking Paused",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Enable App Tracking in Settings to log screen time.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            InteractiveScreenTimeDonut(
                                totalDurationFormatted = if (totalMillis > 0) totalFormatted else "0m",
                                apps = topApps,
                                selectedAppPackage = selectedPackageForDonut,
                                onAppSelected = { pkg ->
                                    selectedPackageForDonut = if (selectedPackageForDonut == pkg) null else pkg
                                    updateRecommendations(statsList, false, selectedPackageForDonut)
                                }
                            )
                        }
                    } else {
                        if (!isWebTrackingOn) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "🔒 Website Usage Tracking Paused",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Enable Website Tracking in Settings to record domain durations on-device.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            InteractiveScreenTimeDonut(
                                totalDurationFormatted = if (totalWebMillis > 0) totalWebFormatted else "0m",
                                apps = topWebsites,
                                selectedAppPackage = selectedPackageForDonut,
                                onAppSelected = { domain ->
                                    selectedPackageForDonut = if (selectedPackageForDonut == domain) null else domain
                                    updateRecommendations(statsList, true, selectedPackageForDonut)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    data class DisplayUsageItem(
        val id: String,
        val title: String,
        val durationMillis: Long,
        val isWebsite: Boolean = false,
        val originalStat: Stat? = null
    )

    inner class AppUsageViewHolder(private val binding: AppUsageItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DisplayUsageItem, packageManager: PackageManager) {
            if (!item.isWebsite && item.originalStat != null) {
                val stats = item.originalStat
                binding.root.setOnClickListener {
                    activity?.supportFragmentManager?.beginTransaction()
                        ?.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        ?.replace(R.id.fragment_holder, AppUsageBreakdown(stats))
                        ?.addToBackStack(null)
                        ?.commit()
                }
                binding.root.setOnLongClickListener {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Add to ignored packages?")
                        .setMessage("This action will cause the tracker to not display any stats from this app.")
                        .setCancelable(true)
                        .setPositiveButton("Okay") { _, _ ->
                            val savedPreferencesLoader = SavedPreferencesLoader(requireContext())
                            val ignoredAppsSP =
                                savedPreferencesLoader.loadIgnoredAppUsageTracker().toMutableSet()
                            ignoredAppsSP.add(stats.packageName)
                            ignoredPackages.addAll(ignoredAppsSP)
                            savedPreferencesLoader.saveIgnoredAppUsageTracker(ignoredAppsSP)

                            lifecycleScope.launch(Dispatchers.IO) {
                                val localDate = Instant.ofEpochMilli(selectedDate)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()

                                setUsageStats(localDate)
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }

                // Load app icon and label
                val appInfo = try {
                    packageManager.getApplicationInfo(stats.packageName, 0)
                } catch (e: Exception) {
                    null
                }
                binding.appIcon.setImageDrawable(appInfo?.loadIcon(packageManager))
                binding.appName.text = item.title
                binding.appUsage.text = TimeTools.formatTime(item.durationMillis)
            } else {
                // Website domain item
                binding.appIcon.setImageResource(R.drawable.baseline_language_24)
                binding.appName.text = item.title
                binding.appUsage.text = TimeTools.formatTime(item.durationMillis)

                binding.root.setOnClickListener {
                    showWebsiteControlDialog(item.title, item.durationMillis)
                }

                binding.root.setOnLongClickListener {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Ignore Website Domain?")
                        .setMessage("Hide statistics for ${item.title} from web browsing tracking.")
                        .setPositiveButton("Ignore Domain") { _, _ ->
                            val ignored = savedPreferencesLoader.loadIgnoredWebDomains().toMutableSet()
                            ignored.add(item.title)
                            savedPreferencesLoader.saveIgnoredWebDomains(ignored)
                            updateUsageList(true)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
            }
        }
    }

    inner class AppUsageAdapter(
        private var items: List<DisplayUsageItem>
    ) : RecyclerView.Adapter<AppUsageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppUsageViewHolder {
            val binding = AppUsageItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return AppUsageViewHolder(binding)
        }

        override fun onBindViewHolder(holder: AppUsageViewHolder, position: Int) {
            holder.bind(items[position], holder.itemView.context.packageManager)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newItems: List<DisplayUsageItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageStatsPermission(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    data class UsageSession(
        val startTime: ZonedDateTime,
        val durationMillis: Long
    )

    class Stat(
        val packageName: String,
        val totalTime: Long,
        val sessions: List<UsageSession>
    ) {
        val startTimes: List<ZonedDateTime>
            get() = sessions.map { it.startTime }
    }

}
