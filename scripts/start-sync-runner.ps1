<#
Simple PowerShell wrapper that calls scripts\start-sync.ps1 with default flags.
#>

try {
    # Call the actual runner that does the creds dot-sourcing and starts the python script
    $scriptPath = Join-Path $PSScriptRoot 'run-bbbank-sync.ps1'
    if (-not (Test-Path $scriptPath)) {
        Write-Error "run-bbbank-sync.ps1 not found: $scriptPath"
        exit 2
    }

    & $scriptPath -LastOnly:$true -Debug:$true
    exit $LASTEXITCODE
} catch {
    Write-Error ("Wrapper error: " + $_.ToString())
    exit 1
}
