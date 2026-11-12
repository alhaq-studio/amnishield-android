# Original User Request

## 2026-10-20T15:05:17Z

Execute a comprehensive documentation audit, codebase reconciliation, and technical drift remediation for AmniShield-Android (`d:\PROJECTS\Amni\AmniShield-Android`). Produce exact git diffs for `README.md`, `ROADMAP.md`, and `ARCHITECTURE.md` that resolve documented persistence myths, dual-host UI container structures, blocker priority inversions, cross-app roadmap scope creep, premium feature architecture, and Zero-Emoji Policy violations. Write the complete deliverable into `AMNISHIELD_PROJECT_SUMMARY.md`.

Working directory: d:\PROJECTS\Amni\AmniShield-Android
Integrity mode: development

## Requirements

### R1. Persistence Reality & De-Hallucination (`ARCHITECTURE.md` & `README.md`)
Audit and rectify documentation claiming Room DB, SQLite caching, or DataStore. Reconcile with codebase reality: AmniShield-Android contains zero Room and zero DataStore dependencies, with persistence operating 100% via SharedPreferences in `SavedPreferencesLoader.kt` (12+ plain XML files) and `EncryptedSharedPreferences` strictly isolated to `SecureKeyStore.kt` for Supabase auth tokens. Document the in-flight Gson deserialization bottleneck where `loadAppBlockerScheduleRules()` executes reflection-based `Gson().fromJson()` multiple times synchronously on the Main Binder Thread during `onAccessibilityEvent`.

### R2. Dual-Host Container Presentation Architecture (`ARCHITECTURE.md` & `README.md`)
Reconcile the UI presentation model with actual codebase architecture: `MainActivity.kt` (2,022 lines) hosts the 4 primary bottom navigation tabs, while `FragmentActivity.kt` (210 lines) acts as a secondary router hosting 18 sub-screens, onboarding flows, and feature configuration fragments embedding `ComposeView`. Explicitly document the origin and disconnected status of `AmniShieldAdaptiveApp.kt` (connected in commit `7155646`, disconnected in commit `ed57548` to avoid FragmentManager lifecycle crashes).

### R3. Blocker Interceptor Pipeline Priority Alignment (`ARCHITECTURE.md`)
Correct the priority table in `ARCHITECTURE.md:40-47` to reflect the verified execution sequence in `AmniShieldAccessibilityService.kt`:
1. Priority 0: `AntiUninstallDetector` (Line 284)
2. Priority 1: `FocusModeBlocker` (Line 317)
3. Priority 2: `WebsiteBlockerDetector` (Line 350)
4. Priority 3: `AppBlocker` (Line 417)
5. Priority 4: `KeywordBlocker` & `ReelBlocker` (Deferred Conflated Channel, Line 475)
Debunk the 50k local domain lookup myth: candidate domains are strictly the 1-20 user-configured schedule targets evaluated via linear loop in `WebsiteBlockerDetector.kt`, while asset files `adult_domains.txt` and `haram_domains.txt` are dead, unreferenced assets.

### R4. Premium Architecture & Licensing Verification (`ARCHITECTURE.md`, `README.md`, `ROADMAP.md`)
Audit and document the complete Premium and Supporter Pass architecture across flavors:
- **Tier Structure**: Free Open-Source Core (base app, website, and reels blocking, keyword filter) vs. Supporter/Pro Tiers (`UserType.PREMIUM`, `UserType.FOUNDER` 90-day early supporter pass, `UserType.COMPASSIONATE` community access).
- **Premium Gated Features**: Rule PIN / Password Security Locking (`RuleProBadge()` in `RuleSecurityComponents.kt` and `SetRulePinDialog.kt`), Advanced Strict Enforcement Mode (`AdvancedScreen.kt`), Bypass PIN Protection, and Multi-Device Cloud Policy Sync via Supabase.
- **Flavor Implementation**:
  - `playstore`: Google Play `BillingClientWrapper.kt` managing subscriptions (`amnishield_premium_monthly`, `amnishield_premium_yearly`, `amnishield_premium_lifetime`, `amnishield_founder_pass`).
  - `fdroid` & `universal`: 100% offline cryptographic NIST P-256 ECDSA public key verification (`SHA256withECDSA` in `LicenseValidator.kt`) with optional Stripe Direct checkout via Supabase Edge Functions.

