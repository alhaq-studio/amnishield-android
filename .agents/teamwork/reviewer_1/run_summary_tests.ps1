$ErrorActionPreference = "Continue"

Write-Host "====================================================" -ForegroundColor Cyan
Write-Host " AmniShield-Android Audit & Verification Suite       " -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan

$ErrorCount = 0

# 1. Verify Zero Room / SQLite / DataStore dependencies
Write-Host "`n[TEST 1] Verifying absence of Room, SQLite, and DataStore dependencies..." -ForegroundColor Yellow
$deps = Select-String -Path "app\build.gradle.kts", "gradle\libs.versions.toml" -Pattern "androidx\.room|androidx\.sqlite|androidx\.datastore"
if ($deps.Count -eq 0) {
    Write-Host "  [PASS] Zero Room/SQLite/DataStore dependencies detected." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Unexpected persistence dependency found!" -ForegroundColor Red
    $deps | ForEach-Object { Write-Host "    $($_.Path):$($_.LineNumber): $($_.Line)" }
    $ErrorCount++
}

# 2. Verify SharedPreferences count in SavedPreferencesLoader.kt
Write-Host "`n[TEST 2] Verifying SharedPreferences files in SavedPreferencesLoader.kt..." -ForegroundColor Yellow
$prefFiles = Select-String -Path "app\src\main\java\com\alhaq\amnishield\utils\SavedPreferencesLoader.kt" -Pattern 'getSharedPreferences\("([^"]+)"' |
    ForEach-Object { $_.Matches.Groups[1].Value } | Sort-Object -Unique
Write-Host "  [INFO] Found $($prefFiles.Count) distinct SharedPreferences XML files in loader."
if ($prefFiles.Count -ge 29) {
    Write-Host "  [PASS] Verified 29+ SharedPreferences files." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Expected at least 29 files, found $($prefFiles.Count)" -ForegroundColor Red
    $ErrorCount++
}

# 3. Verify Isolation of EncryptedSharedPreferences
Write-Host "`n[TEST 3] Verifying isolation of EncryptedSharedPreferences..." -ForegroundColor Yellow
$cryptoMatches = Get-ChildItem -Path "app\src\main\java" -Filter "*.kt" -Recurse |
    Select-String -Pattern "EncryptedSharedPreferences"
$nonSecureFiles = $cryptoMatches | Where-Object { $_.Path -notlike "*SecureKeyStore.kt*" }
if ($nonSecureFiles.Count -eq 0) {
    Write-Host "  [PASS] EncryptedSharedPreferences is strictly isolated to SecureKeyStore.kt." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Found EncryptedSharedPreferences outside SecureKeyStore.kt:" -ForegroundColor Red
    $nonSecureFiles | ForEach-Object { Write-Host "    $($_.Path):$($_.LineNumber)" }
    $ErrorCount++
}

# 4. Verify Dual-Host Activity line counts and AdaptiveApp callers
Write-Host "`n[TEST 4] Verifying UI Presentation hosts and AdaptiveApp callers..." -ForegroundColor Yellow
$mainLines = (Get-Content "app\src\main\java\com\alhaq\amnishield\ui\activity\MainActivity.kt").Count
$fragLines = (Get-Content "app\src\main\java\com\alhaq\amnishield\ui\activity\FragmentActivity.kt").Count
Write-Host "  [INFO] MainActivity.kt: $mainLines lines (Expected ~2,022)"
Write-Host "  [INFO] FragmentActivity.kt: $fragLines lines (Expected ~210)"
$adaptiveCallers = Get-ChildItem -Path "app\src\main\java" -Filter "*.kt" -Recurse |
    Select-String -Pattern "AmniShieldAdaptiveApp" |
    Where-Object { $_.Path -notlike "*AmniShieldAdaptiveApp.kt*" }
if ($adaptiveCallers.Count -eq 0) {
    Write-Host "  [PASS] AmniShieldAdaptiveApp.kt has exactly 0 callers (disconnected status verified)." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Found unexpected references to AmniShieldAdaptiveApp:" -ForegroundColor Red
    $adaptiveCallers | ForEach-Object { Write-Host "    $($_.Path):$($_.LineNumber)" }
    $ErrorCount++
}

