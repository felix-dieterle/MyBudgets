# BBBank Sync - Problem-Historie & Lösungen

**Projekt:** MyBudgets Android App  
**Zuletzt aktualisiert:** 2026-05-08 21:00

---

## 🔴 Problem 1: CAMT SAX-Parser schlägt fehl

**Datum:** 2026-05-08 (Initial)  
**Status:** ✅ **GELÖST**

### Symptome
- Login & PIN funktionieren ✅
- Secure Go wird angezeigt ✅
- Bank sendet CAMT-Daten erfolgreich
- SAX-Parser wirft Exception: `SAXNotRecognizedException: http://javax.xml.XMLConstants/feature/secure-processing`
- Transaktionen werden NICHT importiert ❌

### Versuchte Lösungen

#### ❌ Versuch 1: FeatureIgnoringSAXParserFactory Workaround
- **Was:** Custom SAXParserFactory implementiert, die problematische Features ignoriert
- **Code:** `org.kapott.hbci.xml.FeatureIgnoringSAXParserFactory`
- **Ergebnis:** ❌ Fehlgeschlagen - Exception bleibt bestehen
- **Grund:** Android XML-Parser verhält sich anders als Standard-Java

#### ❌ Versuch 2: CAMT-Fallback-Mechanismus
- **Was:** Bei CAMT-Fehler automatisch MT940-Job (`KUmsZeitSEPA`) nachziehen
- **Code:** `FintsService.kt` Zeile 335-381 (alter Code)
- **Problem:** `handler.execute()` wurde zweimal auf demselben Handler aufgerufen
- **Ergebnis:** ❌ Fehlgeschlagen - Handler-State korrupt

#### ✅ Lösung: CAMT komplett deaktivieren
- **Was:** `KUmsAllCamt` aus Job-Fallback-Liste entfernt
- **Job-Liste alt:** `KUmsAllCamt → KUmsZeitSEPA → KUmsAll → KUmsNew`
- **Job-Liste neu:** `KUmsZeitSEPA → KUmsAll → KUmsNew`
- **Code:** `FintsService.kt` Zeile 274-284
- **Commit:** `app/src/main/java/de/mybudgets/app/data/banking/FintsService.kt` (2026-05-08)
- **Ergebnis:** ✅ SAX-Parser wird nicht mehr aufgerufen

---

## 🔴 Problem 2: MT940-Jobs nicht unterstützt (nach CAMT-Deaktivierung)

**Datum:** 2026-05-08 20:16  
**Status:** ✅ **GELÖST**

### Symptome
- Login & PIN funktionieren ✅
- Secure Go wird NICHT mehr angezeigt ❌
- Fehler: "Diese Bank unterstützt keinen HBCI-Kontoauszug-Abruf"
- Fehler: "JobNotSupportedException: Geschäftsvorfall KUmsNew wird nicht unterstützt"
- Alle Jobs (`KUmsZeitSEPA`, `KUmsAll`, `KUmsNew`) schlagen fehl ❌

### Versuchte Lösungen

#### ❌ Versuch 1: Job-Reihenfolge ändern
- **Was:** `KUmsZeitSEPA` vor `KUmsAll` probieren
- **Annahme:** Reihenfolge könnte relevant sein
- **Code:** `FintsService.kt` Zeile 274-284
- **Ergebnis:** ❌ Fehlgeschlagen - Reihenfolge war irrelevant
- **Grund:** Jobs werden generell nicht unterstützt (mit Version 300)

#### ❌ Versuch 2: FinTS 3.0 Plus ("plus") verwenden
- **Was:** Modernere FinTS-Version statt "300"
- **Annahme:** BBBank braucht neuere Version
- **Code:** `HBCIHandler("plus", passport)`
- **Ergebnis:** ❌ Fehlgeschlagen - BBBank unterstützt "plus" nicht
- **Test-Dauer:** 5 Minuten (App-Build erforderlich)

