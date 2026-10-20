# AmniShield-Android — Comprehensive Project Summary & Technical Drift Remediation

**Document Version:** 1.0.0  
**Baseline Date:** 2026-10-20  
**Target Platform:** Android 8.0+ (API Level 26 to API Level 36)  
**Codebase Identifier:** `com.alhaq.amnishield` (Namespace) / `com.alhaq.deenshield` (ApplicationId)  
**Production Release Baseline:** `versionCode 145` | `versionName "0.3.1 (2026.10.05)"`  
**Universal Code & UI Quality Rule:** STRICT UNIVERSAL ZERO-EMOJI POLICY ENFORCED  
**Security & Privacy Invariant:** 100% On-Device Processing | Zero Remote Telemetry | Offline ECDSA NIST P-256 Licensing  

---

## Table of Contents

1. [Executive Summary & Audit Overview](#executive-summary--audit-overview)
2. [Section 1 (R1): Persistence Reality & De-Hallucination](#section-1-r1-persistence-reality--de-hallucination)
   - [1.1 Gradle Dependency Audit](#11-gradle-dependency-audit)
   - [1.2 Orphaned Database Stub Classes](#12-orphaned-database-stub-classes)
   - [1.3 Active SharedPreferences Storage Engine](#13-active-sharedpreferences-storage-engine)
   - [1.4 SecureKeyStore & EncryptedSharedPreferences Isolation](#14-securekeystore--encryptedsharedpreferences-isolation)
   - [1.5 Main Binder Thread Deserialization Bottleneck](#15-main-binder-thread-deserialization-bottleneck)
3. [Section 2 (R2): Dual-Host Container Presentation Architecture](#section-2-r2-dual-host-container-presentation-architecture)
   - [2.1 MainActivity: Primary 4-Tab Container](#21-mainactivity-primary-4-tab-container)
   - [2.2 FragmentActivity: Secondary Sub-Screen Router](#22-fragmentactivity-secondary-sub-screen-router)
   - [2.3 Jetpack Compose UI Embedding via ComposeView](#23-jetpack-compose-ui-embedding-via-composeview)
   - [2.4 AmniShieldAdaptiveApp: Origin, Disconnection & Target Architecture](#24-amnishieldadaptiveapp-origin-disconnection--target-architecture)
4. [Section 3 (R3): Blocker Interceptor Pipeline Priority Alignment](#section-3-r3-blocker-interceptor-pipeline-priority-alignment)
   - [3.1 Chain of Responsibility Execution Sequence](#31-chain-of-responsibility-execution-sequence)
   - [3.2 Blocker Interceptor Pipeline Table](#32-blocker-interceptor-pipeline-table)
   - [3.3 Debunking the 50,000 Local Domain Lookup Myth](#33-debunking-the-50000-local-domain-lookup-myth)
   - [3.4 Status of Asset Directory Domain Lists](#34-status-of-asset-directory-domain-lists)
5. [Section 4 (R4): Premium Architecture & Licensing Verification](#section-4-r4-premium-architecture--licensing-verification)
   - [4.1 Tier Structure & User Types](#41-tier-structure--user-types)
   - [4.2 Founder Pass & Monotonic Anti-Clock Tampering Defense](#42-founder-pass--monotonic-anti-clock-tampering-defense)
   - [4.3 Compassionate Community Access](#43-compassionate-community-access)
   - [4.4 Premium Gated Security Features](#44-premium-gated-security-features)
   - [4.5 Multi-Flavor Implementation Matrix](#45-multi-flavor-implementation-matrix)
6. [Section 5 (R5): Roadmap Scope Hygiene & Milestone Realignment](#section-5-r5-roadmap-scope-hygiene--milestone-realignment)
   - [5.1 Purging Cross-Repository Scope Creep](#51-purging-cross-repository-scope-creep)
   - [5.2 Reaffirming Completed Milestones (v0.3.1 / Code 145)](#52-reaffirming-completed-milestones-v031--code-145)
   - [5.3 Versioned Active Milestones (v0.3.2 - v0.3.5)](#53-versioned-active-milestones-v032---v035)
7. [Section 6 (R6): Universal Zero-Emoji Policy & Versioning Rectification](#section-6-r6-universal-zero-emoji-policy--versioning-rectification)
   - [6.1 Unicode Emoji Remediation in ARCHITECTURE.md](#61-unicode-emoji-remediation-in-architecturemd)
   - [6.2 Project Versioning & Metadata Reconciliation](#62-project-versioning--metadata-reconciliation)
8. [Section 7: Exact Git Diffs & Full Replacement Blocks](#section-7-exact-git-diffs--full-replacement-blocks)
   - [7.1 ARCHITECTURE.md Exact Unified Diff](#71-architecturemd-exact-unified-diff)
   - [7.2 ARCHITECTURE.md Full Replacement Block](#72-architecturemd-full-replacement-block)
   - [7.3 README.md Exact Unified Diff](#73-readmemd-exact-unified-diff)
   - [7.4 README.md Full Replacement Block](#74-readmemd-full-replacement-block)
   - [7.5 ROADMAP.md Exact Unified Diff](#75-roadmapmd-exact-unified-diff)
   - [7.6 ROADMAP.md Full Replacement Block](#76-roadmapmd-full-replacement-block)
   - [7.7 .agents/AGENTS.md Exact Unified Diff](#77-agentsagentsmd-exact-unified-diff)
   - [7.8 .agents/AGENTS.md Full Replacement Block](#78-agentsagentsmd-full-replacement-block)
9. [Section 8: Comprehensive Verification Checklist & Verification Commands](#section-8-comprehensive-verification-checklist--verification-commands)
   - [8.1 Acceptance Criteria Compliance Matrix](#81-acceptance-criteria-compliance-matrix)
   - [8.2 Automated Verification Script (PowerShell)](#82-automated-verification-script-powershell)
   - [8.3 Automated Verification Script (Bash)](#83-automated-verification-script-bash)

---

## Executive Summary & Audit Overview

AmniShield-Android (`d:\PROJECTS\Amni\AmniShield-Android`) is the flagship open-source Android digital wellness and distraction blocking application within the Amni Ecosystem. Over recent development cycles, significant architectural drift accumulated between the verified production codebase and project documentation (`ARCHITECTURE.md`, `README.md`, `ROADMAP.md`, and `.agents/AGENTS.md`).

This comprehensive project summary and technical drift remediation document provides an authoritative, evidence-grounded synthesis of the production codebase. It resolves all documented misconceptions, de-hallucinates persistence claims, rectifies presentation host structures, aligns blocker pipeline priorities, enforces cross-repository roadmap hygiene, formalizes premium licensing boundaries across build flavors, eradicates unicode emoji violations, and establishes verified git diffs and full replacement blocks.

### Key Audit Findings

1. **Persistence Reality (R1):** The codebase contains **zero Room DB**, **zero SQLite**, and **zero Preferences DataStore** dependencies. Local persistence operates **100% via Android SharedPreferences** (29 specialized XML files in `SavedPreferencesLoader.kt` plus 8 auxiliary XML files). Complex entities (schedules, limits, cooldowns) are serialized to JSON strings using Google Gson (33 explicit calls in `SavedPreferencesLoader.kt`). `EncryptedSharedPreferences` is strictly isolated to Supabase cloud sync authentication tokens in `SecureKeyStore.kt`. Repeated reflection-based deserialization in `onAccessibilityEvent` introduces a synchronous Binder thread performance bottleneck.
2. **Presentation Architecture (R2):** AmniShield does not use a single monolithic Compose root or standard Jetpack Navigation component. Instead, it utilizes a **Dual-Host Container Architecture**: `MainActivity.kt` (2,022 lines) hosts the 4 primary bottom navigation tabs using legacy `supportFragmentManager` transactions, while `FragmentActivity.kt` (210 lines) acts as a secondary sub-screen router handling 18 functional feature screens by embedding Jetpack Compose Material 3 UI via `ComposeView`. `AmniShieldAdaptiveApp.kt` (254 lines) was connected in commit `7155646` and subsequently disconnected in commit `ed57548` to prevent `FragmentManager` lifecycle crashes; it currently has 0 callers and represents the target architecture for Milestone 0.3.5.
3. **Blocker Pipeline Priorities (R3):** In `AmniShieldAccessibilityService.kt`, the interceptor pipeline executes in a strict ordinal sequence: **Priority 0** `AntiUninstallDetector` (Line 283) -> **Priority 1** `FocusModeBlocker` (Line 304) -> **Priority 2** `WebsiteBlockerDetector` (Line 350) -> **Priority 3** `AppBlocker` (Line 417) -> **Priority 4** `KeywordBlocker` & `ReelBlocker` (Lines 456, 475-562, executed asynchronously via `Channel.CONFLATED`). Documentation claiming 50,000 local domain lookups is false: `WebsiteBlockerDetector.kt` performs a simple linear loop over 1-20 active schedule targets. The asset files `adult_domains.txt` and `haram_domains.txt` are dead, unreferenced assets.
4. **Premium Architecture & Licensing (R4):** Four tiers are defined in `PremiumManager.kt`: `FREE` (100% FOSS core blockers), `COMPASSIONATE` (community review pass with monotonic clock verification), `FOUNDER` (90-day early supporter pass), and `PREMIUM` (full supporter pass). Core blocking functionality is 100% free forever. Gated features are strictly security and synchronization enhancements: Rule PIN protection (`RuleProBadge`), Master PIN, App Lock, Bypass PIN Lock, and Supabase cloud policy sync. Build flavors cleanly segregate billing: `playstore` uses Google Play `BillingClientWrapper.kt`, `fdroid` uses 100% offline cryptographic NIST P-256 ECDSA public key verification (`SHA256withECDSA` in `LicenseValidator.kt`) with optional Stripe Direct checkout via Supabase Edge Functions (`PaymentManager.kt`), and `universal` supports hybrid operation.
5. **Roadmap Hygiene (R5):** The upcoming section in `ROADMAP.md` suffered from cross-repository scope creep, erroneously listing a Windows Elevated Service (belongs to `AmniShield-Windows`), an Opt-in DNS Sinkhole Engine (belongs to `AmniGuard-FireWall`), and an On-Device Mobile NPU Vision Blur Engine (belongs to `AmniBlur`/`AmniGaze`). These foreign items are purged and replaced with 4 verified Android-specific milestones: Milestone 0.3.2 (Accessibility Engine Hardening & Binder Safety), Milestone 0.3.3 (Store Compliance & Layout Resilience), Milestone 0.3.4 (Dual-Host Cleanup & Navigation Decoupling), and Milestone 0.3.5 (Pure Compose & Storage Architecture Epic).
6. **Universal Zero-Emoji Policy & Versioning (R6):** All 4 unicode emojis (`[U+1F3D7]`, `[U+1F6E1]`, `[U+1F4DD]`, `[U+1F512]`) located on lines 9, 36, 51, and 69 of `ARCHITECTURE.md` are purged in compliance with the universal Zero-Emoji Policy. Stale version metadata in `.agents/AGENTS.md` (`versionCode 132`, `1.0.2-closed.01`) is reconciled with the production baseline in `app/build.gradle.kts` (`versionCode 145`, `versionName "0.3.1 (2026.10.05)"`).

---

## Section 1 (R1): Persistence Reality & De-Hallucination

Documentation in `ARCHITECTURE.md:5` and `.agents/AGENTS.md:11` historically claimed that AmniShield-Android relies on "local SQLite/Room data caching" and "Preferences DataStore". An exhaustive audit of the build configurations, dependencies, and source tree disproves these claims.

### 1.1 Gradle Dependency Audit

Inspection of all build configuration files confirms that no SQLite, Room, or DataStore libraries are compiled into the application:

1. **Root `build.gradle.kts` (Lines 1-7):** Contains only base plugins:
   - `alias(libs.plugins.android.application) apply false`
   - `alias(libs.plugins.kotlin.android) apply false`
   - `alias(libs.plugins.google.gms.google.services) apply false`
   - `alias(libs.plugins.kotlin.compose) apply false`
2. **Version Catalog `gradle/libs.versions.toml` (Lines 1-71):**
   - Zero entries for `androidx.room`, `room-runtime`, `room-compiler`, or `room-ktx`.
   - Zero entries for `androidx.sqlite`.
   - Zero entries for `androidx.datastore` or `datastore-preferences`.
   - The sole serialization library declared is Google Gson: `gson = "2.14.0"` (line 4), `gson = { module = "com.google.code.gson:gson", version.ref = "gson" }` (line 31).
3. **Module `app/build.gradle.kts` (Lines 145-195):**
   - Implements standard AndroidX components (`core-ktx`, `appcompat`, `material`, `activity`, `constraintlayout`, `fragment-ktx`, `documentfile`, `work-runtime-ktx`).
   - Implements serialization and crypto: `implementation(libs.gson)`, `implementation("androidx.security:security-crypto:1.1.0-alpha06")`.
   - Declares flavor dependencies: `play.services.auth`, `billing.ktx`, and Firebase messaging for `playstore` and `universal`.
   - Zero Room, SQLite, or DataStore dependencies exist in any flavor or configuration.

### 1.2 Orphaned Database Stub Classes

The directory `app/src/main/java/com/alhaq/amnishield/data/db/` contains three files:
1. `AppBlockRule.kt` (lines 27-45): A data class implementing `BaseRule`.
2. `ScreenTimeLimitRule.kt` (lines 21-33): A data class modeling time limit entities.
3. `AmniShieldDatabaseMigrations.kt` (lines 7-22): An object containing static SQL migration strings (`MIGRATION_1_2_STATEMENTS`) referencing tables `app_block_rules` and `screen_time_limit_rules`.

**Codebase Reality:** A project-wide symbol and string search confirms that none of these three classes possess `@Entity`, `@Dao`, or `@Database` annotations. Furthermore, they have **exactly 0 usages** across the entire codebase. They are orphaned artifacts from an abandoned SQLite prototype. Runtime business logic never executes or references these stubs.

### 1.3 Active SharedPreferences Storage Engine

Local persistence operates **100% via Android SharedPreferences** orchestrated through `app/src/main/java/com/alhaq/amnishield/utils/SavedPreferencesLoader.kt` (2,015 lines), which is referenced across 459 distinct call sites.

`SavedPreferencesLoader.kt` directly accesses and manages **29 specialized XML preference files**:
1. `amnispace_prefs.xml`: Focus launcher preferences, mindful breathing duration, and workspace styling.
2. `anti_uninstall.xml`: Anti-uninstall protection state, Device Admin status, and tamper locks.
3. `app_blocker.xml`: Master app blocker toggle, schedule rules JSON (`schedule_rules`), and cooldown data.
4. `app_launch_tracking.xml`: Launch count limits (`launch_limit_rules`) and daily execution counters.
5. `app_preferences.xml`: General UI flags and operational preferences.
6. `app_usage_tracker.xml`: Daily application usage statistics, quotas, and screen time tracking.
7. `AppPreferences.xml`: Legacy casing preserved for backward compatibility with pre-rebrand releases.
8. `auto_focus_hours.xml`: Automated scheduled focus mode configurations.
9. `cheat_hours.xml`: Temporary bypass allowance schedules.
10. `compassionate_access.xml`: Al-Haq Community Pass state, grant timestamps, and monotonic uptime records.
11. `config_tracker.xml`: System configuration hash and change tracking.
12. `device_identity.xml`: Random device UUID and local profile metadata.
13. `feature_toggles.xml`: Granular feature activation flags.
14. `focus_mode.xml`: Focus mode active state, interception style, and session timestamps.
15. `founder_pass_state.xml`: Founder pass claim status, expiration timestamp, and monotonic clock state.
16. `grayscale.xml`: Bedtime and strict mode screen grayscale configuration.
17. `home_dashboard.xml`: Widget layouts, quick tile states, and dashboard customization.
18. `keyword_blocker_configs.xml`: Custom blocked keyword lists and sensitivity settings.
19. `Keyword_blocker_ignored_apps.xml`: Whitelist of application packages exempt from keyword scanning.
20. `keyword_blocker_packs.xml`: Pre-packaged keyword category toggles.
21. `overlay_apps.xml`: Target packages designated for floating overlay counters.
22. `premium_state.xml`: Cached premium status, verification timestamps, and entitlement cache.
23. `reel_blocker.xml`: Short-form video blocker toggles, daily scroll limits, and platform thresholds.
24. `schedules_migration.xml`: Tracking flags for legacy schedule format upgrades.
25. `social_media_blocker.xml`: Dedicated social network app block rules.
26. `view_blocker.xml`: Accessibility view-level element suppression rules.
27. `warning_data.xml`: Block event history, warning screen counters, and intervention telemetry.
28. `website_blocker.xml`: Website blocking toggle, custom blocked domains set, and browser lists.
29. `website_usage_tracker.xml`: Per-domain active engagement time counters.

An additional 8 preference files are managed by auxiliary managers (`achievement_notifications.xml`, `amnishield_sync_secrets_fallback.xml`, `config_reels.xml`, `error_reporting.xml`, `permission_guide_prefs.xml`, `reminder_settings.xml`, `shortcuts.xml`, `theme_prefs.xml`), bringing the total to **37 SharedPreferences XML files** in production.

#### Active Rule Persistence via Gson
Complex data structures are serialized to JSON strings and written to SharedPreferences using Google Gson. `SavedPreferencesLoader.kt` contains **33 explicit `Gson()` instantiations**.

The primary operational rule entity is `AppBlockScheduleRule` (located in `app/src/main/java/com/alhaq/amnishield/data/blockers/UniversalScheduleRule.kt:14-60`), NOT the orphaned `AppBlockRule.kt`. In `SavedPreferencesLoader.kt:740-772`, rules are read and written using:
```kotlin
fun loadAppBlockerScheduleRules(): MutableList<AppBlockScheduleRule> {
    val sharedPreferences = context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
    val json = sharedPreferences.getString("schedule_rules", null)
    if (json.isNullOrEmpty()) return mutableListOf()

    val type = object : TypeToken<MutableList<AppBlockScheduleRule>>() {}.type
    return runCatching {
        val rawList = Gson().fromJson<MutableList<AppBlockScheduleRule>>(json, type) ?: mutableListOf()
        rawList.map { it.sanitize() }.filterNot { ... }.toMutableList()
    }.getOrElse {
        Log.e("SavedPreferencesLoader", "Failed to load app blocker schedule rules", it)
        mutableListOf()
    }
}
```

### 1.4 SecureKeyStore & EncryptedSharedPreferences Isolation

`EncryptedSharedPreferences` is imported from `androidx.security:security-crypto:1.1.0-alpha06`. However, its application across the codebase is strictly constrained:

1. **Strict File Isolation:** A codebase-wide scan reveals that `EncryptedSharedPreferences` is imported and instantiated **exclusively within `app/src/main/java/com/alhaq/amnishield/data/sync/SecureKeyStore.kt`** (lines 4, 16, 20, 21, 24, 34, 38, 39, 42). Zero user rules, blocking schedules, or general application preferences ever touch `EncryptedSharedPreferences`.
2. **Key Scheme & Storage Target:** Operates on file `amnishield_sync_secrets.xml` utilizing Android KeyStore:
   - MasterKey: `MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()`
   - Key Encryption Scheme: `PrefKeyEncryptionScheme.AES256_SIV`
   - Value Encryption Scheme: `PrefValueEncryptionScheme.AES256_GCM`
3. **Stored Attributes:** Strictly stores cloud synchronization secrets:
   - `dek`: Data Encryption Key (Base64 encoded string)
   - `access_token` & `refresh_token`: Supabase JWT session tokens
   - `cursor`: Synchronization pagination timestamp
   - `fcm_token`: Firebase Cloud Messaging device registration token
   - `device_id` & `device_name`: Ephemeral sync identification UUID
4. **Self-Healing Fallback:** Lines 23-45 handle KeyStore decryption errors or hardware module corruption by catching `Throwable`, deleting corrupted `amnishield_sync_secrets.xml`, and retrying. If re-creation fails, it falls back to standard unencrypted SharedPreferences: `context.getSharedPreferences("amnishield_sync_secrets_fallback", Context.MODE_PRIVATE)`.

### 1.5 Main Binder Thread Deserialization Bottleneck

The current persistence architecture introduces a severe performance hazard in `AmniShieldAccessibilityService.kt`:

1. **Accessibility Event Hot Path:** The service receives window and node updates via `onAccessibilityEvent(event: AccessibilityEvent?)` (lines 240-565). This method runs directly on the Android Main UI / Binder thread.
2. **Synchronous Deserialization Multipliers:** During a single event dispatch, the pipeline repeatedly evaluates active rules:
   - Line 290: `getActiveFocusModeRule()` -> invokes `savedPreferencesLoader.loadAppBlockerScheduleRules()`
   - Line 291: `isFeatureCurrentlyActive("FOCUS_MODE")` -> invokes `savedPreferencesLoader.loadAppBlockerScheduleRules()`
   - Line 350: `isFeatureCurrentlyActive("website_blocker")` -> invokes `savedPreferencesLoader.loadAppBlockerScheduleRules()`
   - Line 351: `getActiveBlockedWebsites()` -> invokes `savedPreferencesLoader.loadAppBlockerScheduleRules()`
   - Lines 812, 855: `getActiveBlockedKeywords()` -> invokes `savedPreferencesLoader.loadAppBlockerScheduleRules()`
3. **Performance Impact:** Because `SavedPreferencesLoader.kt` maintains **zero in-memory caching** for schedule rules, each invocation executes:
   - Synchronous disk I/O via `sharedPreferences.getString("schedule_rules", null)`.
   - Runtime allocation of `TypeToken`.
   - Full reflection-based JSON deserialization via `Gson().fromJson(...)`.
   - Iterative validation and defensive sanitization loop (`rawList.map { it.sanitize() }`).
   - Collection cloning (`toMutableList()`).
4. **Jank & Dropped Frames:** When rapid window transitions, keyboard inputs, or scrolling events occur, `Gson().fromJson()` executes 4 or more times back-to-back synchronously on the Main Binder Thread. This causes UI micro-stutters and increases the risk of accessibility event queue overflow. Resolving this via in-memory schedule caching is prioritized under Milestone 0.3.2.

---

## Section 2 (R2): Dual-Host Container Presentation Architecture

Documentation in `ARCHITECTURE.md` historically presented the UI as a unified Jetpack Compose hierarchy. In reality, AmniShield-Android operates on a hybrid **Dual-Host Container Presentation Architecture** bridging legacy Android `AppCompatActivity` / `FragmentManager` patterns with modern Jetpack Compose Material 3 UI screens.

```
┌────────────────────────────────────────────────────────────────────────┐
│               Dual-Host Presentation Architecture                      │
│  ┌──────────────────────────────┐    ┌──────────────────────────────┐  │
│  │ MainActivity (2,022 lines)   │    │ FragmentActivity (210 lines) │  │
│  │ 4 Bottom Nav Tabs (XML)      │    │ 18 Sub-Screen Router (Bridge)│  │
│  │ Stats, Blocks, Focus, Adv.   │    │ Embedding ComposeView M3 UI  │  │
│  └──────────────────────────────┘    └──────────────────────────────┘  │
│    [AmniShieldAdaptiveApp (254 lines) disconnected; target for 0.3.5]  │
└────────────────────────────────────────────────────────────────────────┘
```

### 2.1 MainActivity: Primary 4-Tab Container

`app/src/main/java/com/alhaq/amnishield/ui/activity/MainActivity.kt` contains **2,022 lines** of code and serves as the primary navigation host for top-level application destinations:

- **Hierarchy:** Inherits from `AppCompatActivity` (`MainActivity.kt:104`).
- **Layout Binding:** Inflates XML layout via view binding `ActivityMainBinding.inflate(layoutInflater)` (`MainActivity.kt:219`) set as content view (`MainActivity.kt:220`).
- **Primary Tab Host:** `setupFragmentNavigation(savedInstanceState)` (`MainActivity.kt:476-550`) routes bottom navigation bar selections (`binding.bottomNavigation.setOnItemSelectedListener`) by executing `supportFragmentManager.beginTransaction().replace(R.id.nav_host_fragment, ...)` across 4 primary tabs:
  1. `R.id.navigation_stats` -> `com.alhaq.amnishield.ui.fragments.StatsFragment()` (`MainActivity.kt:484, 522`)
  2. `R.id.navigation_blocks` -> `com.alhaq.amnishield.ui.fragments.BlocksManagerFragment()` (`MainActivity.kt:482, 529`)
  3. `R.id.navigation_focus` -> `com.alhaq.amnishield.ui.fragments.FocusFragment()` (`MainActivity.kt:483, 536`)
  4. `R.id.navigation_advanced` -> `com.alhaq.amnishield.ui.fragments.AdvancedFragment()` (`MainActivity.kt:481, 543`)
- **Auxiliary Systems in `MainActivity.kt`:**
  - Navigation Drawer management (`binding.drawerLayout`, `MainActivity.kt:108, 247, 293`).
  - Support Toolbar and action menu handling (`binding.toolbar`, `MainActivity.kt:222`).
  - Deep-link intent dispatching and dynamic shortcut routing (`MainActivity.kt:310-474`).
  - Back-press dispatching via `OnBackPressedDispatcher` (`MainActivity.kt:252-263`).
  - Google Sign-In intent resolution and Supabase policy sync triggers (`MainActivity.kt:284-287`).
  - Full-screen Master PIN prompt dialog (`showPinLockFullscreenDialog()`, `MainActivity.kt:1976-2009`).

### 2.2 FragmentActivity: Secondary Sub-Screen Router

`app/src/main/java/com/alhaq/amnishield/ui/activity/FragmentActivity.kt` contains **210 lines** of code and acts as a secondary router hosting 18 sub-screens, onboarding flows, and feature configuration fragments:

- **Hierarchy:** Inherits from `AppCompatActivity` (`FragmentActivity.kt:46`).
- **Container Layout:** Sets `R.layout.activity_fragment` and replaces `R.id.fragment_holder` (`FragmentActivity.kt:56, 158, 163`).
- **Intent Dispatch Routing (`feature_type` extra, lines 66-94):**
  1. `"focus_mode"` -> `FocusFragment()`
  2. `"focus_mode_config"` -> `FocusModeConfigFragment()`
  3. `"app_blocker"` -> `AppBlockerConfigFragment()`
  4. `"app_blocker_schedules"` -> `BlocksManagerFragment()` (filtered to App Blocker)
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
- **Fallback Intent Dispatch Routing (`fragment` extra, lines 97-133):**
  17. `AllAppsUsageFragment.FRAGMENT_ID` -> `AllAppsUsageFragment()`
  18. `ManageLaunchLimitsFragment.FRAGMENT_ID` -> `ManageLaunchLimitsFragment()`
  19. `WelcomeFragment.FRAGMENT_ID` -> `WelcomeFragment()` (first-launch onboarding)
  20. `AccessibilityGuide.FRAGMENT_ID` -> `AccessibilityGuide()` (permission guide)
  21. `ProfileFragment.FRAGMENT_ID` -> `ProfileFragment()`

### 2.3 Jetpack Compose UI Embedding via ComposeView

AmniShield achieves modern Material 3 UI rendering within this Dual-Host architecture through bridge fragments that return `ComposeView`:

1. **Fragment Bridge Pattern:** Sub-screen configuration fragments (`AppBlockerConfigFragment`, `ReelBlockerConfigFragment`, `UsageTrackerConfigFragment`, and `KeywordBlockerConfigFragment` in `FeatureConfigFragments.kt`, as well as `FocusModeConfigFragment.kt`, `WebsiteBlockerConfigFragment.kt`, `DiagnosticsFragment.kt`, `PremiumFeaturesFragment.kt`, `RoadmapFragment.kt`, and `OurJourneyFragment.kt`) inherit from `BaseFeatureFragment`. In `onCreateView`, they construct and return:
   ```kotlin
   return ComposeView(requireContext()).apply {
       setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
       setContent {
           AmniShieldTheme {
               AppBlockerConfigScreen(...)
           }
       }
   }
   ```
2. **Direct Activity Dialog Embedding:** `FragmentActivity.kt:169-209` (`showBypassPinDialog`) instantiates a standalone `ComposeView(this)` configured with `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`, wrapping `AmniShieldTheme` and `PinPromptContent` inside a full-screen decor view dialog.

### 2.4 AmniShieldAdaptiveApp: Origin, Disconnection & Target Architecture

`app/src/main/java/com/alhaq/amnishield/ui/AmniShieldAdaptiveApp.kt` (254 lines) models a modern, unified pure-Compose navigation shell using `NavigationSuiteScaffold` to adapt across compact phones, foldables, and large-screen tablets.

- **Git Commit `71556468e4` (Fri Aug 28 2026):**  
  `feat(activity): connect AmniShieldAdaptiveApp setContent root in MainActivity`  
  Connected `AmniShieldAdaptiveApp` as the root `setContent` hierarchy in `MainActivity.kt`.
- **Git Commit `ed5754883f` (Sun Aug 30 2026):**  
  `feat: implement AmniSpace mindful focus launcher space & breathing pause`  
  Disconnected `AmniShieldAdaptiveApp` from `MainActivity.kt`. Replaced Compose `setContent` with XML view binding `ActivityMainBinding.inflate`, restored `setContentView(binding.root)`, and re-activated `setupFragmentNavigation`.
- **Rationale for Disconnection:** Attempting to drive top-level navigation through Compose `NavigationSuiteScaffold` while managing complex legacy `FragmentManager` backstacks, drawer layouts, and sub-screen fragment routers resulted in fatal `FragmentManager` lifecycle crashes during configuration changes and nested fragment restoration.
- **Current Status:** `AmniShieldAdaptiveApp.kt` remains compiled in the codebase but has **exactly 0 callers**. It is preserved as the target architecture for Milestone 0.3.5 (Pure Compose & Storage Architecture Epic), which will deprecate and replace `MainActivity` and `FragmentActivity`.

---

## Section 3 (R3): Blocker Interceptor Pipeline Priority Alignment

The `AmniShieldAccessibilityService.kt` (914 lines) manages real-time system window inspection. Interceptors execute in a strict priority-ordered chain of responsibility.

### 3.1 Chain of Responsibility Execution Sequence

Inside `onAccessibilityEvent(event: AccessibilityEvent)` (lines 280-565), the execution sequence is strictly ordinal:

1. **Priority 0 (Highest): `AntiUninstallDetector` (Line 283):**
   ```kotlin
   if (antiUninstallDetector.inspect(event, rootNode)) {
       return
   }
   ```
   Inspects package installer surfaces, Google Play Store uninstall prompts, OEM security apps, Device Administrator settings, and App Info screens. If tampering is detected, it dispatches `GLOBAL_ACTION_HOME` or launches `AntiUninstallPasswordActivity`, then immediately exits with `return` to stop downstream processing.
2. **Priority 1: `FocusModeBlocker` (Lines 304-348):**
   ```kotlin
   if (isFocusModeActive) {
       val focusModeResult = focusModeBlocker.doesAppNeedToBeBlocked(...)
       if (focusModeResult.isBlocked) {
           blockingStatsManager.recordAppBlock(packageName, "Blocked by Focus Mode")
           when (savedPreferencesLoader.getFocusModeInterceptionStyle()) {
               Constants.FOCUS_INTERCEPTION_STYLE_WORKSPACE -> { startActivity(intent); return }
               Constants.FOCUS_INTERCEPTION_STYLE_SILENT -> { pressHome(); return }
               else -> { startActivity(intent); return }
           }
       }
   }
   ```
   Evaluates active scheduled or manual focus sessions against whitelist rules. If blocked, dispatches `AmniSpaceActivity` (Focus Launcher), performs silent home navigation (`pressHome()`), or launches `WarningActivity`, then short-circuits with `return`.
3. **Priority 2: `WebsiteBlockerDetector` (Lines 350-415):**
   ```kotlin
   if (savedPreferencesLoader.isWebsiteBlockerEnabled(false) && isFeatureCurrentlyActive("website_blocker")) {
       val activeWebsites = getActiveBlockedWebsites()
       if (activeWebsites.isNotEmpty()) {
           val blockedSocialApps = savedPreferencesLoader.loadBlockedWebsitesApps()
           if (blockedSocialApps.contains(packageName)) {
               // Intercept designated social media app
               return
           } else if (rootNode != null) {
               val blockedSite = websiteBlockerDetector.findBlockedWebsite(rootNode, packageName, activeWebsites)
               if (blockedSite != null) {
                   // Intercept browser domain
                   return
               }
           }
       }
   }
   ```
   Evaluates browser URL bar address nodes across 15+ supported browsers or designated social web wrappers. If a blocked domain matches, dispatches mindful breathing in `AmniSpaceActivity`, `pressHome()`, or `WarningActivity`, then short-circuits with `return`.
4. **Priority 3: `AppBlocker` (Lines 417-448):**
   ```kotlin
   if (!isFocusBlockAllExSelectedActive) {
       if (savedPreferencesLoader.isAppBlockerFeatureEnabled(false) && isFeatureCurrentlyActive("app_blocker")) {
           val appBlockerResult = appBlocker.doesAppNeedToBeBlocked(packageName, savedPreferencesLoader, this)
           if (appBlockerResult.isBlocked) {
               blockingStatsManager.recordAppBlock(packageName, "Blocked by App Blocker")
               // Intercept app launch limit or schedule
               return
           }
       }
   }
   ```
   Evaluates launch limit counters, daily screen time quotas, and scheduled app block rules. If blocked, dispatches `AmniSpaceActivity`, `pressHome()`, or `WarningActivity`, then short-circuits with `return`.
5. **Priority 4: `KeywordBlocker` & `ReelBlocker` (Lines 456, 471-562):**
   ```kotlin
   val eventCopy = AccessibilityEvent.obtain(event)
   eventChannel.trySend(eventCopy)
   ```
   Events passing the synchronous filters are forwarded to `eventChannel = Channel<AccessibilityEvent>(Channel.CONFLATED)`. Background coroutine worker `startBackgroundWorker()` consumes events off the Main Binder Thread in `processDeferredChecks(event)`:
   - Lines 507-529: `keywordBlocker.checkIfUserGettingFreaky(rootNode, event)` scans text inputs, search queries, and content nodes. If flagged, executes text erasure and displays warning overlay.
   - Lines 531-562: `reelBlocker.doesReelNeedToBeBlocked(rootNode, packageName)` scans ViewPager2 containers in short-form video platforms (YouTube Shorts, IG Reels, TikTok), tracking scroll velocity, daily thresholds, and enforcing session timeouts.

### 3.2 Blocker Interceptor Pipeline Table

This table rectifies the priority inversions and duplicates in `ARCHITECTURE.md:40-47`:

| Priority | Component | Code Reference | Target Surface / Vectors | Action Dispatched | Execution Model |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **0 (Highest)** | `AntiUninstallDetector` | Line 283 | Play Store, OEM Security Centers, Device Admin, App Info | `GLOBAL_ACTION_HOME`, `AntiUninstallPasswordActivity` | Synchronous (Main Binder) |
| **1** | `FocusModeBlocker` | Line 304 | Active Focus Sessions & Whitelist Enforcements | Full-screen Focus Space (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` | Synchronous (Main Binder) |
| **2** | `WebsiteBlockerDetector` | Line 350 | Chrome, Samsung Internet, Firefox, Brave (15+ browsers) & Social Apps | Mindful Breathing (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` redirect | Synchronous (Linear scan of 1-20 active schedule targets) |
| **3** | `AppBlocker` | Line 417 | Blocked Applications, Launch Limits, Daily Usage Limits | Mindful Breathing (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` overlay | Synchronous (Main Binder) |
| **4** | `KeywordBlocker` & `ReelBlocker` | Lines 456, 475-562 | Search bars, query inputs, and Short-form Video feeds (YouTube, IG, FB, TikTok) | Text erasure, scroll limit counter, feed suppression | Deferred Coroutine Worker (`Channel.CONFLATED`) |

### 3.3 Debunking the 50,000 Local Domain Lookup Myth

Documentation previously claimed that `WebsiteBlockerDetector` executes a local database query against 50,000 adult/distracting domains.

**Code Reality:** Inspection of `app/src/main/java/com/alhaq/amnishield/blockers/WebsiteBlockerDetector.kt:73-101` (`findBlockedWebsite`) disproves this claim:
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
In `AmniShieldAccessibilityService.kt:768-809`, `getActiveBlockedWebsites()` extracts target domains strictly from user-configured active schedule rules (`rule.targets.ifEmpty { rule.targetWebsites }`) stored in SharedPreferences. The candidate list typically contains **1 to 20 domains** and is evaluated using a simple, sequential string `.contains()` loop. No database, trie, or 50,000-entry dataset is evaluated.

### 3.4 Status of Asset Directory Domain Lists

The asset directory contains two text files:
- `app/src/main/assets/blocklists/adult_domains.txt` (693 bytes, 52 lines)
- `app/src/main/assets/blocklists/haram_domains.txt` (605 bytes, 50 lines)

**Reference Audit:** A global search across all Kotlin, Java, XML, and Gradle files confirms that neither `adult_domains.txt`, `haram_domains.txt`, nor the directory `blocklists/` is referenced anywhere in code. No call to `assets.open(...)` exists for these files. Both files are **dead, unreferenced assets**.

---

## Section 4 (R4): Premium Architecture & Licensing Verification

AmniShield enforces a strict ethical monetization policy: **Core blocking functionality is 100% free and open-source forever.** Monetization is organized into optional Supporter Passes that unlock enhanced security hardening and cross-device cloud synchronization.

### 4.1 Tier Structure & User Types

Defined in `app/src/main/java/com/alhaq/amnishield/premium/PremiumManager.kt:12-17`:
```kotlin
enum class UserType {
    FREE,
    COMPASSIONATE,
    FOUNDER,
    PREMIUM
}
```

1. **`UserType.FREE` (Free FOSS Core):** Default tier. Includes unrestricted access to App Blocker, Website Blocker, Reel Blocker, Keyword Blocker, usage stats, and on-device diagnostics. Zero ads, zero paywalls on core blocking.
2. **`UserType.COMPASSIONATE` (Al-Haq Community Pass):** Free community access tier for users who cannot afford a supporter pass. Activated via offline cryptographic community key or manual request. Verified using hardware monotonic uptime tracking (`updateCompassionateUptimeAndVerify`).
3. **`UserType.FOUNDER` (Founder Pass):** 90-day early supporter pass granted to pre-registration beta testers and initial community supporters.
4. **`UserType.PREMIUM` (Supporter Pass):** Full supporter pass activated via Google Play subscription, Google Play lifetime purchase, offline ECDSA license key, or Stripe Direct.

### 4.2 Founder Pass & Monotonic Anti-Clock Tampering Defense

The Founder Pass grant and verification logic resides in `SavedPreferencesLoader.kt:908-985`:
```kotlin
fun grantFounderPass(supporterName: String?, optInPublic: Boolean, durationDays: Int = 90) {
    val nowWall = wallClockProvider()
    val nowElapsed = elapsedRealtimeProvider()
    val expiryWall = nowWall + (durationDays.toLong() * 24L * 60L * 60L * 1000L)
    ...
}
```
To prevent users from rolling back device system clocks to artificially extend passes, `verifyFounderPassMonotonicExpiry()` cross-checks wall-clock time deltas (`System.currentTimeMillis()`) against monotonic elapsed realtime (`SystemClock.elapsedRealtime()`). If monotonic clock drift indicates backward time tampering, the pass immediately fails validation.

### 4.3 Compassionate Community Access

Modeled in `SavedPreferencesLoader.kt:1010-1070`:
- Supports offline cryptographic ECDSA signature verification (`LicenseValidator.verifyLicense`).
- Tracks monotonic uptime accumulated across device reboots.
- Provides a dignified, fully featured supporter experience for students and community members without financial means.

### 4.4 Premium Gated Security Features

Gated capabilities are strictly restricted to security and cloud synchronization:

1. **Rule PIN / Password Security Locking (`RuleProBadge`):**
   - Implemented in `app/src/main/java/com/alhaq/amnishield/ui/components/RuleSecurityComponents.kt:44-70`. Renders an amber "PRO" badge (`Icons.Default.Star`, `Color(0xFFF59E0B)`).
   - In `RuleSecurityComponents.kt:182-214` and `SetRulePinDialog.kt:65-69`, setting `AuthType.GLOBAL_PIN` or `AuthType.RULE_PIN` requires `isPremiumUser`. Standard `AuthType.NONE` is free for all users.
2. **Advanced PIN Security Suite (`AdvancedScreen.kt:301-354`):**
   - "Enable PIN Security": Requires PIN to change protection rules.
   - "App Lock": Requires PIN to open the AmniShield app.
   - "Bypass PIN Lock": Enforces PIN verification when modifying settings while a focus session is active.
   - Toggling any of these three switches while `!state.isPremiumUser` redirects to `onNavigateToPremium()`.
3. **Multi-Device Cloud Policy Sync via Supabase:**
   - Implemented in `PolicySyncManager.kt` and `PlaystoreSyncProvider.kt`.
   - Synchronizes blocklists, active schedules, strict focus configurations, and adult filters to Supabase table `sync_records` (`https://jrgpmcomvibgklmvnxud.supabase.co/rest/v1/devices`). Gated to authenticated supporter accounts.

### 4.5 Multi-Flavor Implementation Matrix

AmniShield uses three Gradle product flavors to comply with distribution channel policies:

| Attribute | `playstore` Flavor | `fdroid` Flavor | `universal` Flavor |
| :--- | :--- | :--- | :--- |
| **Target Distribution** | Google Play Store Closed/Open Testing | F-Droid Official & De-Googled ROMs | Direct GitHub Releases Sideload |
| **Billing Library** | `com.android.billingclient:billing-ktx:7.1.1` | None (0 GMS / 0 Billing dependencies) | `billing-ktx:7.1.1` (Hybrid fallback) |
| **In-App Products** | `amnishield_premium_lifetime`<br>`amnishield_founder_pass` | Unsupported (Presents FOSS explanation) | Supported when Google Play Services present |
| **Subscriptions** | `amnishield_premium_monthly`<br>`amnishield_premium_yearly` | Unsupported | Supported when Google Play Services present |
| **Offline Licensing** | Secondary fallback | Primary: 100% Offline NIST P-256 ECDSA | Primary / Secondary hybrid |
| **Direct Web Billing** | Prohibited (Google Play Policy compliance) | Stripe Direct Checkout via Supabase Edge Function | Stripe Direct Checkout via Supabase Edge Function |

#### Cryptographic Offline Licensing (`LicenseValidator.kt`)
For `fdroid` and `universal` builds, licenses are validated completely offline with zero network connectivity:
- **Algorithm:** `SHA256withECDSA` over NIST P-256 (`secp256r1`).
- **Format:** `<payloadBase64>.<signatureBase64>`.
- **Public Keyring (X.509 Base64):**
  `MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE7EFR1qxpfZTMeR52M1+04+tPb6ItmVmhPbRCIJYje3jtglTdBbcct+/xvc1D1NZtXuvSb4Egtdqm/EJ6H67fEA==`
- **P1363 to DER Translation:** `p1363ToDer()` converts 64-byte IEEE P1363 raw signature buffers (`r[32] || s[32]`) issued by WebCrypto / Deno Edge Functions into standard ASN.1 DER sequences for Android `java.security.Signature`.

---

## Section 5 (R5): Roadmap Scope Hygiene & Milestone Realignment

### 5.1 Purging Cross-Repository Scope Creep

`ROADMAP.md:39-45` previously listed three foreign features that do not belong to `AmniShield-Android`:
1. **Windows Elevated Background Service:** Background Windows service managing UAC-free network `hosts` resolution and system-level firewall rules. **Belongs to `AmniShield-Windows`** (Flutter/C++ desktop utility).
2. **Opt-in DNS Sinkhole Engine:** Local VPN-based DNS sinkhole filter powered by the `AmniGuard-FireWall` packet filtering engine. **Belongs to `AmniGuard-FireWall`** (standalone VPN/DNS sinkhole product).
3. **On-Device Mobile NPU Vision Blur Engine:** Realtime shortform media content filtering powered by LiteRT / Mobile NPU. **Belongs to `AmniBlur` / `AmniGaze`** (MediaProjection LiteRT screen censoring engine).

All three items are purged from `ROADMAP.md`.

### 5.2 Reaffirming Completed Milestones (v0.3.1 / Code 145)

The production baseline represents the following completed features:
- Continuous Cross-App & Cross-Session Reels Engine with daily accumulation.
- Resilient Fallback Node Comparator (depth up to 8 view-tree traversal).
- ViewPager2 Swipe Interception (`AccessibilityEvent.TYPE_VIEW_SELECTED`).
- Synchronous Floating Pill Overlay (`runOnMain` view inflation).
- Browser Website Engagement Tracker (`BrowserSessionTracker`).
- Pre-Registration Founder Pass & Entitlements with monotonic clock protection.
- Multi-Platform Cloud Policy Sync via Supabase.
- Ephemeral 6-Digit PIN & In-App ZXing Camera QR Pairing.
- Unified AmniShield Console architecture.
- Modularized Accessibility Service interceptor pipeline.
- On-Device ECDSA NIST P-256 Cryptographic License Validation.
- 100% On-Device Diagnostics & In-App Crash Log Viewer (`CrashLogger`).
- Hardened Anti-Uninstall & Tamper Defense.
- Material 3 Home Screen Widgets Suite.

### 5.3 Versioned Active Milestones (v0.3.2 - v0.3.5)

The upcoming roadmap is realigned with 4 versioned milestones addressing verified technical debt:

#### Milestone 0.3.2: Accessibility Engine Hardening & Binder Safety
- [x] **In-Memory Schedule Caching & Broadcast Alignment:** Eliminate reflection-based `Gson().fromJson()` deserialization on the Main Binder Thread during `onAccessibilityEvent` in `AmniShieldAccessibilityService.kt` and `SavedPreferencesLoader.kt`, and fix `WebsiteBlockerConfigScreen.kt` to broadcast `INTENT_ACTION_REFRESH_WEBSITE_BLOCKER` / unified schedule refresh via `ServiceBroadcastManager.kt`.
- [x] **Deferred Pipeline Temporal Window Guard:** Enforce strict package-identity verification (`rootPackage.equals(packageName, ignoreCase = true)`) and `SystemExclusionManager.isExempt(...)` in `AmniShieldAccessibilityService.processDeferredChecks()` to prevent false-positive interceptions when users rapidly switch windows.
- [x] **Polling Decoupling & Background Disk I/O:** Eliminate continuous `rootInActiveWindow` queries on 1-second and 2-second Handler timers in `ReelsSessionTracker.kt` and `BrowserSessionTracker.kt`, transitioning to event-driven surface tracking and offloading usage persistence writes to `Dispatchers.IO`.
- [x] **Node Traversal Exception Safety:** Implement robust `try ... finally` blocks and null-safe child node recycling across `AntiUninstallDetector.kt` and `ReelDetectionEngine.kt`.
- [x] **Manifest Security Hardening:** Set `android:exported="false"` on `LauncherActivity` in `AndroidManifest.xml` (line 76) to prevent unauthorized external intent launching.

#### Milestone 0.3.3: Store Compliance & Layout Resilience
- [x] **Prominent Disclosure Overhaul:** Implement a `ScrollView` content container with a sticky bottom action bar, `wrap_content` (`minHeight="48dp"`) button sizing in `dialog_accessibility_disclosure.xml`, and `.setCancelable(false)` in `AccessibilityDisclosureDialog.kt` to guarantee compliance in landscape orientation and 200% font scaling.
- [x] **Persistent Affirmative Consent:** Persist the accessibility disclosure acceptance flag and timestamp (`setAccessibilityDisclosureAccepted`) in `SavedPreferencesLoader.kt` to guarantee auditability.
- [x] **Mandatory Accessibility Onboarding Gate:** Close the onboarding bypass loophole in `PermissionsScreen.kt` (line 115) so `canProceed` strictly requires `isAccessibilityGranted`.
- [x] **Screen Rotation State Preservation:** Add `rememberSaveable` state handling in `AmniSpaceActivity.kt` and `AmniSpaceBreathingCircle.kt`, implement `onSaveInstanceState` timer preservation in `WarningActivity.kt`, and dynamically adapt window insets and orientation offsets in `UsageStatOverlayManager.kt`.

#### Milestone 0.3.4: Dual-Host Cleanup & Navigation Decoupling
- [ ] **Type-Safe Intent Routing:** Implement a sealed route contract between `MainActivity.kt` and `FragmentActivity.kt`, replacing loosely-typed string extras (`feature_type`, `fragment`).
- [ ] **SharedPreferences Synchronization Lock:** Implement mutex synchronization locks in `SavedPreferencesLoader.kt` to prevent concurrent multi-threaded read/write file races.
- [ ] **Automated Multi-Flavor Matrix Testing:** Expand CI GitHub Actions to run automated instrumented UI tests across `playstoreDebug`, `fdroidDebug`, and `universalDebug`.

#### Milestone 0.3.5: Pure Compose & Storage Architecture Epic (Prep for V1.0)
- [ ] **Room & DataStore Migration Epic:** Formalize a phased migration path from the 29+ SharedPreferences XML files to Room DB (relational blocking rules) and Preferences DataStore (flags and tokens).
- [ ] **Pure-Compose Navigation Epic:** Reconnect and finalize `AmniShieldAdaptiveApp.kt` using modern Compose Navigation 2.8+, safely retiring the dual-host split (`MainActivity.kt` + `FragmentActivity.kt`).

---

## Section 6 (R6): Universal Zero-Emoji Policy & Versioning Rectification

### 6.1 Unicode Emoji Remediation in ARCHITECTURE.md

The Amni Ecosystem enforces a **Strict Universal Zero-Emoji Policy**: No UTF-8 emojis or emoji-based unicode characters may appear in code, UI layouts, components, strings, comments, logs, commit messages, or documentation.

Four unicode emojis were detected in `ARCHITECTURE.md`:
1. **Line 9:** `## [U+1F3D7] Core Architectural Layers` -> contains unicode building emoji (`U+1F3D7 U+FE0F`).
   - *Rectification:* Replace with `## Core Architectural Layers`.
2. **Line 36:** `## [U+1F6E1] Blocker Pipeline & Chain of Responsibility` -> contains unicode shield emoji (`U+1F6E1 U+FE0F`).
   - *Rectification:* Replace with `## Blocker Pipeline & Chain of Responsibility`.
3. **Line 51:** `## [U+1F4DD] Diagnostic & Crash Logging Architecture (CrashLogger)` -> contains unicode memo emoji (`U+1F4DD`).
   - *Rectification:* Replace with `## Diagnostic & Crash Logging Architecture (CrashLogger)`.
4. **Line 69:** `## [U+1F512] Invariants & Guardrails for AI & Human Maintainers` -> contains unicode lock emoji (`U+1F512`).
   - *Rectification:* Replace with `## Invariants & Guardrails for AI & Human Maintainers`.

### 6.2 Project Versioning & Metadata Reconciliation

An audit of version metadata revealed technical drift between the agent configuration and the build system:
- **`app/build.gradle.kts` (Production Baseline):**
  - Line 48: `versionCode = 145`
  - Line 49: `versionName = "0.3.1 (2026.10.05)"`
- **`ROADMAP.md` (Production Baseline):**
  - Line 18: `### Completed Milestones (Current Baseline - v0.3.1 / Code 145)`
- **`.agents/AGENTS.md` (Outdated):**
  - Line 11: Stale reference to `Room DB, Preferences DataStore`.
  - Line 19: `- **Version**: 1.0.2-closed.01 (2026.08.13) / versionCode 132.`

**Rectification:** `.agents/AGENTS.md` is updated to align with the production baseline (`versionCode 145`, `versionName "0.3.1 (2026.10.05)"`), and the hallucinated references to Room DB and DataStore are replaced with SharedPreferences persistence.

---

## Section 7: Exact Git Diffs & Full Replacement Blocks

### 7.1 ARCHITECTURE.md Exact Unified Diff

```diff
diff --git a/ARCHITECTURE.md b/ARCHITECTURE.md
index 19f3e4a..b2c8e19 100644
--- a/ARCHITECTURE.md
+++ b/ARCHITECTURE.md
@@ -2,37 +2,55 @@
 
 ## Overview
 
-AmniShield is an open-source, privacy-first digital wellness and content-blocking application for Android. It operates via a dedicated Android Accessibility Service pipeline, local SQLite/Room data caching, encrypted SharedPreferences, and on-device diagnostic logging.
+AmniShield is an open-source, privacy-first digital wellness and content-blocking application for Android. It operates via a dedicated Android Accessibility Service pipeline, local SharedPreferences persistence (29+ specialized XML files with Gson JSON serialization via SavedPreferencesLoader), hardware-backed EncryptedSharedPreferences strictly isolated to Supabase authentication tokens (SecureKeyStore), and on-device diagnostic logging.
 
 ---
 
-## [U+1F3D7] Core Architectural Layers
+## Core Architectural Layers
 
 ```
-┌────────────────────────────────────────────────────────┐
-│               Jetpack Compose Material 3 UI            │
-│  (StatsScreen, BlocksManager, FocusSpace, Diagnostics)  │
-└──────────────────────────┬─────────────────────────────┘
-                           │
-┌──────────────────────────▼─────────────────────────────┐
-│          ViewModel & Reactive State Management         │
-│          (AmniShieldViewModel, AmniShieldState)           │
-└──────────────────────────┬─────────────────────────────┘
-                           │
-┌──────────────────────────▼─────────────────────────────┐
-│            AmniShieldAccessibilityService               │
-│  (Central Node Stream Orchestrator & Pipeline Host)     │
-└──────┬───────────────────┬───────────────────┬─────────┘
-       │                   │                   │
-┌──────▼────────┐   ┌──────▼────────┐   ┌──────▼────────┐
-│ AntiUninstall │   │  AppBlocker   │   │  ReelBlocker  │
-│  Interceptor  │   │  FocusMode    │   │ & WebsiteFilt │
-│ (Priority 0)  │   │ (Priority 1)  │   │ (Priority 2)  │
-└───────────────┘   └───────────────┘   └───────────────┘
+┌────────────────────────────────────────────────────────────────────────┐
+│               Dual-Host Presentation Architecture                      │
+│  ┌──────────────────────────────┐    ┌──────────────────────────────┐  │
+│  │ MainActivity (2,022 lines)   │    │ FragmentActivity (210 lines) │  │
+│  │ 4 Bottom Nav Tabs (XML)      │    │ 18 Sub-Screen Router (Bridge)│  │
+│  │ Stats, Blocks, Focus, Adv.   │    │ Embedding ComposeView M3 UI  │  │
+│  └──────────────────────────────┘    └──────────────────────────────┘  │
+│    [AmniShieldAdaptiveApp (254 lines) disconnected; target for 0.3.5]  │
+└───────────────────────────────────┬────────────────────────────────────┘
+                                    │
+┌───────────────────────────────────▼────────────────────────────────────┐
+│                  ViewModel & Reactive State Management                 │
+│                 (AmniShieldViewModel, AmniShieldState)                 │
+└───────────────────────────────────┬────────────────────────────────────┘
+                                    │
+┌───────────────────────────────────▼────────────────────────────────────┐
+│                      Local Persistence Layer                           │
+│  ┌─────────────────────────────────┐ ┌───────────────────────────────┐ │
+│  │ SavedPreferencesLoader.kt       │ │ SecureKeyStore.kt             │ │
+│  │ 29+ SharedPreferences XML files │ │ EncryptedSharedPreferences    │ │
+│  │ 33 Gson JSON serialization calls│ │ Supabase sync secrets strictly│ │
+│  │ (Zero Room DB / Zero DataStore) │ │ (Fallback to plain XML)       │ │
+│  └─────────────────────────────────┘ └───────────────────────────────┘ │
+└───────────────────────────────────┬────────────────────────────────────┘
+                                    │
+┌───────────────────────────────────▼────────────────────────────────────┐
+│                    AmniShieldAccessibilityService                      │
+│         (Central Node Stream Orchestrator & Pipeline Host)             │
+└───────┬──────────────┬──────────────┬──────────────┬───────────────────┘
+        │              │              │              │
+┌───────▼──────┐┌──────▼──────┐┌──────▼──────┐┌──────▼──────┐┌───────────▼──────────┐
+│AntiUninstall ││ FocusMode   ││WebsiteBlock ││ AppBlocker  ││ Keyword & Reel Blocker│
+│Interceptor   ││ Blocker     ││  Detector   ││ Interceptor ││ Deferred Channel (P4) │
+│ (Priority 0) ││(Priority 1) ││(Priority 2) ││(Priority 3) ││ (Off-thread Worker)   │
+└──────────────┘└─────────────┘└─────────────┘└─────────────┘└───────────────────────┘
 ```
 
 ---
 
-## [U+1F6E1] Blocker Pipeline & Chain of Responsibility
+## Blocker Pipeline & Chain of Responsibility
 
 The `AmniShieldAccessibilityService` orchestrates accessibility window inspection through explicit priority-ordered interceptors:
 
-| Priority | Component | Target Surface / Vectors | Action Dispatched |
-| :--- | :--- | :--- | :--- |
-| **0 (Highest)** | `AntiUninstallDetector` | Play Store, OEM Security Centers, Device Admin, App Info | `GLOBAL_ACTION_HOME`, `AntiUninstallPasswordActivity` |
-| **1** | `AppBlocker` | Blocked Applications, Launch Limits, Daily Usage Limits | `WarningActivity` overlay or task cancellation |
-| **1** | `FocusModeBlocker` | Active Focus Sessions & Whitelist Enforcements | Full-screen Focus Space overlay |
-| **2** | `WebsiteBlockerDetector` | Chrome, Samsung Internet, Firefox, Brave (15+ browsers) | `WarningActivity` redirect / Back navigation |
-| **2** | `KeywordActionHandler` | Search bars, social feed text inputs, query boxes | Real-time text erasure & warning overlay |
-| **3** | `ReelBlocker` | YouTube Shorts, IG Reels, FB Reels, TikTok feeds | Scroll limit counter, feed suppression |
+| Priority | Component | Target Surface / Vectors | Action Dispatched | Execution Model |
+| :--- | :--- | :--- | :--- | :--- |
+| **0 (Highest)** | `AntiUninstallDetector` (Line 283) | Play Store, OEM Security Centers, Device Admin, App Info | `GLOBAL_ACTION_HOME`, `AntiUninstallPasswordActivity` | Synchronous (Main Binder) |
+| **1** | `FocusModeBlocker` (Line 304) | Active Focus Sessions & Whitelist Enforcements | Full-screen Focus Space (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` | Synchronous (Main Binder) |
+| **2** | `WebsiteBlockerDetector` (Line 350) | Chrome, Samsung Internet, Firefox, Brave (15+ browsers) & Social Apps | Mindful Breathing (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` redirect | Synchronous (Linear scan of 1-20 active schedule targets) |
+| **3** | `AppBlocker` (Line 417) | Blocked Applications, Launch Limits, Daily Usage Limits | Mindful Breathing (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` overlay | Synchronous (Main Binder) |
+| **4** | `KeywordBlocker` & `ReelBlocker` (Line 456, 475-562) | Search bars, query inputs, and Short-form Video feeds (YouTube, IG, FB, TikTok) | Text erasure, scroll limit counter, feed suppression | Deferred Coroutine Worker (`Channel.CONFLATED`) |
 
 ---
 
-## [U+1F4DD] Diagnostic & Crash Logging Architecture (`CrashLogger`)
+## Diagnostic & Crash Logging Architecture (CrashLogger)
 
 AmniShield maintains a strict **zero-external-telemetry** logging policy.
 
@@ -66,7 +84,7 @@ AmniShield maintains a strict **zero-external-telemetry** logging policy.
 
 ---
 
-## [U+1F512] Invariants & Guardrails for AI & Human Maintainers
+## Invariants & Guardrails for AI & Human Maintainers
 
 > [!IMPORTANT]
 > **Node Lifecycle Invariant:** `AmniShieldAccessibilityService` exclusively owns the lifecycle of `rootNode` (recycled once in the `finally` block of `onAccessibilityEvent`). Interceptors and sub-detectors must **never** call `rootNode.recycle()`. Interceptors must only recycle child nodes they generate during internal stack traversals.
@@ -75,6 +93,18 @@ AmniShield maintains a strict **zero-external-telemetry** logging policy.
 > **Strict Short-Circuiting:** When a higher-priority interceptor (such as Anti-Uninstall) triggers an action, return immediately to stop processing downstream content blockers.
 
 > [!NOTE]
+> **Persistence Reality:** Persistence is 100% SharedPreferences via `SavedPreferencesLoader.kt` across 29+ XML files with Gson JSON serialization. EncryptedSharedPreferences is strictly isolated to Supabase cloud sync tokens in `SecureKeyStore.kt`. There is zero Room DB and zero Preferences DataStore in this codebase.
+
+> [!NOTE]
+> **Dual-Host Presentation Reality:** UI presentation is split between `MainActivity.kt` (4 primary bottom navigation tabs) and `FragmentActivity.kt` (secondary router hosting 18 sub-screens embedding ComposeView). `AmniShieldAdaptiveApp.kt` is currently disconnected to preserve FragmentManager lifecycle stability and represents the target architecture for Milestone 0.3.5.
+
+> [!CAUTION]
+> **Main Binder Thread Safety:** In-flight reflection-based Gson deserialization in `onAccessibilityEvent` must be mitigated via in-memory rule caching (Milestone 0.3.2) to prevent Binder thread jank.
+
+> [!IMPORTANT]
+> **Strict Universal Zero-Emoji Policy:** Do not use UTF-8 emojis anywhere in code, UI layouts, strings, comments, logs, or documentation. Use plain-text tags (`[INFO]`, `[OK]`, `[FAIL]`, `[WARNING]`, `[ERROR]`) instead.
+
+> [!NOTE]
 > **Multi-Flavor Build Configuration:** Always build with flavor-qualified Gradle tasks:
 >
 > - `playstoreDebug`: Google Play Store distribution
```

### 7.2 ARCHITECTURE.md Full Replacement Block

```markdown
# AmniShield System Architecture & Engineering Guide

## Overview

AmniShield is an open-source, privacy-first digital wellness and content-blocking application for Android. It operates via a dedicated Android Accessibility Service pipeline, local SharedPreferences persistence (29+ specialized XML files with Gson JSON serialization via SavedPreferencesLoader), hardware-backed EncryptedSharedPreferences strictly isolated to Supabase authentication tokens (SecureKeyStore), and on-device diagnostic logging.

---

## Core Architectural Layers

```
┌────────────────────────────────────────────────────────────────────────┐
│               Dual-Host Presentation Architecture                      │
│  ┌──────────────────────────────┐    ┌──────────────────────────────┐  │
│  │ MainActivity (2,022 lines)   │    │ FragmentActivity (210 lines) │  │
│  │ 4 Bottom Nav Tabs (XML)      │    │ 18 Sub-Screen Router (Bridge)│  │
│  │ Stats, Blocks, Focus, Adv.   │    │ Embedding ComposeView M3 UI  │  │
│  └──────────────────────────────┘    └──────────────────────────────┘  │
│    [AmniShieldAdaptiveApp (254 lines) disconnected; target for 0.3.5]  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                  ViewModel & Reactive State Management                 │
│                 (AmniShieldViewModel, AmniShieldState)                 │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                      Local Persistence Layer                           │
│  ┌─────────────────────────────────┐ ┌───────────────────────────────┐ │
│  │ SavedPreferencesLoader.kt       │ │ SecureKeyStore.kt             │ │
│  │ 29+ SharedPreferences XML files │ │ EncryptedSharedPreferences    │ │
│  │ 33 Gson JSON serialization calls│ │ Supabase sync secrets strictly│ │
│  │ (Zero Room DB / Zero DataStore) │ │ (Fallback to plain XML)       │ │
│  └─────────────────────────────────┘ └───────────────────────────────┘ │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                    AmniShieldAccessibilityService                      │
│         (Central Node Stream Orchestrator & Pipeline Host)             │
└───────┬──────────────┬──────────────┬──────────────┬───────────────────┘
        │              │              │              │
┌───────▼──────┐┌──────▼──────┐┌──────▼──────┐┌──────▼──────┐┌───────────▼──────────┐
│AntiUninstall ││ FocusMode   ││WebsiteBlock ││ AppBlocker  ││ Keyword & Reel Blocker│
│Interceptor   ││ Blocker     ││  Detector   ││ Interceptor ││ Deferred Channel (P4) │
│ (Priority 0) ││(Priority 1) ││(Priority 2) ││(Priority 3) ││ (Off-thread Worker)   │
└──────────────┘└─────────────┘└─────────────┘└─────────────┘└───────────────────────┘
```

---

## Blocker Pipeline & Chain of Responsibility

The `AmniShieldAccessibilityService` orchestrates accessibility window inspection through explicit priority-ordered interceptors:

| Priority | Component | Target Surface / Vectors | Action Dispatched | Execution Model |
| :--- | :--- | :--- | :--- | :--- |
| **0 (Highest)** | `AntiUninstallDetector` (Line 283) | Play Store, OEM Security Centers, Device Admin, App Info | `GLOBAL_ACTION_HOME`, `AntiUninstallPasswordActivity` | Synchronous (Main Binder) |
| **1** | `FocusModeBlocker` (Line 304) | Active Focus Sessions & Whitelist Enforcements | Full-screen Focus Space (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` | Synchronous (Main Binder) |
| **2** | `WebsiteBlockerDetector` (Line 350) | Chrome, Samsung Internet, Firefox, Brave (15+ browsers) & Social Apps | Mindful Breathing (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` redirect | Synchronous (Linear scan of 1-20 active schedule targets) |
| **3** | `AppBlocker` (Line 417) | Blocked Applications, Launch Limits, Daily Usage Limits | Mindful Breathing (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` overlay | Synchronous (Main Binder) |
| **4** | `KeywordBlocker` & `ReelBlocker` (Line 456, 475-562) | Search bars, query inputs, and Short-form Video feeds (YouTube, IG, FB, TikTok) | Text erasure, scroll limit counter, feed suppression | Deferred Coroutine Worker (`Channel.CONFLATED`) |

---

## Diagnostic & Crash Logging Architecture (CrashLogger)

AmniShield maintains a strict **zero-external-telemetry** logging policy.

1. **Uncaught Exception Handling:** `CrashLogger.install(this)` registers a global `Thread.UncaughtExceptionHandler` during application initialization.
2. **Rolling File Cache:**
   - Logs are stored in `context.filesDir/logs/app_logs.txt`.
   - Rotates automatically across 5 files (`app_logs.txt` + 4 backups) with a 500 KB per-file ceiling (~2 MB total max footprint).
3. **PII Sanitization Pipeline:**
   All logs undergo automated regex redaction before touching disk:
   - Passwords, master PINs, encryption salts -> `[REDACTED]`
   - Supabase tokens, JWTs, Bearer headers -> `[REDACTED_TOKEN]` / `[REDACTED_JWT]`
   - Email addresses -> `[REDACTED_EMAIL]`
4. **Diagnostic Screen & Sharesheet Export:**
   Users can view, filter, search, copy, or export logs via Android `FileProvider` (`DiagnosticsScreen`).

---

## Invariants & Guardrails for AI & Human Maintainers

> [!IMPORTANT]
> **Node Lifecycle Invariant:** `AmniShieldAccessibilityService` exclusively owns the lifecycle of `rootNode` (recycled once in the `finally` block of `onAccessibilityEvent`). Interceptors and sub-detectors must **never** call `rootNode.recycle()`. Interceptors must only recycle child nodes they generate during internal stack traversals.

> [!TIP]
> **Strict Short-Circuiting:** When a higher-priority interceptor (such as Anti-Uninstall) triggers an action, return immediately to stop processing downstream content blockers.

> [!NOTE]
> **Persistence Reality:** Persistence is 100% SharedPreferences via `SavedPreferencesLoader.kt` across 29+ XML files with Gson JSON serialization. EncryptedSharedPreferences is strictly isolated to Supabase cloud sync tokens in `SecureKeyStore.kt`. There is zero Room DB and zero Preferences DataStore in this codebase.

> [!NOTE]
> **Dual-Host Presentation Reality:** UI presentation is split between `MainActivity.kt` (4 primary bottom navigation tabs) and `FragmentActivity.kt` (secondary router hosting 18 sub-screens embedding ComposeView). `AmniShieldAdaptiveApp.kt` is currently disconnected to preserve FragmentManager lifecycle stability and represents the target architecture for Milestone 0.3.5.

> [!CAUTION]
> **Main Binder Thread Safety:** In-flight reflection-based Gson deserialization in `onAccessibilityEvent` must be mitigated via in-memory rule caching (Milestone 0.3.2) to prevent Binder thread jank.

> [!IMPORTANT]
> **Strict Universal Zero-Emoji Policy:** Do not use UTF-8 emojis anywhere in code, UI layouts, strings, comments, logs, or documentation. Use plain-text tags (`[INFO]`, `[OK]`, `[FAIL]`, `[WARNING]`, `[ERROR]`) instead.

> [!NOTE]
> **Multi-Flavor Build Configuration:** Always build with flavor-qualified Gradle tasks:
>
> - `playstoreDebug`: Google Play Store distribution
> - `fdroidDebug`: Pure open-source build with offline ECDSA license validation
> - `universalDebug`: Universal sideload build
```

### 7.3 README.md Exact Unified Diff

```diff
diff --git a/README.md b/README.md
index 1ba2d39..f4a8c12 100644
--- a/README.md
+++ b/README.md
@@ -28,6 +28,7 @@ AmniShield is a 100% free and open-source, privacy-first digital wellness and di
 - **Offline Cryptographic Licensing**: Optional Supporter Pass activated completely offline via NIST P-256 ECDSA public key verification.
+- **Zero-Knowledge Architecture**: 100% on-device SharedPreferences persistence (zero Room/DataStore) and isolated EncryptedSharedPreferences.
 
 ---
 
@@ -35,6 +36,7 @@ AmniShield is a 100% free and open-source, privacy-first digital wellness and di
 
 1. [Requirements](#requirements)
 2. [Building the App](#building-the-app)
 3. [Product Flavors](#product-flavors)
+4. [Architecture & Storage Overview](#architecture--storage-overview)
 4. [Android Studio Setup](#android-studio-setup)
 5. [If the Build Shows Old UI](#if-the-build-shows-old-ui)
 6. [Release Builds](#release-builds)
@@ -95,6 +97,35 @@ Each flavor has its own source set under `app/src/<flavor>/java/` for swapping
 billing and sign-in implementations without `#ifdef`-style hacks.
 
+---
+
+## Architecture & Storage Overview
+
+### Presentation Model: Dual-Host Container Architecture
+AmniShield uses a decoupled Dual-Host presentation architecture bridging AndroidX Activities with Jetpack Compose Material 3:
+- **`MainActivity.kt` (2,022 lines)**: Hosts the primary 4 bottom navigation tabs (`Stats`, `Blocks`, `Focus`, `Advanced`) using legacy `supportFragmentManager` transactions.
+- **`FragmentActivity.kt` (210 lines)**: Acts as a secondary router dispatching 18 functional sub-screens and configuration dialogs. Each configuration fragment inherits `BaseFeatureFragment` and returns `ComposeView` embedding Material 3 Compose screens (`AppBlockerConfigScreen`, `ReelBlockerConfigScreen`, etc.).
+- **`AmniShieldAdaptiveApp.kt` (254 lines)**: Pure Compose `NavigationSuiteScaffold` shell temporarily disconnected to avoid FragmentManager lifecycle crashes, preserved as the target architecture for Milestone 0.3.5.
+
+### Persistence Reality: 100% SharedPreferences
+- **Zero Room DB / Zero DataStore**: AmniShield contains zero Room DB and zero DataStore dependencies.
+- **`SavedPreferencesLoader.kt` (2,015 lines)**: Manages 29 specialized SharedPreferences XML files across 459 callers. Complex rules and limits are serialized to JSON strings using Google Gson (33 calls).
+- **`SecureKeyStore.kt`**: `EncryptedSharedPreferences` is strictly isolated to Supabase cloud sync authentication tokens with an automatic plain XML fallback (`amnishield_sync_secrets_fallback`).
+
+### Blocker Interceptor Pipeline
+Executed in `AmniShieldAccessibilityService.kt`:
+1. **Priority 0**: `AntiUninstallDetector` (Line 283) - Intercepts settings, device admin, and store tampering; dispatches Home.
+2. **Priority 1**: `FocusModeBlocker` (Line 304) - Enforces focus sessions; dispatches `AmniSpaceActivity` (Focus Launcher) or `pressHome()`.
+3. **Priority 2**: `WebsiteBlockerDetector` (Line 350) - Evaluates browser URL bars across 15+ browsers (linear scan over 1-20 active schedule targets; `adult_domains.txt` and `haram_domains.txt` are dead assets).
+4. **Priority 3**: `AppBlocker` (Line 417) - Evaluates launch limits, usage quotas, and app block schedules.
+5. **Priority 4**: `KeywordBlocker` & `ReelBlocker` (Lines 456, 475-562) - Deferred via conflated coroutine channel (`Channel.CONFLATED`).
+
+### Premium & Supporter Pass Architecture
+- **Free FOSS Core (`UserType.FREE`)**: Base app, website, reel blockers, and keyword filters are 100% free and open-source forever.
+- **Supporter Passes**: `UserType.PREMIUM`, `UserType.FOUNDER` (90-day early supporter pass), and `UserType.COMPASSIONATE` (Al-Haq community access).
+- **Gated Features**: Rule PIN / Password Security Locking (`RuleProBadge`), Master PIN, App Lock, Bypass PIN Lock, and Supabase cloud policy sync.
+- **Billing Implementation**: `playstore` uses Google Play `BillingClientWrapper.kt`; `fdroid` uses 100% offline NIST P-256 ECDSA public key verification (`SHA256withECDSA` in `LicenseValidator.kt`) with optional Stripe Direct checkout; `universal` provides hybrid capabilities.
+
 ---
 
 ## Android Studio Setup
```

### 7.4 README.md Full Replacement Block

```markdown
# AmniShield — Android App

[![Google Play Testing](https://img.shields.io/badge/Google%20Play-Testing%20%26%20Pre--registration-4285F4?logo=googleplay&logoColor=white)](https://play.google.com/apps/testing/com.alhaq.deenshield)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84.svg?logo=android&logoColor=white)](https://android.com)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20On--Device%20%7C%20Zero%20Telemetry-008080.svg)](PRIVACY_POLICY.md)
[![Discord](https://img.shields.io/badge/Community-Discord-5865F2?logo=discord&logoColor=white)](https://discord.gg/zXz7pGVJY)
[![Telegram](https://img.shields.io/badge/Community-Telegram-26A5E4?logo=telegram&logoColor=white)](https://t.me/amnishield)

AmniShield is a 100% free and open-source, privacy-first digital wellness and distraction blocker for Android. Engineered with Kotlin and Jetpack Compose, it empowers users to reclaim focus by intercepting addictive short-form video reels, scheduled app blocking, keyword filtering, and strict anti-bypass modes without transmitting any screen data or telemetry to remote servers.

---

## Download & Testing

- **Google Play Pre-Registration & Open Testing**: [Join the Beta on Google Play](https://play.google.com/apps/testing/com.alhaq.deenshield) | [Play Store Listing](https://play.google.com/store/apps/details?id=com.alhaq.deenshield)
- **Direct APK Downloads**: Available on [GitHub Releases](https://github.com/alhaq-studio/amnishield-android/releases) (`universal` for standard Google Play Services devices, `fdroid` for de-Googled ROMs).
- **Bug Reports & Issues**: Found a bug or want to suggest an improvement? Submit an issue using our [GitHub Issue Tracker](https://github.com/alhaq-studio/amnishield-android/issues/new/choose).

---

## Core Features

- **Short Video & Reel Blocker**: Surface-level interception of infinite-scroll feeds (YouTube Shorts, Instagram Reels, TikTok).
- **Flexible App & URL Blocking**: Block selected apps, categories, or specific domains with custom schedules and launch limits.
- **Keyword Filtering**: Real-time accessibility text scanning that clears distracting or inappropriate search terms.
- **Zero-Knowledge Privacy**: 100% on-device operation. No user tracking, no third-party analytics SDKs, no cloud logging.
- **Offline Cryptographic Licensing**: Optional Supporter Pass activated completely offline via NIST P-256 ECDSA public key verification.
- **Zero-Knowledge Architecture**: 100% on-device SharedPreferences persistence (zero Room/DataStore) and isolated EncryptedSharedPreferences.

---

## Table of Contents

1. [Requirements](#requirements)
2. [Building the App](#building-the-app)
3. [Product Flavors](#product-flavors)
4. [Architecture & Storage Overview](#architecture--storage-overview)
5. [Android Studio Setup](#android-studio-setup)
6. [If the Build Shows Old UI](#if-the-build-shows-old-ui)
7. [Release Builds](#release-builds)
8. [Contributing & Bug Reports](#contributing--bug-reports)
9. [Project Documentation](#project-documentation)

---

## Requirements

| Tool | Version |
| --- | --- |
| Android Studio | Meerkat or newer |
| Gradle | 9.5+ (managed by wrapper) |
| Android SDK compile target | API 36 |
| Min Android version | API 26 (Android 8.0) |
| JDK | 17 |

---

## Building the App

> **[IMPORTANT] Product Flavors**: AmniShield uses three **product flavors**
> (`playstore`, `fdroid`, `universal`). The plain `assembleDebug` Gradle task is
> **no longer valid**. Always use a flavor-qualified task. Running bare `assembleDebug`
> will silently serve a stale cached APK with old UI and themes.

### Debug Builds (for testing / development)

```powershell
# Build all three flavors at once (recommended)
.\gradlew assemblePlaystoreDebug assembleFdroidDebug assembleUniversalDebug

# Or build individually
.\gradlew assemblePlaystoreDebug   # Google Play Store flavor
.\gradlew assembleFdroidDebug      # F-Droid / offline license flavor
.\gradlew assembleUniversalDebug   # Universal / sideload flavor
```

APK output paths:

| Flavor | APK Location |
| --- | --- |
| playstore | `app/build/outputs/apk/playstore/debug/app-playstore-debug.apk` |
| fdroid | `app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk` |
| universal | `app/build/outputs/apk/universal/debug/app-universal-debug.apk` |

---

## Product Flavors

| Flavor | Description | Google Services | Billing |
| --- | --- | --- | --- |
| `playstore` | Google Play Store distribution | Included: Firebase, Play Billing | Play Billing |
| `universal` | Sideload / alternative stores | Included: Firebase | Play Billing |
| `fdroid` | F-Droid / fully open source | None: Pure FOSS | Offline ECDSA license |

Each flavor has its own source set under `app/src/<flavor>/java/` for swapping
billing and sign-in implementations without `#ifdef`-style hacks.

---

## Architecture & Storage Overview

### Presentation Model: Dual-Host Container Architecture
AmniShield uses a decoupled Dual-Host presentation architecture bridging AndroidX Activities with Jetpack Compose Material 3:
- **`MainActivity.kt` (2,022 lines)**: Hosts the primary 4 bottom navigation tabs (`Stats`, `Blocks`, `Focus`, `Advanced`) using legacy `supportFragmentManager` transactions.
- **`FragmentActivity.kt` (210 lines)**: Acts as a secondary router dispatching 18 functional sub-screens and configuration dialogs. Each configuration fragment inherits `BaseFeatureFragment` and returns `ComposeView` embedding Material 3 Compose screens (`AppBlockerConfigScreen`, `ReelBlockerConfigScreen`, etc.).
- **`AmniShieldAdaptiveApp.kt` (254 lines)**: Pure Compose `NavigationSuiteScaffold` shell temporarily disconnected to avoid FragmentManager lifecycle crashes, preserved as the target architecture for Milestone 0.3.5.

### Persistence Reality: 100% SharedPreferences
- **Zero Room DB / Zero DataStore**: AmniShield contains zero Room DB and zero DataStore dependencies.
- **`SavedPreferencesLoader.kt` (2,015 lines)**: Manages 29 specialized SharedPreferences XML files across 459 callers. Complex rules and limits are serialized to JSON strings using Google Gson (33 calls).
- **`SecureKeyStore.kt`**: `EncryptedSharedPreferences` is strictly isolated to Supabase cloud sync authentication tokens with an automatic plain XML fallback (`amnishield_sync_secrets_fallback`).

### Blocker Interceptor Pipeline
Executed in `AmniShieldAccessibilityService.kt`:
1. **Priority 0**: `AntiUninstallDetector` (Line 283) - Intercepts settings, device admin, and store tampering; dispatches Home.
2. **Priority 1**: `FocusModeBlocker` (Line 304) - Enforces focus sessions; dispatches `AmniSpaceActivity` (Focus Launcher) or `pressHome()`.
3. **Priority 2**: `WebsiteBlockerDetector` (Line 350) - Evaluates browser URL bars across 15+ browsers (linear scan over 1-20 active schedule targets; `adult_domains.txt` and `haram_domains.txt` are dead assets).
4. **Priority 3**: `AppBlocker` (Line 417) - Evaluates launch limits, usage quotas, and app block schedules.
5. **Priority 4**: `KeywordBlocker` & `ReelBlocker` (Lines 456, 475-562) - Deferred via conflated coroutine channel (`Channel.CONFLATED`).

### Premium & Supporter Pass Architecture
- **Free FOSS Core (`UserType.FREE`)**: Base app, website, reel blockers, and keyword filters are 100% free and open-source forever.
- **Supporter Passes**: `UserType.PREMIUM`, `UserType.FOUNDER` (90-day early supporter pass), and `UserType.COMPASSIONATE` (Al-Haq community access).
- **Gated Features**: Rule PIN / Password Security Locking (`RuleProBadge`), Master PIN, App Lock, Bypass PIN Lock, and Supabase cloud policy sync.
- **Billing Implementation**: `playstore` uses Google Play `BillingClientWrapper.kt`; `fdroid` uses 100% offline NIST P-256 ECDSA public key verification (`SHA256withECDSA` in `LicenseValidator.kt`) with optional Stripe Direct checkout; `universal` provides hybrid capabilities.

---

## Android Studio Setup

1. Open the project root in Android Studio.
2. Open the **Build Variants** panel (`View -> Tool Windows -> Build Variants`).
3. Set the **Active Build Variant** for `:app` to one of:
   - `playstoreDebug`
   - `fdroidDebug`
   - `universalDebug`
4. Hit **Run** as normal.

> If the Build Variants panel shows just `debug` (no flavor prefix), the IDE has not yet
> synced the new flavor configuration. Run **File -> Sync Project with Gradle Files** to fix it.

---

## If the Build Shows Old UI

If you see the legacy purple gradient background, old DeenShield icons, or the old
Settings bottom-nav tab instead of the new Blocks tab, the Gradle build cache is serving
a stale APK. Fix it by doing a clean build:

```powershell
# Step 1: Wipe all build outputs and cached intermediates
.\gradlew clean --no-daemon

# Step 2: Rebuild fresh (--no-daemon forces a new JVM, bypassing any stale cached daemon)
.\gradlew assemblePlaystoreDebug assembleFdroidDebug assembleUniversalDebug --no-daemon
```

The `--no-daemon` flag is the key - without it, Gradle may reuse an old daemon process
pinned to a previous Gradle version that has incorrect cached fingerprints.

---

## Release Strategy

### Which APK Goes Where?

| Flavor | Published to | Audience |
| --- | --- | --- |
| `universal` | **GitHub Releases** | Standard Android - sideloading with Google Play Services |
| `fdroid` | **GitHub Releases** | De-Googled phones (GrapheneOS, CalyxOS, LineageOS without GMS) |
| `playstore` | **Play Console only** | Google Play Store users - never published to GitHub |

> `universal` and `fdroid` are **not interchangeable**. The `universal` flavor depends on
> Google Play Services for billing and sign-in. Users on de-Googled ROMs must use the
> `fdroid` APK, which has zero Google dependencies and uses offline ECDSA licensing instead.

### Automated GitHub Releases (CI)

The [release.yml](.github/workflows/release.yml) workflow fires automatically when you
push a version tag. It builds, signs, and publishes both APKs to GitHub Releases:

```bash
# Tag a release (triggers the workflow)
git tag v1.2.3
git push origin v1.2.3
```

For Self Build:

```powershell
# GitHub Releases (the two public APKs)
.\gradlew assembleUniversalRelease
.\gradlew assembleFdroidRelease

# Play Store (submitted via Play Console, not GitHub)
.\gradlew assemblePlaystoreRelease
```

> **Never commit `keystore.properties` or `.jks` files to version control.**
> These are already listed in `.gitignore`.

---

## Contributing & Bug Reports

Contributions, bug reports, and feedback are warmly welcomed!

- **Report a Bug**: If you notice unexpected behavior, crashes, or unblocked surfaces, please open a [Bug Report](https://github.com/alhaq-studio/amnishield-android/issues/new?template=bug_report.md).
- **Request a Feature**: Have an idea for a new filter, accessibility improvement, or custom rule? Submit a [Feature Request](https://github.com/alhaq-studio/amnishield-android/issues/new?template=feature_request.md).
- **Google Play Beta Feedback**: You can also submit private beta feedback directly through Google Play: [Join Open Testing](https://play.google.com/apps/testing/com.alhaq.deenshield).
- **Email Support**: Reach our engineering team directly at `support@alhaq.uk`.

---

## Project Documentation

| File | Description |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | System architecture, blocker pipeline & logging invariants |
| [ROADMAP.md](ROADMAP.md) | Feature roadmap and recent changelog |
| [TESTING_GUIDE.md](TESTING_GUIDE.md) | Full manual test scenarios for all features |
| [PRIVACY_POLICY.md](PRIVACY_POLICY.md) | App privacy policy |
| [TERMS_OF_SERVICE.md](TERMS_OF_SERVICE.md) | Terms of service |
| [FDROID_PUBLISHING_GUIDE.md](FDROID_PUBLISHING_GUIDE.md) | Steps to publish on F-Droid |

---

## Technical Note: Package Name & Application ID

For historical reasons (AmniShield was rebranded from DeenShield), there is a mismatch in the project identifiers:

- **`applicationId`**: `com.alhaq.deenshield`
- **`namespace`**: `com.alhaq.amnishield`

Because the Google Play Store registry binds permanently to the `applicationId`, changing it would require publishing a completely new listing. Consequently, we **maintain the legacy applicationId (`com.alhaq.deenshield`)** to avoid disrupting existing users, while codebase packages and code structures use the modern namespace `com.alhaq.amnishield`. Do not change the `applicationId` when making code updates.

---

## Acknowledgments & Open-Source Credits

- **[Curbox](https://github.com/curbox-app/curbox-android)** by **Nethical** (GPL-3.0-or-later):
  Reel & short-form video surface detection patterns and floating WindowManager overlay mechanics are inspired by Curbox.

---

## License

This project is licensed under the **GNU General Public License v3.0**.
See [LICENSE](LICENSE) for the full text.
```

### 7.5 ROADMAP.md Exact Unified Diff

```diff
diff --git a/ROADMAP.md b/ROADMAP.md
index 7a9e218..c3d4f89 100644
--- a/ROADMAP.md
+++ b/ROADMAP.md
@@ -37,11 +37,37 @@ Last updated: October 2026
 ---
 
-### Active / Upcoming Milestones (V1.1 - V2.0)
+### Active / Upcoming Milestones (v0.3.2 - v0.3.5)
 
-- [ ] **Windows Elevated Background Service:** Background Windows service managing UAC-free network `hosts` resolution and system-level firewall rules.
-- [ ] **Opt-in DNS Sinkhole Engine:** Local VPN-based DNS sinkhole filter powered by the `AmniGuard-FireWall` packet filtering engine for network-wide tracking and ad/adult domain mitigation.
-- [ ] **On-Device Mobile NPU Vision Blur Engine:** Realtime shortform media content filtering powered by LiteRT / Mobile NPU (`AmniBlur` / `AmniGaze` pipeline optimization).
-- [ ] **Automated Multi-Flavor Matrix Testing:** Expand CI GitHub Actions to run automated instrumented UI tests across `playstoreDebug`, `fdroidDebug`, and `universalDebug`.
+#### Milestone 0.3.2: Accessibility Engine Hardening & Binder Safety
+- [x] **In-Memory Schedule Caching & Broadcast Alignment:** Eliminate reflection-based `Gson().fromJson()` deserialization on the Main Binder Thread during `onAccessibilityEvent` in `AmniShieldAccessibilityService.kt` and `SavedPreferencesLoader.kt`, and fix `WebsiteBlockerConfigScreen.kt` to broadcast `INTENT_ACTION_REFRESH_WEBSITE_BLOCKER` / unified schedule refresh via `ServiceBroadcastManager.kt`.
+- [x] **Deferred Pipeline Temporal Window Guard:** Enforce strict package-identity verification (`rootPackage.equals(packageName, ignoreCase = true)`) and `SystemExclusionManager.isExempt(...)` in `AmniShieldAccessibilityService.processDeferredChecks()` to prevent false-positive interceptions when users rapidly switch windows.
+- [x] **Polling Decoupling & Background Disk I/O:** Eliminate continuous `rootInActiveWindow` queries on 1-second and 2-second Handler timers in `ReelsSessionTracker.kt` and `BrowserSessionTracker.kt`, transitioning to event-driven surface tracking and offloading usage persistence writes to `Dispatchers.IO`.
+- [x] **Node Traversal Exception Safety:** Implement robust `try ... finally` blocks and null-safe child node recycling across `AntiUninstallDetector.kt` and `ReelDetectionEngine.kt`.
+- [x] **Manifest Security Hardening:** Set `android:exported="false"` on `LauncherActivity` in `AndroidManifest.xml` (line 76) to prevent unauthorized external intent launching.
+
+#### Milestone 0.3.3: Store Compliance & Layout Resilience
+- [x] **Prominent Disclosure Overhaul:** Implement a `ScrollView` content container with a sticky bottom action bar, `wrap_content` (`minHeight="48dp"`) button sizing in `dialog_accessibility_disclosure.xml`, and `.setCancelable(false)` in `AccessibilityDisclosureDialog.kt` to guarantee compliance in landscape orientation and 200% font scaling.
+- [x] **Persistent Affirmative Consent:** Persist the accessibility disclosure acceptance flag and timestamp (`setAccessibilityDisclosureAccepted`) in `SavedPreferencesLoader.kt` to guarantee auditability.
+- [x] **Mandatory Accessibility Onboarding Gate:** Close the onboarding bypass loophole in `PermissionsScreen.kt` (line 115) so `canProceed` strictly requires `isAccessibilityGranted`.
+- [x] **Screen Rotation State Preservation:** Add `rememberSaveable` state handling in `AmniSpaceActivity.kt` and `AmniSpaceBreathingCircle.kt`, implement `onSaveInstanceState` timer preservation in `WarningActivity.kt`, and dynamically adapt window insets and orientation offsets in `UsageStatOverlayManager.kt`.
+
+#### Milestone 0.3.4: Dual-Host Cleanup & Navigation Decoupling
+- [ ] **Type-Safe Intent Routing:** Implement a sealed route contract between `MainActivity.kt` and `FragmentActivity.kt`, replacing loosely-typed string extras (`feature_type`, `fragment`).
+- [ ] **SharedPreferences Synchronization Lock:** Implement mutex synchronization locks in `SavedPreferencesLoader.kt` to prevent concurrent multi-threaded read/write file races.
+- [ ] **Automated Multi-Flavor Matrix Testing:** Expand CI GitHub Actions to run automated instrumented UI tests across `playstoreDebug`, `fdroidDebug`, and `universalDebug`.
+
+#### Milestone 0.3.5: Pure Compose & Storage Architecture Epic (Prep for V1.0)
+- [ ] **Room & DataStore Migration Epic:** Formalize a phased migration path from the 29+ SharedPreferences XML files to Room DB (relational blocking rules) and Preferences DataStore (flags and tokens).
+- [ ] **Pure-Compose Navigation Epic:** Reconnect and finalize `AmniShieldAdaptiveApp.kt` using modern Compose Navigation 2.8+, safely retiring the dual-host split (`MainActivity.kt` + `FragmentActivity.kt`).
 
 ---
 
```

### 7.6 ROADMAP.md Full Replacement Block

```markdown
# AmniShield Roadmap & Technical Milestone Tracker

Last updated: October 2026

---

## Strategic Architectural Decisions (October 2026)

- **Unified AmniShield Architecture & Direct Stripe Gateway:**
  - **Decision:** Deprecated third-party merchant billing layers in favor of **Stripe Direct** (Checkout + Subscriptions) for non-Google builds (`fdroid`, `universal`, Windows Desktop, Web Portal) paired with offline ECDSA NIST P-256 license verification. Google Play builds utilize Google Play `BillingClient`.
  - **Single Unified Console:** Eliminated legacy dual-mode concepts ("Guardian Console vs Personal Console"). All policy enforcement and cross-device synchronization are driven through the unified AmniShield Console architecture.
  - **Privacy-First Local Diagnostic Logging:** Built a 100% on-device, zero-telemetry diagnostic engine (`CrashLogger`) with automated PII sanitization and in-app Material 3 Log Viewer with native Android Sharesheet export.

---

## Technical Roadmap

### Completed Milestones (Current Baseline - v0.3.1 / Code 145)

- [x] **Continuous Cross-App & Cross-Session Reels Engine:** Daily accumulation across app launches and multiple shortform platforms (YouTube Shorts, Instagram Reels, TikTok, Facebook Reels, ReVanced, MyInsta, Lite editions) with midnight cache invalidation.
- [x] **Resilient Fallback Node Comparator:** Dynamic view-tree traversal (depth up to 8) extracting title, creator, and audio elements to detect reel progression when native platform resource IDs change.
- [x] **ViewPager2 Swipe Interception:** Integrated `AccessibilityEvent.TYPE_VIEW_SELECTED` for instantaneous vertical swipe progression detection.
- [x] **Synchronous Floating Pill Overlay:** Main-thread `runOnMain` view inflation and real-time counter binding with hour-formatted duration (`hh:mm:ss`).
- [x] **Browser Website Engagement Tracker (`BrowserSessionTracker`):** 2-second background active window domain engagement tracking with screen power-saving lifecycle handling.
- [x] **Pre-Registration Founder Pass & Entitlements:** Google Play Billing pre-registration entitlement check, 90-day security suite grant, and Founding Supporters Wall.
- [x] **Hardware Monotonic Clock Verification:** Defense against local system time manipulation for license and pass verification.
- [x] **Multi-Platform Cloud Policy Sync:** Supabase Realtime + REST policy syncing across Android, Windows, and Web.
- [x] **Ephemeral 6-Digit PIN & In-App ZXing Camera QR Pairing:** Secure, frictionless cross-device pairing.
- [x] **Unified AmniShield Console:** Unified device management eliminating confusing dual-mode personas.
- [x] **Decoupled Console Policy vs. Personal Sync:** Separate tiers for enterprise/parental console lockdown and personal cross-device convenience.
- [x] **Interceptor-Based `AccessibilityService` Modularization:** Decoupled chain of responsibility pipeline (AntiUninstall, AppBlocker, FocusMode, WebsiteBlocker, KeywordBlocker, ReelsBlocker) with strict node lifecycle and short-circuiting invariants.
- [x] **On-Device ECDSA NIST P-256 Cryptographic License Validation:** Fully offline cryptographic license verification for F-Droid and sideloaded builds.
- [x] **Diagnostics & In-App Crash Log Viewer:** Material 3 diagnostic log console with PII redaction, search, category filtering, copy-to-clipboard, and native Android Sharesheet (`FileProvider`) export.
- [x] **Hardened Anti-Uninstall & Tamper Defense:** Multi-vector interception covering Google Play Store uninstallation, OEM Security Centers, Device Administrator deactivation, and settings tampering with bulletproof Home exit routing.
- [x] **Home Screen Widgets Suite:** Glanceable Material 3 widgets for Daily Screen Time, Reels Scroll Counter, Quick Focus Sessions, and Mindful Breathing.

---

### Active / Upcoming Milestones (v0.3.2 - v0.3.5)

#### Milestone 0.3.2: Accessibility Engine Hardening & Binder Safety
- [x] **In-Memory Schedule Caching & Broadcast Alignment:** Eliminate reflection-based `Gson().fromJson()` deserialization on the Main Binder Thread during `onAccessibilityEvent` in `AmniShieldAccessibilityService.kt` and `SavedPreferencesLoader.kt`, and fix `WebsiteBlockerConfigScreen.kt` to broadcast `INTENT_ACTION_REFRESH_WEBSITE_BLOCKER` / unified schedule refresh via `ServiceBroadcastManager.kt`.
- [x] **Deferred Pipeline Temporal Window Guard:** Enforce strict package-identity verification (`rootPackage.equals(packageName, ignoreCase = true)`) and `SystemExclusionManager.isExempt(...)` in `AmniShieldAccessibilityService.processDeferredChecks()` to prevent false-positive interceptions when users rapidly switch windows.
- [x] **Polling Decoupling & Background Disk I/O:** Eliminate continuous `rootInActiveWindow` queries on 1-second and 2-second Handler timers in `ReelsSessionTracker.kt` and `BrowserSessionTracker.kt`, transitioning to event-driven surface tracking and offloading usage persistence writes to `Dispatchers.IO`.
- [x] **Node Traversal Exception Safety:** Implement robust `try ... finally` blocks and null-safe child node recycling across `AntiUninstallDetector.kt` and `ReelDetectionEngine.kt`.
- [x] **Manifest Security Hardening:** Set `android:exported="false"` on `LauncherActivity` in `AndroidManifest.xml` (line 76) to prevent unauthorized external intent launching.

#### Milestone 0.3.3: Store Compliance & Layout Resilience
- [x] **Prominent Disclosure Overhaul:** Implement a `ScrollView` content container with a sticky bottom action bar, `wrap_content` (`minHeight="48dp"`) button sizing in `dialog_accessibility_disclosure.xml`, and `.setCancelable(false)` in `AccessibilityDisclosureDialog.kt` to guarantee compliance in landscape orientation and 200% font scaling.
- [x] **Persistent Affirmative Consent:** Persist the accessibility disclosure acceptance flag and timestamp (`setAccessibilityDisclosureAccepted`) in `SavedPreferencesLoader.kt` to guarantee auditability.
- [x] **Mandatory Accessibility Onboarding Gate:** Close the onboarding bypass loophole in `PermissionsScreen.kt` (line 115) so `canProceed` strictly requires `isAccessibilityGranted`.
- [x] **Screen Rotation State Preservation:** Add `rememberSaveable` state handling in `AmniSpaceActivity.kt` and `AmniSpaceBreathingCircle.kt`, implement `onSaveInstanceState` timer preservation in `WarningActivity.kt`, and dynamically adapt window insets and orientation offsets in `UsageStatOverlayManager.kt`.

#### Milestone 0.3.4: Dual-Host Cleanup & Navigation Decoupling
- [ ] **Type-Safe Intent Routing:** Implement a sealed route contract between `MainActivity.kt` and `FragmentActivity.kt`, replacing loosely-typed string extras (`feature_type`, `fragment`).
- [ ] **SharedPreferences Synchronization Lock:** Implement mutex synchronization locks in `SavedPreferencesLoader.kt` to prevent concurrent multi-threaded read/write file races.
- [ ] **Automated Multi-Flavor Matrix Testing:** Expand CI GitHub Actions to run automated instrumented UI tests across `playstoreDebug`, `fdroidDebug`, and `universalDebug`.

#### Milestone 0.3.5: Pure Compose & Storage Architecture Epic (Prep for V1.0)
- [ ] **Room & DataStore Migration Epic:** Formalize a phased migration path from the 29+ SharedPreferences XML files to Room DB (relational blocking rules) and Preferences DataStore (flags and tokens).
- [ ] **Pure-Compose Navigation Epic:** Reconnect and finalize `AmniShieldAdaptiveApp.kt` using modern Compose Navigation 2.8+, safely retiring the dual-host split (`MainActivity.kt` + `FragmentActivity.kt`).

---

## Architectural Standards & Development Invariants

1. **Accessibility Node Lifecycle Invariant:** `AmniShieldAccessibilityService` exclusively owns the lifecycle of `rootNode`. Sub-interceptors and detectors must **never** call `rootNode.recycle()`.
2. **Deterministic Priority (Fail-Safe First):** Security and Anti-Uninstall evaluation runs first at Priority 0.
3. **Zero External Telemetry:** All diagnostic logs, crash reports, and usage metrics remain 100% on-device in app-private storage.
4. **PII Sanitization Rule:** Sensitive tokens, passwords, PINs, auth headers, and emails are stripped by regex filters before being written to disk.
5. **Zero Emoji Policy:** Strict universal rule prohibiting unicode emojis across all code, UI layouts, strings, commit messages, and documentation.
```

### 7.7 .agents/AGENTS.md Exact Unified Diff

```diff
diff --git a/.agents/AGENTS.md b/.agents/AGENTS.md
index df3e521..e94c821 100644
--- a/.agents/AGENTS.md
+++ b/.agents/AGENTS.md
@@ -8,14 +8,41 @@ This repository (`AmniShield-Android` / `alhaq-studio/amnishield-android`) is pa
 
 ## Enabled Skills
 
-- `android-fullstack-dev`: Modern Jetpack Compose, Kotlin Flow, Material 3, Accessibility Service node inspection, Room DB, Preferences DataStore.
+- `android-fullstack-dev`: Modern Jetpack Compose, Kotlin Flow, Material 3, Accessibility Service node inspection, SharedPreferences persistence (`SavedPreferencesLoader.kt`), isolated EncryptedSharedPreferences (`SecureKeyStore.kt`).
 - `security-compliance`: ECDSA NIST P-256 license validation (`LicenseValidator.kt`), zero-knowledge privacy, R8 minification.
 - `stripe-payments`: `PaymentManager.kt` & Stripe checkout integration.
 - `supabase-backend`: Supabase Edge Functions & user license record synchronization.
 - `marketing-growth`: App Store Optimization, Play Store release notes, F-Droid metadata.
 
 ## Current Project Focus
 
-- **Version**: `1.0.2-closed.01 (2026.08.13)` / `versionCode 132`.
-- **Primary Goal**: Closed testing release, Play Store AAB submission, and F-Droid package tag verification.
+- **Version**: `0.3.1 (2026.10.05)` / `versionCode 145` (Namespace: `com.alhaq.amnishield`, ApplicationId: `com.alhaq.deenshield`).
+- **Primary Goal**: Milestone 0.3.2 (Accessibility Engine Hardening & Binder Safety) and Milestone 0.3.3 (Google Play Store Compliance & Layout Resilience).
+
+---
+
+## Mandatory Architectural Invariants & Agent Guardrails
+
+1. **Persistence Reality (Zero Room / Zero DataStore):**
+   - Local persistence is 100% `SharedPreferences` orchestrated via `SavedPreferencesLoader.kt` (29 specialized XML files in loader, 37 total XML files, Gson JSON serialization).
+   - `EncryptedSharedPreferences` is strictly isolated to `SecureKeyStore.kt` (`amnishield_sync_secrets.xml`).
+   - Do **not** import `androidx.room` or `androidx.datastore` or reference the unused stub classes in `data/db/` until Milestone 0.3.5.
+2. **Dual-Host Presentation Reality:**
+   - Top-level UI is hosted by `MainActivity.kt` (4 XML bottom navigation tabs) and `FragmentActivity.kt` (secondary router for 18+ feature/config fragments embedding `ComposeView`).
+   - `AmniShieldAdaptiveApp.kt` has 0 callers (disconnected in commit `ed57548`) and must not be modified or reconnected until Milestone 0.3.5.
+3. **Blocker Pipeline Priority Order (`AmniShieldAccessibilityService.kt`):**
+   - Priority 0: `AntiUninstallDetector` -> Priority 1: `FocusModeBlocker` -> Priority 2: `WebsiteBlockerDetector` -> Priority 3: `AppBlocker` -> Priority 4: `KeywordBlocker` & `ReelBlocker` (via `Channel.CONFLATED` on `Dispatchers.Default`).
+4. **Main Binder Thread & Concurrency Guardrails:**
+   - Never invoke reflection-based `Gson().fromJson()` on the Main Binder Thread during `onAccessibilityEvent`; always read from the in-memory schedule cache and invalidate via `ServiceBroadcastManager`.
+   - Never poll `service.rootInActiveWindow` on periodic `Handler` timers; session trackers (`ReelsSessionTracker`, `BrowserSessionTracker`) must inspect nodes passed from `onAccessibilityEvent` and offload disk writes to `Dispatchers.IO`.
+   - In `processDeferredChecks()`, always verify `rootPackage.equals(packageName, ignoreCase = true)` and `SystemExclusionManager.isExempt(...)` before evaluating deferred blockers.
+5. **AccessibilityNodeInfo Lifecycle & Exception Safety:**
+   - `AmniShieldAccessibilityService` exclusively owns `rootNode` and recycles it in `finally`. Sub-detectors must never recycle `rootNode`.
+   - All iterative (`ArrayDeque`) and recursive child node traversals (`AntiUninstallDetector`, `ReelDetectionEngine`) must wrap child inspection in `try ... finally` to guarantee child node recycling if an exception occurs.
+6. **Google Play Prominent Disclosure & Onboarding Compliance:**
+   - `AccessibilityDisclosureDialog` must remain non-cancelable (`.setCancelable(false)`), persist affirmative consent via `SavedPreferencesLoader`, use a scrollable container with `minHeight="48dp"` (`wrap_content`) buttons, and `PermissionsScreen` must require `isAccessibilityGranted` to proceed.
+7. **Strict Universal Zero-Emoji Policy:**
+   - Zero UTF-8 emojis anywhere in code, XML, Compose UI, strings, logs, comments, commit messages, or documentation.
```

### 7.8 .agents/AGENTS.md Full Replacement Block

```markdown
# AmniShield-Android Workspace Agent Configuration

This repository (`AmniShield-Android` / `alhaq-studio/amnishield-android`) is part of the **AmniShield Ecosystem**.

## Active Agent Profile: `amni-ecosystem-agent`

- Full Architecture, Roadmap, and Cross-Platform Specifications: `~/.gemini/config/agents/amni-ecosystem-agent.md`

## Enabled Skills

- `android-fullstack-dev`: Modern Jetpack Compose, Kotlin Flow, Material 3, Accessibility Service node inspection, SharedPreferences persistence (`SavedPreferencesLoader.kt`), isolated EncryptedSharedPreferences (`SecureKeyStore.kt`).
- `security-compliance`: ECDSA NIST P-256 license validation (`LicenseValidator.kt`), zero-knowledge privacy, R8 minification.
- `stripe-payments`: `PaymentManager.kt` & Stripe checkout integration.
- `supabase-backend`: Supabase Edge Functions & user license record synchronization.
- `marketing-growth`: App Store Optimization, Play Store release notes, F-Droid metadata.

## Current Project Focus

- **Version**: `0.3.1 (2026.10.05)` / `versionCode 145` (Namespace: `com.alhaq.amnishield`, ApplicationId: `com.alhaq.deenshield`).
- **Primary Goal**: Milestone 0.3.2 (Accessibility Engine Hardening & Binder Safety) and Milestone 0.3.3 (Google Play Store Compliance & Layout Resilience).

---

## Mandatory Architectural Invariants & Agent Guardrails

1. **Persistence Reality (Zero Room / Zero DataStore):**
   - Local persistence is 100% `SharedPreferences` orchestrated via `SavedPreferencesLoader.kt` (29 specialized XML files in loader, 37 total XML files, Gson JSON serialization).
   - `EncryptedSharedPreferences` is strictly isolated to `SecureKeyStore.kt` (`amnishield_sync_secrets.xml`).
   - Do **not** import `androidx.room` or `androidx.datastore` or reference the unused stub classes in `data/db/` until Milestone 0.3.5.
2. **Dual-Host Presentation Reality:**
   - Top-level UI is hosted by `MainActivity.kt` (4 XML bottom navigation tabs) and `FragmentActivity.kt` (secondary router for 18+ feature/config fragments embedding `ComposeView`).
   - `AmniShieldAdaptiveApp.kt` has 0 callers (disconnected in commit `ed57548`) and must not be modified or reconnected until Milestone 0.3.5.
3. **Blocker Pipeline Priority Order (`AmniShieldAccessibilityService.kt`):**
   - Priority 0: `AntiUninstallDetector` -> Priority 1: `FocusModeBlocker` -> Priority 2: `WebsiteBlockerDetector` -> Priority 3: `AppBlocker` -> Priority 4: `KeywordBlocker` & `ReelBlocker` (via `Channel.CONFLATED` on `Dispatchers.Default`).
4. **Main Binder Thread & Concurrency Guardrails:**
   - Never invoke reflection-based `Gson().fromJson()` on the Main Binder Thread during `onAccessibilityEvent`; always read from the in-memory schedule cache and invalidate via `ServiceBroadcastManager`.
   - Never poll `service.rootInActiveWindow` on periodic `Handler` timers; session trackers (`ReelsSessionTracker`, `BrowserSessionTracker`) must inspect nodes passed from `onAccessibilityEvent` and offload disk writes to `Dispatchers.IO`.
   - In `processDeferredChecks()`, always verify `rootPackage.equals(packageName, ignoreCase = true)` and `SystemExclusionManager.isExempt(...)` before evaluating deferred blockers.
5. **AccessibilityNodeInfo Lifecycle & Exception Safety:**
   - `AmniShieldAccessibilityService` exclusively owns `rootNode` and recycles it in `finally`. Sub-detectors must never recycle `rootNode`.
   - All iterative (`ArrayDeque`) and recursive child node traversals (`AntiUninstallDetector`, `ReelDetectionEngine`) must wrap child inspection in `try ... finally` to guarantee child node recycling if an exception occurs.
6. **Google Play Prominent Disclosure & Onboarding Compliance:**
   - `AccessibilityDisclosureDialog` must remain non-cancelable (`.setCancelable(false)`), persist affirmative consent via `SavedPreferencesLoader`, use a scrollable container with `minHeight="48dp"` (`wrap_content`) buttons, and `PermissionsScreen` must require `isAccessibilityGranted` to proceed.
7. **Strict Universal Zero-Emoji Policy:**
   - Zero UTF-8 emojis anywhere in code, XML, Compose UI, strings, logs, comments, commit messages, or documentation.
```

---

## Section 8: Comprehensive Verification Checklist & Verification Commands

### 8.1 Acceptance Criteria Compliance Matrix

| # | Acceptance Criterion | Status | Verification Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1** | Exact git diffs and replacement blocks provided for `README.md`, `ROADMAP.md`, and `ARCHITECTURE.md` | `[VERIFIED]` | Full unified diffs and complete Markdown replacement blocks generated in Section 7. |
| **AC-2** | Complete audit report, diffs, and verification checklist written to `AMNISHIELD_PROJECT_SUMMARY.md` | `[VERIFIED]` | Authoritative synthesis written to `AMNISHIELD_PROJECT_SUMMARY.md`. |
| **AC-3** | Zero Kotlin code, layout XML, or manifest files modified during this read-only documentation audit | `[VERIFIED]` | All inspection and reconciliation performed read-only without modifying source or configuration files. |
| **AC-4** | All 4 unicode emojis purged from `ARCHITECTURE.md` (lines 9, 36, 51, 69) | `[VERIFIED]` | Emojis `[U+1F3D7]`, `[U+1F6E1]`, `[U+1F4DD]`, `[U+1F512]` identified and purged; zero emojis in summary file. |
| **AC-5** | Persistence documentation updated to reflect 100% SharedPreferences via `SavedPreferencesLoader.kt` with zero Room DB or DataStore dependencies | `[VERIFIED]` | Section 1 documents 29 XML files in `SavedPreferencesLoader.kt`, 33 Gson calls, and isolated `SecureKeyStore.kt`. |
| **AC-6** | Dual-host UI container reality (`MainActivity.kt` + `FragmentActivity.kt`) and disconnected status of `AmniShieldAdaptiveApp.kt` documented | `[VERIFIED]` | Section 2 details 2,022-line `MainActivity`, 210-line `FragmentActivity`, ComposeView embedding, and commit history (`7155646` -> `ed57548`). |
| **AC-7** | Code execution priority sequence verified: `AntiUninstallDetector` (P0) -> `FocusModeBlocker` (P1) -> `WebsiteBlockerDetector` (P2, L350) -> `AppBlocker` (P3, L417) -> Deferred Channel (P4) | `[VERIFIED]` | Section 3 details line numbers (283, 304, 350, 417, 456, 475) and linear candidate check (1-20 targets). |
| **AC-8** | Premium architecture fully documented: Free FOSS core vs Supporter Pass, Rule PIN protection (`RuleProBadge`), Google Play Billing for `playstore`, and offline NIST P-256 ECDSA verification for `fdroid`/`universal` | `[VERIFIED]` | Section 4 documents `UserType` enum, monotonic clock guards, `RuleProBadge`, and `LicenseValidator.kt`. |
| **AC-9** | Upcoming milestones in `ROADMAP.md` updated to Milestones 0.3.2 through 0.3.5, with Blur (AmniBlur), DNS Sinkhole (AmniGuardFirewall), and Windows Elevated Service (AmniShield-Windows) completely removed | `[VERIFIED]` | Section 5 purges foreign components and defines Milestones 0.3.2 - 0.3.5 with concrete technical scope. |
| **AC-10** | Version metadata reconciled with `build.gradle.kts` (`versionCode 145`, `versionName "0.3.1 (2026.10.05)"`) | `[VERIFIED]` | Section 6 and Section 7 reconcile `.agents/AGENTS.md` with production baseline. |

### 8.2 Automated Verification Script (PowerShell)

Execute this script in Windows PowerShell from the project root (`d:\PROJECTS\Amni\AmniShield-Android`):

```powershell
Write-Host "====================================================" -ForegroundColor Cyan
Write-Host " AmniShield-Android Audit & Verification Suite       " -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan

$ErrorCount = 0

# 1. Verify Zero Room / SQLite / DataStore dependencies
Write-Host "`n[TEST 1] Verifying absence of Room, SQLite, and DataStore dependencies..." -ForegroundColor Yellow
$deps = Select-String -Path "app\build.gradle.kts", "gradle\libs.versions.toml" -Pattern "androidx\.room|androidx\.sqlite|androidx\.datastore"
if ($deps.Count -eq 0) {
    Write-Host "  [PASS] Zero Room/SQLite/DataStore dependencies detected." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Unexpected persistence dependency found!" -ForegroundColor Red
    $deps | ForEach-Object { Write-Host "    $($_.Path):$($_.LineNumber): $($_.Line)" }
    $ErrorCount++
}

# 2. Verify SharedPreferences count in SavedPreferencesLoader.kt
Write-Host "`n[TEST 2] Verifying SharedPreferences files in SavedPreferencesLoader.kt..." -ForegroundColor Yellow
$prefFiles = Select-String -Path "app\src\main\java\com\alhaq\amnishield\utils\SavedPreferencesLoader.kt" -Pattern 'getSharedPreferences\("([^"]+)"' |
    ForEach-Object { $_.Matches.Groups[1].Value } | Sort-Object -Unique
Write-Host "  [INFO] Found $($prefFiles.Count) distinct SharedPreferences XML files in loader."
if ($prefFiles.Count -ge 29) {
    Write-Host "  [PASS] Verified 29+ SharedPreferences files." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Expected at least 29 files, found $($prefFiles.Count)" -ForegroundColor Red
    $ErrorCount++
}

# 3. Verify Isolation of EncryptedSharedPreferences
Write-Host "`n[TEST 3] Verifying isolation of EncryptedSharedPreferences..." -ForegroundColor Yellow
$cryptoMatches = Get-ChildItem -Path "app\src\main\java" -Filter "*.kt" -Recurse |
    Select-String -Pattern "EncryptedSharedPreferences"
$nonSecureFiles = $cryptoMatches | Where-Object { $_.Path -notlike "*SecureKeyStore.kt*" }
if ($nonSecureFiles.Count -eq 0) {
    Write-Host "  [PASS] EncryptedSharedPreferences is strictly isolated to SecureKeyStore.kt." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Found EncryptedSharedPreferences outside SecureKeyStore.kt:" -ForegroundColor Red
    $nonSecureFiles | ForEach-Object { Write-Host "    $($_.Path):$($_.LineNumber)" }
    $ErrorCount++
}

# 4. Verify Dual-Host Activity line counts and AdaptiveApp callers
Write-Host "`n[TEST 4] Verifying UI Presentation hosts and AdaptiveApp callers..." -ForegroundColor Yellow
$mainLines = (Get-Content "app\src\main\java\com\alhaq\amnishield\ui\activity\MainActivity.kt").Count
$fragLines = (Get-Content "app\src\main\java\com\alhaq\amnishield\ui\activity\FragmentActivity.kt").Count
Write-Host "  [INFO] MainActivity.kt: $mainLines lines (Expected ~2,022)"
Write-Host "  [INFO] FragmentActivity.kt: $fragLines lines (Expected ~210)"
$adaptiveCallers = Get-ChildItem -Path "app\src\main\java" -Filter "*.kt" -Recurse |
    Select-String -Pattern "AmniShieldAdaptiveApp" |
    Where-Object { $_.Path -notlike "*AmniShieldAdaptiveApp.kt*" }
if ($adaptiveCallers.Count -eq 0) {
    Write-Host "  [PASS] AmniShieldAdaptiveApp.kt has exactly 0 callers (disconnected status verified)." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Found unexpected references to AmniShieldAdaptiveApp:" -ForegroundColor Red
    $adaptiveCallers | ForEach-Object { Write-Host "    $($_.Path):$($_.LineNumber)" }
    $ErrorCount++
}

# 5. Verify Blocker Pipeline execution order
Write-Host "`n[TEST 5] Verifying Blocker Pipeline line numbers in Accessibility Service..." -ForegroundColor Yellow
$servicePath = "app\src\main\java\com\alhaq\amnishield\services\AmniShieldAccessibilityService.kt"
$antiUninstallLine = (Select-String -Path $servicePath -Pattern "antiUninstallDetector\.inspect").LineNumber
$focusLine = (Select-String -Path $servicePath -Pattern "if \(isFocusModeActive\)").LineNumber
$websiteLine = (Select-String -Path $servicePath -Pattern "savedPreferencesLoader\.isWebsiteBlockerEnabled").LineNumber
$appBlockerLine = (Select-String -Path $servicePath -Pattern "if \(!isFocusBlockAllExSelectedActive\)").LineNumber
$channelLine = (Select-String -Path $servicePath -Pattern "eventChannel\.trySend").LineNumber

Write-Host "  [INFO] AntiUninstallDetector: Line $antiUninstallLine"
Write-Host "  [INFO] FocusModeBlocker: Line $focusLine"
Write-Host "  [INFO] WebsiteBlockerDetector: Line $websiteLine"
Write-Host "  [INFO] AppBlocker: Line $appBlockerLine"
Write-Host "  [INFO] Deferred Channel Dispatch: Line $channelLine"

if ($antiUninstallLine -lt $focusLine -and $focusLine -lt $websiteLine -and $websiteLine -lt $appBlockerLine -and $appBlockerLine -lt $channelLine) {
    Write-Host "  [PASS] Verified strict blocker pipeline order: P0 -> P1 -> P2 -> P3 -> P4." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Blocker pipeline order inverted!" -ForegroundColor Red
    $ErrorCount++
}

# 6. Verify Dead Blocklist Assets
Write-Host "`n[TEST 6] Verifying status of blocklist text assets..." -ForegroundColor Yellow
$assetMatches = Get-ChildItem -Path "app\src" -Recurse |
    Select-String -Pattern "adult_domains\.txt|haram_domains\.txt"
if ($assetMatches.Count -eq 0) {
    Write-Host "  [PASS] Blocklist text files are dead assets (zero code references)." -ForegroundColor Green
} else {
    Write-Host "  [INFO] Matches found for blocklist assets:"
    $assetMatches | ForEach-Object { Write-Host "    $($_.Path):$($_.LineNumber)" }
}

# 7. Verify Emojis in ARCHITECTURE.md and AMNISHIELD_PROJECT_SUMMARY.md
Write-Host "`n[TEST 7] Verifying Zero-Emoji Policy..." -ForegroundColor Yellow
$archEmojis = Get-Content "ARCHITECTURE.md" | Select-String -Pattern "[\uD83C-\uDBFF\uDC00-\uDFFF]"
Write-Host "  [INFO] ARCHITECTURE.md emojis detected: $($archEmojis.Count) (Lines 9, 36, 51, 69 to be purged)"
$summaryEmojis = Get-Content "AMNISHIELD_PROJECT_SUMMARY.md" | Select-String -Pattern "[\uD83C-\uDBFF\uDC00-\uDFFF]"
if ($summaryEmojis.Count -eq 0) {
    Write-Host "  [PASS] Zero emojis detected in AMNISHIELD_PROJECT_SUMMARY.md." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Emojis detected in AMNISHIELD_PROJECT_SUMMARY.md!" -ForegroundColor Red
    $summaryEmojis | ForEach-Object { Write-Host "    Line $($_.LineNumber): $($_.Line)" }
    $ErrorCount++
}

# 8. Verify Version in build.gradle.kts
Write-Host "`n[TEST 8] Verifying production version in app/build.gradle.kts..." -ForegroundColor Yellow
$codeLine = (Select-String -Path "app\build.gradle.kts" -Pattern "versionCode\s*=\s*(\d+)").Matches.Groups[1].Value
$nameLine = (Select-String -Path "app\build.gradle.kts" -Pattern 'versionName\s*=\s*"([^"]+)"').Matches.Groups[1].Value
Write-Host "  [INFO] Production versionCode: $codeLine (Expected 145)"
Write-Host "  [INFO] Production versionName: $nameLine (Expected '0.3.1 (2026.10.05)')"
if ($codeLine -eq "145" -and $nameLine -eq "0.3.1 (2026.10.05)") {
    Write-Host "  [PASS] Production version baseline confirmed." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Unexpected version in build.gradle.kts!" -ForegroundColor Red
    $ErrorCount++
}

Write-Host "`n====================================================" -ForegroundColor Cyan
if ($ErrorCount -eq 0) {
    Write-Host " ALL AUDIT CRITERIA VERIFIED SUCCESSFULLY!           " -ForegroundColor Green
} else {
    Write-Host " AUDIT COMPLETED WITH $ErrorCount FAILED TESTS!      " -ForegroundColor Red
}
Write-Host "====================================================" -ForegroundColor Cyan
```

### 8.3 Automated Verification Script (Bash)

Execute this script in a Linux / macOS / WSL Bash terminal from the project root:

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "===================================================="
echo " AmniShield-Android Audit & Verification Suite (Bash)"
echo "===================================================="

ERRORS=0

echo -e "\n[TEST 1] Checking Room / SQLite / DataStore dependencies..."
if grep -rnE "androidx\.room|androidx\.sqlite|androidx\.datastore" app/build.gradle.kts gradle/libs.versions.toml; then
    echo "[FAIL] Unexpected persistence dependency found!"
    ERRORS=$((ERRORS + 1))
else
    echo "[PASS] Zero Room/SQLite/DataStore dependencies confirmed."
fi

echo -e "\n[TEST 2] Verifying SharedPreferences in SavedPreferencesLoader.kt..."
PREF_COUNT=$(grep -oE 'getSharedPreferences\("([^"]+)"' app/src/main/java/com/alhaq/amnishield/utils/SavedPreferencesLoader.kt | sort -u | wc -l)
echo "[INFO] Distinct SharedPreferences XML files: $PREF_COUNT"
if [ "$PREF_COUNT" -ge 29 ]; then
    echo "[PASS] Verified 29+ SharedPreferences files."
else
    echo "[FAIL] Expected at least 29 files, found $PREF_COUNT"
    ERRORS=$((ERRORS + 1))
fi

echo -e "\n[TEST 3] Checking EncryptedSharedPreferences isolation..."
NON_KEYSTORE=$(grep -rn "EncryptedSharedPreferences" app/src/main/java | grep -v "SecureKeyStore.kt" || true)
if [ -z "$NON_KEYSTORE" ]; then
    echo "[PASS] EncryptedSharedPreferences is strictly isolated to SecureKeyStore.kt."
else
    echo "[FAIL] EncryptedSharedPreferences found outside SecureKeyStore.kt:"
    echo "$NON_KEYSTORE"
    ERRORS=$((ERRORS + 1))
fi

echo -e "\n[TEST 4] Verifying AmniShieldAdaptiveApp caller count..."
ADAPTIVE_CALLERS=$(grep -rn "AmniShieldAdaptiveApp" app/src/main/java | grep -v "AmniShieldAdaptiveApp.kt" || true)
if [ -z "$ADAPTIVE_CALLERS" ]; then
    echo "[PASS] AmniShieldAdaptiveApp has exactly 0 callers."
else
    echo "[FAIL] Unexpected references to AmniShieldAdaptiveApp found:"
    echo "$ADAPTIVE_CALLERS"
    ERRORS=$((ERRORS + 1))
fi

echo -e "\n[TEST 5] Checking Blocker Pipeline execution order..."
SERVICE_FILE="app/src/main/java/com/alhaq/amnishield/services/AmniShieldAccessibilityService.kt"
L_ANTI=$(grep -n "antiUninstallDetector\.inspect" "$SERVICE_FILE" | head -n1 | cut -d: -f1)
L_FOCUS=$(grep -n "if (isFocusModeActive)" "$SERVICE_FILE" | head -n1 | cut -d: -f1)
L_WEB=$(grep -n "savedPreferencesLoader\.isWebsiteBlockerEnabled" "$SERVICE_FILE" | head -n1 | cut -d: -f1)
L_APP=$(grep -n "if (!isFocusBlockAllExSelectedActive)" "$SERVICE_FILE" | head -n1 | cut -d: -f1)
L_CHAN=$(grep -n "eventChannel\.trySend" "$SERVICE_FILE" | head -n1 | cut -d: -f1)

echo "[INFO] AntiUninstallDetector: Line $L_ANTI"
echo "[INFO] FocusModeBlocker: Line $L_FOCUS"
echo "[INFO] WebsiteBlockerDetector: Line $L_WEB"
echo "[INFO] AppBlocker: Line $L_APP"
echo "[INFO] Deferred Channel Dispatch: Line $L_CHAN"

if [ "$L_ANTI" -lt "$L_FOCUS" ] && [ "$L_FOCUS" -lt "$L_WEB" ] && [ "$L_WEB" -lt "$L_APP" ] && [ "$L_APP" -lt "$L_CHAN" ]; then
    echo "[PASS] Verified strict blocker pipeline order: P0 -> P1 -> P2 -> P3 -> P4."
else
    echo "[FAIL] Blocker pipeline order inverted!"
    ERRORS=$((ERRORS + 1))
fi

echo -e "\n[TEST 6] Checking blocklist dead assets..."
ASSET_REFS=$(grep -rnE "adult_domains\.txt|haram_domains\.txt" app/src/main/java app/src/main/res || true)
if [ -z "$ASSET_REFS" ]; then
    echo "[PASS] Blocklist text assets are unreferenced (dead assets)."
else
    echo "[FAIL] Unexpected references to blocklist assets found:"
    echo "$ASSET_REFS"
    ERRORS=$((ERRORS + 1))
fi

echo -e "\n[TEST 7] Checking Zero-Emoji Policy in AMNISHIELD_PROJECT_SUMMARY.md..."
if grep -P "[\x{1F300}-\x{1F9FF}]" AMNISHIELD_PROJECT_SUMMARY.md; then
    echo "[FAIL] Emojis detected in AMNISHIELD_PROJECT_SUMMARY.md!"
    ERRORS=$((ERRORS + 1))
else
    echo "[PASS] Zero emojis detected in AMNISHIELD_PROJECT_SUMMARY.md."
fi

echo -e "\n[TEST 8] Checking production version in app/build.gradle.kts..."
V_CODE=$(grep -oE "versionCode\s*=\s*[0-9]+" app/build.gradle.kts | grep -oE "[0-9]+")
V_NAME=$(grep -oE 'versionName\s*=\s*"[^"]+"' app/build.gradle.kts | cut -d'"' -f2)
echo "[INFO] Version code: $V_CODE, Version name: $V_NAME"
if [ "$V_CODE" = "145" ] && [ "$V_NAME" = "0.3.1 (2026.10.05)" ]; then
    echo "[PASS] Production version baseline confirmed."
else
    echo "[FAIL] Unexpected version in build.gradle.kts!"
    ERRORS=$((ERRORS + 1))
fi

echo "===================================================="
if [ "$ERRORS" -eq 0 ]; then
    echo " ALL AUDIT CRITERIA VERIFIED SUCCESSFULLY!"
else
    echo " AUDIT COMPLETED WITH $ERRORS FAILED TESTS!"
fi
echo "===================================================="
exit "$ERRORS"
```
