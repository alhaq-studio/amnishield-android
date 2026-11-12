# AmniShield-Android Workspace Agent Configuration

This repository (`AmniShield-Android` / `alhaq-studio/amnishield-android`) is part of the **AmniShield Ecosystem**.

## Active Agent Profile: `amni-ecosystem-agent`

- Full Architecture, Roadmap, and Cross-Platform Specifications: `~/.gemini/config/agents/amni-ecosystem-agent.md`

## Enabled Skills

- `android-fullstack-dev`: Modern Jetpack Compose, Kotlin Flow, Material 3, Accessibility Service node inspection, relational SQLite persistence (`AmniShieldRuleDatabase.kt`), Preferences DataStore (`AmniShieldDataStore.kt`), SharedPreferences persistence (`SavedPreferencesLoader.kt`), isolated EncryptedSharedPreferences (`SecureKeyStore.kt`).
- `security-compliance`: ECDSA NIST P-256 license validation (`LicenseValidator.kt`), zero-knowledge privacy, R8 minification.
- `stripe-payments`: `PaymentManager.kt` & Stripe checkout integration.
- `supabase-backend`: Supabase Edge Functions & user license record synchronization.
- `marketing-growth`: App Store Optimization, Play Store release notes, F-Droid metadata.

## Current Project Focus

- **Version**: `0.3.5 (2026.10.21)` / `versionCode 145` (Namespace: `com.alhaq.amnishield`, ApplicationId: `com.alhaq.deenshield`).
- **Completed Milestones**:
  - Milestone 0.3.2 (Accessibility Engine Hardening & Binder Safety)
  - Milestone 0.3.3 (Google Play Store Compliance & Layout Resilience)
  - Milestone 0.3.4 (Dual-Host Cleanup, Type-Safe Routing & SharedPreferences Synchronization)
  - Milestone 0.3.5 (Pure Compose & Storage Architecture Epic)
- **Primary Goal**: Finalize V1.0 Release Preparation, automated instrumented CI tests, and production distribution builds.

---

## Mandatory Architectural Invariants & Agent Guardrails

1. **Persistence Architecture (Synchronized Hybrid Engine):**
   - Local persistence is driven by a synchronized hybrid storage model:
     - `SavedPreferencesLoader.kt` orchestrates 30 specialized XML files with Gson JSON serialization, in-memory `@Volatile` schedule caching, and static companion mutex locks (`rulesLock` / `statsLock`).
     - `AmniShieldRuleDatabase.kt` provides relational SQLite persistence for app blocking rules and screen-time launch limits with schema version migrations (V1 -> V2) and reactive `StateFlow` streams.
     - `AmniShieldDataStore.kt` provides reactive Preferences DataStore streaming for core feature flags, onboarding consent, theme, and PIN security.
     - `EncryptedSharedPreferences` is strictly isolated to `SecureKeyStore.kt` (`amnishield_sync_secrets.xml`).
2. **Dual-Host & Adaptive Presentation Reality:**
   - Presentation layer supports both legacy XML tab navigation and modern pure-Compose adaptive shells:
     - `MainActivity.kt` hosts bottom navigation tabs and provides `mountAdaptiveComposeShell()` to activate `AmniShieldAdaptiveApp.kt` (`NavigationSuiteScaffold` + `NavHost`).
     - `FragmentActivity.kt` provides secondary sub-screen routing for 18+ configuration fragments embedding `ComposeView` via the type-safe `FragmentDestination.kt` sealed route contract.
3. **Blocker Pipeline Priority Order (`AmniShieldAccessibilityService.kt`):**
   - Priority 0: `AntiUninstallDetector` -> Priority 1: `FocusModeBlocker` -> Priority 2: `WebsiteBlockerDetector` -> Priority 3: `AppBlocker` -> Priority 4: `KeywordBlocker` & `ReelBlocker` (via `Channel.CONFLATED` on `Dispatchers.Default`).
4. **Main Binder Thread & Concurrency Guardrails:**
   - Never invoke reflection-based `Gson().fromJson()` on the Main Binder Thread during `onAccessibilityEvent`; always read from the in-memory schedule cache and invalidate via `SavedPreferencesLoader.invalidateScheduleRulesCache()`.
   - Never poll `service.rootInActiveWindow` on periodic `Handler` timers; session trackers (`ReelsSessionTracker`, `BrowserSessionTracker`) must inspect nodes passed from `onAccessibilityEvent` and offload disk writes to `Dispatchers.IO`.
   - In `processDeferredChecks()`, always verify `rootPackage.equals(packageName, ignoreCase = true)` and `SystemExclusionManager.isExempt(...)` before evaluating deferred blockers.
5. **AccessibilityNodeInfo Lifecycle & Exception Safety:**
   - `AmniShieldAccessibilityService` exclusively owns `rootNode` and recycles it in `finally`. Sub-detectors must never recycle `rootNode`.
   - All iterative (`ArrayDeque`) and recursive child node traversals (`AntiUninstallDetector`, `ReelDetectionEngine`) must wrap child inspection in `try ... finally` to guarantee child node recycling if an exception occurs.
6. **Google Play Prominent Disclosure & Onboarding Compliance:**
   - `AccessibilityDisclosureDialog` must remain non-cancelable (`.setCancelable(false)`), persist affirmative consent via `SavedPreferencesLoader` and `AmniShieldDataStore`, use a scrollable container with `minHeight="48dp"` (`wrap_content`) buttons, and `PermissionsScreen` must require `isAccessibilityGranted` to proceed.
7. **Strict Universal Zero-Emoji Policy:**
   - Zero UTF-8 emojis anywhere in code, XML, Compose UI, strings, logs, comments, commit messages, or documentation.
