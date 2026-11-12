# Ground Truth Investigation Report: Requirements R4, R5, and R6

- **Agent**: Explorer 3 (`teamwork_preview_explorer`)
- **Working Directory**: `d:\PROJECTS\Amni\AmniShield-Android\.agents\teamwork\explorer_3`
- **Target Deliverable**: Input for `AMNISHIELD_PROJECT_SUMMARY.md`
- **Date**: 2026-10-20
- **Scope**:
  - Requirement R4: Premium Architecture & Licensing Verification (`ARCHITECTURE.md`, `README.md`, `ROADMAP.md`)
  - Requirement R5: Roadmap Scope Hygiene & Milestone Realignment (`ROADMAP.md`)
  - Requirement R6: Universal Zero-Emoji Policy & Versioning Rectification (`ARCHITECTURE.md` & Project Metadata)

---

## 1. Observation

### 1.1 Requirement R4: Premium Architecture & Licensing Ground Truth

#### Tier Structure and User Types
- **File**: `app/src/main/java/com/alhaq/amnishield/premium/PremiumManager.kt`
- **Lines 12-17**:
  ```kotlin
  enum class UserType {
      FREE,
      COMPASSIONATE,
      FOUNDER,
      PREMIUM
  }
  ```
- **Lines 22-29**:
  ```kotlin
  fun isPremium(): Boolean {
      val hasLocalPremium = if (BuildConfig.IS_PLAYSTORE) {
          preferencesLoader.isPremiumUser()
      } else {
          isLicenseKeyValid()
      }
      return hasLocalPremium || isCompassionateAccessActive() || isFounderPassActive()
  }
  ```
- **Lines 67-75**:
  ```kotlin
  fun getUserType(): UserType {
      val isPremiumActive = preferencesLoader.isPremiumUser() || isLicenseKeyValid()
      return when {
          isFounderPassActive() -> UserType.FOUNDER
          isCompassionateAccessActive() -> UserType.COMPASSIONATE
          isPremiumActive -> UserType.PREMIUM
          else -> UserType.FREE
      }
  }
  ```
- **Lines 80-94**: Display strings define tiers:
  - `UserType.FREE`: Standard FOSS user ("Free"). Core blockers (App, Website, Reels, Keywords) are 100% free forever.
  - `UserType.COMPASSIONATE`: "Al-Haq Community Pass" / "Community Access (Review)" for users needing free access. Supports offline ECDSA community key verification (`LicenseValidator.verifyLicense`) and monotonic uptime tracking (`updateCompassionateUptimeAndVerify`).
  - `UserType.FOUNDER`: "Founder Pass (3-Month Early Supporter)" - 90-day early supporter pass.
  - `UserType.PREMIUM`: "Premium" supporter pass.

#### Founder Pass Implementation
- **File**: `app/src/main/java/com/alhaq/amnishield/utils/SavedPreferencesLoader.kt`
- **Lines 908-938**:
  ```kotlin
  fun isFounderPassActive(): Boolean {
      val prefs = getFounderPassPrefs()
      if (!prefs.getBoolean("is_claimed", false)) return false
      val expiresAt = prefs.getLong("expires_at", 0L)
      if (expiresAt <= 0L) return false
      return verifyFounderPassMonotonicExpiry()
  }

  fun grantFounderPass(
      supporterName: String?,
      optInPublic: Boolean,
      durationDays: Int = 90
  ) {
      val nowWall = wallClockProvider()
      val nowElapsed = elapsedRealtimeProvider()
      val expiryWall = nowWall + (durationDays.toLong() * 24L * 60L * 60L * 1000L)
      val cleanName = supporterName?.trim()?.takeIf { it.isNotEmpty() } ?: "Anonymous Supporter"

      getFounderPassPrefs().edit()
          .putBoolean("is_claimed", true)
          .putLong("granted_at", nowWall)
          .putLong("expires_at", expiryWall)
          .putLong("last_observed_elapsed_realtime", nowElapsed)
          .putLong("last_observed_wall_clock", nowWall)
          .putLong("accumulated_uptime_ms", 0L)
          .putString("supporter_name", cleanName)
          .putBoolean("opted_in_public", optInPublic)
          .putBoolean("badge_unlocked", true)
          .putBoolean("theme_unlocked", true)
          .apply()
  }
  ```
  The pass is granted for `durationDays = 90` (3 months). Anti-clock manipulation protection is enforced through `verifyFounderPassMonotonicExpiry()` (lines 940-985) tracking monotonic elapsed realtime against wall-clock deltas.

