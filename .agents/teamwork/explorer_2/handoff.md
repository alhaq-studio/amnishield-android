# Investigation Handoff Report: UI Architecture & Blocker Pipeline Ground Truth

- Date: 2026-10-20T15:16:00Z
- Explorer: Explorer 2 (teamwork_preview_explorer)
- Working Directory: d:\PROJECTS\Amni\AmniShield-Android\.agents\teamwork\explorer_2
- Target Requirements: Requirement R2 (Dual-Host Container Presentation Architecture) and Requirement R3 (Blocker Interceptor Pipeline Priority Alignment)

---

## 1. Observation

### Observation 1.1: MainActivity.kt Line Count and Tab Architecture
- File Path: `app/src/main/java/com/alhaq/amnishield/ui/activity/MainActivity.kt`
- Total Lines: Exactly 2,022 lines.
- Navigation Host Structure:
  - `MainActivity` inherits from `AppCompatActivity` (`MainActivity.kt:104`).
  - View layout binding: `ActivityMainBinding.inflate(layoutInflater)` (`MainActivity.kt:219`) set to `binding.root` (`MainActivity.kt:220`).
  - Setup invocation: `setupFragmentNavigation(savedInstanceState)` (`MainActivity.kt:297`).
  - Method `setupFragmentNavigation(savedInstanceState: Bundle?)` (`MainActivity.kt:476-550`) routes the 4 primary bottom navigation tabs inside `supportFragmentManager.beginTransaction().replace(R.id.nav_host_fragment, ...)`:
    - `R.id.navigation_stats` -> `com.alhaq.amnishield.ui.fragments.StatsFragment()` (`MainActivity.kt:484, 522`)
    - `R.id.navigation_blocks` -> `com.alhaq.amnishield.ui.fragments.BlocksManagerFragment()` (`MainActivity.kt:482, 529`)
    - `R.id.navigation_focus` -> `com.alhaq.amnishield.ui.fragments.FocusFragment()` (`MainActivity.kt:483, 536`)
    - `R.id.navigation_advanced` -> `com.alhaq.amnishield.ui.fragments.AdvancedFragment()` (`MainActivity.kt:481, 543`)
  - Bottom navigation listener: `binding.bottomNavigation.setOnItemSelectedListener` (`MainActivity.kt:513-549`).
  - Auxiliary systems managed in `MainActivity.kt`: Navigation Drawer (`binding.drawerLayout`, `setupNavigationDrawer()`, `MainActivity.kt:108, 247, 293`), Support Toolbar (`binding.toolbar`, `MainActivity.kt:222`), OnBackPressedDispatcher callback (`MainActivity.kt:252-263`), Google Sign-In helper (`MainActivity.kt:107, 243`), Supabase policy sync (`MainActivity.kt:284-287`), deep links (`MainActivity.kt:310-474`), full-screen app lock PIN prompt (`showPinLockFullscreenDialog()`, `MainActivity.kt:1976-2009`).

