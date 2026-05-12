@echo off
REM Launcher für BBBank Java-Sync

SET SCRIPT_DIR=%~dp0
SET REPO_ROOT=%SCRIPT_DIR%..\
cd /d "%REPO_ROOT%"

cd scripts\java-sync

REM Prüfe ob config.properties existiert
if not exist config.properties (
    echo config.properties nicht gefunden!
    echo Kopiere config.properties.example nach config.properties und fülle die Werte aus.
    pause
    exit /b 2
)

REM Baue das JAR immer neu
echo Baue Java-Sync...
cd /d "%REPO_ROOT%"
call gradlew.bat :scripts:java-sync:jar
if errorlevel 1 (
    echo Build fehlgeschlagen
    pause
    exit /b 1
)
cd /d "%REPO_ROOT%\scripts\java-sync"

REM Starte das Programm
echo Starte BBBank Java-Sync...
java -jar build\libs\java-sync.jar

pause