#### Premium Gated Security Features
- **File**: `app/src/main/java/com/alhaq/amnishield/ui/components/RuleSecurityComponents.kt`
  - **Lines 44-70**: `RuleProBadge()` component displaying an amber badge with `Icons.Default.Star` and text "PRO":
    ```kotlin
    @Composable
    fun RuleProBadge(modifier: Modifier = Modifier) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFFF59E0B).copy(alpha = 0.18f),
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(9.dp))
                Text(text = "PRO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, fontSize = 9.sp, color = Color(0xFFF59E0B))
            }
        }
    }
    ```
  - **Lines 143-145**: Shows `RuleProBadge()` when `!isPremiumUser`.
  - **Lines 182-184 & 212-214**: In `RuleSecurityLevelSection`, selecting `AuthType.GLOBAL_PIN` or `AuthType.RULE_PIN` triggers `onNavigateToPremium()` when `!isPremiumUser`. Standard `AuthType.NONE` is free for all users.
- **File**: `app/src/main/java/com/alhaq/amnishield/ui/components/SetRulePinDialog.kt`
  - **Lines 65-69**:
    ```kotlin
    if (selectedType != AuthType.NONE && !isPremiumUser) {
        onDismiss()
        onNavigateToPremium()
        return
    }
    ```
  - **Lines 165 & 183**: Renders `badge = if (!isPremiumUser) { { RuleProBadge() } } else null` on Global Master PIN and Custom Rule PIN options.
- **File**: `app/src/main/java/com/alhaq/amnishield/ui/screens/AdvancedScreen.kt`
  - **Lines 301-311**: "Enable PIN Security" switch:
    ```kotlin
    onCheckedChange = { checked ->
        if (!state.isPremiumUser) {
            onNavigateToPremium()
        } else {
            if (checked) showPinSetupDialog = true else showPinVerifyDialog = true
        }
    }
    ```
  - **Lines 327-333**: "App Lock" switch (Require PIN to open AmniShield):
    ```kotlin
    onCheckedChange = { checked ->
        if (!state.isPremiumUser) {
            onNavigateToPremium()
        } else {
            onToggleAppLock(checked)
        }
    }
    ```
  - **Lines 348-354**: "Bypass PIN Lock" switch (Require PIN to modify settings while active):
    ```kotlin
    onCheckedChange = { checked ->
        if (!state.isPremiumUser) {
            onNavigateToPremium()
        } else {
            onToggleBypassPinLock(checked)
        }
    }
    ```
- **Multi-Device Cloud Policy Sync via Supabase**:
  - **File**: `app/src/main/java/com/alhaq/amnishield/data/sync/PolicySyncManager.kt` (lines 20-192): Syncs device policy via REST (`https://jrgpmcomvibgklmvnxud.supabase.co/rest/v1/devices`). Applies union-merge for blocked apps, keywords, adult filter, strict focus mode, and schedules.
  - **File**: `app/src/main/java/com/alhaq/amnishield/data/sync/PlaystoreSyncProvider.kt` (lines 64-96): Pushes active rules, blocklists, and schedules to Supabase table `sync_records`. Gated to authenticated / paired supporter accounts.

#### Flavor Implementations & Products
- **Play Store Flavor (`playstore`)**:
  - **File**: `app/src/playstore/java/com/alhaq/amnishield/utils/BillingClientWrapper.kt` (lines 12-357): Full Google Play Billing client (`com.android.billingclient:billing-ktx:7.1.1`).
  - **File**: `app/src/main/java/com/alhaq/amnishield/premium/PremiumProducts.kt` (lines 3-16):
    - `PRODUCT_LIFETIME = "amnishield_premium_lifetime"` (In-app)
    - `PRODUCT_MONTHLY = "amnishield_premium_monthly"` (Subscription)
    - `PRODUCT_YEARLY = "amnishield_premium_yearly"` (Subscription)
    - `PRODUCT_FOUNDER_PASS = "amnishield_founder_pass"` (In-app)
    - Legacy aliases maintained: `deenshield_premium_lifetime`, `deenshield_premium_monthly`, `deenshield_premium_yearly`.