### R5. Roadmap Scope Hygiene & Milestone Realignment (`ROADMAP.md`)
Replace `ROADMAP.md` upcoming section with the verified versioned milestones (v0.3.2 - v0.3.5):
- **Completed Milestones**: Continuous Cross-App Reels Engine, Resilient Node Traversal, ViewPager2 Swipe Interception, Synchronous Floating Pill Overlay, Browser Website Engagement Tracker, Offline Cryptographic Licensing, Hardened Anti-Uninstall Defense, In-App Diagnostics & Crash Viewer, and Glanceable Home Screen Widgets.
- **Milestone 0.3.2: Accessibility Engine Hardening & Binder Safety**:
  - In-Memory Schedule Caching (eliminate reflection-based `Gson().fromJson()` in `onAccessibilityEvent`).
  - Polling Decoupling (eliminate continuous `rootInActiveWindow` queries on Handler timers in `ReelsSessionTracker` and `BrowserSessionTracker`).
  - Node Traversal Exception Safety (`try ... finally` blocks in `AntiUninstallDetector` and `ReelDetectionEngine`).
  - Manifest Security Hardening (`android:exported="false"` on `LauncherActivity`).
- **Milestone 0.3.3: Store Compliance & Layout Resilience**:
  - Prominent Disclosure Overhaul (scrollable container for landscape and 200% font scaling).
  - Persistent Affirmative Consent (persisting acceptance flag in `SharedPreferences`).
  - Screen Rotation State Preservation (`rememberSaveable` in `AmniSpaceActivity.kt`, `onSaveInstanceState` in `WarningActivity.kt`, window insets in `UsageStatOverlayManager.kt`).
- **Milestone 0.3.4: Dual-Host Cleanup & Navigation Decoupling**:
  - Type-Safe Intent Routing (sealed route contract in `FragmentActivity.kt`).
  - SharedPreferences Synchronization Lock (mutex locks in `SavedPreferencesLoader.kt`).
  - Automated Flavor Testing (emulator-based CI workflow in `main-pipeline.yml`).
- **Milestone 0.3.5: Pure Compose & Storage Architecture Epic (Prep for V1.0)**:
  - Room & DataStore Migration Epic (formalize migration path from 12+ SharedPreferences files).
  - Pure-Compose Navigation Epic (reconnect and finalize `AmniShieldAdaptiveApp.kt` to deprecate `MainActivity` and `FragmentActivity`).
- **Expunged External Milestones**: NPU Vision Blur (belongs to `AmniBlur`/`AmniHaze`), DNS Sinkhole (belongs to `AmniGuardFirewall`), and Windows Elevated Background Service (belongs to `AmniShield-Windows`).

### R6. Universal Zero-Emoji Policy & Versioning Rectification (`ARCHITECTURE.md` & Project Metadata)
- Purge all 4 unicode emojis from `ARCHITECTURE.md` (lines 9, 36, 51, 69) and enforce the strict Zero-Emoji Policy across documentation.
- Reconcile documented version (`1.0.2-closed.01 / 132` in `.agents/AGENTS.md`) with the `app/build.gradle.kts` production baseline (`versionCode 145`, `versionName "0.3.1 (2026.10.05)"`).

### R7. Designated Summary Output File Target
Write the complete findings, architectural analysis, premium feature mapping, verification checklist, and exact git diffs for `README.md`, `ROADMAP.md`, and `ARCHITECTURE.md` directly into the project summary file:
`d:\PROJECTS\Amni\AmniShield-Android\AMNISHIELD_PROJECT_SUMMARY.md`.

## Acceptance Criteria

### Audit & Verification Accuracy
- [ ] Exact git diffs and replacement blocks provided for `README.md`, `ROADMAP.md`, and `ARCHITECTURE.md`.
- [ ] Complete audit report, diffs, and verification checklist written to `AMNISHIELD_PROJECT_SUMMARY.md`.
- [ ] Zero Kotlin code, layout XML, or manifest files modified during this read-only documentation audit.
- [ ] All 4 unicode emojis purged from `ARCHITECTURE.md` (lines 9, 36, 51, 69).
- [ ] Persistence documentation updated to reflect 100% SharedPreferences via `SavedPreferencesLoader.kt` with zero Room DB or DataStore dependencies.
- [ ] Dual-host UI container reality (`MainActivity.kt` + `FragmentActivity.kt`) and disconnected status of `AmniShieldAdaptiveApp.kt` documented.
- [ ] Code execution priority sequence verified: `AntiUninstallDetector` (P0) -> `FocusModeBlocker` (P1) -> `WebsiteBlockerDetector` (P2, L350) -> `AppBlocker` (P3, L417) -> Deferred Channel (P4).
- [ ] Premium architecture fully documented: Free FOSS core vs Supporter Pass, Rule PIN protection (`RuleProBadge`), Google Play Billing for `playstore`, and offline NIST P-256 ECDSA verification for `fdroid`/`universal`.
- [ ] Upcoming milestones in `ROADMAP.md` updated to Milestones 0.3.2 through 0.3.5, with Blur (AmniBlur), DNS Sinkhole (AmniGuardFirewall), and Windows Elevated Service (AmniShield-Windows) completely removed.
- [ ] Version metadata reconciled with `build.gradle.kts` (`versionCode 145`, `versionName "0.3.1"`).
