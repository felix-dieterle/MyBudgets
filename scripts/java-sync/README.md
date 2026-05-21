# BBBank Java-Sync

Einfaches Java-Programm mit hbci4java zum Abrufen von Kontoauszügen von der BBBank.
Analog zur `FintsService.kt` Logik in der Android-App.

## Installation

1. Kopiere `config.properties.example` nach `config.properties`
2. Fülle deine Zugangsdaten aus (IBAN, Nutzerkennung, PIN)
3. Starte das Programm über `scripts\start-java-sync.cmd`

## Konfiguration

Die Konfiguration kann über `config.properties` oder Umgebungsvariablen erfolgen:

### config.properties
```
iban=DE89370400440532013000
userId=DEINE_NUTZERKENNUNG
PIN=DEINE_PIN
blz=66090800
tanMethod=900
daysBack=30
debug=false

# Multi-Job Test Config
multiJob.maxChunks=3
multiJob.yearsPerChunk=1
multiJob.useEnddate=false
```

### Umgebungsvariablen
```
MYB_IBAN=DE89370400440532013000
MYB_USER=DEINE_NUTZERKENNUNG
MYB_PIN=DEINE_PIN
MYB_BLZ=66090800
MYB_TAN_METHOD=900
MYB_DAYS_BACK=30
MYB_DEBUG=false
```

## Verwendung

### Standard Sync Test
**Windows:**
```cmd
scripts\start-java-sync.cmd
```

### Multi-Job Dialog Test
**Windows:**
```cmd
scripts\400-multi-job-test.cmd
```

Testet den Multi-Job-Ansatz (wie Hibiscus):
- Mehrere KUmsAllCamt-Jobs in EINEM Dialog
- EINE TAN für alle Jobs
- Ziel: Mehr als 150 Transaktionen pro TAN

**Manuell:**
```bash
cd scripts\java-sync
gradlew.bat :scripts:java-sync:jar
java -jar build\libs\java-sync.jar
```

## Features

- Verwendet hbci4java 3.1.88 (wie die Android-App)
- Persistente Passport-Datei (speichert system_id, BPD, etc.)
- Fallback-Strategie für verschiedene Job-Typen (KUmsAllCamt → KUmsZeitSEPA → KUmsAll → KUmsNew)
- PIN/TAN-Eingabe über Konsole
- Zeigt bis zu 10 Buchungen an

## Test Scripts

### BbbankSync.java
Standard Sync-Test mit single job (wie aktueller App-Code)

### MultiJobTest.java
Multi-Job Dialog Test:
- Erstellt mehrere KUmsAllCamt-Jobs mit unterschiedlichen Date-Windows
- Führt EINEN Dialog aus (ONE TAN!)
- Vergleicht Strategie mit/ohne `enddate` Parameter
- Detailliertes Logging für jeden Job

## Sicherheit

⚠️ **WICHTIG:** Die PIN wird im Klartext in der Konfigurationsdatei gespeichert!
- Verwende Umgebungsvariablen für mehr Sicherheit
- Teile die `config.properties` Datei nicht
- Git ignoriert diese Datei bereits

## Unterschied zur Android-App

- Keine Android-spezifischen Abhängigkeiten
- Konsolen-basierte PIN/TAN-Eingabe (kein UI)
- Speichert Passport-Dateien in `scripts/java-sync/passports/`
- Verwendet dieselbe hbci4java-Version wie die App