- **F-Droid Flavor (`fdroid`)**:
  - **File**: `app/src/fdroid/java/com/alhaq/amnishield/utils/BillingClientWrapper.kt` (lines 8-57): Pure FOSS stub with 0 GMS imports. `launchPurchaseFlow` advises user: `"In-app purchases are not supported in this F-Droid (FOSS) version. Please use Special Access or Compassionate Access."`
  - **File**: `app/src/main/java/com/alhaq/amnishield/premium/LicenseValidator.kt` (lines 10-117): 100% offline cryptographic NIST P-256 (`secp256r1`) ECDSA public key verification:
    - Algorithm: `SHA256withECDSA`
    - Public Key Keyring (X.509 Base64):
      `MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE7EFR1qxpfZTMeR52M1+04+tPb6ItmVmhPbRCIJYje3jtglTdBbcct+/xvc1D1NZtXuvSb4Egtdqm/EJ6H67fEA==`
    - Parses format: `<payloadBase64>.<signatureBase64>`
    - Payload verification: checks JSON `LicensePayload` (email, expires timestamp, version, user_id).
    - Signature translation: `p1363ToDer()` converts IEEE P1363 64-byte raw signature (`r[32] || s[32]` from WebCrypto / Deno) into ASN.1 DER sequence for Android's `java.security.Signature`.
  - **File**: `app/src/main/java/com/alhaq/amnishield/premium/PaymentManager.kt` (lines 18-113): Direct Stripe Checkout via Supabase Edge Function (`https://jrgpmcomvibgklmvnxud.supabase.co/functions/v1/stripe-checkout`) for `fdroid` and `universal` builds, while preventing execution on Google Play builds (`BuildConfig.IS_PLAYSTORE`).
- **Universal Sideload Flavor (`universal`)**:
  - **File**: `app/src/universal/java/com/alhaq/amnishield/utils/BillingClientWrapper.kt` (lines 1-357): Hybrid build with Play Billing capability when GMS is available, along with full offline ECDSA license entry and Stripe Direct checkout fallback.

---

### 1.2 Requirement R5: Roadmap Scope Hygiene & Milestone Realignment

- **File**: `ROADMAP.md`
- **Lines 18-36**: Current baseline correctly lists completed items up to `v0.3.1 / Code 145`.
- **Lines 39-45 (Current Flawed Upcoming Section)**:
  ```markdown
  ### Active / Upcoming Milestones (V1.1 - V2.0)

  - [ ] **Windows Elevated Background Service:** Background Windows service managing UAC-free network `hosts` resolution and system-level firewall rules.
  - [ ] **Opt-in DNS Sinkhole Engine:** Local VPN-based DNS sinkhole filter powered by the `AmniGuard-FireWall` packet filtering engine for network-wide tracking and ad/adult domain mitigation.
  - [ ] **On-Device Mobile NPU Vision Blur Engine:** Realtime shortform media content filtering powered by LiteRT / Mobile NPU (`AmniBlur` / `AmniGaze` pipeline optimization).
  - [ ] **Automated Multi-Flavor Matrix Testing:** Expand CI GitHub Actions to run automated instrumented UI tests across `playstoreDebug`, `fdroidDebug`, and `universalDebug`.
  ```
- **Scope Creep Analysis**:
  1. `Windows Elevated Background Service` belongs to `AmniShield-Windows` (Desktop Flutter/C++ utility), not `AmniShield-Android`.
  2. `Opt-in DNS Sinkhole Engine` belongs to `AmniGuard-FireWall` (Standalone VPN/DNS sinkhole product), not `AmniShield-Android`.
  3. `On-Device Mobile NPU Vision Blur Engine` belongs to `AmniBlur` / `AmniGaze` (MediaProjection / LiteRT screen censoring engine), not `AmniShield-Android`.