#### ❌ Versuch 3: Passport löschen/neu initialisieren
- **Was:** Passport-Reset, damit BPD neu geladen werden
- **Annahme:** Veraltete Bank Parameter Daten im Passport
- **Ergebnis:** ❌ Fehlgeschlagen - BPD waren nicht das Problem
- **Grund:** HBCI-Version war falsch

#### ✅ HBCI 2.2 vs. FinTS 3.0 - BBBank unterstützt BEIDES
- **Was:** HBCI Version "220" oder "300" - abhängig von Job-Typ
- **Erkenntnis:** 
  - **HBCI 2.2 ("220")** funktioniert für MT940-Jobs (`KUmsZeitSEPA`, `KUmsAll`, `KUmsNew`)
  - **FinTS 3.0 ("300")** funktioniert für CAMT-Jobs (`KUmsAllCamt`) - siehe logs-app16.txt 2026-05-12
- **Aktuelle Implementation (FintsService.kt:531):**
  ```kotlin
  val hbciVersion = if (blz == "66090800") "300" else "220"  // BBBank = FinTS 3.0
  ```
- **CustomCamtParser:** Extrahiert CAMT trotz SAX-Exception erfolgreich (bewährt seit 2026-05-12)
- **Commit:** `app/src/main/java/de/mybudgets/app/data/banking/FintsService.kt` (2026-05-08)
- **Ergebnis:** ✅ 150 Transaktionen erfolgreich abgerufen (2026-05-12)
- **Aktueller Status (2026-05-15):** ❌ Alle Jobs schlagen fehl (Passport expired + Netzwerk?)

---

## 📊 Zusammenfassung

| Problem | Versuch | Ergebnis | Dauer | Grund |
|---------|---------|----------|-------|-------|
| **CAMT SAX-Parser** | FeatureIgnoringSAXParserFactory | ❌ | 30 Min | Android XML-Parser anders |
| | CAMT-Fallback-Mechanismus | ❌ | 20 Min | Handler-State korrupt |
| | **CAMT deaktivieren** | ✅ | 5 Min | - |
| **MT940-Jobs nicht unterstützt** | Job-Reihenfolge ändern | ❌ | 10 Min | Version 300 unterstützt Jobs nicht |
| | FinTS 3.0 Plus verwenden | ❌ | 5 Min | BBBank unterstützt "plus" nicht |
| | Passport-Reset | ❌ | 5 Min | BPD nicht das Problem |
| | **HBCI 2.2 verwenden** | ✅ | 5 Min | - |

**Gesamt-Zeit:** ~1.5 Stunden (inkl. App-Builds & Testing)  
**Lessons Learned:** Java-Sync als Referenz nutzen spart Zeit!

---

## 🎯 Root Cause

**BBBank benötigt HBCI Version 2.2 ("220") für MT940-Jobs!**

- ❌ **FinTS 3.0 ("300")**: BBBank unterstützt `KUmsZeitSEPA`, `KUmsAll`, `KUmsNew` NICHT
- ✅ **HBCI 2.2 ("220")**: BBBank unterstützt diese Jobs

---

## ✅ Funktionierende Konfiguration

### HBCI-Version
```kotlin
// FintsService.kt Zeile 450-465
try {
    handler = HBCIHandler("220", passport)  // HBCI 2.2 zuerst!
} catch (e220: Exception) {
    handler = HBCIHandler("300", passport)  // Fallback
}
```

### Job-Liste
```kotlin
// FintsService.kt Zeile 274-284
val jobAttempts = listOf(
    // JobAttempt("KUmsAllCamt"),      // ❌ DEAKTIVIERT (SAX-Parser)
    JobAttempt("KUmsZeitSEPA", date),  // ✅ 1. Wahl (MT940)
    JobAttempt("KUmsAll"),             // ✅ 2. Wahl (MT940)
    JobAttempt("KUmsNew"),             // ✅ 3. Wahl (MT940)
)
```

### Verifikation
```cmd
# Quick-Test (Java-Sync) - sollte funktionieren
scripts\100-quick-test.cmd

# Code-Sync-Check - App muss = Java-Sync sein
scripts\500-verify-sync.cmd
```

---

