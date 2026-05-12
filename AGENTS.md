# MyBudgets - Projekt-spezifische Regeln

**Projekt:** MyBudgets - Android Budget-Tracking App mit FinTS/HBCI Banking-Integration  
**Repo:** https://github.com/felix-dieterle/MyBudgets

## AI Communication Style

**CRITICAL - Token Optimization:**
- **Answer in patches** - Nie die ganze Datei, nur die Änderungen
- **Never repeat unchanged code** - Kein Kontext außer nötig für Verständnis
- **Keep responses under 80 lines** - Knapp halten, bei großen Tasks aufteilen
- **No explanations** - Code spricht für sich, keine Kommentare warum/wieso
- Nur bei Problemen/Fragen ausführlich werden

## Tech Stack

- **Platform:** Android (Kotlin)
- **Banking:** FinTS/HBCI (hbci4java 3.1.88)
- **Database:** Room
- **DI:** Hilt
- **Build:** Gradle 8.7, AGP 8.3.0
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)

## Projekt-Struktur

```
MyBudgets/
├── app/                        # Android App
│   └── src/main/java/de/mybudgets/app/
│       ├── data/banking/       # FinTS/HBCI Integration
│       ├── data/db/            # Room Database
│       └── viewmodel/          # ViewModels
├── scripts/                    # Test & Build Scripts
│   ├── java-sync/              # Java-Sync Referenz-Implementation
│   ├── build.cmd               # APK Build-Script
│   ├── qt.cmd                  # Quick-Test (Java-Sync)
│   └── workflow.cmd            # Kompletter Test→Build→Install
└── keystore/                   # Debug Keystore
```

## Wichtige Dokumentation

### Banking & FinTS

**⚠️ KRITISCH - Bei BBBank-Sync-Problemen zuerst lesen:**
- **[BBBank-Sync-Troubleshooting.md](./BBBank-Sync-Troubleshooting.md)** - Bekannte Probleme, Lösungen, Lessons Learned
- **[BBBank-Sync-E2E-Test.md](./BBBank-Sync-E2E-Test.md)** - Vollständiges Test-Protokoll & Analyse

**Test & Development:**
- **[TESTING-WORKFLOW.md](./TESTING-WORKFLOW.md)** - Test-Workflow ohne App-Builds (Java-Sync)
- **[QUICK-REFERENCE.md](./QUICK-REFERENCE.md)** - Cheatsheet für schnelle Befehle
- **[scripts/README.md](./scripts/README.md)** - Script-Übersicht

### BBBank-Spezifische Regeln

**HBCI-Version:**
- BBBank benötigt **HBCI 2.2 ("220")** für MT940-Jobs
- Fallback auf FinTS 3.0 ("300") für andere Banken
- **NIEMALS** nur Version 300 verwenden - Java-Sync ist Referenz!

**Job-Typen:**
- ✅ **Funktioniert:** `KUmsZeitSEPA`, `KUmsAll`, `KUmsNew` (mit HBCI 2.2)
- ❌ **Deaktiviert:** `KUmsAllCamt` (CAMT) - SAX-Parser schlägt fehl trotz Workaround

**Referenz-Implementation:**
- `scripts/java-sync/BbbankSync.java` ist die funktionierende Referenz
- **Regel:** App muss Code 1:1 mit Java-Sync synchron halten (HBCI-Version, Jobs, Parameter)

## Build & Deployment

### Lokal bauen

```bash
# APK bauen (oder Alias: scripts\build.cmd)
scripts\200-build-debug.cmd

# Schneller Test ohne App-Build (oder Alias: scripts\qt.cmd)
scripts\100-quick-test.cmd

# Kompletter Workflow: Test → Build → Install
scripts\300-workflow.cmd

# Code-Sync Verifikation (prüft ob App = Java-Sync)
scripts\500-verify-sync.cmd
```

### APK Distribution

