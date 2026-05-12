# MyBudgets Scripts - Schnellreferenz

## Häufigste Commands (mit Nummern + Aliase)

```cmd
# Setup (einmalig)
scripts\001-setup.cmd          # oder: scripts\setup.cmd

# Schneller Test (10-15s)
scripts\100-quick-test.cmd     # oder: scripts\qt.cmd

# Code Verifikation
scripts\500-verify-sync.cmd    # Prüft App = Java-Sync

# App bauen
scripts\200-build-debug.cmd    # oder: scripts\build.cmd

# Kompletter Workflow
scripts\300-workflow.cmd       # oder: scripts\workflow.cmd
```

## Alle verfügbaren Scripts

### 000-099: Setup & Konfiguration
- `001-setup.cmd` - Erstkonfiguration (Alias: `setup.cmd`)

### 100-199: Test-Scripts
- `100-quick-test.cmd` - Java-Sync Test (Alias: `qt.cmd`) ⭐

### 200-299: Build-Scripts
- `200-build-debug.cmd` - APK bauen (Alias: `build.cmd`)
- `202-build-apk.cmd` - Low-level Build (intern)

### 300-399: Workflow-Scripts
- `300-workflow.cmd` - Test → Build → Install (Alias: `workflow.cmd`)

### 500-599: Development Tools
- `500-verify-sync.cmd` - Code-Sync Check
- `501-copy-passport-from-android.cmd` - Passport von Device

### 900-999: Legacy (nicht verwenden)
- `900-start-sync.cmd` - Alte Version
- `901-start-java-sync.cmd` - Alte Version
- `902-run-kotlin-sync.cmd` - Kotlin (deprecated)
- `903-quick-test-fix.cmd` - Alte Version

## Dokumentation

- **Nummerierungs-Schema:** `scripts/CMD-NAMING-CONVENTION.md`
- **Scripts-Übersicht:** `scripts/README.md`
- **Projekt-Regeln:** `AGENTS.md`
- **Troubleshooting:** `BBBank-Sync-Troubleshooting.md`
- **Code-Verifikation:** `CODE-SYNC-VERIFICATION.md`