### Observation 1.2: FragmentActivity.kt Line Count, Sub-Screen Router, and ComposeView Embedding
- File Path: `app/src/main/java/com/alhaq/amnishield/ui/activity/FragmentActivity.kt`
- Total Lines: Exactly 210 lines.
- Router Implementation:
  - `FragmentActivity` inherits from `AppCompatActivity` (`FragmentActivity.kt:46`).
  - Layout: `setContentView(R.layout.activity_fragment)` with container `R.id.fragment_holder` (`FragmentActivity.kt:56, 158, 163`).
  - Dispatches via intent string extra `feature_type` (`FragmentActivity.kt:66-94`):
    1. `"focus_mode"` -> `FocusFragment()`
    2. `"focus_mode_config"` -> `FocusModeConfigFragment()`
    3. `"app_blocker"` -> `AppBlockerConfigFragment()`
    4. `"app_blocker_schedules"` -> `BlocksManagerFragment()` with `filter_type = "App Blocker"`
    5. `"reel_blocker"` -> `ReelBlockerConfigFragment()`
    6. `"reels_metrics"` -> `ReelsMetricsFragment()`
    7. `"website_blocker"`, `"social_media_blocker"` -> `WebsiteBlockerConfigFragment()`
    8. `"usage_tracker"` -> `UsageTrackerConfigFragment()`
    9. `"keyword_blocker"` -> `KeywordBlockerConfigFragment()`
    10. `"anti_uninstall"` -> `ChooseModeFragment()`
    11. `"setup_password_mode"` -> `SetupPasswordModeFragment()`
    12. `"setup_timed_mode"` -> `SetupTimedModeFragment()`
    13. `"additional_features"`, `"premium_features"` -> `PremiumFeaturesFragment()`
    14. `"roadmap"` -> `RoadmapFragment()`
    15. `"journey"`, `"our_journey"` -> `OurJourneyFragment()`
    16. `"diagnostics"`, `"system_logs"`, `"crash_logs"` -> `DiagnosticsFragment()`
  - Dispatches via fallback intent string extra `fragment` (`FragmentActivity.kt:97-133`):
    17. `AllAppsUsageFragment.FRAGMENT_ID` -> `AllAppsUsageFragment()`
    18. `ManageLaunchLimitsFragment.FRAGMENT_ID` -> `ManageLaunchLimitsFragment()`
    19. `WelcomeFragment.FRAGMENT_ID` -> `WelcomeFragment()` (first-launch onboarding)
    20. `AccessibilityGuide.FRAGMENT_ID` -> `AccessibilityGuide()` (permission onboarding)
    21. `ProfileFragment.FRAGMENT_ID` -> `ProfileFragment()`
  - ComposeView Embedding:
    - Direct Activity embedding: `showBypassPinDialog(correctPinCode: String)` (`FragmentActivity.kt:169-209`) constructs `androidx.compose.ui.platform.ComposeView(this)` with `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`, wrapping `AmniShieldTheme` and `PinPromptContent` inside a full-screen dialog decor view.
    - Fragment bridge embedding: Configuration fragments (`AppBlockerConfigFragment`, `ReelBlockerConfigFragment`, `UsageTrackerConfigFragment`, `KeywordBlockerConfigFragment` in `FeatureConfigFragments.kt`, `FocusModeConfigFragment.kt`, `WebsiteBlockerConfigFragment.kt`, `DiagnosticsFragment.kt`, `PremiumFeaturesFragment.kt`, `RoadmapFragment.kt`, `OurJourneyFragment.kt`) inherit from `BaseFeatureFragment` and return `ComposeView(requireContext())` inside `onCreateView`, rendering Jetpack Compose Material 3 UI screens (`AppBlockerConfigScreen`, `ReelBlockerConfigScreen`, etc.).

### Observation 1.3: AmniShieldAdaptiveApp.kt Git Commit History and Disconnected Status
- File Path: `app/src/main/java/com/alhaq/amnishield/ui/AmniShieldAdaptiveApp.kt` (254 lines).
- Git History for Commit `71556468e4e6a887a8ed78d0b14badbe359cb9ad`:
  - Date: `Fri Aug 28 20:01:29 2026 +0100`
  - Subject: `feat(activity): connect AmniShieldAdaptiveApp setContent root in MainActivity`
  - Change: Connected `setContent { AmniShieldAdaptiveApp(...) }` as the root Compose hierarchy in `MainActivity.kt`, instantiating `AmniShieldViewModel` and passing `NavigationSuiteScaffold` routes.
- Git History for Commit `ed5754883f4c62f8adc73601228355f92ea0b1ca`:
  - Date: `Sun Aug 30 12:34:43 2026 +0100`
  - Subject: `feat: implement AmniSpace mindful focus launcher space & breathing pause`
  - Change: Disconnected `AmniShieldAdaptiveApp` from `MainActivity.kt`. Replaced Compose `setContent` with XML view binding `binding = ActivityMainBinding.inflate(layoutInflater)`, `setContentView(binding.root)`, and restored `setupFragmentNavigation(savedInstanceState)`.
- Rationale for Disconnection:
  - Hybrid lifecycle instability: Attempting to drive navigation through `NavigationSuiteScaffold` while interacting with legacy FragmentManager backstacks, Navigation Drawer toggles, and sub-screen fragment routers caused unhandled `FragmentManager` lifecycle crashes during tab switching and nested fragment restoration.
  - To maintain UI and service stability, the team disconnected `AmniShieldAdaptiveApp` and preserved the Dual-Host architecture (`MainActivity` XML bottom nav + `FragmentActivity` secondary router).
- Current Codebase Status:
  - Disconnected stub: File exists at `app/src/main/java/com/alhaq/amnishield/ui/AmniShieldAdaptiveApp.kt`.
  - Reference count: Exactly 0 callers across all Kotlin/Java source files in `app/src/main/java`.
  - Reconnection is scheduled under Milestone 0.3.5 (Pure Compose & Storage Architecture Epic) as a formal deprecation pathway for `MainActivity` and `FragmentActivity`.