**Automatisch beim Build:**
- **Lokal (mama-razzi):** `F:\CascadeProjects\mama-razzi\public\apps\mybudgets\`
- **NAS (secure-storage):** `\\secure-storage\home\Downloads\MyBudgets-latest.apk`

**Download-URLs:**
- **NAS (direkt):** `\\secure-storage\home\Downloads\MyBudgets-latest.apk` (vom Handy via File-Manager)
- **Online (nach FTP-Sync):** http://diekunstgalerie.org/apps/mybudgets/MyBudgets-latest.apk

**FTP-Upload (optional):**
```bash
cd F:\CascadeProjects\mama-razzi
.\scripts\sync-apps-to-ftp.ps1
```

### Versioning

- **versionName:** `app/build.gradle.kts` (Zeile 24-25)
- **versionCode:** Automatisch via Git commit count (`git rev-list --count HEAD`)

## Entwicklungs-Workflow

### Bei Banking-Code-Änderungen

1. **IMMER zuerst Java-Sync ändern** (`scripts/java-sync/BbbankSync.java`)
2. **Java-Sync testen** (`scripts\100-quick-test.cmd` oder Alias `qt.cmd`) - dauert nur 10-15s
3. **Änderungen in App übertragen** (`app/src/main/java/de/mybudgets/app/data/banking/FintsService.kt`)
4. **Verifikation** (`scripts\500-verify-sync.cmd`) - prüft ob App = Java-Sync
5. **App bauen & testen** (`scripts\200-build-debug.cmd` oder Alias `build.cmd`)

**Vorteil:** 80-90% Zeitersparnis durch schnelle Java-Sync-Iterationen statt App-Builds

### Test-Credentials

- **Location:** `scripts/java-sync/config.properties` (lokal, nicht im Git)
- **Template:** `scripts/java-sync/config.properties.example`
- **Setup:** `scripts\001-setup.cmd` (interaktiver Config-Wizard)

## Bekannte Probleme & Fixes

### BBBank-Sync schlägt fehl

**Problem:** "Diese Bank unterstützt keinen HBCI-Kontoauszug-Abruf"

**Lösung:** Siehe **[BBBank-Sync-Troubleshooting.md](./BBBank-Sync-Troubleshooting.md)**

**Quick-Check:**
1. Java-Sync funktioniert? (`scripts\100-quick-test.cmd` oder `qt.cmd`)
2. HBCI-Version in App = HBCI 2.2 mit Fallback auf 3.0? ✅
3. Job-Liste in App = `KUmsZeitSEPA → KUmsAll → KUmsNew`? ✅
4. CAMT deaktiviert? ✅
5. Verifikation: `scripts\500-verify-sync.cmd` ✅

### Build-Probleme

**SDK location not found:**
```
FAILURE: SDK location not found
```

**Lösung:** `local.properties` fehlt - wird automatisch erstellt bei erstem Build

**Gradle Daemon Probleme:**
```bash
# Gradle Cache cleanen
.\gradlew.bat clean
```

## Security

### Credentials

- **App:** User-Eingabe, gespeichert in App-internem Storage (verschlüsselt)
- **Java-Sync:** `scripts/java-sync/config.properties` (lokal, `.gitignore`)
- **NIEMALS** echte Credentials ins Git committen!

### Keystore

- **Debug:** `keystore/debug.keystore` (im Git, Passwort: `mybudgets`)
- **Release:** Separater Keystore (NICHT im Git!)

## GitHub Workflows

- `.github/workflows/build.yml` - CI/CD Pipeline
- Baut APK bei jedem Push
- Lokal: `scripts\build.cmd` nutzt gleiche Build-Befehle wie CI

## Kontakt & Support

- **Repo:** https://github.com/felix-dieterle/MyBudgets
- **Issues:** GitHub Issues für Bug-Reports
- **Entwickler:** Felix Dieterle

---

**Zuletzt aktualisiert:** 2026-05-08
