package com.alhaq.amnishield.ui.fragments

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityOptionsCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.R
import com.alhaq.amnishield.ui.activity.MainActivity
import com.alhaq.amnishield.ui.activity.FragmentActivity
import com.alhaq.amnishield.ui.activity.RemindersActivity
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.premium.PremiumManager
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.alhaq.amnishield.utils.ZipUtils
import com.alhaq.amnishield.utils.GoogleSignInHelper
import com.alhaq.amnishield.ui.screens.SettingsScreen
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.ui.viewmodel.AmniShieldViewModel
import java.util.Locale

class SettingsFragment : Fragment() {

    private lateinit var viewModel: AmniShieldViewModel
    private val premiumManager by lazy { PremiumManager.getInstance(requireContext().applicationContext) }
    private val savedPreferencesLoader by lazy { SavedPreferencesLoader(requireContext().applicationContext) }
    private lateinit var googleSignInHelper: GoogleSignInHelper

    // Backup launcher
    private val backupDirLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
                
                // Create backup file in selected directory
                val fileName = ZipUtils.createZipFileName()
                val documentUri = requireContext().contentResolver.run {
                    val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                        uri,
                        android.provider.DocumentsContract.getTreeDocumentId(uri)!!
                    )
                    android.provider.DocumentsContract.createDocument(
                        this,
                        docUri,
                        "application/zip",
                        fileName
                    )
                }
                
                documentUri?.let { zipUri ->
                    ZipUtils.zipSharedPreferencesToUri(requireContext(), zipUri)
                    Toast.makeText(requireContext(), "Backup saved: $fileName", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Restore launcher
    private val restoreFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            try {
                ZipUtils.unzipSharedPreferencesFromUri(requireContext(), uri)
                Toast.makeText(requireContext(), "Restore complete! Restart app to apply changes.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AmniShieldViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val activeTheme = com.alhaq.amnishield.utils.ThemeUtils.resolveAppTheme(requireContext())
                viewModel.updateTheme(activeTheme)
                val state by viewModel.state.collectAsState()
                AmniShieldTheme(appTheme = activeTheme) {
                    SettingsScreen(
                        state = state,
                        viewModel = viewModel,
                        onNavigateToProfile = {
                            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                                putExtra("fragment", "profile")
                            }
                            val options = ActivityOptionsCompat.makeCustomAnimation(
                                requireContext(),
                                R.anim.fade_in,
                                R.anim.fade_out
                            )
                            startActivity(intent, options.toBundle())
                        },
                        onBackupRestore = { showBackupRestoreDialog() },
                        onReminders = {
                            val intent = Intent(requireContext(), RemindersActivity::class.java)
                            val options = ActivityOptionsCompat.makeCustomAnimation(
                                requireContext(),
                                R.anim.fade_in,
                                R.anim.fade_out
                            )
                            startActivity(intent, options.toBundle())
                        },
                        onShareCrashLogs = { shareCrashLogs() },
                        onDiagnostics = {
                            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                                putExtra("feature_type", "diagnostics")
                            }
                            val options = ActivityOptionsCompat.makeCustomAnimation(
                                requireContext(),
                                R.anim.fade_in,
                                R.anim.fade_out
                            )
                            startActivity(intent, options.toBundle())
                        },
                        onHelpFAQ = { showFAQDialog() },
                        onAbout = { showAboutDialog() },
                        onLanguage = { showLanguageDialog() },
                        onSignOut = { showSignOutDialog() },
                        onToggleWebFilter = { enabled ->
                            savedPreferencesLoader.setWebsiteBlockerEnabled(enabled)
                            sendRefreshRequest(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
                            viewModel.loadState(viewModel.state.value.copy(isWebFilterEnabled = enabled))
                        },
                        onToggleUsageLimit = { enabled ->
                            savedPreferencesLoader.setUsageTrackerFeatureEnabled(enabled)
                            savedPreferencesLoader.setAppUsageTrackingEnabled(enabled)
                            viewModel.loadState(viewModel.state.value.copy(
                                isUsageLimitEnabled = enabled,
                                isAppUsageTrackingEnabled = enabled
                            ))
                        },
                        onToggleAppUsageTracking = { enabled ->
                            savedPreferencesLoader.setAppUsageTrackingEnabled(enabled)
                            savedPreferencesLoader.setUsageTrackerFeatureEnabled(enabled)
                            viewModel.loadState(viewModel.state.value.copy(
                                isAppUsageTrackingEnabled = enabled,
                                isUsageLimitEnabled = enabled
                            ))
                        },
                        onToggleWebsiteUsageTracking = { enabled ->
                            savedPreferencesLoader.setWebsiteUsageTrackingEnabled(enabled)
                            viewModel.loadState(viewModel.state.value.copy(isWebsiteUsageTrackingEnabled = enabled))
                        },
                        onToggleReelsTracking = { enabled ->
                            savedPreferencesLoader.setReelsTrackingEnabled(enabled)
                            viewModel.loadState(viewModel.state.value.copy(isReelsTrackingEnabled = enabled))
                        },
                        onUpdatePinResetCooldown = { mins ->
                            savedPreferencesLoader.setPinResetCooldownMinutes(mins)
                            viewModel.updatePinResetCooldown(mins)
                        },
                        onUpdateEmergencyCooldown = { mins ->
                            savedPreferencesLoader.setEmergencyAccessCooldownMinutes(mins)
                            viewModel.updateEmergencyAccessCooldown(mins)
                        },
                        onBack = {
                            if (!parentFragmentManager.popBackStackImmediate()) {
                                (activity as? MainActivity)?.let { main ->
                                    main.selectTab(main.getSelectedTabId())
                                } ?: requireActivity().finish()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        googleSignInHelper = GoogleSignInHelper(requireContext())
        loadSettingsState()
    }

    override fun onResume() {
        super.onResume()
        loadSettingsState()
    }

    private fun loadSettingsState() {
        val webFilterEnabled = savedPreferencesLoader.isWebsiteBlockerEnabled()
        val usageTrackerEnabled = savedPreferencesLoader.isUsageTrackerFeatureEnabled()
        val appUsageTrackingEnabled = savedPreferencesLoader.isAppUsageTrackingEnabled()
        val websiteUsageTrackingEnabled = savedPreferencesLoader.isWebsiteUsageTrackingEnabled()
        val reelsTrackingEnabled = savedPreferencesLoader.isReelsTrackingEnabled()
        val account = googleSignInHelper.getLastSignedInAccount()
        val name = account?.displayName ?: "Alhaq DST"
        val email = account?.email ?: "alhaq.dst@gmail.com"
        val pinResetCooldown = savedPreferencesLoader.getPinResetCooldownMinutes()
        val emergencyCooldown = savedPreferencesLoader.getEmergencyAccessCooldownMinutes()
        
        val isAdvanced = true
        
        viewModel.loadState(
            viewModel.state.value.copy(
                isWebFilterEnabled = webFilterEnabled,
                isUsageLimitEnabled = usageTrackerEnabled,
                isAppUsageTrackingEnabled = appUsageTrackingEnabled,
                isWebsiteUsageTrackingEnabled = websiteUsageTrackingEnabled,
                isReelsTrackingEnabled = reelsTrackingEnabled,
                pinResetCooldownMinutes = pinResetCooldown,
                emergencyAccessCooldownMinutes = emergencyCooldown,
                userName = name,
                userEmail = email,
                isAdvancedMode = isAdvanced
            )
        )
    }

    private fun sendRefreshRequest(action: String) {
        val ctx = requireContext()
        ctx.sendBroadcast(Intent(action).setPackage(ctx.packageName))
    }

    private fun showBackupRestoreDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Backup & Restore")
            .setMessage("Choose an action:")
            .setPositiveButton("Backup") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                backupDirLauncher.launch(intent)
            }
            .setNegativeButton("Restore") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                }
                restoreFileLauncher.launch(intent)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun shareCrashLogs() {
        val ctx = requireContext()
        try {
            val crashLogger = com.alhaq.amnishield.CrashLogger.getInstance(ctx)
            val exportFile = crashLogger.getExportLogFile()
            val uri = androidx.core.content.FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.provider",
                exportFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "AmniShield Diagnostic Logs")
                putExtra(Intent.EXTRA_TEXT, "Attached are the AmniShield diagnostic system & crash logs.")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("Diagnostic Logs", uri)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Diagnostic Logs"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Failed to share logs: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFAQDialog() {
        context?.let { ctx ->
            val faqs = """
                FREQUENTLY ASKED QUESTIONS
                
                ═══════════════════════════
                GETTING STARTED
                ═══════════════════════════
                
                Q: Do I need to create an account?
                A: No. AmniShield works completely offline. Google Sign-In is optional and only used for premium purchases.
                
                Q: Which permissions does AmniShield need?
                A: AmniShield requires Accessibility Service permission to detect and block apps/content. All processing happens on your device - no data is collected.
                
                Q: Is my data safe?
                A: Yes! AmniShield doesn't collect, store, or transmit any of your data. All processing happens locally on your device.
                
                ═══════════════════════════
                CORE FEATURES (100% FREE)
                ═══════════════════════════
                
                Q: What is App Blocker?
                A: App Blocker prevents you from opening selected apps. You can block apps individually or auto-block entire categories (gaming, social media, etc.).
                
                Q: What is Keyword Blocker?
                A: Keyword Blocker detects and blocks specified keywords across apps. It works in search fields by default, or can scan all text when enabled.
                
                Q: What is Focus Mode?
                A: Focus Mode temporarily blocks selected apps for a set duration, helping you stay productive. Sessions are tracked for insights.
                
                Q: What is Reel Blocker?
                A: Limits endless scrolling on Instagram Reels, YouTube Shorts, and TikTok. Set maximum views per day to break doomscrolling habits.
                
                ═══════════════════════════
                PREMIUM SECURITY FEATURES
                ═══════════════════════════
                
                Q: What is Anti-Uninstall Protection?
                A: Anti-uninstall prevents removing AmniShield protection using Device Administrator permissions. Choose password mode or timed cooldown mode.
                
                Q: What is 4-Digit Security PIN?
                A: Lock AmniShield settings and block configurations behind a master PIN with enforceable reset cooldowns.
                
                ═══════════════════════════
                OPEN SOURCE & SOURCE CODE
                ═══════════════════════════
                
                Q: Is AmniShield open source?
                A: Yes! AmniShield is 100% open-source under the Al-Haq Initiative. You can inspect the source code, contribute, and report issues.
                
                Q: Where can I find the source code?
                A: Our repository is on GitHub:
                github.com/alhaq-studio/amnishield-android
                ⭐ Please consider starring the repository to support our work!
                
                Q: Where can I see other Al-Haq projects?
                A: Visit alhaq.uk and alhaq-initiative.org for our full suite of privacy and wellbeing tools.
                
                ═══════════════════════════
                SUPPORT & DONATIONS
                ═══════════════════════════
                
                Q: How can I support or donate to AmniShield?
                A: You can support ongoing development through:
                • Al-Haq Initiative: alhaq-initiative.org
                • GitHub Sponsors (Initiative): github.com/sponsors/alhaq-initiative
                • GitHub Sponsors (Developer): github.com/sponsors/Afrasyaab-GH
                • Ko-fi: ko-fi.com/alhaq
                • Buy Me a Coffee: buymeacoffee.com/alhaq
                • Patreon: patreon.com/alhaq
                
                ═══════════════════════════
                TROUBLESHOOTING
                ═══════════════════════════
                
                Q: Features not working?
                A: 1) Enable Accessibility Service in Settings → Accessibility → AmniShield
                   2) Enable Device Admin in Settings → Security → Device Admin Apps
                   3) Restart the app
                
                Q: Settings screen keeps closing?
                A: This is App Protection working. Enter your PIN/password to access Settings.
                
                Q: Battery drain?
                A: AmniShield uses minimal battery. Check Settings → Battery → Background restriction is OFF for AmniShield.
                
                Q: How to backup my settings?
                A: Settings → General → Backup & Restore to save all your configurations.
                
                ═══════════════════════════
                CONTACT & COMMUNITY
                ═══════════════════════════
                
                Q: How do I contact support or join the community?
                A: Email support@alhaq.uk, or join our community on Telegram (t.me/amnishield) and Discord (discord.gg/zXz7pGVJY).
                
                ═══════════════════════════
            """.trimIndent()

            MaterialAlertDialogBuilder(ctx)
                .setTitle("Help & FAQ")
                .setMessage(faqs)
                .setPositiveButton("Email Support") { _, _ ->
                    openUrl("mailto:support@alhaq.uk")
                }
                .setNeutralButton("GitHub") { _, _ ->
                    openUrl(Constants.GITHUB_REPO_URL)
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun showAboutDialog() {
        context?.let { ctx ->
            val versionName = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            } catch (e: Exception) {
                getString(R.string.app_version)
            }

            val message = """
                Version: $versionName
                
                AmniShield is a privacy-first digital wellbeing app that helps users maintain a healthy, balanced digital lifestyle through practical protection, smart blocking, and focus habits.
                
                Free Features (100% Free for Everyone):
                • App Blocker with schedules & category rules
                • Focus Mode with timer & session tracking
                • Reels & Shorts Blocker (YouTube Shorts, Instagram Reels, TikTok)
                • Website & Keyword Blocker with custom packs
                • App Launch Limits & Usage Statistics
                • Smart Notifications, Reminders & Daily Reports
                
                ⭐ Premium Security Features:
                • Anti-Uninstall Protection (Device Admin protection)
                • 4-Digit Security PIN & App Lock (Master PIN lock for settings)
                • Bypass PIN Lock (Require PIN confirmation to edit active blocks)
                
                Privacy First:
                • 100% On-Device Processing
                • Zero Data Collection & Zero Telemetry
                • No Internet required for core features
                
                🌐 Website: amnishield.com
                📂 Source Code: github.com/alhaq-studio/amnishield-android
                ⭐ Star us on GitHub to support our work!
                
                💬 Community:
                • Telegram: t.me/amnishield
                • Discord: discord.gg/zXz7pGVJY
                
                💖 Support Development:
                • Al-Haq Initiative: alhaq-initiative.org
                • GitHub Sponsors (Initiative): github.com/sponsors/alhaq-initiative
                • GitHub Sponsors (Developer): github.com/sponsors/Afrasyaab-GH
                • Ko-fi: ko-fi.com/alhaq
                • Buy Me a Coffee: buymeacoffee.com/alhaq
                • Patreon: patreon.com/alhaq
                
                Developer: Al-Haq Studio (Al-Haq Digital Services & Solutions)
                Free Access Program: Provided by Al-Haq Initiative
                100% Open Source • No Ads • No Tracking • Privacy First
            """.trimIndent()

            MaterialAlertDialogBuilder(ctx)
                .setTitle(getString(R.string.about_amnishield))
                .setMessage(message)
                .setPositiveButton("Website") { _, _ ->
                    openUrl(Constants.AMNISHIELD_WEBSITE_URL)
                }
                .setNeutralButton("GitHub") { _, _ ->
                    openUrl(Constants.GITHUB_REPO_URL)
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun showPremiumUpsell() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.premium_required_title)
            .setMessage(getString(R.string.premium_required_message))
            .setPositiveButton(R.string.premium_view_plans) { _, _ ->
                openPremiumScreen()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openPremiumScreen() {
        val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
            putExtra("feature_type", "premium_features")
        }
        startActivity(intent)
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            val options = ActivityOptionsCompat.makeCustomAnimation(
                requireContext(),
                R.anim.fade_in,
                R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No application found to open the link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSignOutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.sign_out))
            .setMessage(getString(R.string.sign_out_confirmation))
            .setPositiveButton(getString(R.string.sign_out)) { _, _ ->
                googleSignInHelper.signOut {
                    Toast.makeText(requireContext(), getString(R.string.signed_out_successfully), Toast.LENGTH_SHORT).show()
                    loadSettingsState()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf(
            getString(R.string.language_system) to "",
            getString(R.string.language_english) to "en",
            getString(R.string.language_arabic) to "ar",
            getString(R.string.language_urdu) to "ur",
            getString(R.string.language_pashto) to "ps",
            getString(R.string.language_persian) to "fa",
            getString(R.string.language_spanish) to "es",
            getString(R.string.language_french) to "fr",
            getString(R.string.language_hindi) to "hi",
            getString(R.string.language_russian) to "ru",
            getString(R.string.language_turkish) to "tr"
        )

        val languageNames = languages.map { it.first }.toTypedArray()
        val currentLocale = AppCompatDelegate.getApplicationLocales()[0]?.language ?: ""
        val currentIndex = languages.indexOfFirst { it.second == currentLocale }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.choose_language)
            .setSingleChoiceItems(languageNames, currentIndex) { dialog, which ->
                val selectedLanguage = languages[which].second
                setAppLanguage(selectedLanguage)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setAppLanguage(languageCode: String) {
        val localeList = if (languageCode.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
        
        AppCompatDelegate.setApplicationLocales(localeList)
        loadSettingsState()
        
        Toast.makeText(requireContext(), R.string.restart_required, Toast.LENGTH_LONG).show()
    }
}
