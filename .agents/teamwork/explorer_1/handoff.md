# Requirement R1 Ground Truth Investigation & Handoff Report: Persistence Reality & De-Hallucination

**Author**: Explorer 1 (`teamwork_preview_explorer`)  
**Target Milestone**: Requirement R1: Persistence Reality & De-Hallucination  
**Working Directory**: `d:\PROJECTS\Amni\AmniShield-Android\.agents\teamwork\explorer_1`  
**Parent / Recipient**: orchestrator_1 (`f5746a57-2be7-432d-842f-c119d6892220`)  
**Date**: 2026-10-20  

---

## 1. Observation

### 1.1 Gradle Dependencies Audit (Root, App, Version Catalog)
Direct inspection of dependency definitions was conducted across all Gradle configuration files:

1. **`build.gradle.kts` (Root, lines 1-7)**:
   ```kotlin
   // Top-level build file where you can add configuration options common to all sub-projects/modules.
   plugins {
       alias(libs.plugins.android.application) apply false
       alias(libs.plugins.kotlin.android) apply false
       alias(libs.plugins.google.gms.google.services) apply false
       alias(libs.plugins.kotlin.compose) apply false
   }
   ```
   *Finding*: Root build file contains only base Android, Kotlin, Google Services, and Compose plugins.

2. **`gradle/libs.versions.toml` (lines 1-71)**:
   *Finding*: A comprehensive scan of `[versions]` and `[libraries]` reveals:
   - `androidx.room` is completely absent.
   - `androidx.sqlite` is completely absent.
   - `androidx.datastore` is completely absent.
   - The only persistence / serialization library listed is Gson:
     ```toml
     4: gson = "2.14.0"
     31: gson = { module = "com.google.code.gson:gson", version.ref = "gson" }
     ```

3. **`app/build.gradle.kts` (lines 145-195)**:
   ```kotlin
   dependencies {
       implementation(libs.androidx.core.ktx)
       implementation(libs.androidx.appcompat)
       implementation(libs.material)
       implementation(libs.androidx.activity)
       implementation(libs.androidx.constraintlayout)
       implementation(libs.androidx.fragment.ktx)
       implementation(libs.androidx.documentfile)
       implementation(libs.androidx.work.runtime.ktx)
      
       implementation(libs.gson)
       implementation("com.squareup.okhttp3:okhttp:4.12.0")
       implementation("androidx.security:security-crypto:1.1.0-alpha06")
       add("playstoreImplementation", "com.google.firebase:firebase-messaging-ktx:24.1.0")

       testImplementation(libs.junit)
       androidTestImplementation(libs.androidx.junit)
       androidTestImplementation(libs.androidx.espresso.core)

       implementation(libs.mpandroidchart)
       implementation(libs.timerangepicker)
       implementation("com.journeyapps:zxing-android-embedded:4.3.0")
       implementation("com.google.zxing:core:3.5.3")
       
       add("playstoreImplementation", libs.play.services.auth)
       add("playstoreImplementation", libs.billing.ktx)
       add("universalImplementation", libs.play.services.auth)
       add("universalImplementation", libs.billing.ktx)

       // Jetpack Compose Dependencies
       implementation(platform(libs.androidx.compose.bom))
       ...
   }
   ```
   *Finding*: Neither Room DB, nor SQLite, nor Preferences DataStore is declared or imported. The only security/storage dependency is `androidx.security:security-crypto:1.1.0-alpha06`.

### 1.2 Unused Stub Classes in `com.alhaq.amnishield.data.db`
The repository contains a package `com.alhaq.amnishield.data.db` with three files:
1. `AppBlockRule.kt` (lines 27-45):
   ```kotlin
   data class AppBlockRule(
       override val id: String,
       val title: String,
       val packageName: String,
       val type: String = "BLOCK",
       val recurrence: String = "ALWAYS",
       val startMinute: Int = 0,
       val endMinute: Int = 0,
       val selectedDays: String = "",
       val durationHours: Int = 0,
       val activeUntilMillis: Long = 0L,
       val createdAt: Long = System.currentTimeMillis(),
       val groupId: String? = null,
       val groupTitle: String? = null,
       val isEnabled: Boolean = true,
       override val authType: AuthType = AuthType.NONE,
       override val rulePasswordHash: String? = null,
       override val rulePasswordSalt: String? = null
   ) : BaseRule
   ```
