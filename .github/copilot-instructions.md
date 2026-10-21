# AmniShield AI Development Assistant - Copilot Instructions

You are an expert Android/Kotlin developer working on **AmniShield** - an open-source, privacy-first digital wellness and distraction blocker for Android. AmniShield intercepts addictive short-form video reels, enforces scheduled app and website blocking, keyword filtering, and strict anti-bypass modes without transmitting any screen data or telemetry to remote servers.

---

## Project Metadata

- **Current Version**: `0.3.1 (2026.10.05)` (versionCode `145`)
- **Application ID**: `com.alhaq.deenshield` (legacy Play Store binding; do NOT change)
- **Codebase Namespace**: `com.alhaq.amnishield`
- **Target SDK**: Android 15 (API 36)
- **Compile SDK**: API 36
- **Min SDK**: Android 8.0 (API 26)
- **JDK Target**: JDK 17
- **Repository**: https://github.com/alhaq-studio/amnishield-android

---

## STRICT UNIVERSAL UI & CODE QUALITY RULE: NO EMOJIS IN CODE OR UI

1. **ZERO EMOJI POLICY**: Do NOT use UTF-8 emojis or emoji-based unicode characters anywhere in code, UI layouts, components, strings, comments, logs, or commit messages across ANY project type (Android, Web/React, Desktop, VS Code extensions, or CLI).
2. **USE PRODUCTION ICON LIBRARIES ONLY**:
   - Android (Kotlin/Compose/XML): Use Vector Drawables (`ic_*.xml`), Material Symbols, or Compose `ImageVector`.
   - Web & React (TSX/JSX/HTML): Use Lucide React (`lucide-react`), Material UI Icons (`@mui/icons-material`), FontAwesome, or inline SVGs.
   - Desktop & CLI: Use standard UI icon fonts, native framework drawables, or clean plain-text tags (`[OK]`, `[INFO]`, `[FAIL]`, `[WARNING]`, `[ERROR]`).
3. **ACCESSIBILITY & STYLING COMPLIANCE**:
   - Icons must support theme-based color tinting (CSS variables, dynamic color, or vector tinting).
   - Icons must use explicit `contentDescription` / `aria-label` attributes instead of unicode strings.

---

## Architecture Overview & Codebase Reality

### Tech Stack
- **Language**: 100% Kotlin (JVM target 17, Coroutines, StateFlow)
- **UI Framework**: Dual-Host architecture bridging AndroidX Activities with Jetpack Compose Material 3
- **Build System**: Gradle 9.5+ with Kotlin DSL (`build.gradle.kts`)
- **State Management**: 100% SharedPreferences via `SavedPreferencesLoader.kt` with Google Gson JSON serialization
- **Secrets Storage**: Hardware-backed `EncryptedSharedPreferences` strictly isolated to Supabase cloud sync tokens via `SecureKeyStore.kt`
- **Licensing**: Offline-first cryptographic NIST P-256 ECDSA public key verification (`SHA256withECDSA` in `LicenseValidator.kt`) for F-Droid and universal builds; Google Play `BillingClientWrapper.kt` for `playstore` flavor

### Dual-Host Presentation Architecture
1. **`MainActivity.kt` (2,022 lines)**: Hosts the primary 4 bottom navigation tabs (`Stats`, `Blocks`, `Focus`, `Advanced`) using legacy `supportFragmentManager` transactions.
2. **`FragmentActivity.kt` (210 lines)**: Acts as a secondary router dispatching 18 functional sub-screens and configuration dialogs. Each configuration fragment inherits `BaseFeatureFragment` and returns `ComposeView` embedding Material 3 Compose screens (`AppBlockerConfigScreen`, `ReelBlockerConfigScreen`, `WebsiteBlockerConfigScreen`, `DiagnosticsScreen`, etc.).
3. **`AmniShieldAdaptiveApp.kt` (254 lines)**: Pure Compose `NavigationSuiteScaffold` shell temporarily disconnected to avoid FragmentManager lifecycle crashes; preserved as the target architecture for Milestone 0.3.5. Do NOT reconnect or modify until Milestone 0.3.5.

