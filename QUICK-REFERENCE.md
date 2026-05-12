# BBBank-Sync Quick Reference

## 🚀 Schnellstart (nach Fix-Änderung)

```powershell
# Schnellster Test (10-15 Sekunden)
.\scripts\quick-test-fix.ps1

# Mit Android-Test (60 Sekunden)
.\scripts\quick-test-fix.ps1 -RunLiveTest

# Manuelle Tests
cd scripts\java-sync
..\..\gradlew.bat :scripts:java-sync:jar && java -jar build\libs\java-sync.jar
```

---

## 📂 Wichtige Dateien

| Datei | Zweck |
|-------|-------|
| `scripts/java-sync/src/main/java/de/mybudgets/sync/BbbankSync.java` | Java-Sync (schneller Test) |
| `app/src/main/java/de/mybudgets/app/data/banking/FintsService.kt` | App-Banking-Service |
| `scripts/quick-test-fix.ps1` | Automatisierter Test-Workflow |
| `TESTING-WORKFLOW.md` | Detaillierte Test-Dokumentation |
| `BBBank-Sync-E2E-Test.md` | E2E-Test-Protokoll & Erkenntnisse |
| `scripts/java-sync/config.properties` | Credentials (NICHT committen!) |

---

## 🔧 Aktuelle Fixes (2026-05-08)

### Problem: CAMT-Parser schlägt fehl
```
SAXNotRecognizedException: http://javax.xml.XMLConstants/feature/secure-processing
```

### Lösung: CAMT deaktiviert, MT940 zuerst

**In `BbbankSync.java` (Zeile 140):**
```java
String[] jobTypes = {"KUmsZeitSEPA", "KUmsAll", "KUmsNew"};  // KUmsAllCamt entfernt
```

**In `FintsService.kt` (Zeile 273-283):**
```kotlin
val jobAttempts = if (fromDate != null) listOf(
    JobAttempt("KUmsZeitSEPA", fromDate),  // MT940 zuerst
    JobAttempt("KUmsAll"),
    JobAttempt("KUmsNew"),
) else listOf(
    JobAttempt("KUmsZeitSEPA", Date(0)),
    JobAttempt("KUmsAll"),
    JobAttempt("KUmsNew"),
)
```

---

## 🎯 Test-Checkliste

- [ ] Java-Sync konfiguriert (`scripts/java-sync/config.properties`)
- [ ] CAMT aus Job-Liste entfernt (`BbbankSync.java` + `FintsService.kt`)
- [ ] Java-Sync-Test erfolgreich (`.\scripts\quick-test-fix.ps1`)
- [ ] Optional: Gradle Live-Test erfolgreich
- [ ] App gebaut und auf Device getestet
- [ ] Mindestens 1 Transaktion erfolgreich abgerufen

---

## 🐛 Debugging

### Java-Sync Debug aktivieren
```properties
# scripts/java-sync/config.properties
debug=true
```

### App-Logs exportieren
```
App → Einstellungen → Fehlerprotokoll → Export
```

### HBCI Wire-Level-Logging
```java
// BbbankSync.java (initHbci)
props.setProperty("log.loglevel.default", "4");  // 1-5, 4=Trace
```

---

## 📊 Erfolgs-Kriterien

Ein Test gilt als **bestanden**, wenn:

1. ✅ `Bank-Antwort OK` (kein Timeout)
2. ✅ Mindestens 1 Transaktion geparst
3. ✅ Job-Type ist `KUmsZeitSEPA` oder `KUmsAll` (NICHT `KUmsAllCamt`)
4. ✅ Keine `SAXNotRecognizedException` im Log

---

## 🔗 Python-Referenz

```bash
# Python-Skript funktioniert bereits
python scripts/bbbank-sync-debug.py \
    --iban "DE89..." \
    --user NUTZERKENNUNG \
    --tan-method 900 \
    --server https://fints2.atruvia.de/cgi-bin/hbciservlet \
    --debug
```

**Warum funktioniert es?**
- Verwendet `python-fints` (andere Library)
- Besserer CAMT-Parser (oder nutzt MT940)
- Force `system_id = '0'` (umgeht HKSYN3)

---

## 🚨 Häufige Fehler

### `Fehler: Bank-Antwort nicht OK: ... SAXNotRecognizedException`
→ CAMT-Problem, Fix siehe oben

### `Fehler: Kein Job-Typ konnte initialisiert werden`
→ BIC oder Account-Number fehlt in Passport

### `Timeout nach 420s`
→ Früher durch CAMT, sollte nach Fix behoben sein

### `PIN/Nutzerkennung ungültig`
→ Prüfe `config.properties` oder Eingaben

---

## 💡 Performance-Tipps

1. **Immer Java-Sync zuerst** (10-15s statt 2-3min App-Build)
2. **Debug-Logging nur bei Bedarf** (verlangsamt Tests)
3. **Passport-Datei löschen bei Problemen** (`scripts/java-sync/passports/`)
4. **Days-Back reduzieren für schnellere Tests** (`daysBack=7`)

---

**Happy Testing! 🎉**