2. `ScreenTimeLimitRule.kt` (lines 21-33): Plain data class with no annotations.
3. `AmniShieldDatabaseMigrations.kt` (lines 7-22):
   ```kotlin
   object AmniShieldDatabaseMigrations {
       const val CURRENT_VERSION = 2
       val MIGRATION_1_2_STATEMENTS = listOf(
           "ALTER TABLE app_block_rules ADD COLUMN authType TEXT NOT NULL DEFAULT 'NONE'",
           ...
       )
   }
   ```
*Finding*: A project-wide grep search confirms that `AppBlockRule`, `ScreenTimeLimitRule`, and `AmniShieldDatabaseMigrations` have **zero usages** across the entire codebase. None of these classes possess `@Entity`, `@Dao`, or `@Database` annotations. They represent an abandoned or aspirational SQLite schema that is completely disconnected from runtime operations.

### 1.3 `SavedPreferencesLoader.kt` Persistence Implementation
`app/src/main/java/com/alhaq/amnishield/utils/SavedPreferencesLoader.kt` (2,015 lines) is the central storage and state management engine of AmniShield-Android. It is referenced 459 times across the codebase.

1. **Enumeration of SharedPreferences XML Files**:
   Analysis of `SavedPreferencesLoader.kt` confirms it directly opens and manipulates **29 distinct SharedPreferences files**:
   - `amnispace_prefs`
   - `anti_uninstall`
   - `app_blocker`
   - `app_launch_tracking`
   - `app_preferences`
   - `app_usage_tracker`
   - `AppPreferences` (Note: separate casing from `app_preferences`)
   - `auto_focus_hours`
   - `cheat_hours`
   - `compassionate_access`
   - `config_tracker`
   - `device_identity`
   - `feature_toggles`
   - `focus_mode`
   - `founder_pass_state`
   - `grayscale`
   - `home_dashboard`
   - `keyword_blocker_configs`
   - `Keyword_blocker_ignored_apps` (Note: PascalCase prefix)
   - `keyword_blocker_packs`
   - `overlay_apps`
   - `premium_state`
   - `reel_blocker`
   - `schedules_migration`
   - `social_media_blocker`
   - `view_blocker`
   - `warning_data`
   - `website_blocker`
   - `website_usage_tracker`

   Across the wider app, an additional 8 SharedPreferences files are used (`achievement_notifications`, `amnishield_sync_secrets_fallback`, `config_reels`, `error_reporting`, `permission_guide_prefs`, `reminder_settings`, `shortcuts`, `theme_prefs`), bringing the total to 37 XML preference files.

2. **Persistence Mechanism**:
   - Primitives (flags, timestamps, counters, sets) are stored via standard `SharedPreferences.Editor` methods (`putBoolean`, `putStringSet`, `putInt`, `putLong`) using either `.apply()` (asynchronous background flush) or `.commit()` (synchronous disk write).
   - Structured and composite models are persisted as JSON strings using `Gson().toJson(...)` and restored via `Gson().fromJson(json, type)`. There are **33 explicit `Gson()` instantiations** within `SavedPreferencesLoader.kt`.

3. **Key Models and Active Rule Storage**:
   - Active schedule rules are modeled by `AppBlockScheduleRule` (located in `app/src/main/java/com/alhaq/amnishield/data/blockers/UniversalScheduleRule.kt`, lines 14-60), NOT `AppBlockRule.kt`.
   - `AppBlockScheduleRule` is stored in the `"app_blocker"` SharedPreferences file under key `"schedule_rules"`:
     ```kotlin
     // SavedPreferencesLoader.kt:740-772
     fun loadAppBlockerScheduleRules(): MutableList<AppBlockScheduleRule> {
         val sharedPreferences = context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
         val json = sharedPreferences.getString("schedule_rules", null)
         if (json.isNullOrEmpty()) return mutableListOf()

         val type = object : TypeToken<MutableList<AppBlockScheduleRule>>() {}.type
         return runCatching {
             val rawList = Gson().fromJson<MutableList<AppBlockScheduleRule>>(json, type) ?: mutableListOf()
             val legacyDummyIds = setOf(...)
             rawList.map { it.sanitize() }
                 .filterNot { ... }
                 .toMutableList()
         }.getOrElse {
             Log.e("SavedPreferencesLoader", "Failed to load app blocker schedule rules", it)
             mutableListOf()
         }
     }

     fun saveAppBlockerScheduleRules(rules: MutableList<AppBlockScheduleRule>) {
         val sharedPreferences = context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
         val json = Gson().toJson(rules)
         sharedPreferences.edit().putString("schedule_rules", json).apply()
     }
     ```
   - Launch limit rules are modeled by `AppLaunchLimitRule` (lines 1400-1415), persisted as JSON under `"launch_limit_rules"` in `"app_launch_tracking"`.
   - Cooldown snapshots (`Map<String, Long>`) are serialized to JSON in `"app_blocker"`, `"view_blocker"`, and `"reel_blocker"`.

