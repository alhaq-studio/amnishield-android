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
                <br/>
                <b>═══════════════════════════</b><br/>
                <b>OPEN SOURCE &amp; SOURCE CODE</b><br/>
                <b>═══════════════════════════</b><br/><br/>
                
                <b>Q: Is AmniShield open source?</b><br/>
                A: Yes! AmniShield is 100% open-source under the Al-Haq Initiative. You can inspect the source code, contribute, and report issues.<br/><br/>
                
                <b>Q: Where can I find the source code?</b><br/>
                A: Our repository is on GitHub:<br/>
                <a href="${Constants.GITHUB_REPO_URL}"><b>github.com/alhaq-studio/amnishield-android</b></a><br/>
                ⭐ Please consider <a href="${Constants.GITHUB_REPO_URL}"><b>starring the repository</b></a> to support our work!<br/><br/>
                
                <b>Q: Where can I see other Al-Haq projects?</b><br/>
                A: Visit <a href="${Constants.ALHAQ_STUDIO_URL}"><b>alhaq.uk</b></a> and <a href="${Constants.ALHAQ_INITIATIVE_URL}"><b>alhaq-initiative.org</b></a> for our full suite of privacy and wellbeing tools.<br/><br/>
                
                <b>═══════════════════════════</b><br/>
                <b>SUPPORT &amp; DONATIONS</b><br/>
                <b>═══════════════════════════</b><br/><br/>
                
                <b>Q: How can I support or donate to AmniShield?</b><br/>
                A: You can support ongoing development through:<br/>
                • <a href="${Constants.ALHAQ_INITIATIVE_DONATE_URL}"><b>Al-Haq Central Funding Hub</b></a><br/>
                • <a href="${Constants.GITHUB_SPONSORS_INITIATIVE_URL}"><b>GitHub Sponsors (Initiative)</b></a><br/>
                • <a href="${Constants.GITHUB_SPONSORS_PERSONAL_URL}"><b>GitHub Sponsors (Developer)</b></a><br/>
                • <a href="${Constants.KOFI_URL}"><b>Ko-fi</b></a><br/>
                • <a href="${Constants.BUY_ME_A_COFFEE_URL}"><b>Buy Me a Coffee</b></a><br/>
                • <a href="${Constants.PATREON_URL}"><b>Patreon</b></a><br/><br/>
                
                <b>═══════════════════════════</b><br/>
                <b>TROUBLESHOOTING</b><br/>
                <b>═══════════════════════════</b><br/><br/>
                
                <b>Q: Features not working?</b><br/>
                A: 1) Enable Accessibility Service in Settings → Accessibility → AmniShield<br/>
                   2) Enable Device Admin in Settings → Security → Device Admin Apps<br/>
                   3) Restart the app<br/><br/>
                
                <b>Q: Settings screen keeps closing?</b><br/>
                A: This is App Protection working. Enter your PIN/password to access Settings.<br/><br/>
                
                <b>Q: Battery drain?</b><br/>
                A: AmniShield uses minimal battery. Check Settings → Battery → Background restriction is OFF for AmniShield.<br/><br/>
                
                <b>Q: How to backup my settings?</b><br/>
                A: Settings → General → Backup &amp; Restore to save all your configurations.<br/><br/>
                
                <b>═══════════════════════════</b><br/>
                <b>CONTACT &amp; COMMUNITY</b><br/>
                <b>═══════════════════════════</b><br/><br/>
                
                <b>Q: How do I contact support or join the community?</b><br/>
                A: Email <a href="mailto:support@alhaq.uk"><b>support@alhaq.uk</b></a>, or join our community on <a href="${Constants.TELEGRAM_URL}"><b>Telegram</b></a> and <a href="${Constants.DISCORD_URL}"><b>Discord</b></a>.<br/>
            """.trimIndent()

            val messageView = android.widget.TextView(ctx).apply {
                text = androidx.core.text.HtmlCompat.fromHtml(faqs, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                setPadding(64, 32, 64, 24)
                textSize = 14f
                setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE))
                setLinkTextColor(com.google.android.material.color.MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, android.graphics.Color.parseColor("#7C4DFF")))
            }

            val scrollContainer = androidx.core.widget.NestedScrollView(ctx).apply {
                addView(messageView)
            }

            MaterialAlertDialogBuilder(ctx)
                .setTitle("Help & FAQ")
                .setView(scrollContainer)
                .setPositiveButton("Support Hub") { _, _ ->
                    (activity as? MainActivity)?.showSupportOptionsDialog()
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
                <b>Version: $versionName</b><br/><br/>
                
                AmniShield is a privacy-first digital wellbeing app that helps users maintain a healthy, balanced digital lifestyle through practical protection, smart blocking, and focus habits.<br/><br/>
                
                <b>Free Features (100% Free for Everyone):</b><br/>
                • App Blocker with schedules &amp; category rules<br/>
                • Focus Mode with timer &amp; session tracking<br/>
                • Reels &amp; Shorts Blocker (YouTube Shorts, Instagram Reels, TikTok)<br/>
                • Website &amp; Keyword Blocker with custom packs<br/>
                • App Launch Limits &amp; Usage Statistics<br/>
                • Smart Notifications, Reminders &amp; Daily Reports<br/><br/>
                
                <b>⭐ Premium Security Features:</b><br/>
                • Anti-Uninstall Protection (Device Admin protection)<br/>
                • 4-Digit Security PIN &amp; App Lock (Master PIN lock for settings)<br/>
                • Bypass PIN Lock (Require PIN confirmation to edit active blocks)<br/><br/>
                
                <b>Privacy First:</b><br/>
                • 100% On-Device Processing<br/>
                • Zero Data Collection &amp; Zero Telemetry<br/>
                • No Internet required for core features<br/><br/>
                
                🌐 <b>Website:</b> <a href="${Constants.AMNISHIELD_WEBSITE_URL}">amnishield.com</a><br/>
                📂 <b>Source Code:</b> <a href="${Constants.GITHUB_REPO_URL}">github.com/alhaq-studio/amnishield-android</a><br/>
                ⭐ <a href="${Constants.GITHUB_REPO_URL}">Star us on GitHub to support our work!</a><br/><br/>
                
                💬 <b>Community:</b><br/>
                • <a href="${Constants.TELEGRAM_URL}">Telegram: t.me/amnishield</a><br/>
                • <a href="${Constants.DISCORD_URL}">Discord: discord.gg/zXz7pGVJY</a><br/><br/>
                
                💖 <b>Support Development:</b><br/>
                • <a href="${Constants.ALHAQ_INITIATIVE_DONATE_URL}">Al-Haq Central Funding Hub</a><br/>
                • <a href="${Constants.GITHUB_SPONSORS_INITIATIVE_URL}">GitHub Sponsors (Initiative)</a><br/>
                • <a href="${Constants.GITHUB_SPONSORS_PERSONAL_URL}">GitHub Sponsors (Developer)</a><br/>
                • <a href="${Constants.KOFI_URL}">Ko-fi</a> • <a href="${Constants.BUY_ME_A_COFFEE_URL}">Buy Me a Coffee</a> • <a href="${Constants.PATREON_URL}">Patreon</a><br/><br/>
                
                Developer: <a href="${Constants.ALHAQ_STUDIO_URL}">Al-Haq Studio</a><br/>
                Free Access Program: <a href="${Constants.ALHAQ_INITIATIVE_URL}">Al-Haq Initiative</a><br/>
                <b>100% Open Source • No Ads • No Tracking • Privacy First</b>
            """.trimIndent()

            val messageView = android.widget.TextView(ctx).apply {
                text = androidx.core.text.HtmlCompat.fromHtml(message, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                setPadding(64, 32, 64, 24)
                textSize = 14f
                setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.WHITE))
                setLinkTextColor(com.google.android.material.color.MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, android.graphics.Color.parseColor("#7C4DFF")))
            }

            val scrollContainer = androidx.core.widget.NestedScrollView(ctx).apply {
                addView(messageView)
            }

            MaterialAlertDialogBuilder(ctx)
                .setTitle(getString(R.string.about_amnishield))
                .setView(scrollContainer)
                .setPositiveButton("Support Hub") { _, _ ->
                    (activity as? MainActivity)?.showSupportOptionsDialog()
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