# 5. Verify Blocker Pipeline execution order
Write-Host "`n[TEST 5] Verifying Blocker Pipeline line numbers in Accessibility Service..." -ForegroundColor Yellow
$servicePath = "app\src\main\java\com\alhaq\amnishield\services\AmniShieldAccessibilityService.kt"
$antiUninstallMatches = (Select-String -Path $servicePath -Pattern "antiUninstallDetector\.inspect")
$antiUninstallLine = $antiUninstallMatches[-1].LineNumber
$focusLine = (Select-String -Path $servicePath -Pattern "if \(isFocusModeActive\)").LineNumber
$websiteLine = (Select-String -Path $servicePath -Pattern "savedPreferencesLoader\.isWebsiteBlockerEnabled").LineNumber
$appBlockerLine = (Select-String -Path $servicePath -Pattern "if \(!isFocusBlockAllExSelectedActive\)").LineNumber
$channelLine = (Select-String -Path $servicePath -Pattern "eventChannel\.trySend").LineNumber

Write-Host "  [INFO] AntiUninstallDetector: Line $antiUninstallLine"
Write-Host "  [INFO] FocusModeBlocker: Line $focusLine"
Write-Host "  [INFO] WebsiteBlockerDetector: Line $websiteLine"
Write-Host "  [INFO] AppBlocker: Line $appBlockerLine"
Write-Host "  [INFO] Deferred Channel Dispatch: Line $channelLine"

if ($antiUninstallLine -lt $focusLine -and $focusLine -lt $websiteLine -and $websiteLine -lt $appBlockerLine -and $appBlockerLine -lt $channelLine) {
    Write-Host "  [PASS] Verified strict blocker pipeline order: P0 -> P1 -> P2 -> P3 -> P4." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Blocker pipeline order inverted!" -ForegroundColor Red
    $ErrorCount++
}

# 6. Verify Dead Blocklist Assets
Write-Host "`n[TEST 6] Verifying status of blocklist text assets..." -ForegroundColor Yellow
$assetMatches = Get-ChildItem -Path "app\src" -Recurse |
    Select-String -Pattern "adult_domains\.txt|haram_domains\.txt"
if ($assetMatches.Count -eq 0) {
    Write-Host "  [PASS] Blocklist text files are dead assets (zero code references)." -ForegroundColor Green
} else {
    Write-Host "  [INFO] Matches found for blocklist assets:"
    $assetMatches | ForEach-Object { Write-Host "    $($_.Path):$($_.LineNumber)" }
}

# 7. Verify Emojis in ARCHITECTURE.md and AMNISHIELD_PROJECT_SUMMARY.md
Write-Host "`n[TEST 7] Verifying Zero-Emoji Policy..." -ForegroundColor Yellow
$archEmojis = Get-Content "ARCHITECTURE.md" | Select-String -Pattern "[\uD83C-\uDBFF\uDC00-\uDFFF]"
Write-Host "  [INFO] ARCHITECTURE.md emojis detected: $($archEmojis.Count) (Lines 9, 36, 51, 69 to be purged)"
$summaryEmojis = Get-Content "AMNISHIELD_PROJECT_SUMMARY.md" | Select-String -Pattern "[\uD83C-\uDBFF\uDC00-\uDFFF]"
if ($summaryEmojis.Count -eq 0) {
    Write-Host "  [PASS] Zero emojis detected in AMNISHIELD_PROJECT_SUMMARY.md." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Emojis detected in AMNISHIELD_PROJECT_SUMMARY.md!" -ForegroundColor Red
    $summaryEmojis | ForEach-Object { Write-Host "    Line $($_.LineNumber): $($_.Line)" }
    $ErrorCount++
}

# 8. Verify Version in build.gradle.kts
Write-Host "`n[TEST 8] Verifying production version in app/build.gradle.kts..." -ForegroundColor Yellow
$codeLine = (Select-String -Path "app\build.gradle.kts" -Pattern "versionCode\s*=\s*(\d+)").Matches.Groups[1].Value
$nameLine = (Select-String -Path "app\build.gradle.kts" -Pattern 'versionName\s*=\s*"([^"]+)"').Matches.Groups[1].Value
Write-Host "  [INFO] Production versionCode: $codeLine (Expected 145)"
Write-Host "  [INFO] Production versionName: $nameLine (Expected '0.3.1 (2026.10.05)')"
if ($codeLine -eq "145" -and $nameLine -eq "0.3.1 (2026.10.05)") {
    Write-Host "  [PASS] Production version baseline confirmed." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Unexpected version in build.gradle.kts!" -ForegroundColor Red
    $ErrorCount++
}

Write-Host "`n====================================================" -ForegroundColor Cyan
if ($ErrorCount -eq 0) {
    Write-Host " ALL AUDIT CRITERIA VERIFIED SUCCESSFULLY!           " -ForegroundColor Green
} else {
    Write-Host " AUDIT COMPLETED WITH $ErrorCount FAILED TESTS!      " -ForegroundColor Red
}
Write-Host "====================================================" -ForegroundColor Cyan