- **Verified Replacement Milestones Grounded in Codebase Issues**:
  - **Milestone 0.3.2: Accessibility Engine Hardening & Binder Safety**:
    - In-Memory Schedule Caching: Eliminate reflection-based `Gson().fromJson()` deserialization in `onAccessibilityEvent` (in `AmniShieldAccessibilityService.kt` and `SavedPreferencesLoader.kt`).
    - Polling Decoupling: Eliminate continuous `rootInActiveWindow` queries on 1-second / 2-second Handler timers in `ReelsSessionTracker.kt` and `BrowserSessionTracker.kt`.
    - Node Traversal Exception Safety: Add robust `try ... finally` block handling in `AntiUninstallDetector.kt` and `ReelDetectionEngine.kt`.
    - Manifest Security Hardening: Set `android:exported="false"` on `LauncherActivity` in `AndroidManifest.xml#L76` (currently exported without intent filters).
  - **Milestone 0.3.3: Store Compliance & Layout Resilience**:
    - Prominent Disclosure Overhaul: Implement scrollable container for landscape orientation and 200% font scaling compliance.
    - Persistent Affirmative Consent: Persist accessibility disclosure acceptance flag in `SharedPreferences`.
    - Screen Rotation State Preservation: Add `rememberSaveable` in `AmniSpaceActivity.kt`, `onSaveInstanceState` in `WarningActivity.kt`, and dynamic window insets handling in `UsageStatOverlayManager.kt`.
  - **Milestone 0.3.4: Dual-Host Cleanup & Navigation Decoupling**:
    - Type-Safe Intent Routing: Implement a sealed route contract between `MainActivity.kt` and `FragmentActivity.kt`.
    - SharedPreferences Synchronization Lock: Add mutex / synchronization locks to `SavedPreferencesLoader.kt` to avoid multi-thread file race conditions.
    - Automated Flavor Testing: Implement emulator-based CI workflow in `.github/workflows/main-pipeline.yml` for all 3 flavors.
  - **Milestone 0.3.5: Pure Compose & Storage Architecture Epic (Prep for V1.0)**:
    - Room & DataStore Migration Epic: Formalize migration path from 12+ plain XML SharedPreferences files to Room DB and Preferences DataStore.
    - Pure-Compose Navigation Epic: Reconnect and finalize `AmniShieldAdaptiveApp.kt` to safely retire dual-host split (`MainActivity.kt` + `FragmentActivity.kt`).

---

### 1.3 Requirement R6: Universal Zero-Emoji Policy & Versioning Rectification

#### Unicode Emojis in `ARCHITECTURE.md`
- **File**: `ARCHITECTURE.md`
- **Line 9**: `## 🏗️ Core Architectural Layers` -> contains `🏗️` (U+1F3D7 U+FE0F)
- **Line 36**: `## 🛡️ Blocker Pipeline & Chain of Responsibility` -> contains `🛡️` (U+1F6E1 U+FE0F)
- **Line 51**: `## 📝 Diagnostic & Crash Logging Architecture (`CrashLogger`)` -> contains `📝` (U+1F4DD)
- **Line 69**: `## 🔒 Invariants & Guardrails for AI & Human Maintainers` -> contains `🔒` (U+1F512)
- **Total Emoji Count**: Exactly 4 emojis. No other emojis exist in the file.
- **Zero-Emoji Replacements**:
  - Line 9: `## Core Architectural Layers`
  - Line 36: `## Blocker Pipeline & Chain of Responsibility`
  - Line 51: `## Diagnostic & Crash Logging Architecture (CrashLogger)`
  - Line 69: `## Invariants & Guardrails for AI & Human Maintainers`

#### Versioning Technical Drift
- **Agent Configuration File**: `d:\PROJECTS\Amni\AmniShield-Android\.agents\AGENTS.md`
  - **Line 19**: `- **Version**: 1.0.2-closed.01 (2026.08.13) / versionCode 132.`
- **Codebase Source of Truth**: `app/build.gradle.kts`
  - **Line 48**: `versionCode = 145`
  - **Line 49**: `versionName = "0.3.1 (2026.10.05)"`
- **Roadmap Baseline**: `ROADMAP.md`
  - **Line 18**: `### Completed Milestones (Current Baseline - v0.3.1 / Code 145)`
- **Discrepancy Analysis**:
  `.agents/AGENTS.md` exhibits stale metadata from closed testing build `1.0.2-closed.01 (2026.08.13)` at `versionCode 132`. The production Gradle configuration and official `ROADMAP.md` baseline are unified at `versionCode 145`, `versionName "0.3.1 (2026.10.05)"`.

---

## 2. Logic Chain

