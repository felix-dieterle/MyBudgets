<#
start-sync.ps1

Ein sehr einfaches Starter-Skript für Windows, das lokal die vorbereiteten
Helper-Skripte zum sicheren FinTS-Sync aufruft.

Es macht:
- Setzt temporär ExecutionPolicy auf Bypass
- Prüft, ob die Creds-Datei existiert (%APPDATA%\4apps\mybudgets-creds.ps1)
- Ruft das bereits vorhandene Runner-Skript scripts\run-bbbank-sync.ps1 auf

Beispiel:
  .\start-sync.ps1 -LastOnly -Debug

Parameter:
  -LastOnly  : nur die neueste Buchung abrufen (schneller)
  -Debug     : aktiviere --debug
  -DaysBack N: Tage zurück (Default 7)
  -TanMethod : TAN Code (Default '900')
  -Server    : FinTS Server URL (Default BBBank endpoint)
#>

param(
    [switch]$LastOnly,
    [switch]$Debug,
    [string]$DaysBack = '7',
    [string]$TanMethod = '900',
    [string]$Server = 'https://finanzportal.bbbank.de/banking'
)

try {
    Write-Host "Starter: setze temporäre ExecutionPolicy (Bypass)"
    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -ErrorAction Stop

    $creds = Join-Path $env:APPDATA '4apps\mybudgets-creds.ps1'
    if (-not (Test-Path $creds)) {
        Write-Error ("Credentials file not found: " + $creds)
        Write-Error 'Bitte erstelle %APPDATA%\\4apps\\mybudgets-creds.ps1 (Kopie von scripts/mybudgets-creds-template.ps1).'
        exit 2
    }

    # $MyInvocation.MyCommand.Path points to this script (scripts\start-sync.ps1)
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    # Repo root is parent of scripts directory
    $repoRoot = Split-Path -Parent $scriptDir
    $runner = Join-Path $repoRoot 'scripts\run-bbbank-sync.ps1'
    if (-not (Test-Path $runner)) {
        Write-Error "Runner script not found: $runner"
        exit 2
    }

    Write-Host "Starte Sync via: $runner"

    # Validate and convert DaysBack to integer
    $daysBackInt = 7
    if (-not [int]::TryParse($DaysBack, [ref]$daysBackInt)) {
        Write-Warning ("Ungültiger Wert für DaysBack: '" + $DaysBack + "' - verwende Standard 7")
        $daysBackInt = 7
    }

    # Build argument list safely
    $runnerArgs = @()
    if ($LastOnly) { $runnerArgs += '-LastOnly' }
    if ($Debug) { $runnerArgs += '-Debug' }
    $runnerArgs += '-DaysBack'; $runnerArgs += [string]$daysBackInt
    $runnerArgs += '-TanMethod'; $runnerArgs += $TanMethod
    $runnerArgs += '-Server'; $runnerArgs += $Server

    & $runner @runnerArgs

    exit $LASTEXITCODE

} catch {
    Write-Error ("Fehler beim Starten: " + $_.ToString())
    exit 1
}
