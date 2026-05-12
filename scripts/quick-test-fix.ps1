#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Schneller Test-Workflow für BBBank-Sync-Fixes (ohne App-Build)
    
.DESCRIPTION
    Testet Änderungen in folgender Reihenfolge:
    1. Java-Sync (10-15 Sekunden) - Schnellster Test
    2. Optional: Gradle Live-Test (40-60 Sekunden) - Android-spezifischer Test
    
    Erst wenn beide erfolgreich sind, sollte die App gebaut werden.
    
.PARAMETER SkipJavaSync
    Überspringt den Java-Sync-Test
    
.PARAMETER RunLiveTest
    Führt zusätzlich den Gradle-Live-Test aus
    
.EXAMPLE
    .\scripts\quick-test-fix.ps1
    # Nur Java-Sync (schnellster Test)
    
.EXAMPLE
    .\scripts\quick-test-fix.ps1 -RunLiveTest
    # Java-Sync + Gradle-Live-Test
#>

param(
    [switch]$SkipJavaSync,
    [switch]$RunLiveTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

Write-Host "=== Quick Test Workflow für BBBank-Sync-Fix ===" -ForegroundColor Cyan
Write-Host ""

# ============================================================================
# Stufe 1: Java-Sync (10-15 Sekunden)
# ============================================================================

if (-not $SkipJavaSync) {
    Write-Host "[1/2] Java-Sync Test (schnellster Test, ~10-15 Sekunden)..." -ForegroundColor Yellow
    Write-Host ""
    
    # Check config.properties
    $configFile = Join-Path $repoRoot "scripts\java-sync\config.properties"
    if (-not (Test-Path $configFile)) {
        Write-Host "❌ config.properties nicht gefunden!" -ForegroundColor Red
        Write-Host ""
        Write-Host "Bitte zuerst einrichten:" -ForegroundColor Yellow
        Write-Host "  cd scripts\java-sync" -ForegroundColor Gray
        Write-Host "  Copy-Item config.properties.example config.properties" -ForegroundColor Gray
        Write-Host "  # Dann config.properties bearbeiten und Credentials eintragen" -ForegroundColor Gray
        exit 1
    }
    
    # Build Java-Sync
    Write-Host "  Building Java-Sync..." -ForegroundColor Gray
    Push-Location $repoRoot
    try {
        & .\gradlew.bat :scripts:java-sync:jar --quiet
        if ($LASTEXITCODE -ne 0) {
            Write-Host "❌ Java-Sync Build fehlgeschlagen!" -ForegroundColor Red
            exit 1
        }
    } finally {
        Pop-Location
    }
    
    # Run Java-Sync
    Write-Host "  Running Java-Sync..." -ForegroundColor Gray
    Write-Host ""
    $javaSyncJar = Join-Path $repoRoot "scripts\java-sync\build\libs\java-sync.jar"
    Push-Location (Join-Path $repoRoot "scripts\java-sync")
    try {
        java -jar $javaSyncJar
        $javaSyncExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    
    Write-Host ""
    if ($javaSyncExitCode -eq 0) {
        Write-Host "✅ Java-Sync erfolgreich!" -ForegroundColor Green
    } else {
        Write-Host "❌ Java-Sync fehlgeschlagen (Exit Code: $javaSyncExitCode)" -ForegroundColor Red
        Write-Host ""
        Write-Host "Troubleshooting:" -ForegroundColor Yellow
        Write-Host "  - Prüfe config.properties (IBAN, userId, pin korrekt?)" -ForegroundColor Gray
        Write-Host "  - Teste manuell: cd scripts\java-sync && java -jar build\libs\java-sync.jar" -ForegroundColor Gray
        Write-Host "  - Prüfe Logs oben für Details" -ForegroundColor Gray
        exit $javaSyncExitCode
    }
    Write-Host ""
}

# ============================================================================
# Stufe 2: Gradle Live-Test (optional, 40-60 Sekunden)
# ============================================================================

if ($RunLiveTest) {
    Write-Host "[2/2] Gradle Live-Test (Android-spezifischer Test, ~40-60 Sekunden)..." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "⚠️  Dieser Test benötigt interaktive Eingaben (IBAN, PIN, etc.)" -ForegroundColor Yellow
    Write-Host ""
    
    $liveTestScript = Join-Path $repoRoot "scripts\run-live-bbbank-sync-test.sh"
    if (Test-Path $liveTestScript) {
        & bash $liveTestScript
        $liveTestExitCode = $LASTEXITCODE
        
        Write-Host ""
        if ($liveTestExitCode -eq 0) {
            Write-Host "✅ Gradle Live-Test erfolgreich!" -ForegroundColor Green
        } else {
            Write-Host "❌ Gradle Live-Test fehlgeschlagen (Exit Code: $liveTestExitCode)" -ForegroundColor Red
            exit $liveTestExitCode
        }
    } else {
        Write-Host "⚠️  run-live-bbbank-sync-test.sh nicht gefunden, überspringe..." -ForegroundColor Yellow
    }
    Write-Host ""
}

# ============================================================================
# Summary
# ============================================================================

Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "✅ Alle Tests bestanden!" -ForegroundColor Green
Write-Host ""
Write-Host "Nächste Schritte:" -ForegroundColor Yellow
Write-Host "  1. Wenn alles funktioniert hat → App bauen und auf Device testen" -ForegroundColor Gray
Write-Host "  2. Falls noch Probleme → Logs analysieren und iterieren" -ForegroundColor Gray
Write-Host ""
Write-Host "App bauen:" -ForegroundColor Yellow
Write-Host "  gradlew.bat assembleDebug" -ForegroundColor Gray
Write-Host "  # Oder in Android Studio: Build → Build Bundle(s) / APK(s)" -ForegroundColor Gray
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
