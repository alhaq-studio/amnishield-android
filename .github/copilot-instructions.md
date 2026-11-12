# AmniShield AI Development Assistant - Copilot Instructions

You are an expert Android/Kotlin developer working on **AmniShield** - an open-source, privacy-first digital wellness and distraction blocker for Android. AmniShield intercepts addictive short-form video reels, enforces scheduled app and website blocking, keyword filtering, and strict anti-bypass modes without transmitting any screen data or telemetry to remote servers.

---

## Project Metadata

- **Current Version**: `0.3.5 (2026.10.21)` (versionCode `145`)
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
- **UI Framework**: Dual-Host & Pure Compose Adaptive Architecture bridging AndroidX Activities with Jetpack Compose Material 3
- **Build System**: Gradle 9.5+ with Kotlin DSL (`build.gradle.kts`)
- **Persistence Layer**:
  - Relational SQLite storage via `AmniShieldRuleDatabase.kt` for structured blocking rules and screen-time launch limits (V1 -> V2 schema migrations)
  - Reactive Preferences DataStore via `AmniShieldDataStore.kt` for core feature toggles, onboarding consent, theme, and PIN security
  - SharedPreferences persistence via `SavedPreferencesLoader.kt` orchestrating 30 specialized XML files with `@Volatile` in-memory schedule caching and companion mutex locks (`rulesLock`, `statsLock`)
  - Isolated hardware-backed `EncryptedSharedPreferences` strictly restricted to Supabase cloud sync tokens via `SecureKeyStore.kt`
- **Licensing**: Offline-first cryptographic NIST P-256 ECDSA public key verification (`SHA256withECDSA` in `LicenseValidator.kt`) for F-Droid and universal builds; Google Play `BillingClientWrapper.kt` for `playstore` flavor

### Dual-Host & Adaptive Presentation Architecture
1. **`MainActivity.kt`**: Hosts the primary 4 bottom navigation tabs (`Stats`, `Blocks`, `Focus`, `Advanced`) and provides `mountAdaptiveComposeShell()` to activate the pure-Compose adaptive layout (`AmniShieldAdaptiveApp.kt`).
2. **`FragmentActivity.kt`**: Acts as a secondary router dispatching 18 functional sub-screens and configuration dialogs via the type-safe `FragmentDestination.kt` sealed route contract. Each configuration fragment inherits `BaseFeatureFragment` and returns `ComposeView` embedding Material 3 Compose screens (`AppBlockerConfigScreen`, `ReelBlockerConfigScreen`, `WebsiteBlockerConfigScreen`, `DiagnosticsScreen`, etc.).
3. **`AmniShieldAdaptiveApp.kt`**: Pure Compose `NavigationSuiteScaffold` shell supporting adaptive mobile, tablet, and foldable layouts, wired directly to `AmniShieldViewModel`.

### Ecosystem Scope Boundaries (What AmniShield Is NOT)
- **No On-Device Screen Blur / Vision Censoring**: Real-time NPU/TFLite vision blurring belongs to the sister app **`AmniBlur`** (`AmniHaze`). Do NOT implement TFLite screen capture or vision blur inside AmniShield.
- **No DNS Sinkhole / VPN Packet Filtering**: Local VPN packet filtering and DNS sinkholing belongs to the sister app **`AmniGuardFirewall`**. Do NOT implement VPN-based network interception in AmniShield.

---

## Blocker Pipeline & Chain of Responsibility

The `AmniShieldAccessibilityService.kt` pipeline executes on every accessibility event in strict priority order:

1. **Priority 0: `AntiUninstallDetector`**: Immediate short-circuit interceptor checking device admin settings, package installer views, and Play Store uninstallation flows.
2. **Priority 1: `FocusModeBlocker`**: Enforces active focus sessions and strict whitelist protections.
3. **Priority 2: `WebsiteBlockerDetector`**: Domain extraction and linear schedule matching across 15+ supported web browsers.
4. **Priority 3: `AppBlocker`**: Package inspection against blocked app schedules, launch limits, and cheat hour windows.
5. **Priority 4: `KeywordBlocker` & `ReelBlocker`**: Deferred content analysis processed asynchronously on `Dispatchers.Default` using `Channel.CONFLATED`.

---

## Coding Standards & Invariants

1. **Single-Source ViewModels**: `AmniShieldViewModel.kt` must remain strictly in `app/src/main/`. Never duplicate ViewModels inside flavor source sets (`src/playstore` or `src/fdroid`).
2. **Main Binder Thread Safety**:
   - Never invoke reflection-based `Gson().fromJson()` on the Main Binder Thread in `onAccessibilityEvent`. Read from in-memory schedule cache and invalidate via `SavedPreferencesLoader.invalidateScheduleRulesCache()`.
   - Never poll `rootInActiveWindow` on periodic timers. Offload disk persistence to `Dispatchers.IO`.
3. **Node Lifecycle Management**:
   - `AmniShieldAccessibilityService` exclusively owns `rootNode` and recycles it in `finally`.
   - Iterative and recursive child node traversals must wrap inspection in `try ... finally` to guarantee child node recycling.
4. **Multi-Flavor Builds**:
   - Ensure clean compilation across `playstoreDebug`, `fdroidDebug`, and `universalDebug`.
   - Do not leak proprietary Google Play Services dependencies into `fdroid` builds.
