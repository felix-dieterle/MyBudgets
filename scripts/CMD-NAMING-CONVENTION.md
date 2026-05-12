# CMD-Datei Nummerierungs-Konvention

**Zweck:** Eindeutige Identifikation und chronologische Historie von Scripts

## Format

```
NNN-<beschreibung>.cmd
```

- **NNN:** Dreistellige Nummer (001-999)
- **beschreibung:** Kebab-case, beschreibt Funktion

## Nummerierungs-Schema

### 000-099: Setup & Konfiguration
- `001-setup.cmd` - Erstkonfiguration (config.properties erstellen)
- `002-install-dependencies.cmd` - Dependencies installieren

### 100-199: Test-Scripts (Java-Sync)
- `100-quick-test.cmd` (alias: `qt.cmd`) - Schneller Java-Sync Test
- `101-java-sync-debug.cmd` - Java-Sync mit Debug-Logs
- `102-test-with-live-gradle.cmd` - Gradle-basierter Test

### 200-299: Build-Scripts
- `200-build-debug.cmd` (alias: `build.cmd`) - APK bauen (Debug)
- `201-build-release.cmd` - APK bauen (Release)
- `202-build-apk.cmd` - Low-level Build-Script (von anderen genutzt)

### 300-399: Workflow-Scripts (Kombinationen)
- `300-workflow.cmd` - Kompletter Workflow (Test → Build → Install)
- `301-test-and-build.cmd` - Nur Test + Build

### 400-499: Deployment & Distribution
- `400-install-apk.cmd` - APK auf Device installieren
- `401-copy-to-mamarazzi.cmd` - APK zu mama-razzi kopieren

### 500-599: Development Tools
- `500-verify-sync.cmd` - Code-Sync Verification
- `501-copy-passport-from-android.cmd` - Passport von Device holen

### 900-999: Legacy/Deprecated (behalten für Historie)
- `900-start-sync.cmd` - Alte Version von java-sync
- `901-start-java-sync.cmd` - Alte Version
- `902-run-kotlin-sync.cmd` - Kotlin-basierter Ansatz (deprecated)
- `903-quick-test-fix.cmd` - Alte Version von quick-test

## Aliase (Kurznamen)

Für häufig genutzte Scripts werden Aliase erstellt:

```
qt.cmd          → 100-quick-test.cmd
build.cmd       → 200-build-debug.cmd
workflow.cmd    → 300-workflow.cmd
setup.cmd       → 001-setup.cmd
```

**Aliase sind kleine Wrapper:**
```batch
@echo off
call "%~dp0200-build-debug.cmd" %*
```

## Migration Plan

1. **Neue Scripts** sofort mit Nummern erstellen
2. **Bestehende Scripts** nach und nach umbenennen
3. **Aliase** für Rückwärtskompatibilität behalten
4. **Legacy** in 900er-Bereich verschieben (nicht löschen!)

## Vorteile

✅ **Eindeutig:** Keine Namenskonflikte mehr  
✅ **Chronologie:** Entwicklungsgeschichte sichtbar  
✅ **Kategorisierung:** Sofort erkennbar was Script macht  
✅ **Versionierung:** Alte Versionen bleiben als 900er erhalten  
✅ **Dokumentation:** Nummer referenziert in AGENTS.md bleibt stabil

## Beispiel

**Alt:**
```
scripts/
├── qt.cmd
├── build.cmd
├── setup.cmd
└── quick-test-fix.cmd  (alt, aber unklar)
```

**Neu:**
```
scripts/
├── 001-setup.cmd
├── 100-quick-test.cmd
├── 200-build-debug.cmd
├── 300-workflow.cmd
├── 500-verify-sync.cmd
├── 903-quick-test-fix.cmd  (deprecated, aber Historie)
├── qt.cmd          (alias → 100-quick-test.cmd)
├── build.cmd       (alias → 200-build-debug.cmd)
└── setup.cmd       (alias → 001-setup.cmd)
```

---

**Zuletzt aktualisiert:** 2026-05-08