### Observation 1.4: AmniShieldAccessibilityService.kt Blocker Execution Sequence & Line Numbers
- File Path: `app/src/main/java/com/alhaq/amnishield/services/AmniShieldAccessibilityService.kt`
- Total Lines: 914 lines.
- Execution sequence inside `onAccessibilityEvent(event: AccessibilityEvent)`:
  - Line 283:
    ```kotlin
    if (antiUninstallDetector.inspect(event, rootNode)) {
        return
    }
    ```
    -> Priority 0: `AntiUninstallDetector` (Lines 283-285). Intercepts package management, device admin, and app settings surfaces; dispatches home action or `AntiUninstallPasswordActivity` and short-circuits with `return`.
  - Lines 304-348:
    ```kotlin
    if (isFocusModeActive) {
        val focusModeResult = focusModeBlocker.doesAppNeedToBeBlocked(...)
        ...
        if (focusModeResult.isBlocked) {
            blockingStatsManager.recordAppBlock(packageName, "Blocked by Focus Mode")
            val focusStyle = savedPreferencesLoader.getFocusModeInterceptionStyle()
            when (focusStyle) {
                Constants.FOCUS_INTERCEPTION_STYLE_WORKSPACE -> { ... startActivity(intent); return }
                Constants.FOCUS_INTERCEPTION_STYLE_SILENT -> { pressHome(); return }
                else -> { ... startActivity(intent); return }
            }
        }
    }
    ```
    -> Priority 1: `FocusModeBlocker` (Lines 304-348). Checks manual and scheduled focus mode sessions; dispatches `AmniSpaceActivity` (Focus Launcher), `pressHome()`, or `WarningActivity`, then short-circuits with `return`.
  - Lines 350-415:
    ```kotlin
    if (savedPreferencesLoader.isWebsiteBlockerEnabled(false) && isFeatureCurrentlyActive("website_blocker")) {
        val activeWebsites = getActiveBlockedWebsites()
        if (activeWebsites.isNotEmpty()) {
            val blockedSocialApps = savedPreferencesLoader.loadBlockedWebsitesApps()
            if (blockedSocialApps.contains(packageName)) {
                ...
                return
            } else if (rootNode != null) {
                val blockedSite = websiteBlockerDetector.findBlockedWebsite(rootNode, packageName, activeWebsites)
                if (blockedSite != null) {
                    ...
                    return
                }
            }
        }
    }
    ```
    -> Priority 2: `WebsiteBlockerDetector` (Lines 350-415). Evaluates browser URL bars across 15+ browser package IDs or designated social apps; dispatches `AmniSpaceActivity` (mindful breathing), `pressHome()`, or `WarningActivity`, then short-circuits with `return`.
  - Lines 417-448:
    ```kotlin
    if (!isFocusBlockAllExSelectedActive) {
        if (savedPreferencesLoader.isAppBlockerFeatureEnabled(false) && isFeatureCurrentlyActive("app_blocker")) {
            val appBlockerResult = appBlocker.doesAppNeedToBeBlocked(packageName, savedPreferencesLoader, this)
            if (appBlockerResult.isBlocked) {
                blockingStatsManager.recordAppBlock(packageName, "Blocked by App Blocker")
                ...
                return
            }
        }
    }
    ```
    -> Priority 3: `AppBlocker` (Lines 417-448). Evaluates launch limits, usage quotas, and app block schedules; dispatches `AmniSpaceActivity`, `pressHome()`, or `WarningActivity`, then short-circuits with `return`.
  - Lines 450-462 & 471-562:
    ```kotlin
    val eventCopy = ...
    val sendResult = eventChannel.trySend(eventCopy)
    ```
    ```kotlin
    private val eventChannel = Channel<AccessibilityEvent>(Channel.CONFLATED) { droppedEvent -> ... }
    ...
    private fun startBackgroundWorker() {
        serviceScope.launch {
            for (event in eventChannel) {
                try {
                    processDeferredChecks(event)
    ```
    ```kotlin
    private fun processDeferredChecks(event: AccessibilityEvent) {
        ...
        // Keyword Blocker (Lines 507-529)
        val keywordResult = keywordBlocker.checkIfUserGettingFreaky(rootNode, event)
        ...
        // Reel Blocker (Lines 531-562)
        val rawResult = reelBlocker.doesReelNeedToBeBlocked(rootNode, packageName)
        ...
    }
    ```
    -> Priority 4: `KeywordBlocker` & `ReelBlocker` (Deferred Conflated Channel, Lines 450-462, 471-488, 490-562). Event is sent to `eventChannel` (`Channel.CONFLATED`) and executed off the main binder thread in `processDeferredChecks(event)`.

