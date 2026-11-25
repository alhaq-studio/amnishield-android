# AmniShield System Architecture & Engineering Guide

## Overview

AmniShield is an open-source, privacy-first digital wellness and content-blocking application for Android. It operates via a dedicated Android Accessibility Service pipeline, local SharedPreferences persistence (30 specialized XML files orchestrated by `SavedPreferencesLoader.kt`), a relational SQLite persistence layer (`AmniShieldRuleDatabase` for structured blocking rules, screen-time limits, and `UnifiedBlockRule` projections), a reactive Preferences DataStore layer (`AmniShieldDataStore`), hardware-backed EncryptedSharedPreferences strictly isolated to Supabase authentication tokens (`SecureKeyStore`), IPC via `GuardianContract` / `GuardianContentProvider`, and on-device diagnostic logging.

---

## Core Architectural Layers

```
+------------------------------------------------------------------------+
|               Dual-Host & Adaptive Compose Architecture                |
|  +------------------------------+    +------------------------------+  |
|  | MainActivity.kt              |    | FragmentActivity.kt          |  |
|  | 4 Bottom Nav Tabs (XML)      |    | Sub-Screen Router (Bridge)   |  |
|  | + mountAdaptiveComposeShell()|    | via FragmentDestination      |  |
|  +--------------+---------------+    +------------------------------+  |
|                 |                                                      |
|  +--------------v---------------------------------------------------+  |
|  | AmniShieldAdaptiveApp.kt & Unified Blocks Manager                |  |
|  | NavigationSuiteScaffold, BlocksManagerAdaptiveScreen,            |  |
|  | UnifiedBlocksDashboardScreen, CreateOrEditBlockScreen,           |  |
|  | SelectTargetsScreen, AlwaysOnShieldsComponents, Config Screens   |  |
|  +------------------------------------------------------------------+  |
+-----------------------------------+------------------------------------+
                                    |
+-----------------------------------v------------------------------------+
|                  ViewModel & Reactive State Management                 |
|                 (AmniShieldViewModel, AmniShieldState)                 |
+-----------------------------------+------------------------------------+
                                    |
+-----------------------------------v------------------------------------+
|                      Hybrid Local Persistence Layer                    |
|  +------------------------------+ +----------------------------------+ |
|  | AmniShieldRuleDatabase.kt    | | AmniShieldDataStore.kt           | |
|  | Relational SQLite Database   | | Jetpack Preferences DataStore    | |
|  | (AppBlockRule, ScreenTime,   | | (Flags, Onboarding Consent,      | |
|  |  UnifiedBlockRule projection)| |  Theme, PIN Settings)            | |
|  +--------------+---------------+ +-----------------+----------------+ |
|                 |                                   |                  |
|  +--------------v-----------------------------------v----------------+ |
|  | SavedPreferencesLoader.kt (30 Specialized SharedPreferences XML)  | |
|  | Mutex synchronization (rulesLock, statsLock), @Volatile caching,  | |
|  | UnifiedBlockRule + AlwaysOnShieldsConfig -> legacy engine sync    | |
|  +--------------+----------------------------------------------------+ |
|  +--------------v----------------------------------------------------+ |
|  | SecureKeyStore.kt (EncryptedSharedPreferences / AES256_GCM)       | |
|  | Isolated to Supabase sync secrets with fallback plain XML         | |
|  +-------------------------------------------------------------------+ |
+-----------------------------------+------------------------------------+
                                    |
+-----------------------------------v------------------------------------+
|                    AmniShieldAccessibilityService                      |
|         (Central Node Stream Orchestrator & Pipeline Host)             |
+-------+--------------+--------------+--------------+-------------------+
        |              |              |              |
+-------v------+ +-----v--------+ +---v----------+ +-v---------+ +---------v------------+
|AntiUninstall | | FocusMode    | |WebsiteBlock  | |AppBlocker | |Keyword & Reel Blocker|
|Interceptor   | | Blocker      | |  Detector    | |Interceptor| |Deferred Channel (P4) |
| (Priority 0) | | (Priority 1) | | (Priority 2) | |(Priority3)| |(Off-thread Worker)   |
+--------------+ +--------------+ +--------------+ +-----------+ +----------------------+
```

---

## Blocker Pipeline & Chain of Responsibility

The `AmniShieldAccessibilityService` orchestrates accessibility window inspection through explicit priority-ordered interceptors:

