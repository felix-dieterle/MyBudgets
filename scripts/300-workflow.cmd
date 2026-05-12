@echo off
REM ============================================================================
REM Full Workflow: Test → Build → Install
REM ============================================================================
REM
REM Fuehrt den kompletten Workflow aus:
REM   1. Quick Test (Java-Sync)
REM   2. Bei Erfolg: App bauen
REM   3. Optional: APK installieren
REM
REM Usage:
REM   workflow.cmd              - Test + Debug-Build
REM   workflow.cmd release      - Test + Release-Build
REM   workflow.cmd release install - Test + Release-Build + Install
REM
REM ============================================================================

setlocal enabledelayedexpansion

SET SCRIPT_DIR=%~dp0
SET REPO_ROOT=%SCRIPT_DIR%..\
cd /d "%REPO_ROOT%"

SET BUILD_TYPE=debug
SET AUTO_INSTALL=0

REM Parse arguments
if /i "%~1"=="release" set BUILD_TYPE=release
if /i "%~2"=="install" set AUTO_INSTALL=1
if /i "%~1"=="install" set AUTO_INSTALL=1

echo.
echo ========================================================================
echo   MyBudgets Full Workflow
echo ========================================================================
echo.
echo Workflow-Schritte:
echo   1. Quick Test (Java-Sync)
echo   2. App Build (%BUILD_TYPE%)
if !AUTO_INSTALL! equ 1 echo   3. APK installieren (via adb)
echo.

REM ============================================================================
REM Step 1: Quick Test
REM ============================================================================

echo [1/3] Quick Test ausfuehren...
echo.
echo -----------------------------------------------------------------------
call scripts\100-quick-test.cmd
set TEST_EXIT=!ERRORLEVEL!
echo -----------------------------------------------------------------------
echo.

if !TEST_EXIT! neq 0 (
    echo [X] Test fehlgeschlagen!
    echo     Build wird NICHT ausgefuehrt.
    echo.
    echo Bitte beheben Sie den Fehler und versuchen Sie es erneut.
    echo.
    pause
    exit /b !TEST_EXIT!
)

echo [OK] Test erfolgreich!
echo.

REM ============================================================================
REM Step 2: Build APK
REM ============================================================================

echo [2/3] App bauen...
echo.
call scripts\202-build-apk.cmd %BUILD_TYPE%
set BUILD_EXIT=!ERRORLEVEL!

if !BUILD_EXIT! neq 0 (
    echo [X] Build fehlgeschlagen!
    pause
    exit /b !BUILD_EXIT!
)

echo [OK] Build erfolgreich!
echo.

REM ============================================================================
REM Step 3: Install (optional)
REM ============================================================================

if !AUTO_INSTALL! equ 1 (
    echo [3/3] APK installieren...
    echo.
    
    REM Find APK
    if "%BUILD_TYPE%"=="release" (
        for %%f in (app\build\outputs\apk\release\MyBudgets-*.apk) do set APK_FILE=%%f
    ) else (
        for %%f in (app\build\outputs\apk\debug\MyBudgets-*.apk) do set APK_FILE=%%f
    )
    
    if "!APK_FILE!"=="" (
        echo [X] APK nicht gefunden!
        pause
        exit /b 1
    )
    
    echo Installing: !APK_FILE!
    adb install -r "!APK_FILE!"
    
    if errorlevel 1 (
        echo [X] Installation fehlgeschlagen!
        echo.
        echo Troubleshooting:
        echo   - Ist adb installiert? (adb version)
        echo   - Ist Device verbunden? (adb devices)
        echo   - USB-Debugging aktiviert?
        echo.
        pause
        exit /b 1
    )
    
    echo [OK] APK installiert!
    echo.
)

REM ============================================================================
REM Summary
REM ============================================================================

echo ========================================================================
echo [OK] Workflow abgeschlossen!
echo ========================================================================
echo.
echo Naechste Schritte:
echo.
if !AUTO_INSTALL! equ 0 (
    echo   1. APK manuell installieren oder:
    echo      scripts\workflow.cmd %BUILD_TYPE% install
    echo.
)
echo   2. In App testen:
echo      - Konto oeffnen
echo      - Kontoauszug synchronisieren
echo      - Secure Go bestaetigen
echo      - Transaktionen pruefen
echo.
echo   3. Bei Problemen:
echo      - App-Logs exportieren (Einstellungen → Fehlerprotokoll)
echo      - Logs analysieren
echo.
echo ========================================================================

pause
