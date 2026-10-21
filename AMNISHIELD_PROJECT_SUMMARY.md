# AmniShield-Android — Comprehensive Project Summary, Post-Implementation Audit & Critique Verification

**Document Version:** 3.0.0 (Post-v0.3.2, v0.3.3 & v0.3.4 Implementation Baseline)  
**Baseline Date:** 2026-10-21  
**Target Platform:** Android 8.0+ (API Level 26 to API Level 36)  
**Codebase Identifier:** `com.alhaq.amnishield` (Namespace) / `com.alhaq.deenshield` (ApplicationId)  
**Production Release Baseline:** `versionCode 145` | `versionName "0.3.1 (2026.10.05)"` (Milestones 0.3.2, 0.3.3 & 0.3.4 Implemented)  
**Universal Code & UI Quality Rule:** STRICT UNIVERSAL ZERO-EMOJI POLICY ENFORCED  
**Security & Privacy Invariant:** 100% On-Device Processing | Zero Remote Telemetry | Offline ECDSA NIST P-256 Licensing  

---

## Table of Contents

1. [Executive Summary & Post-Implementation Status](#executive-summary--post-implementation-status)
2. [Section 1: Post-Implementation Verification Matrix (What Is Fixed in Code & Docs)](#section-1-post-implementation-verification-matrix-what-is-fixed-in-code--docs)
   - [1.1 Phase A: Documentation & Agent Governance Remediation (Fixed)](#11-phase-a-documentation--agent-governance-remediation-fixed)
   - [1.2 Phase B (Milestone 0.3.2): Accessibility Engine Hardening & Binder Safety (Fixed)](#12-phase-b-milestone-032-accessibility-engine-hardening--binder-safety-fixed)
   - [1.3 Phase C (Milestone 0.3.3): Store Compliance, Onboarding & Layout Resilience (Fixed)](#13-phase-c-milestone-033-store-compliance-onboarding--layout-resilience-fixed)
   - [1.4 Phase E (Milestone 0.3.4): Dual-Host Cleanup, Type-Safe Routing & SharedPreferences Synchronization (Fixed)](#14-phase-e-milestone-034-dual-host-cleanup-type-safe-routing--sharedpreferences-synchronization-fixed)
3. [Section 2: Investigation of Remaining Claims & Critiques (Validity Audit)](#section-2-investigation-of-remaining-claims--critiques-validity-audit)
   - [2.1 Valid Remaining Items (Scheduled for Milestone 0.3.5)](#21-valid-remaining-items-scheduled-for-milestone-035)
   - [2.2 Nuanced / Partially Valid Claims (Codebase Ground Truth)](#22-nuanced--partially-valid-claims-codebase-ground-truth)
   - [2.3 Refuted, Invalid, or Hallucinated Claims in Prior Reports](#23-refuted-invalid-or-hallucinated-claims-in-prior-reports)
4. [Section 3 (R1): Persistence Reality & SharedPreferences Inventory](#section-3-r1-persistence-reality--sharedpreferences-inventory)
   - [3.1 Gradle Dependency Audit (Zero Room / Zero DataStore)](#31-gradle-dependency-audit-zero-room--zero-datastore)
   - [3.2 Orphaned Database Stub Classes](#32-orphaned-database-stub-classes)
   - [3.3 Active SharedPreferences Storage Engine (30 Loader + 7 Auxiliary XML Files)](#33-active-sharedpreferences-storage-engine-29-loader--9-auxiliary-xml-files)
   - [3.4 SecureKeyStore & EncryptedSharedPreferences Isolation](#34-securekeystore--encryptedsharedpreferences-isolation)
   - [3.5 In-Memory Schedule Cache & Thread-Safe Persistence (v0.3.2 & v0.3.4 Resolution)](#35-in-memory-schedule-cache--thread-safe-persistence-v032--v034-resolution)
5. [Section 4 (R2): Dual-Host Container Presentation Architecture](#section-4-r2-dual-host-container-presentation-architecture)
   - [4.1 MainActivity: Primary 4-Tab Container](#41-mainactivity-primary-4-tab-container)
   - [4.2 FragmentActivity: Secondary Sub-Screen Router](#42-fragmentactivity-secondary-sub-screen-router)
   - [4.3 Jetpack Compose UI Embedding via ComposeView](#43-jetpack-compose-ui-embedding-via-composeview)
   - [4.4 AmniShieldAdaptiveApp: Origin, Disconnection & Target Architecture](#44-amnishieldadaptiveapp-origin-disconnection--target-architecture)
6. [Section 5 (R3): Blocker Interceptor Pipeline & Concurrency Architecture](#section-5-r3-blocker-interceptor-pipeline--concurrency-architecture)
   - [5.1 Chain of Responsibility Execution Sequence (P0 to P4)](#51-chain-of-responsibility-execution-sequence-p0-to-p4)
   - [5.2 Blocker Interceptor Pipeline Table](#52-blocker-interceptor-pipeline-table)
   - [5.3 Debunking the 50,000 Local Domain Lookup Myth & Dead Assets](#53-debunking-the-50000-local-domain-lookup-myth--dead-assets)
7. [Section 6 (R4): Premium Architecture & Licensing Verification](#section-6-r4-premium-architecture--licensing-verification)
8. [Section 7 (R5 & R6): Roadmap Status, Zero-Emoji Compliance & Versioning](#section-7-r5--r6-roadmap-status-zero-emoji-compliance--versioning)
9. [Section 8: Automated Post-Implementation Verification Suite (PowerShell)](#section-8-automated-post-implementation-verification-suite-powershell)

---

## Executive Summary & Post-Implementation Status

This document serves as the authoritative post-implementation synthesis for `AmniShield-Android` (`d:\PROJECTS\Amni\AmniShield-Android`). It reconciles all findings from the **Architectural Discovery Report** (`amnishield_architectural_discovery_report.md`) and the **Devil's Advocate Verification Critique Report** (`amnishield_verification_critique_report.md`) against the live codebase following the execution of **Phase A (Documentation & Agent Rules)**, **Phase B (Milestone 0.3.2 — Accessibility Engine Hardening & Binder Safety)**, **Phase C (Milestone 0.3.3 — Store Compliance & Layout Resilience)**, and **Phase E (Milestone 0.3.4 — Dual-Host Cleanup, Type-Safe Routing & SharedPreferences Synchronization)**.

### High-Level Audit Verdict
- **Fixed & Verified (100% of Phase A, Milestone 0.3.2, Milestone 0.3.3, and Milestone 0.3.4):** All 6 documentation/governance drift items, all 5 accessibility concurrency/Binder/node-safety/manifest defects in Milestone 0.3.2, all 4 Google Play disclosure/onboarding/rotation defects in Milestone 0.3.3, and all 4 navigation/synchronization/CI-matrix/defense-in-depth items in Milestone 0.3.4 have been fixed in the live codebase and verified via static checks and multi-flavor Gradle compilation/unit testing (`assemblePlaystoreDebug`, `assembleFdroidDebug`, `assembleUniversalDebug`, and all flavor unit test suites).
- **Remaining & Valid (Scheduled for Milestone 0.3.5):** The final pre-1.0 architectural epic—specifically the phased migration from the 30+ SharedPreferences XML files to Room DB (relational rules) + Preferences DataStore (flags/tokens) and reconnecting the pure-Compose `AmniShieldAdaptiveApp` shell—is tracked under Milestone 0.3.5.
- **Refuted / Invalid Prior Claims:** Several claims from prior reports were disproved or corrected upon live code inspection, including the non-existent `dialog_rule_duration.xml` / `dialog_edit_time.xml` filenames, the invalid claim that `AppInstallReceiver` is vulnerable to third-party `PACKAGE_ADDED` broadcast injection, the refuted `ApiPermissionActivity` UID-spoofing escalation hypothesis, and the debunked 50,000-domain engine myth.

---

## Section 1: Post-Implementation Verification Matrix (What Is Fixed in Code & Docs)

### 1.1 Phase A: Documentation & Agent Governance Remediation (Fixed)

| Audit Finding | Target File(s) | Live Codebase Resolution | Status |
| :--- | :--- | :--- | :--- |
| **Hallucinated Room DB & DataStore Claims** | `ARCHITECTURE.md`, `README.md`, `.agents/AGENTS.md` | Documented 100% `SharedPreferences` persistence via `SavedPreferencesLoader.kt` (30 XML files in loader, 37 distinct `getSharedPreferences` names app-wide, plus 1 encrypted file in `SecureKeyStore.kt`) and isolated `EncryptedSharedPreferences` in `SecureKeyStore.kt`. | `[FIXED & VERIFIED]` |
| **Unmapped Dual-Host UI & `AmniShieldAdaptiveApp`** | `ARCHITECTURE.md`, `README.md`, `.agents/AGENTS.md` | Documented `MainActivity.kt` (2,022 lines, 4 XML tabs) + `FragmentActivity.kt` (210 lines, 18+ ComposeView sub-screens) and the disconnected status of `AmniShieldAdaptiveApp.kt` (0 callers, target for v0.3.5). | `[FIXED & VERIFIED]` |
| **Inverted Blocker Pipeline Table** | `ARCHITECTURE.md`, `README.md` | Aligned priority table with `AmniShieldAccessibilityService.kt`: P0 `AntiUninstallDetector` -> P1 `FocusModeBlocker` -> P2 `WebsiteBlockerDetector` -> P3 `AppBlocker` -> P4 `KeywordBlocker` & `ReelBlocker` (Deferred Channel). | `[FIXED & VERIFIED]` |
| **4 Unicode Emojis in `ARCHITECTURE.md`** | `ARCHITECTURE.md` (lines 9, 36, 51, 69) | Purged all 4 unicode emojis (`[U+1F3D7]`, `[U+1F6E1]`, `[U+1F4DD]`, `[U+1F512]`). Confirmed 0 unicode emojis across all docs and source files. | `[FIXED & VERIFIED]` |
| **Foreign Sister-App Milestones in `ROADMAP.md`** | `ROADMAP.md` | Expunged Windows Elevated Service, DNS Sinkhole, and NPU Vision Blur. Replaced header with `v0.3.2 - v0.3.5` and marked Milestones 0.3.2 and 0.3.3 `[x]`. | `[FIXED & VERIFIED]` |
| **Stale Version & Missing Guardrails in `.agents/AGENTS.md`** | `.agents/AGENTS.md` | Updated to `0.3.1 (2026.10.05)` / `versionCode 145` and codified all 7 Mandatory Architectural Invariants & Agent Guardrails. | `[FIXED & VERIFIED]` |

---

### 1.2 Phase B (Milestone 0.3.2): Accessibility Engine Hardening & Binder Safety (Fixed)

| Defect / Critique Item | Target File(s) & Lines | Live Codebase Resolution | Status |
| :--- | :--- | :--- | :--- |
| **Main Binder Thread `Gson().fromJson()` Bottleneck** | `SavedPreferencesLoader.kt:56-72, 931-962`<br>`AmniShieldAccessibilityService.kt:99-105, 685-709, 770-905` | Implemented `@Volatile` double-checked schedule caching (`cachedScheduleRulesSnapshot` in `SavedPreferencesLoader.kt` and `cachedScheduleRules`, `cachedBlockedWebsites`, `cachedBlockedKeywords` in `AmniShieldAccessibilityService.kt`). Zero per-event `Gson().fromJson()` calls execute on `onAccessibilityEvent`. | `[FIXED & VERIFIED]` |
| **Website Blocker Broadcast Mismatch** | `WebsiteBlockerConfigScreen.kt:174, 413, 429`<br>`ServiceBroadcastManager.kt:23, 56`<br>`AmniShieldAccessibilityService.kt:661-664, 922` | Added `INTENT_ACTION_REFRESH_WEBSITE_BLOCKER` and `onRefreshWebsiteBlocker()` callback (`setupWebsiteBlocker()`), replacing the mismatched `INTENT_ACTION_REFRESH_APP_BLOCKER` broadcast in `WebsiteBlockerConfigScreen.kt`. | `[FIXED & VERIFIED]` |
| **Temporal Window Race in `processDeferredChecks()`** | `AmniShieldAccessibilityService.kt:502-607` | Added `val rootPackage = rootNode.packageName?.toString().orEmpty()` verification (`!rootPackage.equals(packageName, ignoreCase = true)`) and `SystemExclusionManager.isExempt(...)` check before running deferred `KeywordBlocker` and `ReelBlocker`. | `[FIXED & VERIFIED]` |
| **Redundant `rootInActiveWindow` IPC Call in `ReelActionHandler`** | `ReelActionHandler.kt:35-41, 140-157`<br>`AmniShieldAccessibilityService.kt:577-582` | Updated `ReelActionHandler.handleReelBlock()` and `tryRedirectToMainFeed()` to consume the caller-owned `rootNode` and `packageName` from `processDeferredChecks()` instead of calling `service.rootInActiveWindow`. | `[FIXED & VERIFIED]` |
| **1s / 2s Main-Looper `rootInActiveWindow` Polling & Main-Thread Disk I/O** | `ReelsSessionTracker.kt:40-269`<br>`BrowserSessionTracker.kt:27-169`<br>`SavedPreferencesLoader.kt:1687-1697` | Removed `pollActiveWindowForReels()` and `pollActiveWindowForBrowser()`. Both trackers now update surface/domain state strictly from `onAccessibilityEvent(event, rootNode)` and offload `incrementReelsScrolled` (`ReelsStatsManager.recordReelScroll`), `addReelsWatchTime` (`ReelsStatsManager.recordReelWatchTime`), and `@Synchronized recordWebsiteUsage` to `Dispatchers.IO` (`ioScope`). | `[FIXED & VERIFIED]` |
| **Missing `try ... finally` in Child Node Traversals** | `AntiUninstallDetector.kt:267-332`<br>`ReelDetectionEngine.kt:276-408` | Wrapped the `ArrayDeque` loop in `AntiUninstallDetector.scanScreenNodes()` and all 4 recursive/child loops in `ReelDetectionEngine.kt` (`collectText`, `findNodeByResourceId`, `containsViewPager`, `hasShortVideoAspectRatioNode`) in `try ... finally` blocks that guarantee child node recycling without recycling `rootNode`. | `[FIXED & VERIFIED]` |
| **Unprotected Exported `LauncherActivity`** | `AndroidManifest.xml:76` | Changed `LauncherActivity` from `android:exported="true"` to `android:exported="false"`. | `[FIXED & VERIFIED]` |

---

### 1.3 Phase C (Milestone 0.3.3): Store Compliance, Onboarding & Layout Resilience (Fixed)

| Defect / Critique Item | Target File(s) & Lines | Live Codebase Resolution | Status |
| :--- | :--- | :--- | :--- |
| **Disclosure Dialog Landscape/200% Font Clipping & 40dp Buttons** | `dialog_accessibility_disclosure.xml:1-218` | Wrapped header and disclosure cards inside `<ScrollView android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:fillViewport="true">` above a sticky bottom action bar with `android:layout_height="wrap_content"` and `android:minHeight="48dp"`. | `[FIXED & VERIFIED]` |
| **Cancelable Disclosure Dialog & Missing Persistent Consent** | `AccessibilityDisclosureDialog.kt:45-71`<br>`SavedPreferencesLoader.kt:118-132` | Enforced `.setCancelable(false)` and persisted affirmative consent (`KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED` and `KEY_ACCESSIBILITY_DISCLOSURE_TIMESTAMP`) via `setAccessibilityDisclosureAccepted(true/false)`. | `[FIXED & VERIFIED]` |
| **Onboarding Accessibility Bypass Loophole** | `PermissionsScreen.kt:115` | Changed `val canProceed` from `isAccessibilityGranted || (isBatteryGranted && (isOverlayGranted || isUsageGranted))` to strictly `val canProceed = isAccessibilityGranted`. | `[FIXED & VERIFIED]` |
| **Screen Rotation State Loss & Cutout Overlap** | `AmniSpaceActivity.kt:381, 406`<br>`AmniSpaceBreathingCircle.kt:64-65`<br>`WarningActivity.kt:38-45, 75-115, 211-216`<br>`UsageStatOverlayManager.kt:28-140` | Switched `showBreathingModal`, `isBreathingDone`, `secondsRemaining`, and `breathingPhase` to `rememberSaveable`; preserved `remainingProceedMillis`, `isProceedTimerFinished`, and `selectedMinsValue` via `onSaveInstanceState` in `WarningActivity.kt`; added `ACTION_CONFIGURATION_CHANGED` receiver and `WindowInsetsCompat.Type.systemBars() \| displayCutout()` inset listener in `UsageStatOverlayManager.kt`. | `[FIXED & VERIFIED]` |

---

### 1.4 Phase E (Milestone 0.3.4): Dual-Host Cleanup, Type-Safe Routing & SharedPreferences Synchronization (Fixed)

| Defect / Critique Item | Target File(s) & Lines | Live Codebase Resolution | Status |
| :--- | :--- | :--- | :--- |
| **Loosely-Typed String Intent Routing (`feature_type`, `fragment`)** | `FragmentDestination.kt:1-162`<br>`FragmentActivity.kt:31-104`<br>`MainActivity.kt`, `BlocksFragment.kt`, `AdvancedFragment.kt`, `FocusFragment.kt`, `SettingsFragment.kt`, `StatsFragment.kt` | Created sealed route contract `FragmentDestination` with `FragmentActivity.createIntent()` and `FragmentActivity.launch()`. Preserved backward-compatible `fromKey()` / `fromIntent()` fallback for legacy intents and differentiated `AppBlockerSchedules` (`filter_type = "App Blocker"`) from `BlocksManager` (unfiltered "All" view unless `filter_type` supplied). Verified with `FragmentDestinationTest.kt`. | `[FIXED & VERIFIED]` |
| **Multi-Instance `SavedPreferencesLoader` Read-Modify-Write Races** | `SavedPreferencesLoader.kt:50-1710`<br>`SavedPreferencesLoaderConcurrencyTest.kt:1-111` | Added static companion mutex locks `rulesLock` and `statsLock` synchronizing all schedule rules, launch limit rules, launch count tracking, block lists, focus mode data/sessions, keyword blocker lists, website usage stats, and cooldown maps across all `SavedPreferencesLoader` instances. Verified under 20-thread contention in `SavedPreferencesLoaderConcurrencyTest.kt`. | `[FIXED & VERIFIED]` |
| **Sequential CI Flavor Execution** | `.github/workflows/main-pipeline.yml:27-81` | Converted `android-ci` into a parallel `fail-fast: false` GitHub Actions matrix across `playstore`, `fdroid`, and `universal`. | `[FIXED & VERIFIED]` |
| **Manifest Deep-Link Duplication, `ApiPermissionActivity` Referrer Spoofing & Post-Boot Accessibility Alert** | `AndroidManifest.xml:45-65`<br>`ApiPermissionActivity.kt:58`<br>`NotificationHelper.kt:19-111`<br>`BootAndAlarmReceiver.kt:23-62`<br>`SmartBehavioralBrain.kt:70` | Deduplicated `<data>` tags in `MainActivity`; removed `?: referrer?.host` in `ApiPermissionActivity.kt`; added high-importance `security_alerts` ("Protection Paused") notification channel in `NotificationHelper.kt` triggered by `BootAndAlarmReceiver.kt` when accessibility is disabled across reboot/update; purged legacy `⏳` emoji from `SmartBehavioralBrain.kt`. | `[FIXED & VERIFIED]` |

---

## Section 2: Investigation of Remaining Claims & Critiques (Validity Audit)

A thorough cross-examination of every remaining claim in `amnishield_architectural_discovery_report.md` and `amnishield_verification_critique_report.md` against the post-v0.3.4 codebase separates **valid remaining backlog items** from **nuanced platform constraints** and **refuted/invalid claims**.

### 2.1 Valid Remaining Items (Scheduled for Milestone 0.3.5)

1. **Reactive Room DB & Preferences DataStore Migration & Push-Based UDF State (VALID — Milestone 0.3.5):**
   - **Codebase Evidence:** While Milestone 0.3.2 added `@Volatile` schedule caching and Milestone 0.3.4 added static companion mutex locks (`rulesLock` and `statsLock`) across all `SavedPreferencesLoader` JSON mutators, persistence still operates across 30+ SharedPreferences XML files, `BlocksManagerFragment.kt:738` and `SettingsFragment.kt:224` populate `AmniShieldViewModel` via imperative `viewModel.loadState(newState)` snapshots, and `StatsFragment.kt:47-63` maintains 15+ standalone `mutableStateOf` properties.
   - **Impact & Plan:** Phased migration to reactive Room DB (relational rules) + Preferences DataStore (flags/tokens), retiring the 3 dead classes in `data/db/`, is scheduled for Milestone 0.3.5.
2. **Disconnected `AmniShieldAdaptiveApp.kt` & Dual-Host Retirement (VALID — Milestone 0.3.5):**
   - **Codebase Evidence:** `AmniShieldAdaptiveApp.kt` (254 lines), `BlocksManagerAdaptiveScreen.kt`, and `StatsAdaptiveScreen.kt` compile cleanly but have 0 callers after commit `ed57548`.
   - **Impact & Plan:** Preserved untouched per architectural invariant until Milestone 0.3.5 (Pure-Compose Navigation Epic).

---

### 2.2 Nuanced / Partially Valid Claims (Codebase Ground Truth)

1. **`AmniShieldApiService.kt` (`runBlocking` on Binder Thread) & `ApiPermissionActivity.kt` (`referrer?.host` Fallback):**
   - **Prior Claims:** The Discovery Report (line 40) flagged `runBlocking` inside `AmniShieldApiService.kt` (lines 30, 43, 56) as a Binder pool exhaustion risk, and an early hypothesis claimed `ApiPermissionActivity.kt:58` (`callingPackage ?: referrer?.host`) allowed third-party caller spoofing.
   - **Verified Ground Truth:**
     - **Privilege Escalation is Impossible:** As proven in the Critique Report (Section 1.3), `AmniShieldApiService` (`AndroidManifest.xml:18, 120`) enforces `android:permission="${applicationId}.permission.BIND_API"` with `android:protectionLevel="signature"`, and `AmniShieldApiService.callerAllowed()` (line 68) checks `packageManager.getPackagesForUid(Binder.getCallingUid())` from the OS kernel. No third-party app can bind to `AmniShieldApiService` or forge `Binder.getCallingUid()`.
     - **Resolved Defense-in-Depth (`ApiPermissionActivity`):** In Milestone 0.3.4, `?: referrer?.host` was removed in `ApiPermissionActivity.kt:58` so it relies strictly on `callingPackage`.
2. **Hardcoded Button Heights in Legacy XML Layouts (`36dp` / `40dp`):**
   - **Prior Claim (`amnishield_architectural_discovery_report.md` lines 398–399):** Claimed `dialog_rule_duration.xml#L90,L105` and `dialog_edit_time.xml` hardcode `36dp` and `40dp` button heights.
   - **Verified Ground Truth:**
     - **Hallucinated Filenames:** Neither `dialog_rule_duration.xml` nor `dialog_edit_time.xml` exists in `app/src/main/res/layout/`.
     - **Actual Files:** The primary Google Play compliance dialog (`dialog_accessibility_disclosure.xml`) is fixed (`wrap_content` + `minHeight="48dp"`). Eight legacy XML layout files (`block_schedule_item.xml`, `fragment_all_app_usage.xml`, `fragment_chose_anti_uninstall_mode.xml`, `fragment_manage_block_schedules.xml`, `keyword_item.xml`, `launch_limit_item.xml`, `select_apps_item.xml`, `widget_quick_focus.xml`) still use `36dp` or `40dp` heights; most belong to legacy XML fragments superseded by Compose screens and slated for removal in Milestone 0.3.5.
3. **OEM Battery Killers & `BootAndAlarmReceiver.kt`:**
   - **Prior Claim (`amnishield_verification_critique_report.md` lines 245–248):** Noted that `BootAndAlarmReceiver.kt` only reschedules `daily_report_alarm` and lacks recovery logic if an OEM task killer disables `AmniShieldAccessibilityService`.
   - **Verified Ground Truth:** Under Android OS security constraints, an app without `WRITE_SECURE_SETTINGS` cannot programmatically re-enable its own `AccessibilityService` in `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`. In Milestone 0.3.4, `BootAndAlarmReceiver.kt` was enhanced to check `AccessibilityServiceUtils.isAccessibilityServiceEnabled(context)` on `ACTION_BOOT_COMPLETED` and `ACTION_MY_PACKAGE_REPLACED` and post a high-importance "Protection Paused" alert (`CHANNEL_ID_SECURITY_ALERTS`) if active protection rules exist while accessibility is disabled.
4. **`AccessibilityNodeInfo.recycle()` on API 26–29 vs. API 30+:**
   - **Prior Claim (`amnishield_verification_critique_report.md` Section 3.1.A):** Correctly noted that `recycle()` is a deprecated no-op in AOSP on Android 11+ (API 30+), where ART GC tracks native tokens, but remains required on Android 8.0–10 (API 26–29) due to the 50-object `sPool`.
   - **Verified Ground Truth:** With Milestone 0.3.2's `try ... finally` blocks in `AntiUninstallDetector.kt` and `ReelDetectionEngine.kt`, node recycling is now 100% exception-safe for API 26–29 devices while preserving strict `rootNode` ownership in `AmniShieldAccessibilityService.kt`.

---

### 2.3 Refuted, Invalid, or Hallucinated Claims in Prior Reports

| Prior Claim / Hypothesis | Source Report | Verdict | Verification Evidence |
| :--- | :--- | :--- | :--- |
| **50,000+ Local Domain Lookup Engine** | Historical Docs / Prompt Premise | **`[REFUTED / MYTH]`** | `adult_domains.txt` (52 lines) and `haram_domains.txt` (50 lines) in `app/src/main/assets/blocklists/` have 0 references in code. `WebsiteBlockerDetector.kt` performs an $O(N)$ linear scan over 1–20 active schedule targets. |
| **`SharedPreferences.Editor.apply()` Synchronous Disk Write** | Early Discovery Draft | **`[REFUTED]`** | `apply()` updates the in-memory `SharedPreferences` map synchronously and writes to disk asynchronously on `QueuedWork`. |
| **`ReelsStatsManager.recordReelScroll()` Synchronous Main-Thread JSON Write** | Critique Report (Line 219) | **`[RESOLVED IN v0.3.2]`** | `ReelsSessionTracker.kt:186-188, 230-232` now dispatches both `incrementReelsScrolled` and `addReelsWatchTime` asynchronously on `Dispatchers.IO` (`ioScope`), backed by `@Synchronized` methods in `ReelsStatsManager.kt`. |
| **`AppInstallReceiver` Unprotected Broadcast Spoofing** | Discovery Report (Lines 411–423, 467) | **`[REFUTED / INVALID]`** | `android.intent.action.PACKAGE_ADDED` is a system-protected broadcast in Android AOSP (`system_server` UID 1000 only). Furthermore, `AppInstallReceiver.kt:21` checks `intent.action == Intent.ACTION_PACKAGE_ADDED`, ignores self-packages (line 32), and scopes its internal broadcast via `.setPackage(context.packageName)` (line 56). |
| **`dialog_rule_duration.xml` & `dialog_edit_time.xml` Hardcoded Heights** | Discovery Report (Lines 398–399) | **`[HALLUCINATED FILENAMES]`** | Neither `dialog_rule_duration.xml` nor `dialog_edit_time.xml` exists in `app/src/main/res/layout/`. |

---

## Section 3 (R1): Persistence Reality & SharedPreferences Inventory

### 3.1 Gradle Dependency Audit (Zero Room / Zero DataStore)

Inspection of all build configuration files confirms that no SQLite, Room, or DataStore libraries are compiled into the application:
1. **Root `build.gradle.kts` (Lines 1-7):** Base plugins only (`android.application`, `kotlin.android`, `google.gms.google.services`, `kotlin.compose`).
2. **Version Catalog `gradle/libs.versions.toml` (Lines 1-71):** Zero entries for `androidx.room`, `androidx.sqlite`, or `androidx.datastore`. The sole serialization library is Google Gson (`gson = "2.14.0"`).
3. **Module `app/build.gradle.kts` (Lines 145-195):** Implements AndroidX core components, `implementation(libs.gson)`, and `implementation("androidx.security:security-crypto:1.1.0-alpha06")`.

### 3.2 Orphaned Database Stub Classes

The directory `app/src/main/java/com/alhaq/amnishield/data/db/` contains three files:
1. `AppBlockRule.kt`
2. `ScreenTimeLimitRule.kt`
3. `AmniShieldDatabaseMigrations.kt`

None of these three classes possess `@Entity`, `@Dao`, or `@Database` annotations, and they have **0 usages** across the entire codebase. They remain untouched until Milestone 0.3.5.

### 3.3 Active SharedPreferences Storage Engine (30 Loader + 7 Auxiliary XML Files)

Local persistence operates **100% via Android SharedPreferences** orchestrated through `SavedPreferencesLoader.kt` (2,069 lines), which directly manages **29 specialized XML preference files**:
1. `amnispace_prefs.xml`: Focus launcher preferences, mindful breathing duration, and workspace styling.
2. `anti_uninstall.xml`: Anti-uninstall protection state, Device Admin status, and tamper locks.
3. `app_blocker.xml`: Master app blocker toggle, schedule rules JSON (`schedule_rules`), and cooldown data.
4. `app_launch_tracking.xml`: Launch count limits (`launch_limit_rules`) and daily execution counters.
5. `app_preferences.xml`: General UI flags, accessibility disclosure consent (`accessibility_disclosure_accepted`, `accessibility_disclosure_accepted_at`), and operational preferences.
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

`SavedPreferencesLoader.kt` also references `permission_guide_prefs.xml`, bringing its distinct file count to **30**. Seven further preference files are opened by auxiliary components (`achievement_notifications.xml`, `amnishield_sync_secrets_fallback.xml`, `config_reels.xml`, `error_reporting.xml`, `reminder_settings.xml`, `shortcuts.xml`, `theme_prefs.xml`), giving **37 distinct `getSharedPreferences` names** app-wide. The encrypted `amnishield_sync_secrets.xml` (`SecureKeyStore.kt`) is the 38th file on disk.

### 3.4 SecureKeyStore & EncryptedSharedPreferences Isolation

`EncryptedSharedPreferences` is instantiated **exclusively within `SecureKeyStore.kt`** (`amnishield_sync_secrets.xml` with `AES256_SIV` / `AES256_GCM`) strictly for Supabase sync secrets (`dek`, `access_token`, `refresh_token`, `cursor`, `fcm_token`, `device_id`, `device_name`) with an automatic self-healing fallback (`amnishield_sync_secrets_fallback.xml`).

### 3.5 In-Memory Schedule Cache & Thread-Safe Persistence (v0.3.2 & v0.3.4 Resolution)

To eliminate both the Main Binder Thread `Gson().fromJson()` bottleneck and multi-instance read-modify-write races identified during the audit:
- `SavedPreferencesLoader.kt` maintains `@Volatile private var cachedScheduleRulesSnapshot: List<AppBlockScheduleRule>? = null` guarded by `scheduleCacheLock`.
- `loadAppBlockerScheduleRules()` returns a defensive mutable copy of the cached snapshot in $O(1)$ time without touching disk or reflection, and `saveAppBlockerScheduleRules()` atomically updates the snapshot on write.
- Static companion mutexes `rulesLock` and `statsLock` synchronize all schedule rules, launch limit rules, launch count tracking, block lists, focus mode data/sessions, keyword blocker lists, website usage stats, and cooldown maps across all `SavedPreferencesLoader` instances.
- `AmniShieldAccessibilityService.kt` maintains `@Volatile` in-memory caches (`cachedScheduleRules`, `cachedBlockedWebsites`, `cachedBlockedKeywords`) refreshed via `refreshScheduleRulesCache()` when broadcast invalidations arrive through `ServiceBroadcastManager.kt`.

---

## Section 4 (R2): Dual-Host Container Presentation Architecture

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

### 4.1 MainActivity: Primary 4-Tab Container
`MainActivity.kt` (2,022 lines) inflates `ActivityMainBinding` and routes the 4 primary bottom navigation tabs (`StatsFragment`, `BlocksManagerFragment`, `FocusFragment`, `AdvancedFragment`) via `supportFragmentManager`, alongside drawer navigation, deep-link handling, and PIN dialogs.

### 4.2 FragmentActivity: Secondary Sub-Screen Router
`FragmentActivity.kt` inflates `R.layout.activity_fragment` and dispatches 18+ sub-screens and onboarding flows (`WelcomeFragment`, `AccessibilityGuide`, `ProfileFragment`, `DiagnosticsFragment`, and all `*ConfigFragment` bridges) via the type-safe `FragmentDestination.kt` sealed route contract (`FragmentActivity.launch` / `FragmentActivity.createIntent`), with backward-compatible `fromKey()` / `fromIntent()` fallback for legacy intents.

### 4.3 Jetpack Compose UI Embedding via ComposeView
Each configuration fragment inherits from `BaseFeatureFragment` and returns a `ComposeView` configured with `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` wrapping `AmniShieldTheme`.

### 4.4 AmniShieldAdaptiveApp: Origin, Disconnection & Target Architecture
Connected in commit `7155646` and disconnected in commit `ed57548` to avoid `FragmentManager` lifecycle crashes, `AmniShieldAdaptiveApp.kt` (254 lines) has 0 callers and is preserved as the target pure-Compose shell for Milestone 0.3.5.

---

## Section 5 (R3): Blocker Interceptor Pipeline & Concurrency Architecture

### 5.1 Chain of Responsibility Execution Sequence (P0 to P4)

In `AmniShieldAccessibilityService.kt`, `onAccessibilityEvent` executes interceptors in strict ordinal priority:
1. **Priority 0 (`AntiUninstallDetector`):** Intercepts Play Store, package installer, OEM security center, Device Admin, and App Info tampering; dispatches `GLOBAL_ACTION_HOME` or `AntiUninstallPasswordActivity` and short-circuits.
2. **Priority 1 (`FocusModeBlocker`):** Evaluates active focus sessions; dispatches `AmniSpaceActivity`, `pressHome()`, or `WarningActivity` and short-circuits.
3. **Priority 2 (`WebsiteBlockerDetector`):** Evaluates blocked social apps and browser URL bars using in-memory `cachedBlockedWebsites`; dispatches `AmniSpaceActivity`, `WarningActivity`, or `pressHome()` and short-circuits.
4. **Priority 3 (`AppBlocker`):** Evaluates app schedules, launch limits, and usage quotas; dispatches `AmniSpaceActivity`, `pressHome()`, or `WarningActivity` and short-circuits.
5. **Priority 4 (`KeywordBlocker` & `ReelBlocker` via `Channel.CONFLATED`):** `processDeferredChecks(event)` runs on `Dispatchers.Default`, verifies `!rootPackage.equals(packageName, ignoreCase = true)` and `SystemExclusionManager.isExempt(...)`, and passes `rootNode` directly to `KeywordBlocker`, `ReelBlocker`, and `ReelActionHandler`.

### 5.2 Blocker Interceptor Pipeline Table

| Priority | Component | Target Surface / Vectors | Action Dispatched | Execution Model |
| :--- | :--- | :--- | :--- | :--- |
| **0 (Highest)** | `AntiUninstallDetector` | Play Store, OEM Security Centers, Device Admin, App Info | `GLOBAL_ACTION_HOME`, `AntiUninstallPasswordActivity` | Synchronous (Main Binder) |
| **1** | `FocusModeBlocker` | Active Focus Sessions & Whitelist Enforcements | Full-screen Focus Space (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` | Synchronous (Main Binder, Cached Rules) |
| **2** | `WebsiteBlockerDetector` | Chrome, Samsung Internet, Firefox, Brave (15+ browsers) & Social Apps | Mindful Breathing (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` redirect | Synchronous (Cached 1-20 active schedule targets) |
| **3** | `AppBlocker` | Blocked Applications, Launch Limits, Daily Usage Limits | Mindful Breathing (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` overlay | Synchronous (Main Binder, Cached Rules) |
| **4** | `KeywordBlocker` & `ReelBlocker` | Search bars, query inputs, and Short-form Video feeds (YouTube, IG, FB, TikTok) | Text erasure, scroll limit counter, feed suppression | Deferred Coroutine Worker (`Channel.CONFLATED` + Temporal Window Guard) |

### 5.3 Debunking the 50,000 Local Domain Lookup Myth & Dead Assets
`WebsiteBlockerDetector.kt` iterates over 1–20 user-configured domains from active schedule rules (`cachedBlockedWebsites`). The static files `app/src/main/assets/blocklists/adult_domains.txt` (52 lines) and `haram_domains.txt` (50 lines) have 0 references in code.

---

## Section 6 (R4): Premium Architecture & Licensing Verification

- **Tiers (`PremiumManager.kt`):** `UserType.FREE` (100% FOSS core blockers forever), `UserType.COMPASSIONATE` (Al-Haq community pass with monotonic uptime verification), `UserType.FOUNDER` (90-day early supporter pass with `SystemClock.elapsedRealtime()` monotonic clock verification), and `UserType.PREMIUM`.
- **Gated Features:** Strictly limited to security locking (`RuleProBadge`, Master PIN, App Lock, Bypass PIN Lock) and Supabase multi-device cloud policy sync (`PolicySyncManager.kt`).
- **Multi-Flavor Billing Separation:**
  - `playstore`: Google Play `BillingClientWrapper.kt`.
  - `fdroid`: Zero proprietary GMS/Billing dependencies; 100% offline NIST P-256 ECDSA signature verification (`SHA256withECDSA` in `LicenseValidator.kt` with IEEE P1363-to-DER translation) and optional Stripe Direct checkout.
  - `universal`: Hybrid Play Billing + Offline ECDSA + Stripe Direct fallback.

---

## Section 7 (R5 & R6): Roadmap Status, Zero-Emoji Compliance & Versioning

### 7.1 Active / Upcoming Milestones (`ROADMAP.md` State)

#### Milestone 0.3.2: Accessibility Engine Hardening & Binder Safety (COMPLETED)
- [x] **In-Memory Schedule Caching & Broadcast Alignment:** Eliminate reflection-based `Gson().fromJson()` deserialization on the Main Binder Thread during `onAccessibilityEvent` in `AmniShieldAccessibilityService.kt` and `SavedPreferencesLoader.kt`, and fix `WebsiteBlockerConfigScreen.kt` to broadcast `INTENT_ACTION_REFRESH_WEBSITE_BLOCKER` / unified schedule refresh via `ServiceBroadcastManager.kt`.
- [x] **Deferred Pipeline Temporal Window Guard:** Enforce strict package-identity verification (`rootPackage.equals(packageName, ignoreCase = true)`) and `SystemExclusionManager.isExempt(...)` in `AmniShieldAccessibilityService.processDeferredChecks()` to prevent false-positive interceptions when users rapidly switch windows.
- [x] **Polling Decoupling & Background Disk I/O:** Eliminate continuous `rootInActiveWindow` queries on 1-second and 2-second Handler timers in `ReelsSessionTracker.kt` and `BrowserSessionTracker.kt`, transitioning to event-driven surface tracking and offloading usage persistence writes to `Dispatchers.IO`.
- [x] **Node Traversal Exception Safety:** Implement robust `try ... finally` blocks and null-safe child node recycling across `AntiUninstallDetector.kt` and `ReelDetectionEngine.kt`.
- [x] **Manifest Security Hardening:** Set `android:exported="false"` on `LauncherActivity` in `AndroidManifest.xml` (line 76) to prevent unauthorized external intent launching.

#### Milestone 0.3.3: Store Compliance & Layout Resilience (COMPLETED)
- [x] **Prominent Disclosure Overhaul:** Implement a `ScrollView` content container with a sticky bottom action bar, `wrap_content` (`minHeight="48dp"`) button sizing in `dialog_accessibility_disclosure.xml`, and `.setCancelable(false)` in `AccessibilityDisclosureDialog.kt` to guarantee compliance in landscape orientation and 200% font scaling.
- [x] **Persistent Affirmative Consent:** Persist the accessibility disclosure acceptance flag and timestamp (`setAccessibilityDisclosureAccepted`) in `SavedPreferencesLoader.kt` to guarantee auditability.
- [x] **Mandatory Accessibility Onboarding Gate:** Close the onboarding bypass loophole in `PermissionsScreen.kt` (line 115) so `canProceed` strictly requires `isAccessibilityGranted`.
- [x] **Screen Rotation State Preservation:** Add `rememberSaveable` state handling in `AmniSpaceActivity.kt` and `AmniSpaceBreathingCircle.kt`, implement `onSaveInstanceState` timer preservation in `WarningActivity.kt`, and dynamically adapt window insets and orientation offsets in `UsageStatOverlayManager.kt`.

#### Milestone 0.3.4: Dual-Host Cleanup & Navigation Decoupling (COMPLETED)
- [x] **Type-Safe Intent Routing:** Implement a sealed route contract (`FragmentDestination.kt`) between `MainActivity.kt` and `FragmentActivity.kt`, replacing loosely-typed string extras (`feature_type`, `fragment`), plus defense-in-depth hardening in `ApiPermissionActivity.kt`, `BootAndAlarmReceiver.kt`, and `AndroidManifest.xml`.
- [x] **SharedPreferences Synchronization Lock:** Implement static companion mutex synchronization locks (`rulesLock`, `statsLock`) in `SavedPreferencesLoader.kt` to prevent concurrent multi-threaded read/write file races across instances.
- [x] **Automated Multi-Flavor Matrix Testing:** Expand CI GitHub Actions (`main-pipeline.yml`) to execute parallel matrix builds and unit tests across `playstore`, `fdroid`, and `universal`.

#### Milestone 0.3.5: Pure Compose & Storage Architecture Epic (Prep for V1.0)
- [ ] **Room & DataStore Migration Epic:** Formalize a phased migration path from the 29+ SharedPreferences XML files to Room DB (relational blocking rules) and Preferences DataStore (flags and tokens), retiring the dead stubs in `data/db/`.
- [ ] **Pure-Compose Navigation Epic:** Reconnect and finalize `AmniShieldAdaptiveApp.kt` using modern Compose Navigation 2.8+, safely retiring the dual-host split (`MainActivity.kt` + `FragmentActivity.kt`) and legacy XML layouts.

---

## Section 8: Automated Post-Implementation Verification Suite (PowerShell)

Execute this script in Windows PowerShell from `d:\PROJECTS\Amni\AmniShield-Android` to verify both the documentation baseline (Phase A) and the Milestone 0.3.2 & 0.3.3 code implementations (Phases B & C):

```powershell
Write-Host "====================================================" -ForegroundColor Cyan
Write-Host " AmniShield-Android Post-v0.3.3 Verification Suite   " -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan

$ErrorCount = 0

# 1. Verify Zero Room / SQLite / DataStore dependencies
Write-Host "`n[TEST 1] Verifying absence of Room, SQLite, and DataStore dependencies..." -ForegroundColor Yellow
$deps = Select-String -Path "app\build.gradle.kts", "gradle\libs.versions.toml" -Pattern "androidx\.room|androidx\.sqlite|androidx\.datastore"
if ($deps.Count -eq 0) {
    Write-Host "  [PASS] Zero Room/SQLite/DataStore dependencies detected." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Unexpected persistence dependency found!" -ForegroundColor Red
    $ErrorCount++
}

# 2. Verify In-Memory Schedule Cache & Website Blocker Broadcast (Milestone 0.3.2)
Write-Host "`n[TEST 2] Verifying Schedule Cache & Website Blocker Broadcast..." -ForegroundColor Yellow
$cacheCheck = Select-String -Path "app\src\main\java\com\alhaq\amnishield\services\AmniShieldAccessibilityService.kt" -Pattern "INTENT_ACTION_REFRESH_WEBSITE_BLOCKER|refreshScheduleRulesCache"
if ($cacheCheck.Count -ge 4) {
    Write-Host "  [PASS] Schedule cache & INTENT_ACTION_REFRESH_WEBSITE_BLOCKER verified." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Missing schedule cache or website blocker broadcast!" -ForegroundColor Red
    $ErrorCount++
}

# 3. Verify Zero rootInActiveWindow Polling in Trackers & ReelActionHandler (Milestone 0.3.2)
Write-Host "`n[TEST 3] Verifying zero rootInActiveWindow polling in trackers & ReelActionHandler..." -ForegroundColor Yellow
$pollMatches = Select-String -Path `
    "app\src\main\java\com\alhaq\amnishield\trackers\ReelsSessionTracker.kt", `
    "app\src\main\java\com\alhaq\amnishield\trackers\BrowserSessionTracker.kt", `
    "app\src\main\java\com\alhaq\amnishield\blockers\ReelActionHandler.kt" `
    -Pattern "rootInActiveWindow"
if ($pollMatches.Count -eq 0) {
    Write-Host "  [PASS] Zero rootInActiveWindow calls in trackers & ReelActionHandler." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Found rootInActiveWindow in trackers or ReelActionHandler!" -ForegroundColor Red
    $ErrorCount++
}

# 4. Verify LauncherActivity is not exported (Milestone 0.3.2)
Write-Host "`n[TEST 4] Verifying LauncherActivity exported=false in AndroidManifest.xml..." -ForegroundColor Yellow
$launcherExported = Select-String -Path "app\src\main\AndroidManifest.xml" -Pattern 'LauncherActivity"[^>]*android:exported="true"'
if ($launcherExported.Count -eq 0) {
    Write-Host "  [PASS] LauncherActivity is android:exported=false." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] LauncherActivity is still exported=true!" -ForegroundColor Red
    $ErrorCount++
}

# 5. Verify Prominent Disclosure ScrollView, 48dp MinHeight, Non-Cancelable & Mandatory Gate (Milestone 0.3.3)
Write-Host "`n[TEST 5] Verifying Google Play Prominent Disclosure & Onboarding Gate..." -ForegroundColor Yellow
$hasScroll = Select-String -Path "app\src\main\res\layout\dialog_accessibility_disclosure.xml" -Pattern "<ScrollView"
$has48dp = Select-String -Path "app\src\main\res\layout\dialog_accessibility_disclosure.xml" -Pattern 'android:minHeight="48dp"'
$nonCancel = Select-String -Path "app\src\main\java\com\alhaq\amnishield\utils\AccessibilityDisclosureDialog.kt" -Pattern "setCancelable\(false\)"
$strictGate = Select-String -Path "app\src\main\java\com\alhaq\amnishield\ui\screens\PermissionsScreen.kt" -Pattern "val canProceed = isAccessibilityGranted$"
if ($hasScroll -and $has48dp.Count -ge 2 -and $nonCancel -and $strictGate) {
    Write-Host "  [PASS] Disclosure ScrollView, 48dp buttons, setCancelable(false), and mandatory gate verified." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Disclosure or onboarding gate check failed!" -ForegroundColor Red
    $ErrorCount++
}

# 6. Verify Zero Emojis across Docs & Source
Write-Host "`n[TEST 6] Verifying Strict Universal Zero-Emoji Policy..." -ForegroundColor Yellow
$docFiles = @("ARCHITECTURE.md", "README.md", "ROADMAP.md", ".agents\AGENTS.md", "AMNISHIELD_PROJECT_SUMMARY.md")
foreach ($f in $docFiles) {
    $text = [System.IO.File]::ReadAllText((Resolve-Path $f))
    if ([regex]::IsMatch($text, '[\uD800-\uDBFF][\uDC00-\uDFFF]|[\u2600-\u27BF]|\u200B')) {
        Write-Host "  [FAIL] Emoji or zero-width char found in $f" -ForegroundColor Red
        $ErrorCount++
    }
}
if ($ErrorCount -eq 0) {
    Write-Host "  [PASS] Zero emojis confirmed across all documentation files." -ForegroundColor Green
}

Write-Host "`n====================================================" -ForegroundColor Cyan
if ($ErrorCount -eq 0) {
    Write-Host " ALL POST-IMPLEMENTATION CHECKS PASSED!              " -ForegroundColor Green
} else {
    Write-Host " VERIFICATION FAILED WITH $ErrorCount ERRORS!        " -ForegroundColor Red
}
Write-Host "====================================================" -ForegroundColor Cyan
```