| Priority | Component | Target Surface / Vectors | Action Dispatched | Execution Model |
| :--- | :--- | :--- | :--- | :--- |
| **0 (Highest)** | `AntiUninstallDetector` | Play Store, OEM Security Centers, Device Admin, App Info | `GLOBAL_ACTION_HOME`, `AntiUninstallPasswordActivity` | Synchronous (Main Binder) |
| **1** | `FocusModeBlocker` | Active Focus Sessions & Whitelist Enforcements | Full-screen Focus Space (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` | Synchronous (Main Binder) |
| **2** | `WebsiteBlockerDetector` | Chrome, Samsung Internet, Firefox, Brave (15+ browsers) & Social Apps | Mindful Breathing (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` redirect | Synchronous (Cached blocklists + `DomainBlocklistEngine` 64-bit FNV-1a sorted hash matcher over gzipped `adult_domains.gz` / `gambling_domains.gz` assets, no disk I/O) |
| **3** | `AppBlocker` | Blocked Applications, Launch Limits, Daily Usage Limits | Mindful Breathing (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` overlay | Synchronous (O(1) cached schedule checks, async UsageStats refresh on `Dispatchers.IO`) |
| **4** | `KeywordBlocker` & `ReelBlocker` | Search bars, query inputs, and Short-form Video feeds (YouTube, IG, FB, TikTok) | Text erasure, scroll limit counter (`LIMIT_BY_COUNT` or 24/7 block), feed suppression | Deferred Coroutine Worker (`Channel.CONFLATED` on `Dispatchers.Default`) |

---

## Unified Blocks Manager & Always-On Shields Architecture

1. **Always-On Shields (`AlwaysOnShieldsConfig.kt`)**:
   - Unified configuration for **Short Video & Reels Guard** (`ReelsGuardConfig`), **Permanent Website Guard** (`WebsiteGuardConfig`), and **Keyword & Search Filter** (`KeywordGuardConfig`).
   - Supports asymmetric lock-mode friction (`ShieldLockConfig`): weakening an active lock (reducing `strengthRank`, switching to `NONE`, disabling a shield, or shortening `PAUSE_DELAY` duration) requires completing the current lock challenge first. Pro expiry never blocks unlocking or downgrading an active Pro lock.
   - `onRefreshAlwaysOnShields()` reloads configuration on `Dispatchers.IO` before updating `@Volatile` accessibility snapshots.
2. **Unified Block Cards (`UnifiedBlockRule.kt`)**:
   - Combines App Blocking, Daily/Hourly Launch Limits (`LimitPeriod`), Weekly Schedules (`WeeklyScheduleTrigger`), and Focus Mode presets into a single rule model.
   - Domain normalization (`WebsiteGuardConfig.normalizeDomainInput`) and keyword filtering remain strictly isolated across storage, UI validation, and runtime enforcement.
   - Saved `UnifiedBlockRule` lists are synchronized into legacy engine prefs, `@Volatile` accessibility caches, and `AmniShieldRuleDatabase` (`syncUnifiedBlockRules`).
3. **Unified UI & Compose Sub-Screen Flows**:
   - `BlocksManagerAdaptiveScreen.kt` (within `NavigationSuiteScaffold`) and `BlocksManagerFragment.kt` (via `FragmentDestination.BlocksManager`) host `UnifiedBlocksDashboardScreen`, `CreateOrEditBlockScreen`, `SelectTargetsScreen`, and `WeeklyUsageReportScreen`.
   - `CreateOrEditBlockScreen` preserves draft state across configuration changes via Gson-backed `rememberSaveable`, validates non-empty targets and blank QR/barcode payloads, and enforces `AuthType.RULE_PIN` / Global PIN without double-prompting.
   - Dedicated feature config Compose screens (`AppBlockerConfigScreen`, `ReelBlockerConfigScreen`, `WebsiteBlockerConfigScreen`, `KeywordBlockerConfigScreen`, `FocusModeConfigScreen`) serve as direct configuration surfaces backed by `SavedPreferencesLoader` and `ServiceBroadcastManager` package-scoped refreshes.

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
> **Node Lifecycle Invariant:** `AmniShieldAccessibilityService` exclusively owns the lifecycle of `rootNode` (recycled once in the `finally` block of `onAccessibilityEvent`). Interceptors and sub-detectors must **never** call `rootNode.recycle()`. Interceptors must only recycle child nodes they generate during internal stack traversals inside `try ... finally`.

> [!TIP]
> **Strict Short-Circuiting:** When a higher-priority interceptor (such as Anti-Uninstall) triggers an action, return immediately to stop processing downstream content blockers.

> [!NOTE]
> **Persistence & Sync Architecture:** Local persistence is driven by `SavedPreferencesLoader.kt` (30 SharedPreferences XML files with `rulesLock`/`statsLock` mutexes and `@Volatile` schedule cache), relational SQLite projection via `AmniShieldRuleDatabase` (including `syncUnifiedBlockRules`), `AmniShieldDataStore` for feature flags/onboarding consent/theme, and `SecureKeyStore.kt` (`EncryptedSharedPreferences`) strictly for sync secrets. Cross-device Cloud Sync remains gated behind `Coming Soon (Milestone V1.1)` with `SyncWorker` dormant.

> [!NOTE]
> **Dual-Host & Adaptive Presentation:** UI presentation is hosted by `MainActivity.kt` (supporting 4 bottom navigation tabs and the pure-Compose adaptive shell `AmniShieldAdaptiveApp.kt` via `mountAdaptiveComposeShell()`), while `FragmentActivity.kt` routes secondary screens via the type-safe `FragmentDestination.kt` sealed route contract. `GuardianContract` & `GuardianContentProvider` expose read-only IPC gated by `com.alhaq.amnishield.permission.GUARDIAN_IPC`.

> [!CAUTION]
> **Main Binder Thread & Broadcast Safety:** Reflection-based Gson deserialization, UsageStats queries, and disk writes must never run synchronously on the Main Binder Thread during `onAccessibilityEvent`. All dynamic broadcast receivers use `ContextCompat.RECEIVER_NOT_EXPORTED`, and internal refresh intents scope the package via `intent.setPackage(context.packageName)`.

> [!IMPORTANT]
> **Strict Universal Zero-Emoji Policy:** Do not use UTF-8 emojis anywhere in code, UI layouts, strings, comments, logs, or documentation. Use plain-text tags (`[INFO]`, `[OK]`, `[FAIL]`, `[WARNING]`, `[ERROR]`) instead.

> [!NOTE]
> **Multi-Flavor Build Configuration:** Always build with flavor-qualified Gradle tasks:
>
> - `playstoreDebug`: Google Play Store distribution
> - `fdroidDebug`: Pure open-source build (no proprietary GMS/Firebase/Play Billing SDKs) with offline ECDSA license validation
> - `universalDebug`: Universal sideload build
