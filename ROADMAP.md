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
- [x] **Type-Safe Intent Routing:** Implement a sealed route contract (`FragmentDestination.kt`) between `MainActivity.kt` and `FragmentActivity.kt`, replacing loosely-typed string extras (`feature_type`, `fragment`), plus defense-in-depth hardening in `ApiPermissionActivity.kt`, `BootAndAlarmReceiver.kt`, and `AndroidManifest.xml`.
- [x] **SharedPreferences Synchronization Lock:** Implement static companion mutex synchronization locks (`rulesLock`, `statsLock`) in `SavedPreferencesLoader.kt` to prevent concurrent multi-threaded read/write file races across instances.
- [x] **Automated Multi-Flavor Matrix Testing:** Expand CI GitHub Actions (`main-pipeline.yml`) to execute parallel matrix builds and unit tests across `playstore`, `fdroid`, and `universal`.

#### Milestone 0.3.5: Pure Compose & Storage Architecture Epic (Prep for V1.0)
- [x] **Room & DataStore Migration Epic:** Relational SQLite persistence engine (`AmniShieldRuleDatabase`, `AppBlockRule`, `ScreenTimeLimitRule`, `AmniShieldDatabaseMigrations`) and reactive Preferences DataStore (`AmniShieldDataStore`) integrated with `SavedPreferencesLoader.kt` for seamless migration and zero Main Binder thread latency.
- [x] **Pure-Compose Navigation Epic:** Reconnected and verified `AmniShieldAdaptiveApp.kt` using modern Compose Navigation, adaptive multi-pane scaffolds (`BlocksManagerAdaptiveScreen`, `StatsAdaptiveScreen`), type-safe sub-destinations (`AppDestinations.kt`), and dynamic mounting in `MainActivity.kt` (`mountAdaptiveComposeShell()`).

#### Milestone 0.3.6: Ecosystem Cloud Sync & Multi-Platform Policy Payload v2 Architecture
- [x] **Policy Payload v2 Engine:** Full implementation and migration in `PolicySyncManager.kt` handling structured `websites`, `keywords`, `apps`, `schedules`, and `settings` with conflict resolution and cryptographic verification.
- [x] **Supabase REST & Realtime Integration:** Zero-dependency, lightweight REST client (`SupabaseRest.kt`) supporting user auth token lifecycle, device heartbeat, policy pulling/pushing, and pairing flows.
- [x] **Adaptive UI & Profile Alignment:** Account authentication, pairing modal, and manual/auto sync toggles in `ProfileScreen.kt`, `SettingsScreen.kt`, and `AmniShieldAdaptiveApp.kt` with live status indicators and zero telemetry compliance.
- [x] **Comprehensive Sync Unit Testing:** Added rigorous suite (`PolicySyncManagerTest.kt`) verifying schema migration, payload transformation, schedule conversion, and null-safe deserialization.


---

## Architectural Standards & Development Invariants

1. **Accessibility Node Lifecycle Invariant:** `AmniShieldAccessibilityService` exclusively owns the lifecycle of `rootNode`. Sub-interceptors and detectors must **never** call `rootNode.recycle()`.
2. **Deterministic Priority (Fail-Safe First):** Security and Anti-Uninstall evaluation runs first at Priority 0.
3. **Zero External Telemetry:** All diagnostic logs, crash reports, and usage metrics remain 100% on-device in app-private storage.
4. **PII Sanitization Rule:** Sensitive tokens, passwords, PINs, auth headers, and emails are stripped by regex filters before being written to disk.
5. **Zero Emoji Policy:** Strict universal rule prohibiting unicode emojis across all code, UI layouts, strings, commit messages, and documentation.
