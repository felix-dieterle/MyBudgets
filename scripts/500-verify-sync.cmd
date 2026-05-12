@echo off
REM ============================================================================
REM Code-Sync Verification
REM ============================================================================
REM
REM Prueft ob Java-Sync und App synchron sind:
REM   - HBCI Version (220 mit Fallback auf 300)
REM   - Job-Liste (KUmsZeitSEPA, KUmsAll, KUmsNew ohne CAMT)
REM   - Java-Sync Test (qt.cmd)
REM
REM Usage:
REM   500-verify-sync.cmd
REM
REM Exit Codes:
REM   0 = All checks passed
REM   1 = Checks failed
REM
REM ============================================================================

powershell -ExecutionPolicy Bypass -File "%~dp0verify-sync.ps1"
exit /b %ERRORLEVEL%
