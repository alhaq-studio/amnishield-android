# AmniShield Roadmap & Technical Milestone Tracker

Last updated: August 2026

---

## 🏛️ Strategic Architectural Decisions (August 2026)

- **Unified AmniShield Architecture & Direct Stripe Gateway:**
  - **Decision:** Deprecated third-party merchant billing layers in favor of **Stripe Direct** (Checkout + Subscriptions) for non-Google builds (`fdroid`, `universal`, Windows Desktop, Web Portal) paired with offline ECDSA NIST P-256 license verification. Google Play builds utilize Google Play `BillingClient`.
  - **Single Unified Console:** Eliminated legacy dual-mode concepts ("Guardian Console vs Personal Console"). All policy enforcement and cross-device synchronization are driven through the unified AmniShield Console architecture.
  - **Privacy-First Local Diagnostic Logging:** Built a 100% on-device, zero-telemetry diagnostic engine (`CrashLogger`) with automated PII sanitization and in-app Material 3 Log Viewer with native Android Sharesheet export.

---

## 🗺️ V1.0 - V2.0 Roadmap

### ✅ Completed Milestones (V1.0 Baseline)

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

### 🚧 Active / Upcoming Milestones (V1.1 - V2.0)

- [ ] **Windows Elevated Background Service:** Background Windows service managing UAC-free network `hosts` resolution and system-level firewall rules.
- [ ] **Opt-in DNS Sinkhole Engine:** Local VPN-based DNS sinkhole filter powered by the `AmniGuard-FireWall` packet filtering engine for network-wide tracking and ad/adult domain mitigation.
- [ ] **On-Device Mobile NPU Vision Blur Engine:** Realtime shortform media content filtering powered by LiteRT / Mobile NPU (`AmnGaze` pipeline optimization).
- [ ] **Automated Multi-Flavor Matrix Testing:** Expand CI GitHub Actions to run automated instrumented UI tests across `playstoreDebug`, `fdroidDebug`, and `universalDebug`.

---

## 📜 Architectural Standards & Development Invariants

1. **Accessibility Node Lifecycle Invariant:** `AmnShieldAccessibilityService` exclusively owns the lifecycle of `rootNode`. Sub-interceptors and detectors must **never** call `rootNode.recycle()`.
2. **Deterministic Priority (Fail-Safe First):** Security and Anti-Uninstall evaluation runs first at Priority 0.
3. **Zero External Telemetry:** All diagnostic logs, crash reports, and usage metrics remain 100% on-device in app-private storage.
4. **PII Sanitization Rule:** Sensitive tokens, passwords, PINs, auth headers, and emails are stripped by regex filters before being written to disk.
