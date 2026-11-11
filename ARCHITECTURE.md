# AmniShield System Architecture & Engineering Guide

## Overview

AmniShield is an open-source, privacy-first digital wellness and content-blocking application for Android. It operates via a dedicated Android Accessibility Service pipeline, local SharedPreferences persistence (30 specialized XML files orchestrated by `SavedPreferencesLoader.kt`), a relational SQLite persistence layer (`AmniShieldRuleDatabase` for structured blocking rules and screen-time limits), a reactive Preferences DataStore layer (`AmniShieldDataStore`), hardware-backed EncryptedSharedPreferences strictly isolated to Supabase authentication tokens (`SecureKeyStore`), and on-device diagnostic logging.

---

## Core Architectural Layers

```
┌────────────────────────────────────────────────────────────────────────┐
│               Dual-Host & Adaptive Compose Architecture                │
│  ┌──────────────────────────────┐    ┌──────────────────────────────┐  │
│  │ MainActivity.kt              │    │ FragmentActivity.kt          │  │
│  │ 4 Bottom Nav Tabs (XML)      │    │ 18 Sub-Screen Router (Bridge)│  │
│  │ + mountAdaptiveComposeShell()│    │ Embedding ComposeView M3 UI  │  │
│  └──────────────┬───────────────┘    └──────────────────────────────┘  │
│                 │                                                      │
│  ┌──────────────▼───────────────────────────────────────────────────┐  │
│  │ AmniShieldAdaptiveApp.kt (Adaptive Navigation & M3 Scaffolds)    │  │
│  │ BlocksManagerAdaptiveScreen, StatsAdaptiveScreen, AppDestinations│  │
│  └──────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                  ViewModel & Reactive State Management                 │
│                 (AmniShieldViewModel, AmniShieldState)                 │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                      Hybrid Local Persistence Layer                    │
│  ┌──────────────────────────────┐ ┌──────────────────────────────────┐ │
│  │ AmniShieldRuleDatabase.kt    │ │ AmniShieldDataStore.kt           │ │
│  │ Relational SQLite Database   │ │ Jetpack Preferences DataStore    │ │
│  │ (AppBlockRule, ScreenTime)   │ │ (Flags, Theme, PIN Settings)     │ │
│  └──────────────┬───────────────┘ └─────────────────┬────────────────┘ │
│                 │                                   │                  │
│  ┌──────────────▼───────────────────────────────────▼────────────────┐ │
│  │ SavedPreferencesLoader.kt (30 Specialized SharedPreferences XML)  │ │
│  │ Mutex synchronization (rulesLock, statsLock), in-memory caching   │ │
│  └──────────────────────────────┬────────────────────────────────────┘ │
│  ┌──────────────────────────────▼────────────────────────────────────┐ │
│  │ SecureKeyStore.kt (EncryptedSharedPreferences / AES256_GCM)       │ │
│  │ Isolated to Supabase sync secrets with fallback plain XML         │ │
│  └───────────────────────────────────────────────────────────────────┘ │
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
| **0 (Highest)** | `AntiUninstallDetector` (Line 300) | Play Store, OEM Security Centers, Device Admin, App Info | `GLOBAL_ACTION_HOME`, `AntiUninstallPasswordActivity` | Synchronous (Main Binder) |
| **1** | `FocusModeBlocker` (Line 321) | Active Focus Sessions & Whitelist Enforcements | Full-screen Focus Space (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` | Synchronous (Main Binder) |
| **2** | `WebsiteBlockerDetector` (Line 367) | Chrome, Samsung Internet, Firefox, Brave (15+ browsers) & Social Apps | Mindful Breathing (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` redirect | Synchronous (Linear scan of 1-20 active schedule targets) |
| **3** | `AppBlocker` (Line 434) | Blocked Applications, Launch Limits, Daily Usage Limits | Mindful Breathing (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` overlay | Synchronous (Main Binder) |
| **4** | `KeywordBlocker` & `ReelBlocker` (Lines 473, 507-598) | Search bars, query inputs, and Short-form Video feeds (YouTube, IG, FB, TikTok) | Text erasure, scroll limit counter, feed suppression | Deferred Coroutine Worker (`Channel.CONFLATED`) |

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
> **Persistence Architecture:** Persistence is supported by a synchronized hybrid engine: `SavedPreferencesLoader.kt` orchestrates 30 SharedPreferences XML files with companion mutex locks (`rulesLock`, `statsLock`) and in-memory `@Volatile` schedule caching for zero Main Binder thread latency. Relational rules are persisted in SQLite via `AmniShieldRuleDatabase`, core toggles in `AmniShieldDataStore`, and cloud sync tokens in `SecureKeyStore.kt` (`EncryptedSharedPreferences`).

> [!NOTE]
> **Dual-Host & Adaptive Presentation:** UI presentation is hosted by `MainActivity.kt` (supporting both legacy 4 bottom navigation tabs and the pure-Compose adaptive shell `AmniShieldAdaptiveApp.kt` via `mountAdaptiveComposeShell()`), while `FragmentActivity.kt` provides secondary sub-screen routing via the type-safe `FragmentDestination.kt` sealed route contract.

> [!CAUTION]
> **Main Binder Thread Safety:** Reflection-based Gson deserialization in `onAccessibilityEvent` is strictly avoided via thread-safe in-memory rule caching (`@Volatile` + `synchronized(scheduleCacheLock)`) and invalidated via `ServiceBroadcastManager` to prevent Binder thread jank.

> [!IMPORTANT]
> **Strict Universal Zero-Emoji Policy:** Do not use UTF-8 emojis anywhere in code, UI layouts, strings, comments, logs, or documentation. Use plain-text tags (`[INFO]`, `[OK]`, `[FAIL]`, `[WARNING]`, `[ERROR]`) instead.

> [!NOTE]
> **Multi-Flavor Build Configuration:** Always build with flavor-qualified Gradle tasks:
>
> - `playstoreDebug`: Google Play Store distribution
> - `fdroidDebug`: Pure open-source build with offline ECDSA license validation
> - `universalDebug`: Universal sideload build