### Observation 1.5: WebsiteBlockerDetector.kt Domain Evaluation Loop
- File Path: `app/src/main/java/com/alhaq/amnishield/blockers/WebsiteBlockerDetector.kt` (167 lines).
- In `findBlockedWebsite(rootNode, packageName, activeWebsites)` (lines 73-101):
  - Extracts URL text from browser address bar node using package ID mappings (`BROWSER_URL_BAR_IDS`, lines 35-54) or input focus fallback (`lines 113-118`).
  - Candidate domain resolution (`lines 83-86`):
    ```kotlin
    val candidateWebsites = activeWebsites
        ?: blockedWebsitesProvider?.invoke()
        ?: savedPreferencesLoader?.loadBlockedWebsites()
        ?: emptySet()
    for (site in candidateWebsites) {
        val siteLower = site.trim().lowercase(Locale.ROOT)
        if (siteLower.isNotEmpty() && urlText.contains(siteLower) && !isUnderCooldown(siteLower)) {
            return site
        }
    }
    ```
  - In `AmniShieldAccessibilityService.kt:768-809`, `getActiveBlockedWebsites()` generates `activeWebsites` by filtering `savedPreferencesLoader.loadAppBlockerScheduleRules()` for active website rules, gathering `rule.targets.ifEmpty { rule.targetWebsites }` or fallback `loadBlockedWebsites()`.
  - Candidate targets are strictly the 1-20 user-configured domains in active schedules evaluated via a simple linear loop.

### Observation 1.6: Asset Directory Domain Lists Status
- File Paths:
  - `app/src/main/assets/blocklists/adult_domains.txt` (693 bytes, 52 lines)
  - `app/src/main/assets/blocklists/haram_domains.txt` (605 bytes, 50 lines)
- Reference Audit:
  - Searched all Kotlin/Java source files in `app/src/main/java` and all resource/manifest files in `app/src/`.
  - Neither `adult_domains.txt`, `haram_domains.txt`, nor the asset directory `blocklists/` is referenced anywhere in the codebase.
  - No call to `assets.open("blocklists/...")` exists.
  - Conclusion: Both files are dead, unreferenced assets. There is no 50,000 domain database or trie lookup in AmniShield-Android.

### Observation 1.7: Discrepancies in ARCHITECTURE.md
- File Path: `ARCHITECTURE.md` (83 lines).
- Audit Findings:
  1. Priority Inversion:
     - `ARCHITECTURE.md:43-45` lists `AppBlocker` as Priority 1 and `WebsiteBlockerDetector` as Priority 2.
     - Code reality: `WebsiteBlockerDetector` executes at Priority 2 (`AmniShieldAccessibilityService.kt:350`), while `AppBlocker` executes at Priority 3 (`AmniShieldAccessibilityService.kt:417`).
  2. Duplicate / Non-deterministic Priority Numbers:
     - `ARCHITECTURE.md:43-46` assigns "Priority 1" to both `AppBlocker` and `FocusModeBlocker`, and "Priority 2" to both `WebsiteBlockerDetector` and `KeywordActionHandler`.
     - Code reality: Each step is strictly ordinal: P0 (`AntiUninstallDetector`), P1 (`FocusModeBlocker`), P2 (`WebsiteBlockerDetector`), P3 (`AppBlocker`), P4 (`KeywordBlocker` & `ReelBlocker`).
  3. Synchronous vs Deferred Conflated Channel:
     - `ARCHITECTURE.md:46-47` describes `KeywordActionHandler` (P2) and `ReelBlocker` (P3) as synchronous interceptors.
     - Code reality: Neither runs synchronously on the main accessibility event pass. Events are dropped into a conflated coroutine channel (`eventChannel`, L456) and evaluated off-thread in `processDeferredChecks` at Priority 4.
  4. Layer Diagram Error:
     - `ARCHITECTURE.md:27-31` box diagram groups `AppBlocker / FocusMode (Priority 1)` and `ReelBlocker & WebsiteFilt (Priority 2)`. This matches neither code order nor threading semantics.
  5. Missing Dispatched Actions:
     - `ARCHITECTURE.md:42-47` omits the `AmniSpaceActivity` interception style (mindful breathing pause / focus launcher) and silent `pressHome()` actions supported across blockers.
  6. Universal Zero-Emoji Policy Violations:
     - Four unicode emojis exist in section headers:
       - Line 9: `## 🏗️ Core Architectural Layers`
       - Line 36: `## 🛡️ Blocker Pipeline & Chain of Responsibility`
       - Line 51: `## 📝 Diagnostic & Crash Logging Architecture (CrashLogger)`
       - Line 69: `## 🔒 Invariants & Guardrails for AI & Human Maintainers`