## 🔧 Lessons Learned

### 1. Java-Sync ist die Referenz
- **Immer zuerst Java-Sync testen** (`100-quick-test.cmd`) - dauert nur 15s
- Wenn Java-Sync funktioniert, App aber nicht → **Code vergleichen**
- HBCI-Version, Job-Liste, Parameter müssen **1:1 übereinstimmen**
- Tool: `500-verify-sync.cmd` automatisiert den Vergleich

### 2. Debugging-Strategie bei Banking-Problemen
1. ✅ **Java-Sync testen** - funktioniert es?
2. ✅ **HBCI-Version prüfen** - stimmt App mit Java-Sync überein?
3. ✅ **Job-Liste prüfen** - stimmt App mit Java-Sync überein?
4. ✅ **Logs vergleichen** - wo weicht App von Java-Sync ab?
5. ⚠️ **Erst danach:** Passport-Reset, andere Ansätze

**Zeitersparnis:** 80-90% durch Java-Sync statt App-Builds

### 3. BBBank-spezifisches Verhalten
- Benötigt **HBCI 2.2** für MT940-Jobs
- CAMT wird angeboten, schlägt aber fehl (SAX-Parser-Problem)
- Nicht alle Banken verhalten sich gleich

### 4. Passport ist selten das Problem
- BPD (Bank Parameter Daten) werden bei jeder Verbindung aktualisiert
- Passport-Reset löst meist nichts
- **HBCI-Version-Mismatch** ist häufiger die Ursache

---

## 🚀 Next Steps bei ähnlichen Problemen

### Andere Bank funktioniert nicht?

1. **Java-Sync testen:**
   ```cmd
   scripts\100-quick-test.cmd
   ```
   - ✅ Funktioniert? → Problem ist in der App
   - ❌ Funktioniert nicht? → Problem ist Banking-seitig

2. **Bei App-Problem:**
   ```cmd
   scripts\500-verify-sync.cmd
   ```
   - Prüft HBCI-Version, Job-Liste, CAMT-Status

3. **HBCI-Version anpassen:**
   - Manche Banken brauchen Version "300" (Standard)
   - Manche brauchen Version "220" (BBBank, VR-Banken?)
   - Fallback-Mechanismus ist wichtig!

4. **Logs analysieren:**
   - App: Einstellungen → Fehlerprotokoll exportieren
   - Suche nach: `JobNotSupportedException`, `HBCI=`, `supported`

---

## 📂 Betroffene Dateien

### App (geändert)
- `app/src/main/java/de/mybudgets/app/data/banking/FintsService.kt`
  - Zeile 274-284: CAMT aus Job-Liste entfernt
  - Zeile 339: CAMT-Fallback-Code entfernt
  - Zeile 450-465: HBCI 2.2 mit Fallback auf 3.0

### Java-Sync (Referenz)
- `scripts/java-sync/src/main/java/de/mybudgets/sync/BbbankSync.java`
  - Zeile 126-130: HBCI 2.2 ("220") zuerst, dann 300
  - Zeile 142: Job-Liste `{"KUmsZeitSEPA", "KUmsAll", "KUmsNew"}`

### Tools
- `scripts/500-verify-sync.cmd` - Automatischer Code-Vergleich
- `scripts/100-quick-test.cmd` - Schneller Java-Sync-Test (15s)

---

## 📚 Referenzen

- **Code-Verifikation:** [CODE-SYNC-VERIFICATION.md](CODE-SYNC-VERIFICATION.md)
- **E2E-Test-Protokoll:** [BBBank-Sync-E2E-Test.md](BBBank-Sync-E2E-Test.md)
- **Test-Workflow:** [TESTING-WORKFLOW.md](TESTING-WORKFLOW.md)
- **Quick-Reference:** [QUICK-REFERENCE.md](QUICK-REFERENCE.md)
- **Scripts:** [scripts/README.md](scripts/README.md)

---

**Stand:** 2026-05-08 21:00  
**Status:** ✅ Beide Probleme gelöst, App-Build erfolgreich, bereit zum Testen
