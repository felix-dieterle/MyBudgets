# Test-Scripts Inventar

**Zweck:** Übersicht aller Test-Scripts mit Status, Zweck und Relevanz.  
**Erstellt:** 2026-05-15  
**Aktualisiert:** Automatisch bei Änderungen

---

## ✅ Aktiv & Empfohlen

### Java-Sync Reference Implementation

**Zweck:** 1:1 Referenz für App-Code, schnelle Iterationen ohne App-Build.

| Script | Typ | Zweck | Erfolg | Letzter Test |
|--------|-----|-------|--------|--------------|
| `100-quick-test.cmd` (`qt.cmd`) | CMD | Java-Sync Schnelltest | ❌ 2026-05-15 (Passport expired + Netzwerk?) | 2026-05-15 |
| `scripts/java-sync/src/BbbankSync.java` | Java | Reference Implementation (hbci4java 3.1.88) | ❌ 2026-05-15 | 2026-05-15 |

**Konfiguration:**
- `scripts/java-sync/config.properties` (lokale Credentials)
- Passport: `scripts/java-sync/passports/passport_66090800.dat` (GELÖSCHT 2026-05-15 - expired)

**Build:**
```cmd
cd F:\CascadeProjects\MyBudgets
gradlew.bat :scripts:java-sync:jar
```

---

### Python Alternative (fints Library)

**Zweck:** Alternative Test-Implementation ohne Java, für Debugging von Protokoll-Problemen.

| Script | Typ | Zweck | Erfolg | Letzter Test |
|--------|-----|-------|--------|--------------|
| `new_bbbank-sync-debug.py` | Python | FinTS-Test mit fints library (neueste Version, UTF-8 Fix) | ⏳ Nicht getestet | - |
| `bbbank-sync-debug.py` | Python | Alte Version (ohne UTF-8 Handling) | ❌ Deprecated | - |

**Dependencies:**
```bash
pip install fints
```

**Usage:**
```bash
# Neueste Version verwenden (empfohlen)
python scripts/new_bbbank-sync-debug.py --iban "..." --user "..." --server "https://fints2.atruvia.de/cgi-bin/hbciservlet" --tan-method 900 --debug
```

**Unterschiede neue Version:**
- UTF-8 Console-Output Fix (Windows Encoding-Probleme)
- Besseres Logging-Setup
- Interaktive Eingabe wenn Parameter fehlen

**Status:** Noch nicht validiert gegen BBBank. Kann helfen herauszufinden ob Problem bei hbci4java oder Bank liegt.

**TODO nach Netzwerk-Fix:** 1x erfolgreich durchlaufen und als Alternative validieren.

---

## 📦 Build & Deploy

| Script | Typ | Zweck | Erfolg | Letzter Test |
|--------|-----|-------|--------|--------------|
| `200-build-debug.cmd` (`build.cmd`) | CMD | APK bauen + Deploy zu mama-razzi/NAS | ✅ | 2026-05-15 (v1.0.43) |
| `202-build-apk.cmd` | CMD | Low-level APK Build (von anderen genutzt) | ✅ | 2026-05-15 |
| `300-workflow.cmd` (`workflow.cmd`) | CMD | Kompletter Workflow: Test → Build → Install | ⏳ | - |

---

## 🔍 Verification & Debugging

| Script | Typ | Zweck | Erfolg | Letzter Test |
|--------|-----|-------|--------|--------------|
| `500-verify-sync.cmd` | CMD | Prüft ob App-Code = Java-Sync Code | ✅ | 2026-05-15 |
| `501-copy-passport-from-android.cmd` | CMD | Passport vom Device für Debugging | ⏳ | - |
| `redact-log.ps1` | PS1 | Entfernt Credentials aus Logs | ⏳ | - |

---

## 🗄️ Legacy / Deprecated (900+)

**Status:** NICHT MEHR VERWENDEN - nur für Historie behalten.

| Script | Typ | Zweck | Status | Grund |
|--------|-----|-------|--------|-------|
| `900-start-sync.cmd` | CMD | Alte Version von java-sync | ❌ Deprecated | Ersetzt durch 100-quick-test.cmd |
| `901-start-java-sync.cmd` | CMD | Alte Version | ❌ Deprecated | Ersetzt durch 100-quick-test.cmd |
| `902-run-kotlin-sync.cmd` | CMD | Kotlin-basierter Ansatz | ❌ Deprecated | Nicht erfolgreich, zurück zu Java |
| `903-quick-test-fix.cmd` | CMD | Alte Version von quick-test | ❌ Deprecated | Ersetzt durch 100-quick-test.cmd |

---

## 📝 PowerShell Utilities (Standalone)

**WICHTIG:** Diese Scripts nutzen Python (bbbank-sync-debug.py) statt Java-Sync.