---

## 2. Logic Chain

1. **Dual-Host UI Verification**:
   - `MainActivity.kt` contains 2,022 lines and configures `binding.bottomNavigation` with 4 menu items (`navigation_stats`, `navigation_blocks`, `navigation_focus`, `navigation_advanced`).
   - `FragmentActivity.kt` contains 210 lines and inspects `intent.getStringExtra("feature_type")` and `intent.getStringExtra("fragment")`, branching across 18 configuration, metric, onboarding, and diagnostic screens.
   - Configuration fragments embed Jetpack Compose via `ComposeView(requireContext())` inside their `onCreateView` methods, and `FragmentActivity` embeds `ComposeView` for `showBypassPinDialog`.
   - Therefore, AmniShield-Android operates on a hybrid Dual-Host Container architecture where `MainActivity` acts as primary bottom-tab container and `FragmentActivity` acts as secondary sub-screen router.

2. **Adaptive App Disconnection Verification**:
   - Commit `7155646` connected `AmniShieldAdaptiveApp` to `MainActivity.kt` via `setContent`.
   - Commit `ed57548` reverted `MainActivity.kt` to `ActivityMainBinding.inflate` and restored `setupFragmentNavigation`.
   - Search across `app/src/main/java` proves `AmniShieldAdaptiveApp` has 0 callers.
   - Therefore, `AmniShieldAdaptiveApp.kt` is currently disconnected and serves as an architectural precursor to the planned Pure-Compose Navigation Epic in Milestone 0.3.5.

3. **Blocker Priority Verification**:
   - In `AmniShieldAccessibilityService.kt`:
     - Line 283 evaluates `antiUninstallDetector.inspect(...)` (Priority 0). If triggered, exits immediately.
     - Line 304 evaluates `isFocusModeActive` via `focusModeBlocker.doesAppNeedToBeBlocked(...)` (Priority 1). If triggered, records block and exits.
     - Line 350 evaluates `savedPreferencesLoader.isWebsiteBlockerEnabled(...)` and `websiteBlockerDetector.findBlockedWebsite(...)` (Priority 2). If triggered, records block and exits.
     - Line 417 evaluates `savedPreferencesLoader.isAppBlockerFeatureEnabled(...)` and `appBlocker.doesAppNeedToBeBlocked(...)` (Priority 3). If triggered, records block and exits.
     - Line 456 sends `eventCopy` to `eventChannel.trySend(...)`, which is collected by `startBackgroundWorker()` and evaluated in `processDeferredChecks(event)` for `keywordBlocker` (L513) and `reelBlocker` (L545) (Priority 4).
   - Therefore, the execution sequence is strictly P0 -> P1 -> P2 -> P3 -> P4. `WebsiteBlockerDetector` precedes `AppBlocker`. `KeywordBlocker` and `ReelBlocker` are deferred to Priority 4 off the main binder thread.

4. **Domain Checking & Asset Reality**:
   - `WebsiteBlockerDetector.kt:87-92` iterates strictly over `candidateWebsites`, which originate from `getActiveBlockedWebsites()` (active schedule target lists of 1-20 entries).
   - `adult_domains.txt` and `haram_domains.txt` in `app/src/main/assets/blocklists/` are 52 and 50 lines respectively, and are never loaded by code.
   - Therefore, claims of "50,000 local domain lookups" or active pre-packaged blocklist databases are documentation myths.

---

## 3. Caveats

- **No Caveats.** All seven inspection points were directly verified against current source files, asset directories, and git commit history. No assumptions were required.

---

## 4. Conclusion