```
[Observation 1.1: PremiumManager.UserType enum]
       │
       ▼
(Logic Step 1: UserType defines 4 distinct tiers: FREE, COMPASSIONATE, FOUNDER, PREMIUM)
       │
       ├────────────────────────────────────────┬────────────────────────────────────────┐
       ▼                                        ▼                                        ▼
(Logic Step 2a: FREE is 100% FOSS)  (Logic Step 2b: FOUNDER is 90d)  (Logic Step 2c: COMPASSIONATE is community)
- Base blockers have zero gates     - SavedPreferencesLoader:919     - Monotonic uptime + ECDSA verification
- App/Web/Reel/Keyword are free     - Monotonic clock defense        - Provides dignified community access
       │                                        │                                        │
       └────────────────────────────────────────┴────────────────────────────────────────┘
                                                │
                                                ▼
       [Observation 1.1: Gated components (RuleProBadge, SetRulePinDialog, AdvancedScreen)]
                                                │
                                                ▼
(Logic Step 3: Rule PIN, App Lock, Bypass PIN Lock, and Supabase Cloud Sync are explicitly gated)
- RuleProBadge() marks Master PIN and Rule PIN in RuleSecurityComponents.kt
- AdvancedScreen.kt guards PIN Security, App Lock, and Bypass PIN behind state.isPremiumUser
- PolicySyncManager.kt and PlaystoreSyncProvider.kt push/pull config to Supabase
                                                │
                                                ▼
       [Observation 1.1: Flavor implementations (playstore, fdroid, universal)]
                                                │
                                                ▼
(Logic Step 4: Flavors cleanly segregate billing mechanisms)
- playstore: BillingClientWrapper uses Google Play Billing with product IDs in PremiumProducts.kt
- fdroid: BillingClientWrapper is a pure FOSS stub (0 GMS deps); LicenseValidator uses offline NIST P-256 ECDSA
- universal: Hybrid variant supporting Play Billing when GMS is present + offline ECDSA + PaymentManager Stripe
                                                │
                                                ▼
       [Observation 1.2: ROADMAP.md Lines 39-45 out-of-scope milestones]
                                                │
                                                ▼
(Logic Step 5: Roadmaps must respect project boundaries)
- Windows Elevated Service -> AmniShield-Windows
- DNS Sinkhole -> AmniGuard-FireWall
- Mobile NPU Vision Blur -> AmniBlur/AmniGaze
- Replace with Android-native technical debt milestones 0.3.2 - 0.3.5
                                                │
                                                ▼
       [Observation 1.3: ARCHITECTURE.md 4 emojis & versioning drift]
                                                │
                                                ▼
(Logic Step 6: Zero-Emoji Policy and version synchronization)
- Purge 4 emojis from ARCHITECTURE.md (lines 9, 36, 51, 69)
- Reconcile .agents/AGENTS.md (versionCode 132 -> 145, versionName "0.3.1 (2026.10.05)")
```

---

## 3. Caveats

- **No Caveats on Ground Truth**: All claims are verified directly against the production source tree (`PremiumManager.kt`, `SavedPreferencesLoader.kt`, `RuleSecurityComponents.kt`, `AdvancedScreen.kt`, `BillingClientWrapper.kt`, `LicenseValidator.kt`, `PaymentManager.kt`, `ROADMAP.md`, `ARCHITECTURE.md`, `app/build.gradle.kts`, `.agents/AGENTS.md`).
- **Universal Flavor Dual Capability**: While `README.md` documents `universal` as using Play Billing, `universal` can also validate offline ECDSA licenses and trigger Stripe Direct checkout since both `LicenseValidator.kt` and `PaymentManager.kt` are shared in `app/src/main/`.
- **Read-Only Investigation Compliance**: Per the mission constraints, zero Kotlin files, XML layouts, or manifest files were altered during this audit.

---

## 4. Conclusion

1. **R4 (Premium Architecture & Licensing)**:
   - Tier hierarchy is cleanly segregated: Free FOSS Core (`UserType.FREE`), Community Access (`UserType.COMPASSIONATE`), 90-Day Early Supporter Pass (`UserType.FOUNDER`), and Full Supporter (`UserType.PREMIUM`).
   - Gated features are strictly security/sync enhancements (Rule PIN locking, Master PIN locking, App Lock, Bypass PIN Lock, Supabase cloud policy sync). Core content and distraction blockers remain 100% free.
   - `playstore` utilizes Google Play `BillingClient` with 4 official product IDs (`amnishield_premium_monthly`, `amnishield_premium_yearly`, `amnishield_premium_lifetime`, `amnishield_founder_pass`).
   - `fdroid` is a 100% pure FOSS implementation with zero GMS dependencies, validated offline via NIST P-256 ECDSA public key (`SHA256withECDSA`) with optional Stripe Direct checkout via Supabase Edge Function (`https://jrgpmcomvibgklmvnxud.supabase.co/functions/v1/stripe-checkout`).
