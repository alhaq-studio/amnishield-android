# AmniShield Roadmap & Technical Milestone Tracker

Last updated: October 2026

---

## Strategic Architectural Decisions (October 2026)

- **Unified AmniShield Architecture & Direct Stripe Gateway:**
  - **Decision:** Deprecated third-party merchant billing layers in favor of **Stripe Direct** (Checkout + Subscriptions) for non-Google builds (`fdroid`, `universal`, Windows Desktop, Web Portal) paired with offline ECDSA NIST P-256 license verification. Google Play builds utilize Google Play `BillingClient`.
  - **Single Unified Console:** Eliminated legacy dual-mode concepts ("Guardian Console vs Personal Console"). All policy enforcement and cross-device synchronization are driven through the unified AmniShield Console architecture.
  - **Privacy-First Local Diagnostic Logging:** Built a 100% on-device, zero-telemetry diagnostic engine (`CrashLogger`) with automated PII sanitization and in-app Material 3 Log Viewer with native Android Sharesheet export.

---

## V1.0 - V2.0 Roadmap

### Completed Milestones (Current Baseline - v0.3.1 / Code 145)

- [x] **Continuous Cross-App & Cross-Session Reels Engine:** Daily accumulation across app launches and multiple shortform platforms (YouTube Shorts, Instagram Reels, TikTok, Facebook Reels, ReVanced, MyInsta, Lite editions) with midnight cache invalidation.
- [x] **Resilient Fallback Node Comparator:** Dynamic view-tree traversal (depth up to 8) extracting title, creator, and audio elements to detect reel progression when native platform resource IDs change.
- [x] **ViewPager2 Swipe Interception:** Integrated `AccessibilityEvent.TYPE_VIEW_SELECTED` for instantaneous vertical swipe progression detection.
- [x] **Synchronous Floating Pill Overlay:** Main-thread `runOnMain` view inflation and real-time counter binding with hour-formatted duration (`hh:mm:ss`).
- [x] **Browser Website Engagement Tracker (`BrowserSessionTracker`):** 2-second background active window domain engagement tracking with screen power-saving lifecycle handling.
- [x] **Pre-Registration Founder Pass & Entitlements:** Google Play Billing pre-registration entitlement check, 90-day security suite grant, and Founding Supporters Wall.
- [x] **Hardware Monotonic Clock Verification:** Defense against local system time manipulation for license and pass verification.
- [x] **Interceptor-Based `AccessibilityService` Modularization:** Decoupled chain of responsibility pipeline (AntiUninstall, AppBlocker, FocusMode, WebsiteBlocker, KeywordBlocker, ReelsBlocker) with strict node lifecycle and short-circuiting invariants.
- [x] **On-Device ECDSA NIST P-256 Cryptographic License Validation:** Fully offline cryptographic license verification for F-Droid and sideloaded builds.
- [x] **Diagnostics & In-App Crash Log Viewer:** Material 3 diagnostic log console with PII redaction, search, category filtering, copy-to-clipboard, and native Android Sharesheet (`FileProvider`) export.
- [x] **Hardened Anti-Uninstall & Tamper Defense:** Multi-vector interception covering Google Play Store uninstallation, OEM Security Centers, Device Administrator deactivation, and settings tampering with bulletproof Home exit routing.
- [x] **Home Screen Widgets Suite:** Glanceable Material 3 widgets for Daily Screen Time, Reels Scroll Counter, Quick Focus Sessions, and Mindful Breathing.

---

### Active / Upcoming Milestones (V1.1 - V2.0)

- [ ] **Multi-Platform Cloud Policy Sync [Under Development - Premium Feature]:**
  - Planned end-to-end encrypted rule and policy synchronization across Android, Windows, and Web Console for AmniShield Premium.
  - **Phase 1: Backend Infrastructure & Cryptographic Pairing Handshake (Milestone V1.1)**
    - Production PostgreSQL database schema provisioning for `devices`, `sync_records`, and audit ledger in Supabase.
    - Hardened Row Level Security (RLS) enforcement ensuring all policy access is strictly isolated by authenticated user ID (`auth.uid() = owner_id`).
    - Ephemeral 6-digit PIN and ZXing camera QR pairing protocol with zero-knowledge AES-256-GCM client-side key derivation.
  - **Phase 2: Bidirectional Delta Synchronization & WorkManager Engine (Milestone V1.2)**
    - Monotonic hardware clock and vector timestamp conflict resolution (additive union for domain/keyword blocklists; last-write-wins for scheduled focus intervals).
    - Android WorkManager periodic background sync with exponential backoff and network unmetered constraints.
    - Offline-first cache: local Room DB / EncryptedSharedPreferences with rollback protection and audit journal.
  - **Phase 3: Unified Web Console & Fleet Policy Enforcement (Milestone V1.3)**
    - Production deployment of `app.amnishield.com` Web Console for centralized multi-device fleet oversight.
    - Role-based separation: Personal Mode (self-management) vs Guardian Mode (parental/enterprise non-bypassable policy enforcement).
    - Remote device heartbeat, status monitoring, rule propagation, and instantaneous remote unpair.
  - **Phase 4: Cross-Platform Desktop & Extensions (Milestone V1.5 - V2.0)**
    - Windows elevated background service managing UAC-free network `hosts` resolution and desktop process monitoring.
    - Browser extension policy synchronization across Chromium and Firefox engines.
- [ ] **Ephemeral 6-Digit PIN & In-App ZXing Camera QR Pairing [Under Development - Premium Feature]:**
  - Secure, frictionless cross-device pairing between mobile devices and the Web Console.
- [ ] **Unified AmniShield Console [Under Development - Premium Feature]:**
  - Web management console for multi-device focus rules and schedules.
- [ ] **Decoupled Console Policy vs. Personal Sync [Under Development - Premium Feature]:**
  - Separate tiers for parental/enterprise console lockdown and personal cross-device sync.
- [ ] **Advanced Bulk List Import & Export (CSV/TXT) [In Active Development - V1.1]:**
  - Structured CSV/TXT import and export engine for custom Keyword and Website blocklists.

---

## Architectural Standards & Development Invariants

1. **Accessibility Node Lifecycle Invariant:** `AmniShieldAccessibilityService` exclusively owns the lifecycle of `rootNode`. Sub-interceptors and detectors must **never** call `rootNode.recycle()`.
2. **Deterministic Priority (Fail-Safe First):** Security and Anti-Uninstall evaluation runs first at Priority 0.
3. **Zero External Telemetry:** All diagnostic logs, crash reports, and usage metrics remain 100% on-device in app-private storage.
4. **PII Sanitization Rule:** Sensitive tokens, passwords, PINs, auth headers, and emails are stripped by regex filters before being written to disk.
5. **Zero Emoji Policy:** Strict universal rule prohibiting unicode emojis across all code, UI layouts, strings, commit messages, and documentation.
