@echo off
REM ============================================================================
REM Build APK (wie GitHub CI)
REM ============================================================================
REM
REM Baut die App lokal genau wie im GitHub CI-Build.
REM
REM Usage:
REM   build-apk.cmd              - Debug-Build (schneller)
REM   build-apk.cmd release      - Release-Build (optimiert)
REM   build-apk.cmd release 123  - Release-Build mit versionCode=123
REM
REM ============================================================================

setlocal enabledelayedexpansion

SET SCRIPT_DIR=%~dp0
SET REPO_ROOT=%SCRIPT_DIR%..\
cd /d "%REPO_ROOT%"

SET BUILD_TYPE=debug
SET VERSION_CODE=

REM Parse arguments
if /i "%~1"=="release" set BUILD_TYPE=release
if not "%~2"=="" set VERSION_CODE=%~2

echo.
echo ========================================================================
echo   MyBudgets APK Build
echo ========================================================================
echo.
echo Build-Type: %BUILD_TYPE%

REM ============================================================================
REM Check Java
REM ============================================================================

echo.
echo [1/5] Java pruefen...
java -version >nul 2>&1
if errorlevel 1 (
    echo [X] Java nicht gefunden!
    echo.
    echo Bitte installieren Sie Java JDK 17 oder hoeher.
    echo.
    echo Option 1: Java aus Android Studio nutzen
    echo   set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
    echo   set PATH=%%JAVA_HOME%%\bin;%%PATH%%
    echo.
    echo Option 2: Java downloaden
    echo   https://adoptium.net/temurin/releases/?version=17
    echo.
    pause
    exit /b 1
)

for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VERSION=%%i
echo [OK] Java gefunden: %JAVA_VERSION%

REM ============================================================================
REM Check Keystore
REM ============================================================================

echo.
echo [2/5] Keystore pruefen...
if not exist "keystore\debug.keystore" (
    echo [X] keystore\debug.keystore nicht gefunden!
    echo     Build kann nicht fortgesetzt werden.
    pause
    exit /b 1
)
echo [OK] Keystore vorhanden: keystore\debug.keystore

REM ============================================================================
REM Extract Version Name
REM ============================================================================

echo.
echo [3/5] Version ermitteln...

REM Extract versionName from app/build.gradle.kts
for /f "tokens=2 delims==" %%i in ('findstr /C:"versionName = " app\build.gradle.kts') do (
    set VERSION_NAME_RAW=%%i
)
REM Remove quotes and whitespace
set VERSION_NAME=!VERSION_NAME_RAW:"=!
set VERSION_NAME=!VERSION_NAME: =!

if "%VERSION_NAME%"=="" (
    echo [!] Warnung: Konnte versionName nicht ermitteln, nutze "unknown"
    set VERSION_NAME=unknown
)

echo [OK] versionName: %VERSION_NAME%

REM Extract or generate versionCode
if not "%VERSION_CODE%"=="" (
    echo [OK] versionCode: %VERSION_CODE% (manuell gesetzt)
) else (
    REM Try git rev-list count
    for /f %%i in ('git rev-list --count HEAD 2^>nul') do set VERSION_CODE=%%i
    if "!VERSION_CODE!"=="" (
        echo [!] Warnung: Konnte versionCode nicht per Git ermitteln, nutze 1
        set VERSION_CODE=1
    ) else (
        echo [OK] versionCode: !VERSION_CODE! (via Git)
    )
)

REM ============================================================================
REM Build APK
REM ============================================================================

echo.
echo [4/5] APK bauen...
echo.

if "%BUILD_TYPE%"=="release" (
    echo Building RELEASE APK...
    if not "%VERSION_CODE%"=="" (
        call gradlew.bat assembleRelease --no-daemon -PversionCode=%VERSION_CODE%
    ) else (
        call gradlew.bat assembleRelease --no-daemon
    )
    set APK_PATH=app\build\outputs\apk\release\app-release.apk
) else (
    echo Building DEBUG APK...
    call gradlew.bat assembleDebug --no-daemon
    set APK_PATH=app\build\outputs\apk\debug\app-debug.apk
)