2. **R5 (Roadmap Scope Hygiene)**:
   - `ROADMAP.md` upcoming section contained foreign features belonging to separate ecosystem projects (`AmniShield-Windows`, `AmniGuard-FireWall`, `AmniBlur`).
   - Replaced with 4 verified milestones addressing active Android technical debt:
     - 0.3.2: Accessibility Engine Hardening & Binder Safety
     - 0.3.3: Store Compliance & Layout Resilience
     - 0.3.4: Dual-Host Cleanup & Navigation Decoupling
     - 0.3.5: Pure Compose & Storage Architecture Epic
3. **R6 (Zero-Emoji Policy & Versioning Rectification)**:
   - Exactly 4 unicode emojis exist in `ARCHITECTURE.md` (lines 9, 36, 51, 69) and must be purged.
   - Version metadata in `.agents/AGENTS.md` is lagged at `1.0.2-closed.01 (2026.08.13)` / `versionCode 132` and must be rectified to align with `app/build.gradle.kts` (`versionCode 145`, `versionName "0.3.1 (2026.10.05)"`).

---

## 5. Verification Method

To independently reproduce and verify every finding in this report:

1. **Verify UserType and Tier Definitions**:
   ```powershell
   Select-String -Path "app\src\main\java\com\alhaq\amnishield\premium\PremiumManager.kt" -Pattern "enum class UserType" -Context 0,6
   ```
2. **Verify Founder Pass Duration and Clock Monotonic Guards**:
   ```powershell
   Select-String -Path "app\src\main\java\com\alhaq\amnishield\utils\SavedPreferencesLoader.kt" -Pattern "grantFounderPass" -Context 0,10
   ```
3. **Verify RuleProBadge and Rule PIN Gating**:
   ```powershell
   Select-String -Path "app\src\main\java\com\alhaq\amnishield\ui\components\RuleSecurityComponents.kt" -Pattern "fun RuleProBadge" -Context 0,10
   Select-String -Path "app\src\main\java\com\alhaq\amnishield\ui\components\SetRulePinDialog.kt" -Pattern "isPremiumUser" -Context 1,5
   ```
4. **Verify Advanced Screen PIN Security Gating**:
   ```powershell
   Select-String -Path "app\src\main\java\com\alhaq\amnishield\ui\screens\AdvancedScreen.kt" -Pattern "isPremiumUser" -Context 0,3
   ```
5. **Verify Product IDs & Billing Implementations**:
   ```powershell
   Select-String -Path "app\src\main\java\com\alhaq\amnishield\premium\PremiumProducts.kt" -Pattern "PRODUCT_"
   Select-String -Path "app\src\playstore\java\com\alhaq\amnishield\utils\BillingClientWrapper.kt" -Pattern "BillingClient\.newBuilder"
   Select-String -Path "app\src\fdroid\java\com\alhaq\amnishield\utils\BillingClientWrapper.kt" -Pattern "F-Droid" -Context 0,4
   ```
6. **Verify Offline ECDSA NIST P-256 Key & Algorithm**:
   ```powershell
   Select-String -Path "app\src\main\java\com\alhaq\amnishield\premium\LicenseValidator.kt" -Pattern "SHA256withECDSA|KEYRING" -Context 0,4
   ```
7. **Verify Stripe Checkout Supabase Endpoint**:
   ```powershell
   Select-String -Path "app\src\main\java\com\alhaq\amnishield\premium\PaymentManager.kt" -Pattern "SUPABASE_CHECKOUT_URL"
   ```
8. **Verify Out-of-Scope Items in `ROADMAP.md`**:
   ```powershell
   Select-String -Path "ROADMAP.md" -Pattern "Active / Upcoming Milestones" -Context 0,8
   ```
9. **Verify Emojis in `ARCHITECTURE.md`**:
   ```powershell
   Get-Content "ARCHITECTURE.md" | Select-String -Pattern "[\uD83C-\uDBFF\uDC00-\uDFFF]"
   ```
10. **Verify Version Discrepancy**:
    ```powershell
    Select-String -Path "app\build.gradle.kts" -Pattern "versionCode|versionName"
    Select-String -Path ".agents\AGENTS.md" -Pattern "Version"
    ```