| Script | Typ | Zweck | Status |
|--------|-----|-------|--------|
| `start-sync-runner.ps1` | PS1 | Wrapper für run-bbbank-sync.ps1 (mit Default-Flags) | ❌ Deprecated (nutzt Python statt Java) |
| `start-sync.ps1` | PS1 | Starter für run-bbbank-sync.ps1 (prüft Credentials) | ❌ Deprecated (nutzt Python statt Java) |
| `run-bbbank-sync.ps1` | PS1 | Lädt Credentials + startet bbbank-sync-debug.py | ❌ Deprecated (nutzt Python statt Java) |
| `quick-test-fix.ps1` | PS1 | Workflow: Java-Sync + Optional Gradle-Live-Test | ❌ Deprecated (ersetzt durch 100-quick-test.cmd) |

**Grund Deprecated:**
- Diese Scripts nutzen Python-Implementation (bbbank-sync-debug.py)
- Reference-Implementation ist Java-Sync (BbbankSync.java)
- Empfohlen: 100-quick-test.cmd nutzen

**TODO:** Archivieren oder für Python-Tests reaktivieren (nach Validierung).

---

## 🧪 Test-Strategie nach Erfolg/Fehlschlag

### Erfolgreicher Sync (2026-05-12, logs-app16.txt)

**Konfiguration:**
- App Version: v1.0.42
- HBCI-Version: "300" (FinTS 3.0) für BBBank
- Job: **KUmsAllCamt** (CAMT/FinTS 3.0)
- Parser: **CustomCamtParser** (eigene Implementation)
- Passport: 4 Tage alt (erstellt ~2026-05-08)
- Ergebnis: ✅ 150 Transaktionen erfolgreich abgerufen

**Lessons Learned:**
- CustomCamtParser funktioniert trotz SAX-Exception
- KUmsAllCamt (CAMT) ist NICHT kaputt
- Passport-Alter wichtig (max 7 Tage?)

### Fehlgeschlagener Sync (2026-05-15, logs-app15.txt)

**Konfiguration:**
- App Version: v1.0.43
- HBCI-Version: "300" (FinTS 3.0) für BBBank
- Jobs: KUmsZeitSEPA, KUmsAll, KUmsNew (CAMT entfernt!)
- Parser: -
- Passport: 7 Tage alt (expired) → GELÖSCHT für Neuinitialisierung
- Ergebnis: ❌ Alle Jobs fehlgeschlagen (JobNotSupportedException)

**Java-Sync auch defekt:**
- HBCI 2.2 ("220"): Signing Error "secfunc 999 ungültig"
- HBCI 3.0 ("300"): FileNotFoundException (Netzwerk?)
- JVM: "Auslagerungsdatei zu klein"

**Root Cause Hypothese:**
1. Passport expired (7 Tage alt)
2. Neuinitialisierung schlägt fehl (Netzwerk/Bank-Problem?)
3. Möglicherweise temporär

---

## 🎯 Nächste Schritte (Stand 2026-05-15)

### 1. Test-Infrastruktur aufräumen ✅

**DONE:** Dieses Dokument erstellt.

**TODO:**
- [ ] PowerShell-Scripts durchgehen: Zweck dokumentieren oder löschen
- [ ] Python-Scripts testen (1x erfolgreich durchlaufen)
- [ ] Legacy-Scripts (900+) archivieren oder löschen

### 2. Hybrider Ansatz für v1.0.44

**Strategie (User-Vorschlag):**

1. **Passport-Initialisierung:**
   - Erstmal nur Passport-Datei erzeugen (ohne Transaktionen)
   - Separat testen ob Verbindung steht

2. **Custom Parser verwenden:**
   - KUmsAllCamt RE-AKTIVIEREN (funktionierte 2026-05-12)
   - CustomCamtParser nutzen (bewährte Implementation)

3. **Behutsam vorgehen:**
   - Jeden Schritt dokumentieren in BBBank-Sync-E2E-Test.md
   - Fehlschläge protokollieren
   - Nicht raten, sondern testen

**Test-Plan:**
```
Phase 1: Netzwerk/Bank-Problem abwarten
→ qt.cmd erneut testen (1-2 Tage später?)
→ Falls erfolgreich: Weiter mit Phase 2

Phase 2: Python-Script validieren
→ bbbank-sync-debug.py testen
→ Zeigt ob Problem bei hbci4java oder Bank

Phase 3: Passport-Only Test
→ Java-Sync anpassen: Nur Session aufbauen, keine Jobs
→ Prüfen ob Passport erstellt wird

Phase 4: App v1.0.44 vorbereiten
→ KUmsAllCamt RE-AKTIVIEREN
→ CustomCamtParser behalten
→ Vorsichtig deployen & testen
```

---

## 📚 Weitere Dokumentation

- **Scripts Übersicht:** [scripts/README.md](scripts/README.md)
- **E2E-Test-Protokoll:** [BBBank-Sync-E2E-Test.md](BBBank-Sync-E2E-Test.md)
- **Troubleshooting:** [BBBank-Sync-Troubleshooting.md](BBBank-Sync-Troubleshooting.md)
- **Workflow:** [TESTING-WORKFLOW.md](TESTING-WORKFLOW.md)

---

**Änderungshistorie:**

| Datum | Änderung |
|-------|----------|
| 2026-05-15 | Initial erstellt (nach User-Feedback: Test-Scripts aufräumen) |
