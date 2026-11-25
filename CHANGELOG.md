# Changelog

All notable changes to AmniShield will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.4.0] - 2026-11-25 (versionCode 146)

### Added
- **Unified Blocks Manager & Always-On Shields Redesign**:
  - Added the redesigned `UnifiedBlocksDashboardScreen`, `CreateOrEditBlockScreen`, `SelectTargetsScreen`, and `WeeklyUsageReportScreen` flow inside `BlocksManagerAdaptiveScreen`.
  - Introduced `UnifiedBlockRule` supporting combined weekly schedules, launch/open limits, and per-rule authentication.
  - Added `AlwaysOnShieldsConfig` (Short Video & Reels Guard, Permanent Website Guard, Keyword & Search Filter) with asymmetric lock friction (`ShieldLockConfig`) and Pro expiry unlock safety.
- **High-Speed Domain Blocklist Engine (`DomainBlocklistEngine`)**:
  - Primitive sorted 64-bit FNV-1a hash matcher backed by gzipped `adult_domains.gz` and `gambling_domains.gz` assets loaded off the Main Binder Thread.
- **Synchronized Hybrid Storage & Pure Compose Navigation**:
  - Relational SQLite persistence (`AmniShieldRuleDatabase`, V1 -> V2 migrations) and reactive Preferences DataStore (`AmniShieldDataStore`) synchronized with `SavedPreferencesLoader` mutex locks.
  - Pure Compose adaptive navigation shell (`AmniShieldAdaptiveApp`) alongside `FragmentDestination` type-safe routing.
- **Expanded Verification Suite**:
  - Added unit test suites covering `UnifiedBlockRule`, `AlwaysOnShieldsConfig`, `AlwaysOnShieldAuthTest`, `DomainBlocklistEngine`, `AmniShieldRuleDatabase`, `FragmentDestination`, and `OnboardingStateHelpers` (169 unit tests per flavor).

### Changed
- **GitHub Release Pipeline**:
  - Fixed `release.yml` keystore conditional check so push events no longer trigger workflow validation failures.
  - Configured GitHub Releases to publish exclusively the signed Universal APK (`AmniShield-<tag>-universal.apk`).
- **Accessibility Engine Hardening & Store Compliance**:
  - Enforced Main Binder Thread safety, non-cancelable prominent disclosure (`AccessibilityDisclosureDialog`), mandatory accessibility onboarding gate (`PermissionsScreen`), and rotation state preservation.

### Fixed
- Fixed `DomainBlocklistEngine` failing to match the leading domain in UTF-8 BOM-encoded gzipped blocklists (`pornhub.com` and `stake.com`) by stripping `\uFEFF` during normalization.
- Fixed `SettingsFragment` web filter toggle broadcast action to dispatch `INTENT_ACTION_REFRESH_WEBSITE_BLOCKER`.
- Removed remaining UTF-8 emojis from helper scripts, docs, and webhook templates to enforce zero-emoji compliance.

---

## [0.3.1] - 2026-10-05 (versionCode 145)

### Added
- **Continuous Cross-App & Cross-Session Shortform Tracking**:
  - Daily cumulative accumulation for Reels and Shorts counts (todayScrolled) and watch duration (todayWatchSeconds) preserved across app launches, foreground swaps, and device reboots.
  - Multi-app daily tracking supporting YouTube Shorts, Instagram Reels, TikTok, Facebook Reels, and popular community clients (ReVanced, Morphe, MyInsta, Instagram Lite, Facebook Lite).
  - Midnight date-rollover automation resetting active comparator caches at 00:00 without losing persistence.
- **Deep Fallback Node Comparator & ViewPager2 Recognition**:
  - Dynamic fallback scanning in ReelDetectionEngine traversing child view trees up to depth 8 to capture video titles, creators, and audio tags when platform container resource IDs change dynamically.
  - Interception of AccessibilityEvent.TYPE_VIEW_SELECTED for immediate capture of vertical page-swiping across ViewPager2 implementations.
  - Fallback window node inspection (event.source fallback on rapid gestures).
- **Floating Counter Pill Overlay Enhancements**:
  - Synchronous main-thread UI binding via runOnMain eliminating race conditions during window attachment.
  - Real-time display of accumulated daily count and total watch time immediately upon opening any candidate platform.
  - Automatic time format adaptation to hh:mm:ss once watch duration exceeds 60 minutes.
- **Website Domain Usage Tracking (BrowserSessionTracker)**:
  - Low-overhead 2-second active window polling when browsers are in the foreground.
  - Per-domain duration tracking and aggregation with broadcast receiver lifecycle managing screen on/off power saving.

### Fixed
- **Aggressive Session Reset Loop**:
  - Replaced immediate 1-second session teardown with 3-consecutive-poll debounced confirmation, eliminating false resets during loading spinners, swipe gestures, and comment drawers.
  - Retained seen reels cache on session switch via clearActiveDynamicText() so switching apps does not reset today's unique video tally.
- **Scroll Rate Throttle**:
  - Enforced 500ms time gate on rapid swipe increments to prevent double-counting inertial bounces.

---

## [0.3.0] - 2026-09-29 (versionCode 144)