### 1.4 `SecureKeyStore.kt` & EncryptedSharedPreferences Isolation
Inspection of `app/src/main/java/com/alhaq/amnishield/data/sync/SecureKeyStore.kt` (lines 1-93):
1. **MasterKey and Encryption Scheme**:
   ```kotlin
   val masterKey = MasterKey.Builder(context)
       .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
       .build()
   EncryptedSharedPreferences.create(
       context,
       "amnishield_sync_secrets",
       masterKey,
       EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
       EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
   )
   ```
2. **Self-Healing Fallback**:
   If Keystore or preference creation fails (lines 23-45), it catches `Throwable`, attempts to delete `shared_prefs/amnishield_sync_secrets.xml`, and retries. If the retry fails, it falls back to standard unencrypted SharedPreferences:
   ```kotlin
   context.getSharedPreferences("amnishield_sync_secrets_fallback", Context.MODE_PRIVATE)
   ```
3. **Stored Fields**:
   The store strictly holds:
   - `dek` (Data Encryption Key base64 string)
   - `access_token` (Supabase JWT access token)
   - `refresh_token` (Supabase JWT refresh token)
   - `cursor` (Sync pagination timestamp)
   - `fcm_token` (Firebase Cloud Messaging registration token)
   - `device_id` (Random UUID generated on initial access)
   - `device_name`
   - `sync_usage_stats` / `sync_reducer_configs`
   - `usage_device_ids`
4. **Codebase-Wide Isolation Verification**:
   A repository-wide search for `EncryptedSharedPreferences` confirms that it appears **exclusively in `SecureKeyStore.kt`** (lines 4, 16, 20, 21, 24, 34, 38, 39, 42). No blocking rules, cooldown data, or user preferences ever touch `EncryptedSharedPreferences`.

### 1.5 Main Binder Thread Deserialization Bottleneck in `AmniShieldAccessibilityService.kt`
Inspection of `app/src/main/java/com/alhaq/amnishield/services/AmniShieldAccessibilityService.kt` reveals a severe synchronous deserialization bottleneck:

1. **Accessibility Event Hot Path**:
   `onAccessibilityEvent(event: AccessibilityEvent?)` (lines 240-565) is executed synchronously on Android's Main UI / Binder thread whenever a window state change, scroll, or view selection occurs.

2. **Synchronous Deserialization Multipliers**:
   During event processing, the service evaluates active rules:
   - **Line 290**: `getActiveFocusModeRule()` calls `savedPreferencesLoader.loadAppBlockerScheduleRules()`.
   - **Line 291**: `isFeatureCurrentlyActive("FOCUS_MODE")` calls `savedPreferencesLoader.loadAppBlockerScheduleRules()`.
   - **Line 350**: `isFeatureCurrentlyActive("website_blocker")` calls `savedPreferencesLoader.loadAppBlockerScheduleRules()`.
   - **Line 351**: `getActiveBlockedWebsites()` calls `savedPreferencesLoader.loadAppBlockerScheduleRules()`.
   - **Lines 812, 855**: Helper methods `getActiveBlockedKeywords()` and `getActiveFocusModeRule()` call `savedPreferencesLoader.loadAppBlockerScheduleRules()`.

3. **Bottleneck Mechanism**:
   Each invocation of `savedPreferencesLoader.loadAppBlockerScheduleRules()` executes:
   - Synchronous disk I/O via `sharedPreferences.getString("schedule_rules", null)`.
   - Dynamic reflection allocation: `object : TypeToken<MutableList<AppBlockScheduleRule>>() {}.type`.
   - Reflection-based JSON deserialization: `Gson().fromJson(...)`.
   - Defensive sanitation loop: `rawList.map { it.sanitize() }`, performing reflection checks, null checks, and type coercion.
   - List filtering and collection cloning: `.filterNot { ... }.toMutableList()`.

   Because there is **zero in-memory caching** of schedule rules, a single window transition can trigger 4 or more redundant reflection-based Gson deserializations back-to-back on the Main Binder Thread.

