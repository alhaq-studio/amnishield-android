# AmniShield-Android Workspace Agent Configuration

This repository (`AmniShield-Android` / `alhaq-studio/amnishield-android`, branch `feat/redesign-blocks-manager`) is part of the **AmniShield Ecosystem**.

## Active Agent Profile: `amni-ecosystem-agent`

- Full Architecture, Roadmap, and Cross-Platform Specifications: `~/.gemini/config/agents/amn-ecosystem-agent.md`

## Enabled Skills

- `android-fullstack-dev`: Modern Jetpack Compose, Kotlin Flow, Material 3, Accessibility Service node inspection, relational SQLite persistence (`AmniShieldRuleDatabase.kt`), Preferences DataStore (`AmniShieldDataStore.kt`), SharedPreferences persistence (`SavedPreferencesLoader.kt`), isolated EncryptedSharedPreferences (`SecureKeyStore.kt`).
- `security-compliance`: ECDSA NIST P-256 license validation (`LicenseValidator.kt`), zero-knowledge privacy, R8 minification.
- `stripe-payments`: `PaymentManager.kt` & Stripe checkout integration.
- `supabase-backend`: Supabase Edge Functions & user license record synchronization.
- `marketing-growth`: App Store Optimization, Play Store release notes, F-Droid metadata.

## Current Project Focus

- **Version**: `0.3.5 (2026.10.21)` / `versionCode 145` (Namespace: `com.alhaq.amnishield`, ApplicationId: `com.alhaq.deenshield`).
- **Completed Baseline Milestones**:
  - Milestone 0.3.2 (Accessibility Engine Hardening & Binder Safety)
  - Milestone 0.3.3 (Google Play Store Compliance & Layout Resilience)
  - Milestone 0.3.4 (Dual-Host Cleanup, Type-Safe Routing & SharedPreferences Synchronization)
  - Milestone 0.3.5 (Pure Compose & Storage Architecture Epic)
- **Active Redesign Integration (`feat/redesign-blocks-manager`)**:
  - Unified Blocks Manager (`UnifiedBlocksDashboardScreen`, `CreateOrEditBlockScreen`, `SelectTargetsScreen`, `BlocksManagerAdaptiveScreen`), `UnifiedBlockRule` multi-schedule/limit projection, and `AlwaysOnShieldsConfig` with asymmetric friction locks (`ShieldLockConfig`).
  - Cloud Sync & Device Pairing remain gated behind `"Coming Soon (Milestone V1.1)"` with `SyncWorker` dormant.
- **Primary Goal**: Finalize V1.0 Release Preparation, automated unit/instrumented tests, and multi-flavor production distribution builds.

---

## Mandatory Architectural Invariants & Agent Guardrails

1. **Persistence Architecture (Synchronized Hybrid Engine):**
   - Local persistence is driven by a synchronized hybrid storage model:
     - `SavedPreferencesLoader.kt` orchestrates 30 specialized XML files with Gson JSON serialization, in-memory `@Volatile` schedule caching, and static companion mutex locks (`rulesLock` / `statsLock`).
     - `AmniShieldRuleDatabase.kt` provides relational SQLite persistence for app blocking rules, screen-time launch limits, and `UnifiedBlockRule` projections (`syncUnifiedBlockRules`) with schema version migrations (V1 -> V2) and reactive `StateFlow` streams.
     - `AmniShieldDataStore.kt` provides reactive Preferences DataStore streaming for core feature flags, onboarding consent, theme, and PIN security.
     - `EncryptedSharedPreferences` is strictly isolated to `SecureKeyStore.kt` (`amnishield_sync_secrets.xml`).
2. **Dual-Host & Adaptive Presentation Reality:**
   - Presentation layer supports both legacy XML tab navigation and modern pure-Compose adaptive shells:
     - `MainActivity.kt` hosts bottom navigation tabs and provides `mountAdaptiveComposeShell()` to activate `AmniShieldAdaptiveApp.kt` (`NavigationSuiteScaffold` + `NavHost`).
     - `FragmentActivity.kt` provides secondary sub-screen routing for configuration and dashboard screens embedding `ComposeView` via the type-safe `FragmentDestination.kt` sealed route contract.
     - `BlocksManagerAdaptiveScreen.kt` hosts the full Unified Blocks Manager flow (`UnifiedBlocksDashboardScreen`, `CreateOrEditBlockScreen`, `SelectTargetsScreen`, `WeeklyUsageReportScreen`).
3. **Blocker Pipeline Priority Order (`AmniShieldAccessibilityService.kt`):**
   - Priority 0: `AntiUninstallDetector` -> Priority 1: `FocusModeBlocker` -> Priority 2: `WebsiteBlockerDetector` -> Priority 3: `AppBlocker` -> Priority 4: `KeywordBlocker` & `ReelBlocker` (via `Channel.CONFLATED` on `Dispatchers.Default`).
4. **Main Binder Thread, Broadcast & Concurrency Guardrails:**
   - Never invoke reflection-based `Gson().fromJson()`, `UsageStats` queries, or synchronous disk I/O on the Main Binder Thread during `onAccessibilityEvent` or `onRefreshAlwaysOnShields()` / `onRefreshWebsiteBlocker()`; load config on `Dispatchers.IO` before updating `@Volatile` caches and invalidate via `SavedPreferencesLoader.invalidateScheduleRulesCache()`.
   - Never poll `service.rootInActiveWindow` on periodic `Handler` timers; inspect nodes passed from `onAccessibilityEvent` and offload disk writes to `Dispatchers.IO`.
   - In `processDeferredChecks()`, always verify `rootPackage.equals(packageName, ignoreCase = true)` and `SystemExclusionManager.isExempt(...)` before evaluating deferred blockers.
   - All dynamic broadcast receivers must use `ContextCompat.RECEIVER_NOT_EXPORTED`, and internal refresh broadcasts must call `intent.setPackage(context.packageName)`.
5. **AccessibilityNodeInfo Lifecycle & Exception Safety:**
   - `AmniShieldAccessibilityService` exclusively owns `rootNode` and recycles it in `finally`. Sub-detectors (`ReelActionHandler`, etc.) must never recycle `rootNode`.
   - All iterative (`ArrayDeque`) and recursive child node traversals (`AntiUninstallDetector`, `ReelDetectionEngine`) must wrap child inspection in `try ... finally` to guarantee child node recycling if an exception occurs.
6. **Google Play Prominent Disclosure, Asymmetric Friction & Pro Expiry Safety:**
   - `AccessibilityDisclosureDialog` (XML and Compose) must remain non-cancelable, persist affirmative consent via `SavedPreferencesLoader` and `AmniShieldDataStore`, use scrollable containers with `minHeight="48dp"` buttons, and `PermissionsScreen` must require `isAccessibilityGranted` to proceed.
   - Always-On Shield weakening (`PAUSE_DELAY` shortening, rank reduction, switching to `NONE`, or disabling an actively locked shield) must require completing the current lock challenge first, and expired Pro status must never trap a user from unlocking or downgrading an active Pro lock.
7. **Strict Universal Zero-Emoji Policy:**
   - Zero UTF-8 emojis anywhere in code, XML, Compose UI, strings, logs, comments, commit messages, or documentation.