### Added
- **Pre-Registration Founder Pass & Entitlements**:
  - Integrated Google Play Billing pre-registration entitlement check for `amnishield_founder_pass`.
  - Gated Founder Pass claim and public directory opt-in exclusively to verified pre-registered supporters.
  - Automatic 90-day Full Security Suite grant (triple-PIN barrier, anti-uninstall defense, anti-tamper).
  - Hardware monotonic clock protection (`verifyFounderPassMonotonicExpiry`) preventing system clock manipulation.
  - Permanent Founder Obsidian Gold theme and lifetime verified founder badge unlock.
- **Our Journey & Founding Supporters Wall**:
  - Dedicated "Our Journey & Founders" screen accessible to all users via Settings and Navigation.
  - Public Founding Supporters Wall displaying all early pioneers, pioneer IDs, joined dates, and verified gold badges.
  - Informational "Pre-Registration Milestone" card displayed for regular users explaining the founding milestone.
  - Contextual "Verified Pre-Registration Founder" badge chip displayed in user Profile.
- **Privacy & Legal Infrastructure**:
  - Zero-knowledge opt-in architecture supporting pseudonyms and anonymous handles with 0 personal telemetry.
  - Published comprehensive Founder Pass Terms & Conditions at `/legal/founder-pass/` with zero auto-billing guarantees.

### Security & Build Verification
- Verified release packaging across all distribution channels:
  - Production Play Store App Bundle (`.aab`) signed with production keystore.
  - Direct Universal Release APK (`.apk`) verified on live device.
  - Pure F-Droid FOSS Release APK (`.apk`) strictly free of proprietary dependencies.
- Full R8 minification, native debug symbols, and `lintVital` pass with zero warnings or errors.
- Strict Universal Zero Emoji compliance across UI, strings, and code.

---

## [0.2.1] - 2026-09-03 (versionCode 143)

### Added
- **Focus Session Workspace Overlay**:
  - Live real-time session countdown timer displaying remaining minutes and seconds.
  - Dedicated focus overlay mode with quick access to essential whitelisted tools.
  - Automatic workspace launch upon starting a focus session when selected.
- **Dynamic Mindful Breathing Pause**:
  - Automatically resolves package names to app names and icons via `PackageManager`.
  - Contextual blocker badges: `[APP BLOCKER]`, `[WEBSITE BLOCKER]`, `[REELS BLOCKER]`, `[KEYWORD BLOCKER]`, and `[FOCUS MODE PAUSE]`.
  - Context-aware cognitive friction messages tailored to each interception type.
- **Focus Warning Dialog**:
  - Added dedicated focus warning screen mode (`WARNING_SCREEN_MODE_FOCUS_MODE`) preventing silent home exits.
  - Strict enforcement with bypass controls hidden during active focus sessions.
  - In-app customization dialog for focus reminder messages.
- **Focus Settings Categorization**:
  - Organized focus configuration into two clear sections: `QUICK FOCUS DEFAULTS` and `GLOBAL FOCUS & SECURITY ENGINE` with status pill badges.
  - 3-way interception selector: Focus Space Workspace Overlay (Recommended), Focus Warning Dialog (Default), and Silent Instant Exit (Opt-in).
- **Tooling & Build System**:
  - Added automatic Android Studio JBR runtime detection in `gradlew.bat`.
  - Configured `org.gradle.java.home` in `gradle.properties` for seamless Gradle builds across IDE and CLI environments.

### Changed
- **Header Optimization**: Removed bulky "Daily Wellbeing & Mindfulness" header from the Quick Focus screen to maximize screen real estate above the fold.
- **Integrated Focus Settings**: Embedded the settings cog icon directly into the top-right corner of the Hero Focus Card.
- **Rebranding**: Rebranded blocker reaction option from "AmniSpace Mindful Focus Space" to "Mindful Breathing Pause" across App Blocker and Website Blocker configuration screens.
- **Accessibility Enhancements**: Gated DOM tree walks and ensured complete node recycling in `ReelActionHandler` and `AmniShieldAccessibilityService`.

### Security & Compliance
- Full 16 KB page memory alignment verification for Android 15+.
- Zero telemetry and 100% on-device local blocking enforcement.
- Strict Universal Zero Emoji policy enforced across UI and code.

---

## [0.2.0] - 2026-09-03 (versionCode 142)

### Added
- F-Droid publishing recipe (`com.alhaq.deenshield.yml`) and complete packaging guide.
- GitHub Actions CI/CD automated release pipeline with test gating.
- Material 3 adaptive tokens across all feature configuration screens.

### Fixed
- Fixed Play Store and F-Droid build flavor separation.
- Fixed 16 KB page memory alignment in JNI packaging.

---

## [0.1.11] - 2026-08-30 (versionCode 141)

### Added
- Quick Focus session launcher dialogue with duration presets.
- Dynamic Whitelisted apps selector for focus sessions.
- Room database migration for scheduled focus rules.

---

## [0.1.10] - 2026-08-27 (versionCode 140)

### Added
- Short-form video (Reels & Shorts) interceptor for YouTube, Instagram, and TikTok.
- Offline ECDSA NIST P-256 license verification engine.
- Biometric lock support for Settings and Blocker configurations.
