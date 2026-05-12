# Code-Sync Verification Script
# Prüft ob Java-Sync und App synchron sind (HBCI-Version, Job-Liste)

$ErrorActionPreference = "Stop"

Write-Host "========================================================================" -ForegroundColor Cyan
Write-Host "  Code-Sync Verification: Java-Sync vs. App" -ForegroundColor Cyan
Write-Host "========================================================================" -ForegroundColor Cyan
Write-Host ""

$allChecksPass = $true

# ============================================================================
# Check 1: HBCI Version Strategy
# ============================================================================

Write-Host "[1/3] Checking HBCI Version Strategy..." -ForegroundColor Yellow

$appCode = Get-Content "$PSScriptRoot\..\app\src\main\java\de\mybudgets\app\data\banking\FintsService.kt" -Raw

if ($appCode -match 'HBCIHandler\("220".*passport\)') {
    Write-Host "  ✅ App tries HBCI 2.2 (""220"") first" -ForegroundColor Green
} else {
    Write-Host "  ❌ App does NOT try HBCI 2.2 first!" -ForegroundColor Red
    Write-Host "     Expected: HBCIHandler(""220"", passport)" -ForegroundColor Red
    $allChecksPass = $false
}

if ($appCode -match 'HBCIHandler\("300".*passport\)') {
    Write-Host "  ✅ App has fallback to FinTS 3.0 (""300"")" -ForegroundColor Green
} else {
    Write-Host "  ❌ App missing fallback to FinTS 3.0!" -ForegroundColor Red
    $allChecksPass = $false
}

# ============================================================================
# Check 2: Job List (MT940 without CAMT)
# ============================================================================

Write-Host ""
Write-Host "[2/3] Checking Job List..." -ForegroundColor Yellow

# Check that CAMT is commented out
# Split by lines and check each line individually
$appLines = Get-Content "$PSScriptRoot\..\app\src\main\java\de\mybudgets\app\data\banking\FintsService.kt"
$camtActiveLines = $appLines | Where-Object { 
    $_ -match 'JobAttempt\("KUmsAllCamt"' -and $_ -notmatch '^\s*//' 
}

if ($camtActiveLines.Count -gt 0) {
    Write-Host "  ❌ CAMT (KUmsAllCamt) is still ACTIVE in job list!" -ForegroundColor Red
    Write-Host "     Found in lines:" -ForegroundColor Red
    $camtActiveLines | ForEach-Object { Write-Host "       $_" -ForegroundColor Red }
    $allChecksPass = $false
} else {
    Write-Host "  ✅ CAMT (KUmsAllCamt) is deactivated" -ForegroundColor Green
}

# Check job sequence
if ($appCode -match 'JobAttempt\("KUmsZeitSEPA"') {
    Write-Host "  ✅ KUmsZeitSEPA is in job list" -ForegroundColor Green
} else {
    Write-Host "  ❌ KUmsZeitSEPA missing from job list!" -ForegroundColor Red
    $allChecksPass = $false
}

if ($appCode -match 'JobAttempt\("KUmsAll"\)') {
    Write-Host "  ✅ KUmsAll is in job list" -ForegroundColor Green
} else {
    Write-Host "  ❌ KUmsAll missing from job list!" -ForegroundColor Red
    $allChecksPass = $false
}

if ($appCode -match 'JobAttempt\("KUmsNew"\)') {
    Write-Host "  ✅ KUmsNew is in job list" -ForegroundColor Green
} else {
    Write-Host "  ❌ KUmsNew missing from job list!" -ForegroundColor Red
    $allChecksPass = $false
}

# ============================================================================
# Check 3: Java-Sync Test
# ============================================================================

Write-Host ""
Write-Host "[3/3] Running Java-Sync Reference Test..." -ForegroundColor Yellow

$javaSyncExe = "$PSScriptRoot\qt.cmd"
if (Test-Path $javaSyncExe) {
    $output = & $javaSyncExe 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✅ Java-Sync test PASSED" -ForegroundColor Green
    } else {
        Write-Host "  ❌ Java-Sync test FAILED!" -ForegroundColor Red
        Write-Host "     Run 'scripts\qt.cmd' manually to debug" -ForegroundColor Red
        $allChecksPass = $false
    }
} else {
    Write-Host "  ⚠️  Java-Sync test skipped (qt.cmd not found)" -ForegroundColor Yellow
}

# ============================================================================
# Summary
# ============================================================================

Write-Host ""
Write-Host "========================================================================" -ForegroundColor Cyan
if ($allChecksPass) {
    Write-Host "  ✅ ALL CHECKS PASSED - App is in sync with Java-Sync" -ForegroundColor Green
} else {
    Write-Host "  ❌ CHECKS FAILED - App is OUT OF SYNC with Java-Sync!" -ForegroundColor Red
    Write-Host "" -ForegroundColor Red
    Write-Host "  See CODE-SYNC-VERIFICATION.md for details" -ForegroundColor Red
}
Write-Host "========================================================================" -ForegroundColor Cyan
Write-Host ""

if (-not $allChecksPass) {
    exit 1
}
