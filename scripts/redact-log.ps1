<#
  redact-log.ps1

  Kleine Hilfs-Skript, um sensible Daten aus den erzeugten Logs zu entfernen
  bevor du sie hier teilst. Es maskiert:
    - IBANs (zeigt nur die letzten 4 Ziffern: '***1234')
    - lange Zahlen (>=7 Ziffern) -> '[REDACTED_NUM]'
    - PIN/TAN Werte nach 'PIN:' oder 'TAN:' -> 'PIN: [REDACTED]'

  Verwendung:
    .\scripts\redact-log.ps1 -InputPath scripts/logs/bbbank-sync-20260413-120000.log

  Ausgabe: erzeugt dieselbe Datei mit Suffix '-redacted.log' im selben Ordner.
#>

param(
    [Parameter(Mandatory=$true)] [string]$InputPath,
    [string]$OutputPath
)

if (-not (Test-Path $InputPath)) {
    Write-Error "Input file not found: $InputPath"
    exit 2
}

if (-not $OutputPath) {
    $dir = Split-Path -Parent $InputPath
    $base = Split-Path -Leaf $InputPath
    $OutputPath = Join-Path $dir ($base -replace '\.log$', '') + '-redacted.log'
}

try {
    $text = Get-Content -Raw -LiteralPath $InputPath -ErrorAction Stop

    # Mask IBANs like DE12 3456 7890 1234 5678 90 or DE12345678901234567890
    $ibanRegex = [regex] '(?i)\bDE[\d\s]{18,30}\b'
    $text = $ibanRegex.Replace($text, {
        param($m)
        $s = ($m.Value -replace '\s','')
        if ($s.Length -ge 4) { '***' + $s.Substring($s.Length - 4) } else { '***' }
    })

    # Mask PIN/TAN values appearing as 'PIN: 1234' or 'TAN: 123456'
    $text = [regex]::Replace($text, '(?i)\b(PIN|TAN)\s*[:=]\s*\S+', '$1: [REDACTED]')

    # Mask long numeric sequences (>=7 digits) to avoid leaking account numbers, tokens
    $text = [regex]::Replace($text, '\b\d{7,}\b', '[REDACTED_NUM]')

    Set-Content -LiteralPath $OutputPath -Value $text -Encoding UTF8
    Write-Host "Redacted log written to: $OutputPath"
    exit 0
} catch {
    Write-Error "Fehler beim Redacting: $_"
    exit 1
}
