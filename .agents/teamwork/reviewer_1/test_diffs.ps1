$ErrorActionPreference = "Continue"

$summary = Get-Content -Raw "AMNISHIELD_PROJECT_SUMMARY.md"

function Test-Diff($name, $pattern) {
    Write-Host "`nTesting diff for $name..."
    $match = [regex]::Match($summary, $pattern)
    if ($match.Success) {
        $diffPath = ".agents/teamwork/reviewer_1/temp_$name.diff"
        [IO.File]::WriteAllText($diffPath, $match.Groups[1].Value.Trim() + "`n", [Text.Encoding]::UTF8)
        git apply --check --verbose $diffPath
    } else {
        Write-Host "Failed to find diff for $name" -ForegroundColor Red
    }
}

Test-Diff "ARCHITECTURE" '(?s)```diff\r?\n(diff --git a/ARCHITECTURE\.md.*?)\r?\n```'
Test-Diff "README" '(?s)```diff\r?\n(diff --git a/README\.md.*?)\r?\n```'
Test-Diff "ROADMAP" '(?s)```diff\r?\n(diff --git a/ROADMAP\.md.*?)\r?\n```'
Test-Diff "AGENTS" '(?s)```diff\r?\n(diff --git a/.\.agents/AGENTS\.md.*?)\r?\n```'