### 1.6 Documentation Discrepancies and Hallucinations
1. **`ARCHITECTURE.md` (Line 5)**:
   - *Current Claim*:
     `"It operates via a dedicated Android Accessibility Service pipeline, local SQLite/Room data caching, encrypted SharedPreferences, and on-device diagnostic logging."`
   - *Reality*: Zero SQLite and zero Room exist. All rule and state persistence uses plain SharedPreferences XML files via `SavedPreferencesLoader.kt`. `EncryptedSharedPreferences` is strictly isolated to Supabase cloud sync tokens in `SecureKeyStore.kt`.
2. **`ARCHITECTURE.md` Emojis**:
   - Lines 9, 36, 51, and 69 contain unicode emojis (`🏗️`, `🛡️`, `📝`, `🔒`), directly violating the universal Zero-Emoji Policy.
3. **`README.md`**:
   - Lines 186-195 cite `ARCHITECTURE.md` as the definitive architecture guide without presenting an accurate storage model summary.
4. **`.agents/AGENTS.md` and Ecosystem Instructions**:
   - Outdated metadata claims `"Room DB, Preferences DataStore"`, leading external and incoming agents to hallucinate non-existent DAOs.

---

## 2. Logic Chain

1. **Step 1 (Dependencies determine compilation reality)**:
   - *Observation*: `build.gradle.kts`, `app/build.gradle.kts`, and `gradle/libs.versions.toml` contain zero references to `androidx.room`, `androidx.sqlite`, or `androidx.datastore`.
   - *Deduction*: Room DB and DataStore classes cannot be imported or compiled in this repository. Any claim that the app currently caches data in Room or DataStore is a pure documentation hallucination.

2. **Step 2 (Dead code vs active data models)**:
   - *Observation*: Classes in `com.alhaq.amnishield.data.db` (`AppBlockRule`, `ScreenTimeLimitRule`, `AmniShieldDatabaseMigrations`) are completely unreferenced across the codebase and lack database annotations. In contrast, `UniversalScheduleRule.kt` (`AppBlockScheduleRule`) and `AppLaunchLimitRule.kt` are actively referenced by 459 callers.
   - *Deduction*: The files in `data/db` are orphaned remnants of an abandoned or planned migration. Runtime persistence does not touch `data/db`.

3. **Step 3 (Centrality of `SavedPreferencesLoader.kt`)**:
   - *Observation*: `SavedPreferencesLoader.kt` interacts with 29 named SharedPreferences XML files and contains 33 `Gson()` instantiations.
   - *Deduction*: Local persistence operates 100% via Android `SharedPreferences` storing raw primitives or serialized JSON strings.

4. **Step 4 (Scope of `EncryptedSharedPreferences`)**:
   - *Observation*: `EncryptedSharedPreferences` is imported and invoked strictly within `SecureKeyStore.kt` for `amnishield_sync_secrets` (managing tokens, DEK, and sync metadata).
   - *Deduction*: Encryption is not applied to general user rules or settings. SharedPreferences persistence for blocking rules is plain XML.

5. **Step 5 (Binder Thread Performance Degradation)**:
   - *Observation*: `AmniShieldAccessibilityService.onAccessibilityEvent` executes on the Main Binder Thread and triggers up to 4+ calls to `loadAppBlockerScheduleRules()`, each instantiating `Gson()` and deserializing JSON without caching.
   - *Deduction*: This constitutes a demonstrable performance hazard. Eliminating in-flight Gson reflection via in-memory rule caching (Milestone 0.3.2) is a critical technical requirement.

6. **Step 6 (Documentation Remediation Strategy)**:
   - *Observation*: `ARCHITECTURE.md:5` explicitly asserts "local SQLite/Room data caching" and "encrypted SharedPreferences".
   - *Deduction*: `ARCHITECTURE.md` must be updated to replace the Room/SQLite claim with the verified reality of `SavedPreferencesLoader.kt` (29+ XML files, Gson serialization) and clarify that `EncryptedSharedPreferences` is restricted to Supabase authentication tokens. Emojis must be expunged.

---

## 3. Caveats

1. **Asynchronous vs Synchronous Writes**: While `saveAppBlockerCooldownData` uses `.apply()` (asynchronous), other updates in `SavedPreferencesLoader.kt` may execute synchronous operations. Cooldown writes are decoupled from the disk write lock, but read operations on the accessibility path remain synchronous.
2. **EncryptedSharedPreferences Fallback**: On devices with malfunctioning hardware Keystore modules, `SecureKeyStore.kt` falls back to standard `SharedPreferences` (`amnishield_sync_secrets_fallback`). Therefore, even encryption of sync secrets is best-effort with a plain XML fallback.
3. **DataStore Single Comment**: In `app/src/main/java/com/alhaq/amnishield/blockers/BaseBlocker.kt:47`, a code comment reads: `"Restores saved cooldown entries from SharedPreferences/DataStore."` This is a developer comment drift and does not represent an imported DataStore library.
4. **Scope Constraint**: As an explorer subagent under read-only boundaries, no Kotlin source code or build configuration files were modified.

