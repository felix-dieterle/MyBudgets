@echo off
REM Kopiert die Passport-Datei vom Android-Gerät

setlocal

REM Prüfe, ob adb verfügbar ist
adb version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Fehler: adb nicht gefunden. Stelle sicher, dass Android Platform Tools installiert sind und im PATH liegen.
    pause
    exit /b 1
)

REM Liste alle Passport-Dateien auf dem Android-Gerät
echo Suche nach Passport-Dateien auf dem Android-Gerät...
adb shell ls /data/data/de.mybudgets.app/files/hbci_passports/ 2>nul

if %ERRORLEVEL% NEQ 0 (
    echo Keine Passport-Dateien gefunden oder Zugriff verweigert.
    echo Stelle sicher, dass:
    echo 1. Das Android-Gerät USB verbunden ist
    echo 2. USB-Debugging aktiviert ist
    echo 3. Die App mindestens einmal ausgefuhrt wurde
    echo 4. Root-Zugriff verfugbar ist (fcr /data/data)
    pause
    exit /b 1
)

echo.
echo Bitte gib den Dateinamen der zu kopierenden Passport-Datei ein (z.B. passport_66090800_1.dat):
set /p PASSPORT_FILE=Dateiname:

if "%PASSPORT_FILE%"=="" (
    echo Kein Dateiname angegeben.
    pause
    exit /b 1
)

REM BLZ aus Dateinamen extrahieren
for /f "tokens=2 delims=_." %%a in ("%PASSPORT_FILE%") do set BLZ=%%a

REM Passport-Datei auf dem Android-Gerät
set ANDROID_PATH=/data/data/de.mybudgets.app/files/hbci_passports/%PASSPORT_FILE%

REM Lokale Passport-Datei
set LOCAL_DIR=scripts\java-sync\passports
set LOCAL_FILE=%LOCAL_DIR%\passport_%BLZ%.dat

echo Kopiere Passport-Datei von Android-Gerät...
echo Android-Pfad: %ANDROID_PATH%
echo Lokaler Pfad: %LOCAL_FILE%

REM Verzeichnis erstellen
if not exist "%LOCAL_DIR%" mkdir "%LOCAL_DIR%"

REM Kopieren mit adb
adb pull "%ANDROID_PATH%" "%LOCAL_FILE%"

if %ERRORLEVEL% EQU 0 (
    echo Passport-Datei erfolgreich kopiert nach: %LOCAL_FILE%
    echo Du kannst jetzt das Java-Sync-Skript ausfuhren.
) else (
    echo Fehler beim Kopieren der Passport-Datei
    pause
    exit /b 1
)

pause