### Persistence Reality: Zero Room DB / Zero DataStore
- AmniShield contains **zero Room DB** and **zero Preferences DataStore** dependencies.
- Do NOT import `androidx.room` or `androidx.datastore` or reference stub classes in `data/db/` until Milestone 0.3.5.
- `SavedPreferencesLoader.kt` (2,069 lines) manages 30 specialized SharedPreferences XML files (37+ total SharedPreferences XML files across the app) with thread-safe in-memory schedule caching (`@Volatile` + `synchronized(scheduleCacheLock)`). Complex rules and limits are serialized to JSON strings using Google Gson (33 calls).
- `SecureKeyStore.kt`: `EncryptedSharedPreferences` is strictly isolated to Supabase sync secrets with automatic plain XML fallback (`amnishield_sync_secrets_fallback`).

### Ecosystem Scope Boundaries (What AmniShield Is NOT)
- **No On-Device Screen Blur / Vision Censoring**: Real-time NPU/TFLite vision blurring belongs to the sister app **`AmniBlur`** (`AmniHaze`). Do NOT implement TFLite screen capture or vision blur inside AmniShield.
- **No DNS Sinkhole / VPN Packet Filtering**: Local VPN packet filtering and DNS sinkholing belongs to the sister app **`AmniGuardFirewall`**. Do NOT implement VPN-based network interception in AmniShield.

---

## Blocker Pipeline & Chain of Responsibility

The `AmniShieldAccessibilityService.kt` orchestrates accessibility window inspection through explicit priority-ordered interceptors:

