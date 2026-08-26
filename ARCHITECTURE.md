# AmniShield System Architecture & Engineering Guide

## Overview

AmniShield is an open-source, privacy-first digital wellness and content-blocking application for Android. It operates via a dedicated Android Accessibility Service pipeline, local SQLite/Room data caching, encrypted SharedPreferences, and on-device diagnostic logging.

---

## 🏗️ Core Architectural Layers

```
┌────────────────────────────────────────────────────────┐
│               Jetpack Compose Material 3 UI            │
│  (StatsScreen, BlocksManager, FocusSpace, Diagnostics)  │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│          ViewModel & Reactive State Management         │
│          (AmnShieldViewModel, AmnShieldState)           │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│            AmnShieldAccessibilityService               │
│  (Central Node Stream Orchestrator & Pipeline Host)     │
└──────┬───────────────────┬───────────────────┬─────────┘
       │                   │                   │
┌──────▼────────┐   ┌──────▼────────┐   ┌──────▼────────┐
│ AntiUninstall │   │  AppBlocker   │   │  ReelBlocker  │
│  Interceptor  │   │  FocusMode    │   │ & WebsiteFilt │
│ (Priority 0)  │   │ (Priority 1)  │   │ (Priority 2)  │
└───────────────┘   └───────────────┘   └───────────────┘
```

---

## 🛡️ Blocker Pipeline & Chain of Responsibility

The `AmnShieldAccessibilityService` orchestrates accessibility window inspection through explicit priority-ordered interceptors:

| Priority | Component | Target Surface / Vectors | Action Dispatched |
| :--- | :--- | :--- | :--- |
| **0 (Highest)** | `AntiUninstallDetector` | Play Store, OEM Security Centers, Device Admin, App Info | `GLOBAL_ACTION_HOME`, `AntiUninstallPasswordActivity` |
| **1** | `AppBlocker` | Blocked Applications, Launch Limits, Daily Usage Limits | `WarningActivity` overlay or task cancellation |
| **1** | `FocusModeBlocker` | Active Focus Sessions & Whitelist Enforcements | Full-screen Focus Space overlay |
| **2** | `WebsiteBlockerDetector` | Chrome, Samsung Internet, Firefox, Brave (15+ browsers) | `WarningActivity` redirect / Back navigation |
| **2** | `KeywordActionHandler` | Search bars, social feed text inputs, query boxes | Real-time text erasure & warning overlay |
| **3** | `ReelBlocker` | YouTube Shorts, IG Reels, FB Reels, TikTok feeds | Scroll limit counter, feed suppression |

---

## 📝 Diagnostic & Crash Logging Architecture (`CrashLogger`)

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

## 🔒 Invariants & Guardrails for AI & Human Maintainers

> [!IMPORTANT]
> **Node Lifecycle Invariant:** `AmnShieldAccessibilityService` exclusively owns the lifecycle of `rootNode` (recycled once in the `finally` block of `onAccessibilityEvent`). Interceptors and sub-detectors must **never** call `rootNode.recycle()`. Interceptors must only recycle child nodes they generate during internal stack traversals.

> [!TIP]
> **Strict Short-Circuiting:** When a higher-priority interceptor (such as Anti-Uninstall) triggers an action, return immediately to stop processing downstream content blockers.

> [!NOTE]
> **Multi-Flavor Build Configuration:** Always build with flavor-qualified Gradle tasks:
> - `playstoreDebug`: Google Play Store distribution
> - `fdroidDebug`: Pure open-source build with offline ECDSA license validation
> - `universalDebug`: Universal sideload build
