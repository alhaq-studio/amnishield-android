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
- **Completed Milestones**: Milestone 0.3.2 (Accessibility Engine Hardening & Binder Safety), Milestone 0.3.3 (Google Play Store Compliance & Layout Resilience), and Milestone 0.3.4 (Dual-Host Cleanup, Type-Safe Routing & SharedPreferences Synchronization).
- **Primary Goal**: Milestone 0.3.5 (Pure Compose & Storage Architecture Epic).

---

## Mandatory Architectural Invariants & Agent Guardrails

1. **Persistence Reality (Zero Room / Zero DataStore):**
   - Local persistence is 100% `SharedPreferences` orchestrated via `SavedPreferencesLoader.kt` (30 specialized XML files in loader, 37+ total XML files, Gson JSON serialization, in-memory `@Volatile` schedule caching, and static companion mutex locks `rulesLock` / `statsLock`).
   - `EncryptedSharedPreferences` is strictly isolated to `SecureKeyStore.kt` (`amnishield_sync_secrets.xml`).
   - Do **not** import `androidx.room` or `androidx.datastore` or reference the unused stub classes in `data/db/` until Milestone 0.3.5.
2. **Dual-Host Presentation Reality:**
   - Top-level UI is hosted by `MainActivity.kt` (4 XML bottom navigation tabs) and `FragmentActivity.kt` (secondary router for 18+ feature/config fragments embedding `ComposeView`), connected via the type-safe `FragmentDestination.kt` sealed route contract.
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
