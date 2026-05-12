<#
. SICHERHEIT: Dieses Skript dot-sourced die lokale Credentials-Datei
. %APPDATA%\4apps\mybudgets-creds.ps1 und startet das Debug-Skript.
. Es liest keine sensiblen Werte in die Kommandozeile – die Werte bleiben in
. Umgebungsvariablen und werden nicht ins Repo geschrieben.

. Verwendung (PowerShell):
.   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.   .\scripts\run-bbbank-sync.ps1 -LastOnly -Debug

. Optionen (optional):
.   -LastOnly   : Nur die neueste Buchung abrufen (verkürzt Laufzeit)
.   -Debug      : Aktiviert --debug (FinTS Wire-Logs)
.   -DaysBack N : Tage zurück (Default 7)
.   -Server URL : FinTS-Server URL (Default BBBank endpoint)
.   -TanMethod  : TAN-Methode Code (Default 900)

. Logdatei wird nach run im Ordner scripts\logs geschrieben.
#>

param(
    [switch]$LastOnly,
    [switch]$Debug,
    [int]$DaysBack = 7,
    [string]$Server = 'https://fints2.atruvia.de/cgi-bin/hbciservlet',
    [string]$TanMethod = '900'
)

try {
    $creds = Join-Path $env:APPDATA '4apps\mybudgets-creds.ps1'
    if (-not (Test-Path $creds)) {
        Write-Error "Credentials file not found: $creds`nBitte erstellen und den Pfad prüfen."
        exit 2
    }

    # Dot-source the creds file: it should set $env:MYB_IBAN, $env:MYB_USER, $env:MYB_PIN
    . $creds

    # Minimal checks
    if (-not $env:MYB_IBAN -or -not $env:MYB_USER -or -not $env:MYB_PIN) {
        Write-Error "Fehlende Umgebungsvariablen nach Dot-Sourcing. Stelle sicher, dass mybudgets-creds.ps1 die Umgebungsvariablen setzt."
        exit 2
    }

    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $logDir = Join-Path $scriptDir 'logs'
    if (-not (Test-Path $logDir)) { New-Item -Path $logDir -ItemType Directory | Out-Null }
    $ts = Get-Date -Format 'yyyyMMdd-HHmmss'
    $logFile = Join-Path $logDir "bbbank-sync-$ts.log"

    # Build argument list for the Python script without exposing PIN on cmdline
    $argList = @(
        '--iban', $env:MYB_IBAN,
        '--user', $env:MYB_USER,
        '--server', $Server,
        '--tan-method', $TanMethod,
        '--days-back', [string]$DaysBack
    )

    if ($LastOnly) { $argList += '--last-only' }
    if ($Debug) { $argList += '--debug' }

    Write-Host "Starte Sync (Logs: $logFile) ..."
    Write-Host "  IBAN: $($env:MYB_IBAN -replace '\\s','')  (Nutzer: $($env:MYB_USER))"

    # Run Python and tee output to logfile
    # Prefer a repo-local virtualenv (.venv) if present, otherwise fall back to system 'python'
    $repoRoot = Split-Path -Parent $scriptDir
    $venvPython = Join-Path $repoRoot '.venv\Scripts\python.exe'
    if (Test-Path $venvPython) {
        $python = $venvPython
    } else {
        $python = 'python'
    }

    Write-Host "Using Python: $python"
    $scriptPath = Join-Path $scriptDir 'bbbank-sync-debug.py'
    & $python $scriptPath $argList 2>&1 | Tee-Object -FilePath $logFile

    $exitCode = $LASTEXITCODE
    Write-Host "Fertig. Exit-Code: $exitCode. Log: $logFile"
    if ($exitCode -ne 0) { exit $exitCode }
    exit 0

} catch {
    Write-Error "Fehler: $_"
    exit 1
}