---

## 4. Conclusion

1. **Persistence Ground Truth**:
   - Room DB: **0% present** (Zero dependencies, zero database classes, zero DAOs).
   - SQLite: **0% present** (Zero SQLiteOpenHelper, dead unreferenced migration script).
   - Preferences DataStore: **0% present** (Zero dependencies, zero instances).
   - Active Persistence Engine: **100% SharedPreferences** via `SavedPreferencesLoader.kt` across 29+ XML files, storing complex entities as Gson-serialized JSON strings.
   - Encryption Scope: **Isolated to Supabase Sync Secrets** via `SecureKeyStore.kt` (`EncryptedSharedPreferences`), with an automatic plain fallback upon hardware Keystore failure.

2. **Main Binder Thread Hot-Path Hazard**:
   - `onAccessibilityEvent` synchronously re-parses the `"schedule_rules"` JSON via `Gson().fromJson()` up to 4+ times per accessibility event, creating an urgent need for Milestone 0.3.2 in-memory caching.

3. **Required Documentation Rectifications**:
   - **`ARCHITECTURE.md` Line 5**:
     - *Replace*: `"It operates via a dedicated Android Accessibility Service pipeline, local SQLite/Room data caching, encrypted SharedPreferences, and on-device diagnostic logging."`
     - *With*: `"It operates via a dedicated Android Accessibility Service pipeline, local SharedPreferences persistence (29+ specialized XML files with Gson JSON serialization via SavedPreferencesLoader), hardware-backed EncryptedSharedPreferences for Supabase authentication tokens (SecureKeyStore), and on-device diagnostic logging."`
   - **`ARCHITECTURE.md` Emojis**:
     - Strip unicode emojis on lines 9, 36, 51, and 69.
   - **`.agents/AGENTS.md`**:
     - Remove references to `Room DB` and `Preferences DataStore` to prevent agent hallucination loops.

---

## 5. Verification Method

To independently verify the observations and conclusions in this report, execute the following commands in powershell from the project root:

1. **Verify Complete Absence of Room, SQLite, and DataStore Dependencies**:
   ```powershell
   # Search version catalog
   Select-String -Path "gradle\libs.versions.toml" -Pattern "room|sqlite|datastore"
   # Search app dependencies
   Select-String -Path "app\build.gradle.kts" -Pattern "room|sqlite|datastore"
   ```
   *Expected Result*: Zero matching lines.

2. **Verify Orphaned Status of `com.alhaq.amnishield.data.db`**:
   ```powershell
   Get-ChildItem -Path "app\src\main\java" -Recurse -Filter "*.kt" | Select-String -Pattern "AmniShieldDatabaseMigrations|AppBlockRule|ScreenTimeLimitRule"
   ```
   *Expected Result*: Matches only inside their respective declarations within `app/src/main/java/com/alhaq/amnishield/data/db/`. Zero external callers.

3. **Verify Enumeration of 29+ SharedPreferences XML Files**:
   ```powershell
   Select-String -Path "app\src\main\java\com\alhaq\amnishield\utils\SavedPreferencesLoader.kt" -Pattern 'getSharedPreferences\("([^"]+)"' | ForEach-Object { $_.Matches.Groups[1].Value } | Sort-Object -Unique
   ```
   *Expected Result*: 29 unique XML preference file names output.

4. **Verify Isolation of `EncryptedSharedPreferences`**:
   ```powershell
   Get-ChildItem -Path "app\src\main\java" -Recurse -Filter "*.kt" | Select-String -Pattern "EncryptedSharedPreferences"
   ```
   *Expected Result*: All matching lines reside exclusively within `SecureKeyStore.kt`.

5. **Verify Binder Thread Hot Path In-Flight Deserialization**:
   ```powershell
   Select-String -Path "app\src\main\java\com\alhaq\amnishield\services\AmniShieldAccessibilityService.kt" -Pattern "loadAppBlockerScheduleRules"
   ```
   *Expected Result*: Matches on lines 572, 698, 769, 812, 855.

6. **Run Project Unit Tests**:
   ```powershell
   .\gradlew testFdroidDebugUnitTest
   ```
   *Expected Result*: `BUILD SUCCESSFUL` with all legacy rule deserialization and blocker tests passing.
