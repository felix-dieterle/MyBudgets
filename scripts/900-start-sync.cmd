@echo off
REM Simple launcher for Windows (double-clickable)
REM It calls the PowerShell starter with ExecutionPolicy bypass so you don't have to type the long command.

REM %~dp0 is the directory of this .cmd (scripts\). The repo root is one level up.
SET SCRIPT_DIR=%~dp0
SET REPO_ROOT=%SCRIPT_DIR%..\
REM Change into repo root to ensure relative paths inside scripts work
cd /d "%REPO_ROOT%"

REM Call the PowerShell start script located in scripts\ (relative to repo root)
REM Use -Command so named parameters are correctly passed to the script.
REM We already changed into the repo root above, so call the script relative to CWD.
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\start-sync-runner.ps1"

pause
