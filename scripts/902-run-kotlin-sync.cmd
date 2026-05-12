@echo off
REM Führt das Kotlin-Sync-Skript aus (mit Gradle)

setlocal

REM Wechsle zum Projekt-Root
cd /d "%~dp0.."

REM Prüfe, ob gradlew verfügbar ist
if exist "gradlew.bat" (
    echo Führe Kotlin-Sync-Skript aus mit Gradle...
    call gradlew.bat :scripts:java-sync:run
) else (
    echo Fehler: gradlew.bat nicht gefunden.
    echo Stelle sicher, dass du im Projekt-Root bist und Gradle initialisiert ist.
    pause
    exit /b 1
)

pause