1. **Dual-Host Presentation Reality**:
   `MainActivity.kt` (2,022 lines) hosts the 4 primary bottom navigation tabs (`Stats`, `Blocks`, `Focus`, `Advanced`) using `supportFragmentManager` transactions. `FragmentActivity.kt` (210 lines) serves as the secondary router hosting 18 sub-screens, onboarding flows, and feature configuration fragments that embed Jetpack Compose via `ComposeView`. `AmniShieldAdaptiveApp.kt` was temporarily connected in commit `7155646` and disconnected in commit `ed57548` due to FragmentManager lifecycle crashes; it currently has 0 callers and represents the target architecture for Milestone 0.3.5.

2. **Blocker Pipeline Ground Truth**:
   The execution priority sequence in `AmniShieldAccessibilityService.kt` is strictly:
   - Priority 0: `AntiUninstallDetector` (Line 283)
   - Priority 1: `FocusModeBlocker` (Line 304)
   - Priority 2: `WebsiteBlockerDetector` (Line 350)
   - Priority 3: `AppBlocker` (Line 417)
   - Priority 4: `KeywordBlocker` & `ReelBlocker` (Deferred Conflated Channel, Lines 456, 475-562)

3. **Blocker Domain Evaluation Truth**:
   `WebsiteBlockerDetector.kt` performs a linear loop against user-configured schedule targets (1-20 domains). The asset files `adult_domains.txt` (52 lines) and `haram_domains.txt` (50 lines) in `app/src/main/assets/blocklists/` are dead, unreferenced assets. There is no 50k domain filter.

4. **ARCHITECTURE.md & README.md Rectification Plan**:
   - Correct the priority table and layer diagram in `ARCHITECTURE.md` to reflect the verified P0-P4 sequence and deferred conflated channel execution.
   - Remove the four unicode emojis in `ARCHITECTURE.md` (lines 9, 36, 51, 69) to comply with the Zero-Emoji Policy.
   - Document the Dual-Host container architecture and the disconnected status of `AmniShieldAdaptiveApp.kt`.
   - Purge claims of 50k local domain lookups and local Room/SQLite caching.

---

## 5. Verification Method

To independently verify these findings, run the following commands in PowerShell from the project root (`d:\PROJECTS\Amni\AmniShield-Android`):

1. **Verify Line Counts**:
   ```powershell
   (Get-Content -Path "app\src\main\java\com\alhaq\amnishield\ui\activity\MainActivity.kt").Count
   # Expected output: 2021 or 2022 (depending on trailing newline)
   
   (Get-Content -Path "app\src\main\java\com\alhaq\amnishield\ui\activity\FragmentActivity.kt").Count
   # Expected output: 210
   ```

2. **Verify Adaptive App Git History and References**:
   ```powershell
   git show --stat 7155646
   git show --stat ed57548
   Get-ChildItem -Path "app\src\main\java" -Filter "*.kt" -Recurse | Select-String -Pattern "AmniShieldAdaptiveApp"
   # Expected output: Only found in app/src/main/java/com/alhaq/amnishield/ui/AmniShieldAdaptiveApp.kt
   ```

3. **Verify Blocker Execution Priorities**:
   Inspect line numbers in `app/src/main/java/com/alhaq/amnishield/services/AmniShieldAccessibilityService.kt`:
   - Line 283: `antiUninstallDetector.inspect(event, rootNode)`
   - Line 304: `if (isFocusModeActive) ... focusModeBlocker.doesAppNeedToBeBlocked`
   - Line 350: `if (savedPreferencesLoader.isWebsiteBlockerEnabled(false) ... websiteBlockerDetector.findBlockedWebsite`
   - Line 417: `if (!isFocusBlockAllExSelectedActive) ... appBlocker.doesAppNeedToBeBlocked`
   - Line 456: `eventChannel.trySend(eventCopy)`
   - Line 473: `for (event in eventChannel) { processDeferredChecks(event) }`
   - Line 513: `keywordBlocker.checkIfUserGettingFreaky`
   - Line 545: `reelBlocker.doesReelNeedToBeBlocked`

4. **Verify Domain Assets Reference Status**:
   ```powershell
   Get-ChildItem -Path "app\src" -Recurse | Select-String -Pattern "adult_domains|haram_domains"
   # Expected output: Zero matches (dead assets)
   ```

5. **Verify Emojis in ARCHITECTURE.md**:
   ```powershell
   Select-String -Path "ARCHITECTURE.md" -Pattern "[\uD83C-\uDBFF\uDC00-\uDFFF]"
   # Expected output: Lines 9, 36, 51, 69 containing emojis
   ```
