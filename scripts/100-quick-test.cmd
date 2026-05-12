@echo off
REM ============================================================================
REM Quick Test - Wrapper für 903-quick-test-fix.cmd
REM ============================================================================
REM
REM Usage:
REM   100-quick-test              - Java-Sync Test (schnellste Variante)
REM   100-quick-test --with-live  - Mit Gradle Live-Test
REM   100-quick-test -l           - Mit Gradle Live-Test (Kurzform)
REM
REM ============================================================================

SET SCRIPT_DIR=%~dp0

REM Forward alle Argumente an quick-test-fix.cmd
call "%SCRIPT_DIR%903-quick-test-fix.cmd" %*
