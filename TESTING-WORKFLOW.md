# Testing Workflow - Schnelle Iterationen ohne App-Build

Dieser Workflow ermöglicht schnelles Testen von BBBank-Sync-Fixes **ohne** die komplette App bauen zu müssen.

---

## 🎯 Workflow-Übersicht

```
┌─────────────────────────────────────────────────────────────┐
│ Änderung machen                                             │
│   • BbbankSync.java (für Java-Sync)                        │
│   • FintsService.kt (für App)                              │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│ Stufe 1: Java-Sync Test (10-15 Sekunden) ⚡                │
│   → Verwendet hbci4java (gleiche Library wie App)          │
│   → Keine Android-Dependencies                             │
│   → Console-basierter Test                                 │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ├─ ❌ Fehlgeschlagen → Logs prüfen, Fix machen, zurück zu Stufe 1
                 │
                 ▼
                 ✅ Erfolgreich
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│ Stufe 2: Gradle Live-Test (40-60 Sekunden) [OPTIONAL]      │
│   → Testet FintsService.kt direkt                          │
│   → Inkl. Android-spezifische Workarounds                  │
│   → Kein App-Build nötig (nur Test-APK)                    │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ├─ ❌ Fehlgeschlagen → App-spezifisches Problem
                 │
                 ▼
                 ✅ Erfolgreich
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│ Stufe 3: App-Build (2-3 Minuten) [NUR WENN ALLES OK]       │
│   → Finale Verifikation in echter App                      │
│   → Mit UI, Emulator/Device                                │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start

### 1. Setup (einmalig)

```powershell
# Config für Java-Sync anlegen
cd F:\CascadeProjects\MyBudgets\scripts\java-sync
Copy-Item config.properties.example config.properties

# Config bearbeiten:
notepad config.properties

# Benötigte Felder:
# iban=DE89...
# userId=DEINE_NUTZERKENNUNG
# pin=DEINE_PIN
# tanMethod=900
# daysBack=30
```

### 2. Änderung machen

**Option A: Nur Java-Sync testen (schnellste Iteration)**
```java
// scripts/java-sync/src/main/java/de/mybudgets/sync/BbbankSync.java
String[] jobTypes = {"KUmsZeitSEPA", "KUmsAll", "KUmsNew"};  // CAMT entfernt
```

**Option B: Für App-Integration (später)**
```kotlin
// app/src/main/java/de/mybudgets/app/data/banking/FintsService.kt
val jobAttempts = listOf(
    JobAttempt("KUmsZeitSEPA", fromDate),  // CAMT entfernt
    JobAttempt("KUmsAll"),
    JobAttempt("KUmsNew"),
)
```

### 3. Testen

**Schneller Test (Stufe 1 only):**
```powershell
.\scripts\quick-test-fix.ps1
```

**Mit Gradle Live-Test (Stufe 1 + 2):**
```powershell
.\scripts\quick-test-fix.ps1 -RunLiveTest
```

**Manuelle Alternative:**
```powershell
# Stufe 1: Java-Sync
cd scripts\java-sync
..\..\gradlew.bat :scripts:java-sync:jar
java -jar build\libs\java-sync.jar

# Stufe 2: Gradle Live-Test
bash scripts\run-live-bbbank-sync-test.sh
```

---

## 📊 Performance-Vergleich

| Test-Methode | Build-Zeit | Test-Zeit | Android-spezifisch? | Wann nutzen? |
|-------------|-----------|-----------|---------------------|--------------|
| **Java-Sync** | 10-15s | 10-20s | ❌ Nein | Schnelle Iterationen, hbci4java-Logik testen |
| **Gradle Live-Test** | 30-40s | 10-20s | ✅ Ja | Android-Workarounds testen (SAXParserFactory etc.) |
| **App-Build** | 2-3min | 30s | ✅ Ja | Finaler Test vor Release |

---

## 🎯 Typischer Entwicklungs-Workflow

### Szenario: "CAMT-Jobs deaktivieren"

```powershell
# 1. Änderung in BbbankSync.java (CAMT aus Job-Liste entfernen)
# 2. Quick-Test
.\scripts\quick-test-fix.ps1
# → 15 Sekunden später: ✅ Erfolgreich!

# 3. Gleiche Änderung in FintsService.kt übernehmen
# 4. Gradle Live-Test
.\scripts\quick-test-fix.ps1 -RunLiveTest
# → 60 Sekunden später: ✅ Erfolgreich!

# 5. Finale Verifikation (nur wenn alles OK)
gradlew.bat assembleDebug
# → Installiere APK auf Device und teste manuell
```

**Gesamt-Zeit:** ~2 Minuten statt 5+ Minuten bei direktem App-Build!

---

## 🔍 Troubleshooting

### Java-Sync schlägt fehl

**Symptom:** `Fehler: Bank-Antwort nicht OK`

**Lösungen:**
1. Prüfe `config.properties` (IBAN, userId, pin korrekt?)
2. Aktiviere Debug-Logging: `debug=true` in config.properties
3. Prüfe Console-Output für HBCI-Fehler
4. Teste mit Python-Skript als Referenz:
   ```bash
   python scripts/bbbank-sync-debug.py --server https://fints2.atruvia.de/cgi-bin/hbciservlet
   ```

### Gradle Live-Test schlägt fehl, aber Java-Sync funktioniert

**Ursache:** Android-spezifisches Problem (z.B. SAXParserFactory)

**Lösungen:**
1. Prüfe, ob Android-Workarounds aktiv sind (`FeatureIgnoringSAXParserFactory`)
2. Prüfe Logs nach `SAXNotRecognizedException`
3. Vergleiche Job-Listen (Java-Sync vs. FintsService.kt)

### Beide Tests erfolgreich, aber App schlägt fehl

**Ursache:** UI-spezifisches Problem (z.B. PIN/TAN-Provider)

**Lösungen:**
1. Prüfe `AppLogger` Export in der App
2. Prüfe, ob `pinProvider`/`tanProvider` korrekt gesetzt sind
3. Teste mit Emulator statt Device (oder umgekehrt)

---

## 📝 Best Practices

1. **Immer Java-Sync zuerst testen** – schnellste Feedback-Schleife
2. **Gradle Live-Test nur wenn Java-Sync OK** – spart Zeit bei hbci4java-Problemen
3. **App-Build nur für finale Verifikation** – UI-Integration testen
4. **Logs immer speichern** – Bei Fehlern in `scripts/logs/` ablegen
5. **Config nie committen** – `config.properties` enthält Credentials!

---

## 🔗 Weiterführende Dokumentation

- **Python-Referenz:** `scripts/bbbank-sync-debug.py`
- **E2E-Test-Doku:** `BBBank-Sync-E2E-Test.md`
- **Java-Sync README:** `scripts/java-sync/README.md`
- **App-Logs:** Einstellungen → Fehlerprotokoll → Export

---

## 🎉 Erfolg messen

Ein Test gilt als **erfolgreich**, wenn:

1. ✅ Keine Exception geworfen wird
2. ✅ Bank-Response ist `isOK()`
3. ✅ Mindestens 1 Transaktion geparst wird (bei vorhandenen Umsätzen)
4. ✅ Logs zeigen erwarteten Job-Type (z.B. `KUmsZeitSEPA` statt `KUmsAllCamt`)

---

**Viel Erfolg! 🚀**
