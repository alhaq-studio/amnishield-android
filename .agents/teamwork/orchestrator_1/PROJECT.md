# Project: AmniShield-Android Documentation Audit & Technical Drift Remediation

## Architecture
- Target: Codebase reality vs documentation reconciliation across `ARCHITECTURE.md`, `README.md`, `ROADMAP.md`.
- Final Deliverable Target: `d:\PROJECTS\Amni\AmniShield-Android\AMNISHIELD_PROJECT_SUMMARY.md`.
- Read-Only Constraint: Zero modifications to production code (Kotlin, XML, Manifest).

## Feature Inventory (Merged Ground Truth)
| # | Requirement | Verified Codebase Ground Truth | Status | Source |
|---|-------------|--------------------------------|--------|--------|
| 1 | R1: Persistence Reality | Zero Room DB, Zero SQLite, Zero DataStore in build.gradle.kts / libs.versions.toml. 100% SharedPreferences via `SavedPreferencesLoader.kt` across 29+ XML files with 33 Gson instances. `EncryptedSharedPreferences` strictly isolated to `SecureKeyStore.kt` for Supabase auth tokens with plaintext fallback. Synchronous `Gson().fromJson()` reflection bottleneck on Main Binder Thread in `AmniShieldAccessibilityService.kt`. Orphaned dead stubs in `com.alhaq.amnishield.data.db`. | VERIFIED | explorer_1 handoff |
| 2 | R2: Dual-Host Container | `MainActivity.kt` (2,022 lines) hosts 4 bottom tabs (Stats, Blocks, Focus, Advanced). `FragmentActivity.kt` (210 lines) secondary router for 18 sub-screens embedding Jetpack Compose via `ComposeView`. `AmniShieldAdaptiveApp.kt` connected in commit `7155646` and disconnected in commit `ed57548` due to FragmentManager lifecycle crashes (currently 0 callers, precursor for M0.3.5). | VERIFIED | explorer_2 handoff |
| 3 | R3: Blocker Pipeline Priority | Strictly ordered sequence in `AmniShieldAccessibilityService.kt`: P0 `AntiUninstallDetector` (L283) -> P1 `FocusModeBlocker` (L304) -> P2 `WebsiteBlockerDetector` (L350) -> P3 `AppBlocker` (L417) -> P4 `KeywordBlocker` & `ReelBlocker` (Deferred Conflated Channel, L456, L475-562). `WebsiteBlockerDetector.kt` evaluates 1-20 user-configured schedule targets via linear loop. `adult_domains.txt` and `haram_domains.txt` are dead, unreferenced assets. | VERIFIED | explorer_2 handoff |
| 4 | R4: Premium & Licensing | Tier hierarchy in `PremiumManager.kt`: `FREE` (100% FOSS core), `COMPASSIONATE` (community access), `FOUNDER` (90-day early supporter pass in `SavedPreferencesLoader.kt:919` with monotonic clock protection), and `PREMIUM`. Gated features: `RuleProBadge()` in `RuleSecurityComponents.kt:44-70`, `SetRulePinDialog.kt:65-69`, `AdvancedScreen.kt:301-354` (PIN security, App Lock, Bypass PIN), and Supabase cloud policy sync (`PolicySyncManager.kt`, `PlaystoreSyncProvider.kt`). Flavor splits: `playstore` uses Google Play Billing with 4 products in `PremiumProducts.kt`; `fdroid` is 100% FOSS stub with 0 GMS imports, using offline NIST P-256 ECDSA public key verification (`SHA256withECDSA` in `LicenseValidator.kt`) with optional Stripe Direct checkout in `PaymentManager.kt`; `universal` is hybrid. | VERIFIED | explorer_3 handoff |
| 5 | R5: Roadmap Scope Hygiene | Purge out-of-scope milestones from `ROADMAP.md` (Windows Background Service -> AmniShield-Windows, DNS Sinkhole -> AmniGuard-FireWall, Mobile NPU Vision Blur -> AmniBlur). Replace with Android technical debt milestones: 0.3.2 (Accessibility Engine Hardening & Binder Safety), 0.3.3 (Store Compliance & Layout Resilience), 0.3.4 (Dual-Host Cleanup & Navigation Decoupling), 0.3.5 (Pure Compose & Storage Architecture Epic). | VERIFIED | explorer_3 handoff |
| 6 | R6: Zero-Emoji & Versioning | Purge all 4 unicode emojis from `ARCHITECTURE.md` (lines 9, 36, 51, 69). Reconcile `.agents/AGENTS.md` stale version (`1.0.2-closed.01 / 132`) with `app/build.gradle.kts` production baseline (`versionCode 145`, `versionName "0.3.1 (2026.10.05)"`). | VERIFIED | explorer_3 handoff |
| 7 | R7: Summary Output Target | Write complete deliverable, architectural analysis, verification checklist, and exact git diffs for README.md, ROADMAP.md, and ARCHITECTURE.md into `d:\PROJECTS\Amni\AmniShield-Android\AMNISHIELD_PROJECT_SUMMARY.md`. Zero code modifications. | IN_PROGRESS | orchestrator plan |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Exploration & Survey | Verify ground truth in codebase across R1-R6 via 3 Explorers | none | DONE |
| 2 | Drafting & Diffs | Draft comprehensive AMNISHIELD_PROJECT_SUMMARY.md with exact git diffs | M1 | IN_PROGRESS |
| 3 | Review & Challenge | Independent review (2 Reviewers) & adversarial challenge (2 Challengers) | M2 | PLANNED |
| 4 | Forensic Audit & Gate | Forensic audit (Auditor) & Gate verification | M3 | PLANNED |
| 5 | Completion Report | Final verification & handoff to Sentinel | M4 | PLANNED |
