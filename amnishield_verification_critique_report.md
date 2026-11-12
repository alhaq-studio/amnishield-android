# Staff Android Security Auditor & System Verifier Report: AmniShield-Android
## Phase 1 Discovery & Prior Investigation Critique (Devil's Advocate Audit)

**Evaluator**: Senior Staff Android Security Auditor & System Verifier (Devil's Advocate)  
**Target Repository**: `AmniShield-Android` ([`AmniShield-Android`](file:///d:/PROJECTS/Amni/AmniShield-Android))  
**Audited Documents**:  
- [Phase 1 Architectural Discovery Report](file:///c:/Users/HabiburRahman/.gemini/antigravity/brain/c73e3778-ba55-4d61-9bb8-8ff0ccf91020/amnishield_architectural_discovery_report.md)  
- Prior Devil's Advocate Verification Hypotheses  
**Evaluation Standard**: Grounded Codebase Verification, Android Framework Lifecycle Contracts, Zero-Emoji Policy Compliance  
**Evaluation Date**: October 2026  

---

## 1. Executive Summary & Devil's Advocate Critique

A rigorous, evidence-grounded review was conducted to evaluate both the **Phase 1 Architectural Discovery Report** and the **Prior Verification Hypotheses** against the live [`AmniShield-Android`](file:///d:/PROJECTS/Amni/AmniShield-Android) codebase.

### Key Conclusions & Critical Corrections to Prior Findings

1. **The In-Flight Gson Deserialization Hazard (Far Worse Than Simple Disk I/O)**:  
   Prior reviews characterized the bottleneck in `onAccessibilityEvent` as simply reading `isWebsiteBlockerEnabled` from `SharedPreferences`. In reality, the critical performance failure is that `isFeatureCurrentlyActive()` and `getActiveBlockedWebsites()` invoke [`SavedPreferencesLoader.loadAppBlockerScheduleRules()`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/utils/SavedPreferencesLoader.kt#L740-L766), which executes **reflection-based Gson JSON deserialization** of the schedule rules list **2 to 3 times synchronously on the Main Binder Thread** during `onAccessibilityEvent`, plus another 2 times in the deferred background coroutine. On high-frequency events, this causes massive CPU thrashing and frame drops.
2. **Technical Correction of `SharedPreferences.apply()` Semantics**:  
   Prior reports claimed that `loader.setWebsiteBlockerEnabled(...)` causes a "synchronous disk write". Under the Android Framework contract, `SharedPreferences.Editor.apply()` updates the in-memory map **synchronously in memory** and commits to disk **asynchronously**. The accessibility service immediately sees the updated state on the next event because both components share the same process and read the updated in-memory map, not because of a synchronous disk write.
3. **Correction of Logical Leap: `ApiPermissionActivity` Caller Spoofing**:  
   Prior reports asserted that because [`ApiPermissionActivity.kt#L58`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/api/ApiPermissionActivity.kt#L58) falls back to `referrer?.host`, a malicious caller can spoof its identity to obtain unauthorized API execution. This was a logical leap. If an attacker spoofs `referrer?.host` as `com.alhaq.amnishield.guardian`, `ApiAuthStore.grant(this, requester)` records the grant *for the spoofed package*, NOT the attacker. When the attacking application subsequently binds to [`AmniShieldApiService.kt#L68`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/api/AmniShieldApiService.kt#L68), `callerAllowed()` verifies the caller via `packageManager.getPackagesForUid(Binder.getCallingUid())`. The OS Binder kernel provides the unforgeable calling UID, matching the attacker's actual package name, which was never granted. Furthermore, [`AndroidManifest.xml#L18,L120`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/AndroidManifest.xml#L18) guards `AmniShieldApiService` with `permission="${applicationId}.permission.BIND_API"` with `protectionLevel="signature"`. An unprivileged third-party app cannot bind at the OS IPC level. The actual issue is **UI Phishing / Confused Deputy / Denial of Service** (malicious apps repeatedly prompting users with deceptive dialogs) and **Dead Code Architecture**.
4. **Refutation of the "50k+ Local Domain Lookup" Premise**:  
   The assumption that a "50k+ local domain lookup engine" exists is **disproved**. Static assets [`adult_domains.txt`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/assets/blocklists/adult_domains.txt) (52 lines) and [`haram_domains.txt`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/assets/blocklists/haram_domains.txt) (41 lines) are dead text files with **zero references** in Kotlin code. Website matching runs an un-indexed $O(N)$ linear loop in [`WebsiteBlockerDetector.kt#L87-L92`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/blockers/WebsiteBlockerDetector.kt#L87-L92) over user-defined schedule rules loaded dynamically from `SharedPreferences` on the **Main Binder Looper**. If 50,000 domains were ever loaded into this structure, it would execute 50,000 substring comparisons synchronously during `onAccessibilityEvent`, immediately freezing the UI thread and causing guaranteed system ANRs.
5. **Temporal Window Desynchronization in Deferred Processing (Critical Race Hazard)**:  
   While `eventChannel` uses `Channel.CONFLATED` to drop intermediate scroll events, [`AmniShieldAccessibilityService.processDeferredChecks()`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/services/AmniShieldAccessibilityService.kt#L490) fetches a brand-new `rootNode` via `rootInActiveWindow` (line 492) on `Dispatchers.Default` instead of inspecting the node tree from the original event. If a user rapidly swipes away from a short-form video into an innocent app (e.g., Phone Dialer, Home Screen, Messaging), `processDeferredChecks` evaluates the *new* window using the *old* event's package context. This triggers **false-positive blocking actions (Back press or `AmniSpaceActivity` mindful pause) on innocent applications**.
6. **Roadmap Milestone Purge & Re-Alignment**:  
   In [`ROADMAP.md#L39-L45`](file:///d:/PROJECTS/Amni/AmniShield-Android/ROADMAP.md#L39-L45), 3 out of 4 upcoming milestones belong to external sister applications:
   - *"On-Device Mobile NPU Vision Blur Engine"* belongs to **`AmniBlur` / `AmniHaze`**.
   - *"Opt-in DNS Sinkhole Engine"* belongs to **`AmniGuardFirewall`**.
   - *"Windows Elevated Background Service"* belongs to **`AmniShield-Windows`**.  
   These milestones must be purged from `ROADMAP.md`, and the section header must be re-anchored to **`Active / Upcoming Milestones (V 0.3.2 - 0.3.5 /boost)`**.

---

## 2. Hallucination & Evidence Verification

### 2.1 Concrete Symbols & Architecture Verification

- **Room DB & Preferences DataStore**: **CONFIRMED HALLUCINATIONS IN DOCUMENTATION**. Neither library is imported in [`app/build.gradle.kts`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/build.gradle.kts) or [`gradle/libs.versions.toml`](file:///d:/PROJECTS/Amni/AmniShield-Android/gradle/libs.versions.toml). The entity classes in `com.alhaq.amnishield.data.db` ([`AppBlockRule.kt`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/data/db/AppBlockRule.kt)) are completely dead unannotated stubs. Persistence is 100% plain `SharedPreferences` via [`SavedPreferencesLoader.kt`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/utils/SavedPreferencesLoader.kt) across 12+ XML files.
- **Dual-Host UI Reality**: **CONFIRMED**. The presentation layer is split between:
  1. [`MainActivity.kt`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/ui/activity/MainActivity.kt) (2,022 lines): Hosts XML `ActivityMainBinding`, BottomNavigationView, and DrawerLayout for the 4 core tabs.
  2. [`FragmentActivity.kt`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/ui/activity/FragmentActivity.kt) (210 lines): Secondary host routing 18 feature sub-screens via `intent.getStringExtra("feature_type")`.
- **`AmniShieldAdaptiveApp.kt` Git History Trajectory**: **VERIFIED**.  
  Git commit history reveals the exact migration sequence:
  - Commit `7155646` (`feat(activity): connect AmniShieldAdaptiveApp setContent root in MainActivity`): The adaptive Compose scaffold was connected.
  - Commits `47c967a` and `6e6385b`: Hotfixes attempting to resolve startup crashes caused by uninitialized ViewBinding references in `MainActivity.kt`.
  - Commit `ed57548` (`feat: implement AmniSpace mindful focus launcher space & breathing pause`): The author disconnected `AmniShieldAdaptiveApp` and restored 1,942 lines of legacy XML FragmentManager code to avoid breaking existing dialogs and navigation.
- **Base Service & Debounce Implementation**: Present in [`BaseBlockingService.kt#L8-L64`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/services/BaseBlockingService.kt#L8-L64). Defines `DEFAULT_GLOBAL_ACTION_DEBOUNCE_MS = 1000L` for `pressHome()` and `pressBack()`.

---

### 2.2 Spot-Check 1: Exact Code Path from `AccessibilityEvent` to Block Action

File: [`AmniShieldAccessibilityService.kt`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/services/AmniShieldAccessibilityService.kt)

```
Android OS AccessibilityManagerService (Binder IPC)
  │
  ▼
AmniShieldAccessibilityService.onAccessibilityEvent(event: AccessibilityEvent?) [Line 220]
  │
  ├─► [Lines 221-235] Fast exemptions: null check, self package, com.android.systemui
  ├─► [Lines 239-255] Window state changes & app launch tracking
  ├─► [Line 257] Fetch rootNode: rootInActiveWindow ?: runCatching { event.source }.getOrNull()
  ├─► [Lines 258-263] Synchronous feed to ReelsSessionTracker & BrowserSessionTracker
  ├─► [Lines 268-274] SystemExclusionManager.isExempt() -> exempts dialers, emergency, keyboards;
  │                      routes Settings to AntiUninstallDetector.inspect()
  │
  ├─► PRIORITY 0: AntiUninstallDetector.inspect(event, rootNode) [Line 283]
  │     └─► If match in Play Store, Installer, or Device Admin:
  │           Calls handleAntiUninstallAttempt() [AntiUninstallDetector.kt Line 409]
  │           ├─► performGlobalAction(GLOBAL_ACTION_HOME) [Line 412]
  │           └─► context.startActivity(Intent(AntiUninstallPasswordActivity)) [Line 421]
  │           └─► returns true -> onAccessibilityEvent returns immediately [Line 284]
  │
  ├─► PRIORITY 1: FocusModeBlocker.doesAppNeedToBeBlocked(...) [Line 305]
  │     └─► If blocked:
  │           ├─► FOCUS_INTERCEPTION_STYLE_WORKSPACE: Intent(AmniSpaceActivity) [Line 323]
  │           ├─► FOCUS_INTERCEPTION_STYLE_SILENT: pressHome() [Line 333]
  │           └─► FOCUS_INTERCEPTION_STYLE_DIALOG: Intent(WarningActivity) [Line 337]
  │           └─► returns immediately [Lines 330, 334, 344]
  │
  ├─► PRIORITY 2: WebsiteBlockerDetector.findBlockedWebsite(...) [Lines 350-415]
  │     ├─► Reads savedPreferencesLoader.isWebsiteBlockerEnabled(false) [Line 350]
  │     ├─► Executes getActiveBlockedWebsites() -> deserializes Gson rules [Line 351]
  │     ├─► Sub-check A: blockedSocialApps.contains(packageName) [Line 354]
  │     │     └─► AmniSpaceActivity / pressHome() / WarningActivity [RETURN]
  │     └─► Sub-check B: websiteBlockerDetector.findBlockedWebsite(rootNode, packageName, activeWebsites) [Line 381]
  │           └─► If match: AmniSpaceActivity [Line 387], WarningActivity [Line 398], or pressHome() [Line 408] [RETURN]
  │
  ├─► PRIORITY 3: AppBlocker.doesAppNeedToBeBlocked(...) [Lines 417-448]
  │     ├─► Guard: if (!isFocusBlockAllExSelectedActive) [Line 417]
  │     ├─► Reads savedPreferencesLoader.isAppBlockerFeatureEnabled(false) [Line 418]
  │     └─► If blocked: AmniSpaceActivity [Line 424], pressHome() [Line 434], or WarningActivity [Line 437] [RETURN]
  │
  ├─► DEFERRED PIPELINE: Conflated Channel Dispatch [Lines 450-462]
  │     ├─► Copies event: AccessibilityEvent(event) (API 33+) or AccessibilityEvent.obtain(event)
  │     ├─► eventChannel.trySend(eventCopy)
  │     └─► finally: rootNode?.recycle() [Line 467] (Recycled on Main Binder Thread)
  │
  └─► BACKGROUND WORKER: processDeferredChecks(event) [Lines 490-565] (serviceScope @ Dispatchers.Default)
        ├─► Re-queries rootInActiveWindow [Line 492] (Second concurrent Binder IPC call!)
        ├─► KeywordBlocker.checkIfUserGettingFreaky(rootNode, event) [Line 513] -> KeywordActionHandler [Line 516]
        ├─► ReelBlocker.doesReelNeedToBeBlocked(rootNode, packageName) [Line 545] -> ReelActionHandler [Line 551]
        └─► finally: rootNode.recycle() [Line 564]
```

---

### 2.3 Spot-Check 2: Exact Code Path from Compose Setting Change to Service Pickup

Files:
- [`WebsiteBlockerConfigScreen.kt#L168-L178`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/ui/screens/config/WebsiteBlockerConfigScreen.kt#L168-L178)
- [`SavedPreferencesLoader.kt#L1559-L1572`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/utils/SavedPreferencesLoader.kt#L1559-L1572)
- [`ServiceBroadcastManager.kt#L35-L67`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/services/ServiceBroadcastManager.kt#L35-L67)
- [`AmniShieldAccessibilityService.kt#L171,L350`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/services/AmniShieldAccessibilityService.kt#L171)

```
User toggles switch in WebsiteBlockerConfigScreen.kt (Line 168)
  │
  ▼
1. Mutate In-Memory Compose State:
   isFeatureEnabled = checked [Line 171]
  │
  ▼
2. Write to SharedPreferences:
   loader.setWebsiteBlockerEnabled(checked, updateManual = true) [Line 172]
   └─► sharedPreferences.edit().putBoolean("is_enabled", checked)...apply() [SavedPreferencesLoader.kt Line 1565]
   [Framework Note: apply() updates the in-memory map immediately and writes to disk asynchronously]
  │
  ▼
3. Broadcast Dispatch:
   val refreshIntent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER).apply {
       setPackage(context.packageName)
   }
   context.sendBroadcast(refreshIntent) [Line 174-177]
   [Architectural Bug: Sends REFRESH_APP_BLOCKER instead of a website-specific action!]
  │
  ▼
4. Broadcast Received in Service:
   ServiceBroadcastManager.receiver.onReceive(...) [Line 39]
   └─► matches INTENT_ACTION_REFRESH_APP_BLOCKER -> calls callbacks.onRefreshAppBlocker()
  │
  ▼
5. Service State Reload:
   AmniShieldAccessibilityService.setupAppBlocker() [Line 171]
   └─► Reloads app blocker warning config, blocked apps, and app schedules.
   [Notice: setupAppBlocker() does NOT touch website blocker state!]
  │
  ▼
6. How the Change Actually Takes Effect in the Service:
   On the very next onAccessibilityEvent(event):
   Line 350: savedPreferencesLoader.isWebsiteBlockerEnabled(false) reads directly from the
   process-shared in-memory SharedPreferences instance!
   Because apply() updated the memory cache synchronously, the service picks up the new value
   immediately on the next event, rendering the broadcast intent entirely superfluous.
```

---

### 2.4 Spot-Check 3: The 50k+ Local Domain Lookup Debunked & True Bottleneck Revealed

The prompt instructed to evaluate "how the 50k+ local domain lookup operates without blocking the main thread."

**Verdict**: The 50k+ local domain engine is an **absolute myth and hallucination**.

Codebase Evidence:
1. **Asset File Verification**:
   - [`app/src/main/assets/blocklists/adult_domains.txt`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/assets/blocklists/adult_domains.txt): Exactly 52 lines (51 domains, 1 blank line).
   - [`app/src/main/assets/blocklists/haram_domains.txt`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/assets/blocklists/haram_domains.txt): Exactly 41 lines (40 domains, 1 blank line).
2. **Dead Asset Verification**:
   Running `grep_search` across `app/src/main/java` for `adult_domains`, `haram_domains`, or `blocklists/` yields **zero code references**. The only asset files ever opened in the entire project are [`founders.json`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/ui/screens/JourneyAndFoundersScreen.kt#L582) and two branding images in [`MainActivity.kt#L1652`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/ui/activity/MainActivity.kt#L1652).
3. **Execution Reality in `WebsiteBlockerDetector.kt`**:
   [`WebsiteBlockerDetector.kt#L73-L94`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/blockers/WebsiteBlockerDetector.kt#L73-L94) performs a simple linear loop:
   ```kotlin
   for (site in candidateWebsites) {
       val siteLower = site.trim().lowercase(Locale.ROOT)
       if (siteLower.isNotEmpty() && urlText.contains(siteLower) && !isUnderCooldown(siteLower)) {
           return site
       }
   }
   ```
   The candidate set contains only the user-defined domains configured in the schedule rule (typically 1 to 20 domains).
4. **The Uncovered True Bottleneck (In-Flight Gson Deserialization)**:
   While the 50k domain myth was debunked, a far more dangerous performance hazard was uncovered:
   In `AmniShieldAccessibilityService.kt`:
   - Line 350: `isFeatureCurrentlyActive("website_blocker")` calls `savedPreferencesLoader.loadAppBlockerScheduleRules()`.
   - Line 351: `getActiveBlockedWebsites()` calls `savedPreferencesLoader.loadAppBlockerScheduleRules()`.
   - Line 418: `isFeatureCurrentlyActive("app_blocker")` calls `savedPreferencesLoader.loadAppBlockerScheduleRules()`.
   - Lines 508 & 531: `processDeferredChecks()` calls it 2 more times for keywords and reels.  
   In [`SavedPreferencesLoader.kt#L740-L766`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/utils/SavedPreferencesLoader.kt#L740-L766):
   ```kotlin
   val type = object : TypeToken<MutableList<AppBlockScheduleRule>>() {}.type
   val rawList = Gson().fromJson<MutableList<AppBlockScheduleRule>>(json, type) ?: mutableListOf()
   ```
   **Every single accessibility event deserializes the entire JSON schedule database via reflection multiple times on the Main Binder Thread.** This is the primary driver of Binder latency and ANR vulnerability.

---

## 3. Invariant & Edge-Case Stress Testing

### 3.1 Performance & Memory

#### A. `AccessibilityNodeInfo` Lifecycle: API 26-29 vs. API 30+
- **Modern Android (API 30+)**: In Android 11 (API 30), Google officially deprecated `AccessibilityNodeInfo.recycle()`. In AOSP framework source code, `recycle()` is an empty stub. The Android runtime relies entirely on ART Garbage Collection to reclaim native Binder proxy tokens. The claim that missing `recycle()` calls cause "permanent native Binder memory leaks on modern Android" is factually invalid.
- **Legacy Android (API 26-29)**: Because `minSdk = 26`, devices on Android 8.0 through 10 use an internal 50-object node pool (`sPool`). On these devices:
  - In [`AntiUninstallDetector.kt#L368-L380`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/security/AntiUninstallDetector.kt#L368-L380): Traversal pops nodes from an `ArrayDeque` without a `try ... finally` block. An unhandled exception leaves remaining nodes un-recycled, exhausting the 50-object pool.
  - In [`ReelDetectionEngine.kt#L369-L394`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/trackers/ReelDetectionEngine.kt#L369-L394): Recursive tree walk down to depth 8 lacks `try ... finally` child node recycling.

#### B. High-Frequency Scroll Events (Rapid Reels / Shorts Swiping)
When a user rapidly scrolls shortform video feeds (YouTube Shorts, Instagram Reels, TikTok), `TYPE_VIEW_SCROLLED` and `TYPE_VIEW_SELECTED` fire dozens of times per second.
On every single event, the main thread executes:
1. `rootInActiveWindow` IPC Binder call.
2. Synchronous tree comparison in `ReelsSessionTracker.onAccessibilityEvent()`.
3. Synchronous JSON serialization in `ReelsStatsManager.recordReelScroll()` on `Looper.getMainLooper()` ([`ReelsStatsManager.kt#L173-L197`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/utils/ReelsStatsManager.kt#L173-L197)).
4. Multiple reflection-based `Gson().fromJson()` passes in `isFeatureCurrentlyActive()`.
While `eventChannel` uses `Channel.CONFLATED` for deferred checks, the heavy synchronous work prior to channel dispatch creates severe binder queue contention and dropped frames on low-end hardware.

---

### 3.2 Google Play & Compliance Risk

#### A. Disclosure Dialog Clipping Under 200% Font Scale & Landscape
- **File**: [`dialog_accessibility_disclosure.xml`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/res/layout/dialog_accessibility_disclosure.xml#L1-L185)
- **Structural Defect**: The layout root is explicitly defined as a non-scrolling vertical `LinearLayout`:
  `<!-- Single-Screen Non-Scrolling Prominent Disclosure Dialog for Google Play Compliance -->`
- **Failure Modes**:
  1. **Landscape Orientation**: Typical landscape height is 360–400dp. The unscrollable content height exceeds 420dp. The action buttons (`btn_decline` and `btn_agree` at lines 154-182) are pushed entirely off-screen, preventing user interaction.
  2. **Android 14+ 200% Font Scaling**: Dynamic font scaling doubles text heights, expanding the cards and pushing the action buttons off-screen.
  3. **Hardcoded Heights**: `android:layout_height="40dp"` (lines 157, 172) causes localized strings to be vertically clipped.

#### B. Affirmative Consent Persistence Failure
- **Files**: [`AccessibilityDisclosureDialog.kt#L48,L55-L58`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/utils/AccessibilityDisclosureDialog.kt#L48) and [`PermissionsScreen.kt#L170-L182`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/ui/screens/PermissionsScreen.kt#L170-L182)
- **Defect 1**: `.setCancelable(true)` at line 48 allows users to dismiss the dialog by tapping outside. Google Play automated scanners routinely flag disclosures that permit cancel-on-touch-outside.
- **Defect 2**: When "Agree & Enable" is clicked, `onAgree()` directly starts `Settings.ACTION_ACCESSIBILITY_SETTINGS`. **Affirmative consent is never written to disk.** There is zero persistent flag (e.g. `has_user_accepted_disclosure`) recorded in `SharedPreferences`, creating a compliance vulnerability if an auditable consent record is required.

---

### 3.3 Android System Resilience

#### A. OEM Battery Killers & Accessibility Service Lifecycle
- **Framework Reality**: `AmniShieldAccessibilityService` does not declare `FOREGROUND_SERVICE` and has no persistent notification. While AOSP elevates bound accessibility services, OEM battery managers (Xiaomi HyperOS, Oppo ColorOS, Samsung OneUI sleep lists) terminate background processes during screen-off states.
- **Consequence**: When an OEM task killer forcibly kills an `AccessibilityService` process, the system accessibility manager frequently disables the service in `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`. The user must manually re-navigate Android settings to turn it back on.
- **Boot Recovery**: [`BootAndAlarmReceiver.kt#L18-L35`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/notifications/BootAndAlarmReceiver.kt#L18-L35) only reschedules `daily_report_alarm` on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`. It has no watchdog or recovery logic for the accessibility service.

#### B. Active Overlay & Warning State Loss on Orientation Change
- **`WarningActivity.kt`**: Does not declare `android:configChanges`. Screen rotation triggers activity destruction and recreation. In [`WarningActivity.kt#L77-L95,L197`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/ui/activity/WarningActivity.kt#L77-L95), `onDestroy()` cancels `proceedTimer`, and `onCreate()` restarts it at the maximum delay (`warningScreenConfig.proceedDelayInSecs * 1000L`).
- **`AmniSpaceActivity.kt`**: In [`AmniSpaceActivity.kt#L406`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/ui/activity/AmniSpaceActivity.kt#L406):
  `var isBreathingDone by remember { mutableStateOf(false) }`  
  Because it uses `remember` instead of `rememberSaveable`, rotating the device during mindful breathing wipes state, restarts the breathing circle at 0 seconds, and locks the exit button again.
- **`UsageStatOverlayManager.kt`**: Attaches to `WindowManager` with hardcoded portrait pixel offsets ([`UsageStatOverlayManager.kt#L78-L81`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/ui/overlay/UsageStatOverlayManager.kt#L78-L81)). It lacks an orientation listener to recompute status bar offsets and display cutouts when rotating to landscape.

---

## 4. Agent Safety & Mutation Stress Test

### 4.1 Feature Addition Test
If an autonomous agent is instructed to add a new blocker module (e.g. "Gaming App Blocker"):
1. **DataStore Hallucination**: If the agent follows the outdated instruction in `.agents/AGENTS.md` ("Room DB, Preferences DataStore"), it attempts to import DataStore packages that do not exist in the Gradle dependencies, failing the build.
2. **ANR Escalation via Main Binder Thread**: If the agent inserts synchronous checks into `onAccessibilityEvent` that invoke `isFeatureCurrentlyActive()`, it adds another reflection-based `Gson().fromJson()` call to the main binder thread, increasing ANR risk.
3. **Multi-Thread Binder Contention**: If the agent offloads logic to coroutines and queries `service.rootInActiveWindow` in the background, it creates a fifth concurrent unsynchronized caller to the system accessibility IPC, exacerbating `DeadObjectException` crashes on OEM devices.

### 4.2 Feature Removal Test
If an agent removes an interceptor (e.g. `WebsiteBlocker` or `ReelBlocker`):
1. **Orphaned String Routing**: [`FragmentActivity.kt#L68-L93`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/ui/activity/FragmentActivity.kt#L68-L93) uses raw string matching (`when (featureType)`). Removing a fragment leaves orphaned navigation paths that fail silently.
2. **Background Polling Leaks**: `ReelsSessionTracker` and `BrowserSessionTracker` use `Handler(Looper.getMainLooper()).postDelayed(...)` every 1000ms / 2000ms. Removing an interceptor without shutting down its companion tracker leaves orphaned polling runnables interrogating `rootInActiveWindow` indefinitely.
3. **Abandoned Preferences**: Legacy preference files (`view_blocker`, `social_media_blocker`) remain active in `SavedPreferencesLoader.kt`.

### 4.3 Ecosystem Collision & Scope Boundary Enforcement

#### A. Expunging Foreign Milestones from `ROADMAP.md`
In [`ROADMAP.md#L41-L43`](file:///d:/PROJECTS/Amni/AmniShield-Android/ROADMAP.md#L41-L43):
- "Windows Elevated Background Service" belongs exclusively to `AmniShield-Windows`.
- "Opt-in DNS Sinkhole Engine" belongs exclusively to `AmniGuardFirewall`.
- "On-Device Mobile NPU Vision Blur Engine" belongs exclusively to `AmniBlur-Android` (`AmniHaze`).  
All three milestones MUST BE EXPUNGED from `AmniShield-Android/ROADMAP.md`.

#### B. Correcting Milestone Versioning
In [`ROADMAP.md#L39`](file:///d:/PROJECTS/Amni/AmniShield-Android/ROADMAP.md#L39):
Header `### Active / Upcoming Milestones (V1.1 - V2.0)` MUST be corrected to:
`### Active / Upcoming Milestones (V 0.3.2 - 0.3.5)` to align with the active semantic versioning baseline (`0.3.1 / versionCode 145`).

#### C. Pruning Plugin Clutter
The following ecosystem rules in `AGENTS.md` and `GEMINI.md` are completely irrelevant to `AmniShield-Android` and must not be consulted:
- ONNX Runtime direct NIO buffer handling (`AmniQuest`).
- LiteRT MediaProjection screen pixel inference (`AmniBlur`).
- Windows UAC elevation and hosts-file manipulation (`AmniShield-Windows`).
- WebExtensions MV3 and browser DOM scripts (`AmniShield-Extension`).

---

## 5. Comprehensive Audit Deliverable Matrix

| Audit Dimension | Prior Worker Claim | Codebase Ground Reality | Verdict & Impact |
| :--- | :--- | :--- | :--- |
| **UI Container** | Dual-host: `MainActivity` (4 tabs) + `FragmentActivity` (18 screens). | Verified: `MainActivity.kt` (2,022 lines) + `FragmentActivity.kt` (210 lines). | **[CONFIRMED]** Accurately mapped. |
| **Adaptive Scaffold** | `AmniShieldAdaptiveApp.kt` is disconnected. | Verified via git commits `7155646` -> `ed57548`. Disconnected to avoid FragmentManager crashes. | **[CONFIRMED]** Grounded with git commits. |
| **Local Persistence** | Zero Room, Zero DataStore. 100% plain `SharedPreferences`. | Verified: `SavedPreferencesLoader.kt` (2,015 lines) manages 12+ XML preference files. | **[CONFIRMED]** Accurately mapped. |
| **Interceptor Priority** | WebsiteBlocker (L350) runs before AppBlocker (L418). | Verified: `websiteBlockerDetector` at L350; `appBlocker` at L418. | **[CONFIRMED]** Documentation drift in `ARCHITECTURE.md`. |
| **50k+ Local Domain Engine** | Debunked as a myth; asset files have 52 & 40 domains. | Verified: `adult_domains.txt` (52 lines) & `haram_domains.txt` (41 lines) are dead and unreferenced. | **[CONFIRMED]** Myth debunked. |
| **Main Binder Deserialization** | Missed; noted only as reading boolean from SharedPreferences. | Found: `isFeatureCurrentlyActive` executes `Gson().fromJson` 2-3x on Main Binder Thread per event! | **[CORRECTED / CRITICAL RISK FOUND]** Major ANR hazard uncovered. |
| **`apply()` Semantics** | Claimed `setWebsiteBlockerEnabled` is a "synchronous disk write". | Incorrect: `apply()` commits in-memory synchronously and writes to disk asynchronously. | **[CORRECTED]** Corrected framework contract. |
| **Anti-Uninstall Method Name** | Hallucinated method name `showBlockingUI()`. | Incorrect: Actual method is `handleAntiUninstallAttempt()` ([`AntiUninstallDetector.kt#L409`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/security/AntiUninstallDetector.kt#L409)). | **[CORRECTED]** Grounded symbol name. |
| **Domain Fallback Logic** | Claimed service falls back to `loadBlockedWebsites()` if empty. | Incorrect: If rules are empty, it returns `emptySet()`. Fallback occurs only inside active rules with empty targets. | **[CORRECTED]** Logic corrected. |
| **Disclosure Layout** | Noted non-scrolling `LinearLayout` clipping in landscape/font scale. | Verified: Layout root has no `ScrollView`; buttons pushed off-screen in landscape. | **[CONFIRMED]** Critical compliance defect. |
| **Affirmative Consent** | Noted zero persistent record of consent on disk. | Verified: Neither `PermissionsScreen` nor `AccessibilityDisclosureDialog` persists consent. | **[CONFIRMED]** Compliance vulnerability. |
| **Rotation State Loss** | Noted `WarningActivity` countdown timer reset on rotation. | Found: `AmniSpaceActivity.kt#L406` also resets `isBreathingDone` via `remember` instead of `rememberSaveable`. | **[EXPANDED / CRITICAL DEFECT FOUND]** State loss in breathing screen. |
| **Foreign Milestones** | Recommended removing Blur and DNS Sinkhole. | Confirmed: User explicitly instructed removing them; they belong to companion apps. | **[CONFIRMED]** Must expunge from `ROADMAP.md`. |
| **Upcoming Versions** | Updated target version to `V 0.3.2 - 0.3.5`. | Verified: Matches user directive and active build version `0.3.1 / versionCode 145`. | **[CONFIRMED]** Roadmap header corrected. |
| **Zero Emoji Violations** | Found 4 unicode emojis in `ARCHITECTURE.md`. | Verified: Lines 9, 36, 51, and 69 contain unicode emojis in violation of ecosystem policy. | **[CONFIRMED]** Violation verified. |

---

## 6. Agent Rule Verdict (Alignment Mandates)

Before coding agents modify `AmniShield-Android`, the following invariants must be enforced:

1. **ROADMAP.md Cleansing**:
   - Expunge "Windows Elevated Background Service" (line 41).
   - Expunge "Opt-in DNS Sinkhole Engine" (line 42).
   - Expunge "On-Device Mobile NPU Vision Blur Engine" (line 43).
   - Update header line 39 to: `### Active / Upcoming Milestones (V 0.3.2 - 0.3.5)`.
2. **`.agents/AGENTS.md` Baseline Correction**:
   - Update version to `versionCode 145`, `versionName "0.3.1 (2026.10.05)"`.
   - Remove `Room DB` and `Preferences DataStore` from enabled skills. Declare `SavedPreferencesLoader` (`SharedPreferences`) as the active storage engine.
   - Note that `MainActivity.kt` and `FragmentActivity.kt` are the active UI hosts; `AmniShieldAdaptiveApp.kt` is disconnected.
3. **Accessibility Pipeline Performance Invariant**:
   - Strictly prohibit `Gson().fromJson` deserialization on the Main Binder Thread inside `onAccessibilityEvent`. Rule sets must be cached in memory and refreshed via broadcast events rather than re-parsed from JSON on every layout/scroll event.
   - Prohibit calling `service.rootInActiveWindow` from background coroutine dispatchers (`Dispatchers.Default`). Pass down the `rootNode` captured on the main thread instead.
4. **Disclosure Dialog Hardening**:
   - Wrap `dialog_accessibility_disclosure.xml` inside a `<ScrollView>` so landscape orientation and 200% font scaling do not clip action buttons.
   - Change button heights from `40dp` to `wrap_content` with vertical padding.
   - Persist affirmative consent (`savedPreferencesLoader.setAccessibilityDisclosureAccepted(true)`) upon clicking "Agree & Enable".
   - Set `.setCancelable(false)` on `AccessibilityDisclosureDialog`.
5. **State Restoration on Rotation**:
   - Replace `remember { mutableStateOf(false) }` in [`AmniSpaceActivity.kt#L406`](file:///d:/PROJECTS/Amni/AmniShield-Android/app/src/main/java/com/alhaq/amnishield/ui/activity/AmniSpaceActivity.kt#L406) with `rememberSaveable`.
   - Preserve elapsed timer state across configuration changes in `WarningActivity.kt` via `onSaveInstanceState`.
6. **Zero Emoji Purge**:
   - Remove the 4 unicode emojis from [`ARCHITECTURE.md`](file:///d:/PROJECTS/Amni/AmniShield-Android/ARCHITECTURE.md) (lines 9, 36, 51, 69) to maintain strict compliance with the ecosystem Zero Emoji Policy.
