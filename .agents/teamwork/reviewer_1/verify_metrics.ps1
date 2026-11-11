$ErrorActionPreference = "Continue"

Write-Host "=== VERIFYING PERSISTENCE METRICS ==="
$splPath = "app/src/main/java/com/alhaq/amnishield/utils/SavedPreferencesLoader.kt"
$lines = (Get-Content $splPath).Count
Write-Host "SavedPreferencesLoader.kt line count: $lines"

$rawMatches = Select-String -Path $splPath -Pattern 'getSharedPreferences\("([^"]+)"'
$prefFiles = $rawMatches | ForEach-Object { $_.Matches.Groups[1].Value } | Sort-Object -Unique
Write-Host "Unique SharedPreferences files ($($prefFiles.Count)):"
$prefFiles | ForEach-Object { Write-Host "  - $_" }

$gsonCount = (Select-String -Path $splPath -Pattern 'Gson\(\)').Count
Write-Host "Gson() calls in SavedPreferencesLoader.kt: $gsonCount"

$splUsages = (Get-ChildItem -Path "app/src/main/java" -Recurse -Filter "*.kt" | 
    Select-String -Pattern "SavedPreferencesLoader" | 
    Where-Object { $_.Path -notlike "*SavedPreferencesLoader.kt*" }).Count
Write-Host "SavedPreferencesLoader references across codebase: $splUsages"

Write-Host "`n=== VERIFYING ENCRYPTEDSHAREDPREFERENCES ISOLATION ==="
$encMatches = Get-ChildItem -Path "app/src/main/java" -Recurse -Filter "*.kt" |
    Select-String -Pattern "EncryptedSharedPreferences"
Write-Host "EncryptedSharedPreferences occurrences:"
$encMatches | ForEach-Object { Write-Host "  $($_.Path):$($_.LineNumber)" }

Write-Host "`n=== VERIFYING DUAL-HOST ACTIVITIES ==="
$mainPath = "app/src/main/java/com/alhaq/amnishield/ui/activity/MainActivity.kt"
$fragPath = "app/src/main/java/com/alhaq/amnishield/ui/activity/FragmentActivity.kt"
$adaptivePath = "app/src/main/java/com/alhaq/amnishield/ui/AmniShieldAdaptiveApp.kt"

Write-Host "MainActivity.kt line count: $((Get-Content $mainPath).Count)"
Write-Host "FragmentActivity.kt line count: $((Get-Content $fragPath).Count)"
Write-Host "AmniShieldAdaptiveApp.kt line count: $((Get-Content $adaptivePath).Count)"

$adaptiveCallers = Get-ChildItem -Path "app/src/main/java" -Recurse -Filter "*.kt" |
    Select-String -Pattern "AmniShieldAdaptiveApp" |
    Where-Object { $_.Path -notlike "*AmniShieldAdaptiveApp.kt*" }
Write-Host "AmniShieldAdaptiveApp callers count: $($adaptiveCallers.Count)"

Write-Host "`n=== VERIFYING BLOCKER PIPELINE IN AMNISHIELDACCESSIBILITYSERVICE ==="
$svcPath = "app/src/main/java/com/alhaq/amnishield/services/AmniShieldAccessibilityService.kt"
Write-Host "AmniShieldAccessibilityService line count: $((Get-Content $svcPath).Count)"

$p0 = (Select-String -Path $svcPath -Pattern "antiUninstallDetector\.inspect").LineNumber
$p1 = (Select-String -Path $svcPath -Pattern "if \(isFocusModeActive\)").LineNumber
$p2 = (Select-String -Path $svcPath -Pattern "savedPreferencesLoader\.isWebsiteBlockerEnabled").LineNumber
$p3 = (Select-String -Path $svcPath -Pattern "if \(!isFocusBlockAllExSelectedActive\)").LineNumber
$p4 = (Select-String -Path $svcPath -Pattern "eventChannel\.trySend").LineNumber

Write-Host "P0 AntiUninstallDetector line: $p0"
Write-Host "P1 FocusModeBlocker line: $p1"
Write-Host "P2 WebsiteBlockerDetector line: $p2"
Write-Host "P3 AppBlocker line: $p3"
Write-Host "P4 Deferred Channel line: $p4"

Write-Host "`n=== VERIFYING VERSION IN BUILD.GRADLE.KTS ==="
$vcode = (Select-String -Path "app/build.gradle.kts" -Pattern 'versionCode\s*=\s*(\d+)').Matches.Groups[1].Value
$vname = (Select-String -Path "app/build.gradle.kts" -Pattern 'versionName\s*=\s*"([^"]+)"').Matches.Groups[1].Value
Write-Host "versionCode: $vcode"
Write-Host "versionName: $vname"