if errorlevel 1 (
    echo.
    echo [X] Build fehlgeschlagen!
    echo.
    echo Haeufige Fehlerursachen:
    echo   - Java-Version zu alt (braucht JDK 17)
    echo   - Android SDK nicht installiert
    echo   - Gradle-Cache korrupt (Loesung: gradlew.bat clean)
    echo.
    pause
    exit /b 1
)

echo.
echo [OK] Build erfolgreich!

REM ============================================================================
REM Rename APK (optional)
REM ============================================================================

echo.
echo [5/5] APK umbenennen...

if "%BUILD_TYPE%"=="release" (
    set APK_NAME=MyBudgets-%VERSION_CODE%-%VERSION_NAME%.apk
    set APK_DIR=app\build\outputs\apk\release
) else (
    set APK_NAME=MyBudgets-debug-%VERSION_NAME%.apk
    set APK_DIR=app\build\outputs\apk\debug
)

if exist "%APK_DIR%\%APK_NAME%" del "%APK_DIR%\%APK_NAME%"
move "%APK_PATH%" "%APK_DIR%\%APK_NAME%" >nul 2>&1

echo [OK] APK: %APK_DIR%\%APK_NAME%

REM ============================================================================
REM Copy to mama-razzi (optional)
REM ============================================================================

echo.
echo [6/7] APK Distribution...

SET MAMA_RAZZI_DIR=F:\CascadeProjects\mama-razzi\public\apps\mybudgets
SET SECURE_STORAGE_DIR=\\secure-storage\home\Downloads

REM Timestamp for versioned copy
for /f "tokens=2 delims==" %%i in ('wmic os get localdatetime /value') do set datetime=%%i
set TIMESTAMP=!datetime:~0,8!-!datetime:~8,6!

REM 1. Copy to mama-razzi (für Web-Download)
if exist "%MAMA_RAZZI_DIR%" (
    copy "%APK_DIR%\%APK_NAME%" "%MAMA_RAZZI_DIR%\MyBudgets-v%VERSION_NAME%-%VERSION_CODE%-!TIMESTAMP!.apk" >nul 2>&1
    copy "%APK_DIR%\%APK_NAME%" "%MAMA_RAZZI_DIR%\MyBudgets-latest.apk" >nul 2>&1
    echo [OK] APK kopiert nach mama-razzi
) else (
    echo [!] mama-razzi nicht gefunden, uebersprungen
)

REM 2. Copy to secure-storage Downloads (für direkten Handy-Zugriff)
if exist "%SECURE_STORAGE_DIR%" (
    copy "%APK_DIR%\%APK_NAME%" "%SECURE_STORAGE_DIR%\MyBudgets-latest.apk" >nul 2>&1
    echo [OK] APK kopiert nach secure-storage Downloads
) else (
    echo [!] secure-storage nicht gefunden, uebersprungen
)

REM ============================================================================
REM Summary
REM ============================================================================

echo.
echo ========================================================================
echo [OK] Build abgeschlossen!
echo ========================================================================
echo.
echo APK-Datei:
echo   %APK_DIR%\%APK_NAME%
echo.
echo Version:
echo   versionName: %VERSION_NAME%
echo   versionCode: %VERSION_CODE%
echo.
echo Build-Type: %BUILD_TYPE%
echo.
echo Naechste Schritte:
echo.
echo   1. APK auf Device installieren:
echo      adb install -r "%APK_DIR%\%APK_NAME%"
echo.
echo   2. Oder via secure-storage herunterladen:
echo      \\secure-storage\home\Downloads\MyBudgets-latest.apk
echo.
echo   3. Oder via mama-razzi Web-Download:
echo      http://diekunstgalerie.org/apps/mybudgets/MyBudgets-latest.apk
echo.
echo   4. Oder manuell:
echo      - APK auf Handy kopieren
echo      - Installieren
echo.
echo   5. In App testen:
echo      - Konto oeffnen
echo      - Kontoauszug synchronisieren
echo      - Secure Go bestaetigen
echo.
echo ========================================================================

REM Open output directory
echo.
set /p OPEN_DIR="APK-Ordner oeffnen? (j/n): "
if /i "!OPEN_DIR!"=="j" (
    explorer "%APK_DIR%"
)

pause
