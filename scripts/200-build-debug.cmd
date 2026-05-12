@echo off
REM ============================================================================
REM Build - Kurz-Alias für build-apk.cmd
REM ============================================================================
REM
REM Usage:
REM   build           - Debug-Build (schneller)
REM   build release   - Release-Build (optimiert)
REM   build release 123 - Release-Build mit versionCode=123
REM
REM ============================================================================

SET SCRIPT_DIR=%~dp0
call "%SCRIPT_DIR%build-apk.cmd" %*
