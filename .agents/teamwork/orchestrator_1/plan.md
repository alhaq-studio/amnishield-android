# Plan: AmniShield-Android Documentation Audit & Technical Drift Remediation

## Objective
Execute comprehensive documentation audit, codebase reconciliation, and technical drift remediation for AmniShield-Android per ORIGINAL_REQUEST.md. Produce exact git diffs and replacement blocks for README.md, ROADMAP.md, and ARCHITECTURE.md, and write the complete deliverable to AMNISHIELD_PROJECT_SUMMARY.md.

## Requirements Breakdown
- **R1: Persistence Reality & De-Hallucination**: Audit SharedPreferences in `SavedPreferencesLoader.kt` (12+ XML files), EncryptedSharedPreferences in `SecureKeyStore.kt`, Gson deserialization bottleneck on main binder thread during `onAccessibilityEvent`. Zero Room / DataStore dependencies.
- **R2: Dual-Host Container Presentation Architecture**: `MainActivity.kt` (2,022 lines) for 4 bottom nav tabs + `FragmentActivity.kt` (210 lines) for 18 sub-screens embedding `ComposeView`. Disconnected `AmniShieldAdaptiveApp.kt` (commit 7155646 -> ed57548).
- **R3: Blocker Interceptor Pipeline Priority Alignment**: Sequence in `AmniShieldAccessibilityService.kt`: P0 `AntiUninstallDetector` (L284) -> P1 `FocusModeBlocker` (L317) -> P2 `WebsiteBlockerDetector` (L350) -> P3 `AppBlocker` (L417) -> P4 `KeywordBlocker` & `ReelBlocker` (deferred channel L475). Debunk 50k local domain lookup myth (1-20 user schedule targets, dead asset files `adult_domains.txt` & `haram_domains.txt`).
- **R4: Premium Architecture & Licensing Verification**: Free FOSS core vs Supporter/Pro tiers (`PREMIUM`, `FOUNDER`, `COMPASSIONATE`). Premium gated features (`RuleProBadge()`, `SetRulePinDialog.kt`, `AdvancedScreen.kt`, bypass PIN, Supabase sync). Flavors: `playstore` (`BillingClientWrapper.kt`) vs `fdroid`/`universal` (offline NIST P-256 ECDSA in `LicenseValidator.kt`).
- **R5: Roadmap Scope Hygiene & Milestone Realignment**: Replace upcoming with Milestones 0.3.2 to 0.3.5. Purge out-of-scope milestones: NPU Vision Blur (AmniBlur), DNS Sinkhole (AmniGuardFirewall), Windows Elevated Background Service (AmniShield-Windows).
- **R6: Universal Zero-Emoji Policy & Versioning Rectification**: Purge all 4 unicode emojis from `ARCHITECTURE.md` (lines 9, 36, 51, 69). Reconcile version with `build.gradle.kts` (`versionCode 145`, `versionName "0.3.1 (2026.10.05)"`).
- **R7: Designated Summary Output Target**: Write complete audit report, diffs, analysis, and verification checklist to `AMNISHIELD_PROJECT_SUMMARY.md`. Read-only audit: zero code/layout/manifest files modified.

## Phases & Execution Steps
1. **Phase 1: Survey & Exploration (3 Explorers in parallel)**:
   - Explorer 1: Inspect persistence (`SavedPreferencesLoader.kt`, `SecureKeyStore.kt`, build dependencies, Gson bottleneck).
   - Explorer 2: Inspect dual-host UI container (`MainActivity.kt`, `FragmentActivity.kt`, `AmniShieldAdaptiveApp.kt` git history) and Blocker Interceptor Pipeline (`AmniShieldAccessibilityService.kt`, blocker classes, domain assets).
   - Explorer 3: Inspect licensing & premium architecture (`BillingClientWrapper.kt`, `LicenseValidator.kt`, `RuleSecurityComponents.kt`, `UserType.kt`) + Roadmap & Emoji audit (`ROADMAP.md`, `ARCHITECTURE.md`, `README.md`, `build.gradle.kts`).
2. **Phase 2: Worker Implementation (Synthesis & Diffs Creation)**:
   - Worker synthesizes verified codebase findings, prepares exact git diffs and replacement blocks for README.md, ROADMAP.md, ARCHITECTURE.md, and writes full deliverable into `AMNISHIELD_PROJECT_SUMMARY.md`.
3. **Phase 3: Independent Review & Adversarial Challenge**:
   - 2 Reviewers independently inspect `AMNISHIELD_PROJECT_SUMMARY.md` against codebase facts, line references, and all requirements R1-R7.
   - 2 Challengers test exactness of diffs, line numbers, zero-emoji policy compliance, and absence of code modifications.
4. **Phase 4: Forensic Audit**:
   - Auditor runs full integrity verification: verifies zero modified Kotlin/XML/manifest files, zero emojis, exact git diff validity, and complete coverage of R1-R7.
5. **Phase 5: Gate & Sentinel Handoff**:
   - Verify all pass criteria.
   - Send completion message to Sentinel with full report reference.