| Priority | Component | Target Surface / Vectors | Action Dispatched | Execution Model |
| :--- | :--- | :--- | :--- | :--- |
| **0 (Highest)** | `AntiUninstallDetector` (Line 300) | Play Store, OEM Security Centers, Device Admin, App Info | `GLOBAL_ACTION_HOME`, `AntiUninstallPasswordActivity` | Synchronous (Main Binder) |
| **1** | `FocusModeBlocker` (Line 321) | Active Focus Sessions & Whitelist Enforcements | Full-screen Focus Space (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` | Synchronous (Main Binder) |
| **2** | `WebsiteBlockerDetector` (Line 367) | Chrome, Samsung Internet, Firefox, Brave (15+ browsers) & Social Apps | Mindful Breathing (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` redirect | Synchronous (Linear scan of 1-20 active schedule targets) |
| **3** | `AppBlocker` (Line 434) | Blocked Applications, Launch Limits, Daily Usage Limits | Mindful Breathing (`AmniSpaceActivity`), `pressHome()`, or `WarningActivity` overlay | Synchronous (Main Binder) |
| **4** | `KeywordBlocker` & `ReelBlocker` (Lines 473, 507-598) | Search bars, query inputs, and Short-form Video feeds (YouTube, IG, FB, TikTok) | Text erasure, scroll limit counter, feed suppression | Deferred Coroutine Worker (`Channel.CONFLATED` on `Dispatchers.Default`) |

---

## Mandatory Invariants & Agent Guardrails

### 1. Main Binder Thread & Concurrency Guardrails
- **No Reflection Deserialization on Binder Thread**: Never invoke reflection-based `Gson().fromJson()` on the Main Binder Thread during `onAccessibilityEvent`. Always read from an in-memory schedule cache and invalidate via `ServiceBroadcastManager`.
- **No Periodic Polling of `rootInActiveWindow`**: Never poll `service.rootInActiveWindow` on periodic `Handler` timers. Session trackers (`ReelsSessionTracker`, `BrowserSessionTracker`) must inspect nodes passed from `onAccessibilityEvent` and offload disk writes to `Dispatchers.IO`.
- **Temporal Window Guard**: In `processDeferredChecks()`, always verify `rootPackage.equals(packageName, ignoreCase = true)` and `SystemExclusionManager.isExempt(...)` before evaluating deferred blockers to prevent false-positive blocks when users rapidly switch windows.

### 2. Node Lifecycle & Exception Safety
- **Single-Owner Node Lifecycle**: `AmniShieldAccessibilityService` exclusively owns `rootNode` and recycles it in `finally`. Sub-detectors and interceptors must **never** call `rootNode.recycle()`.
- **Traversal Drain & Recycle**: All iterative (`ArrayDeque`) and recursive child node traversals (`AntiUninstallDetector`, `ReelDetectionEngine`) must wrap child inspection in `try ... finally` to guarantee child node recycling if an exception occurs.
- On Android 11+ (API 30+), `recycle()` is a no-op, but on API 26-29, the internal 50-object node cache can easily be exhausted if not recycled.

### 3. Self-Protection Invariant
The app MUST ignore itself in all blocking and detection logic. Always check:
```kotlin
if (packageName.equals("com.alhaq.deenshield", ignoreCase = true) ||
    rootPackage.equals("com.alhaq.deenshield", ignoreCase = true)) {
    return // Skip all processing
}
```

### 4. Google Play Prominent Disclosure & Store Compliance
- **Non-Cancelable Disclosure**: `AccessibilityDisclosureDialog` must remain non-cancelable (`.setCancelable(false)`).
- **Responsive Layout**: Use a scrollable container (`ScrollView`) with sticky bottom action bar and `wrap_content` (`minHeight="48dp"`) buttons in `dialog_accessibility_disclosure.xml` to prevent button clipping under landscape orientation or 200% font scaling.
- **Persistent Consent**: Affirmative consent must be persisted via `SavedPreferencesLoader.setAccessibilityDisclosureAccepted()` before launching `ACTION_ACCESSIBILITY_SETTINGS`.
- **Mandatory Onboarding Gate**: `PermissionsScreen.kt` must require `isAccessibilityGranted` to proceed (`canProceed`).

---

## Diagnostic & Crash Logging Architecture (`CrashLogger`)

AmniShield maintains a strict **zero-external-telemetry** logging policy.
1. **Uncaught Exception Handling**: `CrashLogger.install(this)` registers a global `Thread.UncaughtExceptionHandler` during application initialization.
2. **Rolling File Cache**:
   - Logs stored in `context.filesDir/logs/app_logs.txt`.
   - Rotates automatically across 5 files (`app_logs.txt` + 4 backups) with a 500 KB per-file ceiling (~2 MB total max footprint).
3. **PII Sanitization Pipeline**: All logs undergo automated regex redaction before touching disk:
   - Passwords, master PINs, encryption salts -> `[REDACTED]`
   - Supabase tokens, JWTs, Bearer headers -> `[REDACTED_TOKEN]` / `[REDACTED_JWT]`
   - Email addresses -> `[REDACTED_EMAIL]`
4. **Diagnostic Screen & Sharesheet Export**: Users can view, filter, search, copy, or export logs via Android `FileProvider` (`DiagnosticsScreen`).

---

## Product Flavors & Build System

AmniShield uses three distinct product flavors:

| Flavor | Target Channel | Google Services | Licensing & Billing |
| :--- | :--- | :--- | :--- |
| `playstore` | Google Play Store | Included (Firebase, Play Billing) | Google Play `BillingClientWrapper` |
| `fdroid` | F-Droid / Pure Open-Source | None (100% FOSS clean room) | Offline NIST P-256 ECDSA public key verification |
| `universal` | GitHub Releases / Sideloading | Included (Firebase) | Hybrid / Google Play Billing |

### Build Commands

Never run bare `assembleDebug` or `bundleRelease`. Always use flavor-qualified Gradle tasks:

```powershell
# Debug builds (local development)
.\gradlew assemblePlaystoreDebug
.\gradlew assembleFdroidDebug
.\gradlew assembleUniversalDebug

# Build all three debug flavors
.\gradlew assemblePlaystoreDebug assembleFdroidDebug assembleUniversalDebug

# Release builds
.\gradlew assembleUniversalRelease   # Published to GitHub Releases
.\gradlew assembleFdroidRelease      # Published to GitHub Releases
.\gradlew assemblePlaystoreRelease   # Published to Google Play Console (never to GitHub)

# Clean rebuild (bypasses stale cached daemon)
.\gradlew clean --no-daemon
.\gradlew assembleUniversalDebug --no-daemon
```

### Single-Source ViewModel Invariant
Always keep unified ViewModels in `app/src/main/`. Never duplicate ViewModels inside flavor folders (`src/playstore` or `src/fdroid`). Flavor folders are strictly reserved for flavor-specific implementations of dependency interfaces (e.g., billing wrappers).

---

## Service Broadcast Actions

When modifying settings in Compose or Fragment UI, notify `AmniShieldAccessibilityService` via package-scoped broadcasts:

```kotlin
// Feature refresh actions
INTENT_ACTION_REFRESH_APP_BLOCKER
INTENT_ACTION_REFRESH_WEBSITE_BLOCKER
INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST
INTENT_ACTION_REFRESH_VIEW_BLOCKER
INTENT_ACTION_REFRESH_FOCUS_MODE
INTENT_ACTION_REFRESH_ANTI_UNINSTALL
INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES

// Usage - always scope to own package
val intent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
context.sendBroadcast(intent.setPackage(context.packageName))
```

---

## Active Roadmap (Milestones v0.3.2 - v0.3.5)

- **Milestone 0.3.2: Accessibility Engine Hardening & Binder Safety [COMPLETED]**:
  - [x] In-memory schedule caching to eliminate reflection `Gson().fromJson()` on Main Binder thread.
  - [x] Deferred pipeline temporal window guard (`rootPackage == packageName` check).
  - [x] Eliminate 1s/2s `rootInActiveWindow` polling Handler timers in session trackers.
  - [x] Exception-safe child node traversals with `try ... finally` recycling.
  - [x] Manifest security hardening: `android:exported="false"` on `LauncherActivity`.
- **Milestone 0.3.3: Store Compliance & Layout Resilience [COMPLETED]**:
  - [x] Prominent disclosure overhaul: `ScrollView` container, `wrap_content` buttons (`minHeight="48dp"`), `.setCancelable(false)`.
  - [x] Persistent affirmative consent: `SavedPreferencesLoader.setAccessibilityDisclosureAccepted()`.
  - [x] Mandatory onboarding gate: require `isAccessibilityGranted` in `PermissionsScreen`.
  - [x] Screen rotation state preservation: `rememberSaveable` in `AmniSpaceActivity` and timer preservation in `WarningActivity`.
- **Milestone 0.3.4: Dual-Host Cleanup & Navigation Decoupling [COMPLETED]**:
  - [x] Type-safe intent routing (`FragmentDestination.kt`) between `MainActivity` and `FragmentActivity`, plus `ApiPermissionActivity` and `BootAndAlarmReceiver` hardening.
  - [x] Static companion mutex synchronization (`rulesLock`, `statsLock`) in `SavedPreferencesLoader`.
  - [x] CI automated multi-flavor parallel matrix testing (`playstore`, `fdroid`, `universal`).
- **Milestone 0.3.5: Pure Compose & Storage Architecture Epic (Prep for V1.0) [ACTIVE]**:
  - [ ] Phased migration from 30+ SharedPreferences XML files to Room DB (relational rules) and DataStore (flags/tokens).
  - [ ] Pure Compose Navigation: Reconnect `AmniShieldAdaptiveApp.kt`, retiring the dual-host split.
