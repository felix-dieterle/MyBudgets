@echo off
REM Multi-Job Dialog Test fuer BBBank
REM Testet ob mehrere KUmsAllCamt-Jobs in EINEM Dialog funktionieren (EINE TAN)

cd /d "%~dp0.."

echo ========================================
echo   Multi-Job Dialog Test
echo ========================================
echo.

REM Check if config exists
if not exist "scripts\java-sync\config.properties" (
    echo ERROR: scripts\java-sync\config.properties nicht gefunden
    echo.
    echo Bitte config.properties erstellen:
    echo   1. Copy scripts\java-sync\config.properties.example
    echo   2. Rename to config.properties  
    echo   3. Fill in iban, userId, pin
    echo.
    echo Optional Multi-Job Config add to config.properties:
    echo   multiJob.maxChunks=3
    echo   multiJob.yearsPerChunk=1
    echo   multiJob.useEnddate=false
    echo.
    pause
    exit /b 1
)

REM Build if needed
echo [1/2] Building...
call gradlew.bat -p scripts\java-sync classes -q
if errorlevel 1 (
    echo ERROR: Build failed
    pause
    exit /b 1
)

echo [2/2] Running Multi-Job Test...
echo.
echo ========================================
echo.

REM Run using Gradle task
call gradlew.bat -p scripts\java-sync runMultiJobTest -q
set EXITCODE=%ERRORLEVEL%

echo.
echo ========================================
if %EXITCODE%==0 (
    echo   Test Complete - SUCCESS
) else (
    echo   Test Complete - FAILED exit code %EXITCODE%
)
echo ========================================
pause


