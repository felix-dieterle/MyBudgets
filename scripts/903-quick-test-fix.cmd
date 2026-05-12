@echo off
REM ============================================================================
REM Quick Test Workflow für BBBank-Sync-Fixes
REM ============================================================================
REM 
REM Testet Änderungen ohne kompletten App-Build:
REM   1. Java-Sync Test (~10-15 Sekunden)
REM   2. Optional: Gradle Live-Test (~40-60 Sekunden)
REM
REM Usage:
REM   quick-test-fix.cmd              - Nur Java-Sync (schnellster Test)
REM   quick-test-fix.cmd --with-live  - Mit Gradle Live-Test
REM
REM ============================================================================

setlocal enabledelayedexpansion

SET SCRIPT_DIR=%~dp0
SET REPO_ROOT=%SCRIPT_DIR%..\
SET RUN_LIVE_TEST=0

REM Parse arguments
:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="--with-live" set RUN_LIVE_TEST=1
if /i "%~1"=="-l" set RUN_LIVE_TEST=1
shift
goto parse_args
:args_done

cd /d "%REPO_ROOT%"

echo.
echo ========================================================================
echo   Quick Test Workflow fuer BBBank-Sync-Fix
echo ========================================================================
echo.

REM ============================================================================
REM Stufe 1: Java-Sync Test (10-15 Sekunden)
REM ============================================================================

echo [1/2] Java-Sync Test (schnellster Test, ~10-15 Sekunden)...
echo.

REM Check if config.properties exists
if not exist "scripts\java-sync\config.properties" (
    echo [X] config.properties nicht gefunden!
    echo.
    echo Bitte zuerst einrichten:
    echo   1. cd scripts\java-sync
    echo   2. copy config.properties.example config.properties
    echo   3. notepad config.properties
    echo   4. Fuellen Sie folgende Felder aus:
    echo        iban=DE89...
    echo        userId=IHRE_NUTZERKENNUNG
    echo        pin=IHRE_PIN
    echo        tanMethod=900
    echo.
    
    REM Automatisch Config anlegen und öffnen?
    set /p CREATE_CONFIG="Soll ich config.properties automatisch anlegen? (j/n): "
    if /i "!CREATE_CONFIG!"=="j" (
        cd scripts\java-sync
        copy config.properties.example config.properties >nul 2>&1
        echo.
        echo [OK] config.properties wurde angelegt.
        echo      Bitte jetzt Credentials eintragen und speichern.
        echo.
        echo      Druecken Sie eine Taste, um die Datei zu oeffnen...
        pause >nul
        notepad config.properties
        echo.
        echo [i] Credentials eingetragen? Dann nochmal quick-test-fix.cmd starten.
        cd /d "%REPO_ROOT%"
    )
    
    pause
    exit /b 2
)

REM Build Java-Sync
echo   Building Java-Sync...
call gradlew.bat :scripts:java-sync:jar --quiet
if errorlevel 1 (
    echo.
    echo [X] Java-Sync Build fehlgeschlagen!
    echo.
    echo Troubleshooting:
    echo   - Pruefe ob Java installiert ist: java -version
    echo   - Pruefe ob Gradle-Wrapper vorhanden: dir gradlew.bat
    echo   - Versuche manuell: gradlew.bat :scripts:java-sync:jar
    echo.
    pause
    exit /b 1
)

REM Run Java-Sync
echo   Running Java-Sync...
echo.
echo -----------------------------------------------------------------------
cd scripts\java-sync
java -jar build\libs\java-sync.jar
set JAVA_SYNC_EXIT=!ERRORLEVEL!
cd /d "%REPO_ROOT%"
echo -----------------------------------------------------------------------
echo.

if !JAVA_SYNC_EXIT! equ 0 (
    echo [OK] Java-Sync erfolgreich!
    echo.
) else (
    echo [X] Java-Sync fehlgeschlagen (Exit Code: !JAVA_SYNC_EXIT!)
    echo.
    echo Haeufige Fehlerursachen:
    echo   - PIN/Nutzerkennung falsch
    echo     ^> Pruefe scripts\java-sync\config.properties
    echo.
    echo   - Secure Go nicht bestaetigt
    echo     ^> Pruefe Handy und bestaetigen Sie in der App
    echo.
    echo   - Timeout
    echo     ^> Netzwerk-Verbindung pruefen
    echo     ^> Spaeter nochmal versuchen
    echo.
    echo   - Java nicht gefunden
    echo     ^> java -version ausfuehren
    echo     ^> Ggf. JAVA_HOME setzen
    echo.
    echo Debug-Modus aktivieren:
    echo   1. Oeffne scripts\java-sync\config.properties
    echo   2. Setze: debug=true
    echo   3. Nochmal quick-test-fix.cmd starten
    echo.
    pause
    exit /b !JAVA_SYNC_EXIT!
)

REM ============================================================================
REM Stufe 2: Gradle Live-Test (optional, 40-60 Sekunden)
REM ============================================================================

if !RUN_LIVE_TEST! equ 1 (
    echo [2/2] Gradle Live-Test (Android-spezifischer Test, ~40-60 Sekunden)...
    echo.
    echo [i] Dieser Test benoetigt interaktive Eingaben (IBAN, PIN, etc.)
    echo.
    
    if exist "scripts\run-live-bbbank-sync-test.sh" (
        bash scripts\run-live-bbbank-sync-test.sh
        set LIVE_TEST_EXIT=!ERRORLEVEL!
        
        echo.
        if !LIVE_TEST_EXIT! equ 0 (
            echo [OK] Gradle Live-Test erfolgreich!
        ) else (
            echo [X] Gradle Live-Test fehlgeschlagen (Exit Code: !LIVE_TEST_EXIT!)
            echo.
            echo Wenn Java-Sync OK war, aber Live-Test fehlschlaegt:
            echo   ^> Android-spezifisches Problem (z.B. SAXParserFactory)
            echo   ^> Pruefe App-Logs
            echo.
            pause
            exit /b !LIVE_TEST_EXIT!
        )
    ) else (
        echo [!] run-live-bbbank-sync-test.sh nicht gefunden, ueberspringe...
    )
    echo.
)

REM ============================================================================
REM Summary
REM ============================================================================

echo ========================================================================
echo [OK] Alle Tests bestanden!
echo ========================================================================
echo.
echo Naechste Schritte:
echo.
echo   1. App bauen (2-3 Minuten):
echo      gradlew.bat assembleDebug
echo.
echo   2. APK installieren:
echo      app\build\outputs\apk\debug\app-debug.apk
echo.
echo   3. In App testen:
echo      - Konto oeffnen
echo      - "Kontoauszug synchronisieren" tippen
echo      - Secure Go bestaetigen
echo      - Pruefen ob Transaktionen ankommen
echo.
echo Weitere Iterationen:
echo   - Aenderung machen (BbbankSync.java oder FintsService.kt)
echo   - quick-test-fix.cmd nochmal starten
echo   - Bei Erfolg: gradlew.bat assembleDebug
echo.
echo ========================================================================

pause
