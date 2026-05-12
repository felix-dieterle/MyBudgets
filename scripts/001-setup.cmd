@echo off
REM ============================================================================
REM Setup für Java-Sync Testing (einmalig)
REM ============================================================================
REM
REM Dieses Script richtet die config.properties ein und führt den ersten Test aus.
REM
REM ============================================================================

setlocal

SET SCRIPT_DIR=%~dp0
SET REPO_ROOT=%SCRIPT_DIR%..\

cd /d "%REPO_ROOT%"

echo.
echo ========================================================================
echo   BBBank Java-Sync Setup (einmalig)
echo ========================================================================
echo.
echo Dieses Script richtet alles ein fuer schnelle Test-Iterationen.
echo.

REM Check if config already exists
if exist "scripts\java-sync\config.properties" (
    echo [i] config.properties existiert bereits.
    echo.
    set /p OVERWRITE="Moechten Sie die Config neu anlegen? (j/n): "
    if /i not "!OVERWRITE!"=="j" (
        echo.
        echo [i] Setup uebersprungen. Starte Test...
        echo.
        call scripts\quick-test-fix.cmd
        exit /b !ERRORLEVEL!
    )
)

echo.
echo [1/4] Erstelle config.properties...
cd scripts\java-sync

if exist config.properties.example (
    copy config.properties.example config.properties >nul 2>&1
    echo [OK] config.properties wurde angelegt.
) else (
    echo [X] config.properties.example nicht gefunden!
    echo     Projekt moeglicherweise beschaedigt.
    pause
    exit /b 1
)

echo.
echo [2/4] Credentials eintragen...
echo.
echo      Bitte tragen Sie folgende Felder ein:
echo.
echo        iban         = Ihre BBBank IBAN (z.B. DE89370400440532013000)
echo        userId       = Ihre Online-Banking Nutzerkennung
echo        pin          = Ihre Online-Banking PIN
echo        blz          = BLZ der BBBank (z.B. 37040044)
echo        tanMethod    = 900 (fuer BBBank Secure Go)
echo        daysBack     = 30 (Transaktionen der letzten 30 Tage)
echo        debug        = false (oder true fuer ausfuehrliches Logging)
echo.
echo      Druecken Sie eine Taste, um die Datei zu oeffnen...
pause >nul

notepad config.properties

echo.
echo [3/4] Credentials gespeichert?
set /p SAVED="Haben Sie die Credentials eingetragen und gespeichert? (j/n): "

if /i not "!SAVED!"=="j" (
    echo.
    echo [i] Bitte config.properties bearbeiten und dann setup.cmd nochmal starten.
    echo     Datei: %CD%\config.properties
    pause
    exit /b 0
)

cd /d "%REPO_ROOT%"

echo.
echo [4/4] Starte ersten Test...
echo.
echo -----------------------------------------------------------------------

call scripts\quick-test-fix.cmd

set TEST_EXIT=!ERRORLEVEL!

echo -----------------------------------------------------------------------
echo.

if !TEST_EXIT! equ 0 (
    echo ========================================================================
    echo [OK] Setup abgeschlossen!
    echo ========================================================================
    echo.
    echo Naechste Schritte:
    echo.
    echo   1. Bei Aenderungen testen:
    echo      scripts\quick-test-fix.cmd
    echo      oder kurz: scripts\qt.cmd
    echo.
    echo   2. App bauen:
    echo      gradlew.bat assembleDebug
    echo.
    echo   3. Dokumentation:
    echo      - TESTING-WORKFLOW.md  (detaillierter Workflow)
    echo      - QUICK-REFERENCE.md   (Cheatsheet)
    echo.
    echo ========================================================================
) else (
    echo ========================================================================
    echo [X] Setup fehlgeschlagen
    echo ========================================================================
    echo.
    echo Bitte pruefen Sie:
    echo   - Sind die Credentials korrekt?
    echo   - Ist die Internet-Verbindung aktiv?
    echo   - Haben Sie in Secure Go App bestaetigt?
    echo.
    echo Troubleshooting:
    echo   - Config bearbeiten: notepad scripts\java-sync\config.properties
    echo   - Debug aktivieren: debug=true in config.properties
    echo   - Nochmal versuchen: scripts\setup.cmd
    echo.
    echo ========================================================================
)

pause
