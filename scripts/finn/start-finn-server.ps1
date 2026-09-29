# Start the isolated MyBudgets finn API server (PHP built-in web server).
# - Uses only php.exe from XAMPP (no Apache, no MariaDB changes).
# - Binds to the LAN IP so the app can sync from the phone in the local network.
# - Does NOT bind 0.0.0.0 -> nothing reachable from the internet.
# - Persists PID in $stateFile; -Stop kills only the stored PID.

param(
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'

$php     = 'F:\prg\xampp\php\php.exe'
$docroot = 'F:\prg\xampp\htdocs\apps\finn'
$router  = 'F:\prg\xampp\htdocs\apps\finn\router.php'
$port    = 8089
$stateFile = 'F:\prg\trello-bot-questions\logs\netzwerk-monitoring\finn-server.pid'
$logDir  = 'F:\prg\trello-bot-questions\logs\finn'
$stdout  = Join-Path $logDir 'finn-server.log'
$stderr  = Join-Path $logDir 'finn-server.err.log'

if ($Stop) {
    if (Test-Path $stateFile) {
        $oldPid = Get-Content $stateFile
        if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
            Stop-Process -Id $oldPid -Force
            Write-Host "Stopped finn server (PID $oldPid)"
        }
        Remove-Item $stateFile -Force
    } else {
        Write-Host "No finn server state file - nothing to stop."
    }
    exit 0
}

if (-not (Test-Path $php))   { throw "PHP not found: $php" }
if (-not (Test-Path $docroot)) { throw "Docroot not found: $docroot" }

if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

# LAN IP (not 0.0.0.0) -> local-network only.
# Prefer the real 192.168.178.x home LAN, skip virtual adapters (56.x, 172.x).
$lanIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $_.IPAddress -like '192.168.178.*' -and $_.PrefixOrigin -ne 'WellKnown'
} | Select-Object -First 1).IPAddress
if (-not $lanIp) {
    $lanIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
        $_.IPAddress -like '192.168.*.*' -and $_.PrefixOrigin -ne 'WellKnown'
    } | Select-Object -First 1).IPAddress
}
if (-not $lanIp) { throw "No LAN IP (192.168.*) found on this machine." }

# Port frei?
if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
    throw "Port $port is already in use."
}

$bind = "$lanIp`:$port"
$proc = Start-Process -FilePath $php `
    -ArgumentList @("-S", $bind, "-t", $docroot, $router) `
    -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput $stdout -RedirectStandardError $stderr

Set-Content -Path $stateFile -Value $proc.Id
Write-Host "finn server started: http://$bind/api.php (PID $($proc.Id))"
Write-Host "Logs: $logDir"