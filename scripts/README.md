# CMD/BAT Scripts Übersicht

Alle Scripts für schnelle Test-Iterationen ohne App-Build.

**Nummerierungs-Schema:** Siehe [CMD-NAMING-CONVENTION.md](CMD-NAMING-CONVENTION.md)

---

## 🚀 Quick Start

**Erste Einrichtung (einmalig):**
```cmd
cd F:\CascadeProjects\MyBudgets
scripts\001-setup.cmd   # oder Alias: scripts\setup.cmd
```

**Schneller Test (nach Änderungen):**
```cmd
scripts\100-quick-test.cmd   # oder Alias: scripts\qt.cmd
```

**Code-Verifikation:**
```cmd
scripts\500-verify-sync.cmd   # Prüft ob App = Java-Sync
```

---

## 📂 Verfügbare Scripts

### Setup & Konfiguration (000-099)

#### `001-setup.cmd` - Erste Einrichtung (einmalig)
Richtet `config.properties` ein und führt ersten Test aus.

**Alias:** `setup.cmd`

**Usage:**
```cmd
scripts\001-setup.cmd
scripts\setup.cmd         # Alias
```

---

### Test-Scripts (100-199)

#### `100-quick-test.cmd` - Quick Test ⭐ **EMPFOHLEN**
Schnellster Test via Java-Sync (10-15 Sekunden).

**Alias:** `qt.cmd`

**Usage:**
```cmd
scripts\100-quick-test.cmd              # Nur Java-Sync
scripts\qt.cmd                          # Alias (kürzer)
scripts\qt.cmd --with-live              # Mit Gradle Live-Test
scripts\qt.cmd -l                       # Mit Gradle Live-Test (Kurzform)
```

**Performance:** ~15 Sekunden (ohne Live-Test)

---

### Build-Scripts (200-299)

#### `200-build-debug.cmd` - APK bauen (Debug)
Baut Debug-APK und kopiert nach mama-razzi.

**Alias:** `build.cmd`

**Usage:**
```cmd
scripts\200-build-debug.cmd
scripts\build.cmd                       # Alias
```

#### `202-build-apk.cmd` - Low-level Build-Script
Wird von anderen Scripts genutzt (nicht direkt aufrufen).

---

### Workflow-Scripts (300-399)

#### `300-workflow.cmd` - Kompletter Workflow
Test → Build → (Optional) Install

**Alias:** `workflow.cmd`

**Usage:**
```cmd
scripts\300-workflow.cmd                # Test + Debug-Build
scripts\300-workflow.cmd release        # Test + Release-Build
scripts\300-workflow.cmd release install # Test + Release + Install
scripts\workflow.cmd                    # Alias
```

---

### Development Tools (500-599)

#### `500-verify-sync.cmd` - Code-Sync Verification
Prüft ob Java-Sync und App synchron sind.

**Usage:**
```cmd
scripts\500-verify-sync.cmd
```

**Checks:**
- ✅ HBCI-Version (220 mit Fallback 300)
- ✅ Job-Liste (KUmsZeitSEPA → KUmsAll → KUmsNew)
- ✅ CAMT deaktiviert
- ✅ Java-Sync Test läuft

#### `501-copy-passport-from-android.cmd` - Passport von Device holen
Kopiert Passport-Datei vom Android-Device für Debugging.

---

### Legacy (900-999)

Alte Versionen für Historie (nicht mehr verwenden):

- `900-start-sync.cmd` - Alte Version von java-sync
- `901-start-java-sync.cmd` - Alte Version
- `902-run-kotlin-sync.cmd` - Kotlin-basierter Ansatz (deprecated)
- `903-quick-test-fix.cmd` - Alte Version von quick-test

---

## 🎯 Typischer Workflow

### Szenario: "Banking-Code ändern und testen"

```cmd
REM 1. Einmalige Einrichtung (falls noch nicht gemacht)
cd F:\CascadeProjects\MyBudgets
scripts\001-setup.cmd

REM 2. Änderung in Java-Sync machen
REM    → scripts/java-sync/BbbankSync.java bearbeiten

REM 3. Schneller Test
scripts\100-quick-test.cmd
REM → 15 Sekunden später: ✅ Erfolgreich!

REM 4. Weitere Änderungen, iterieren
scripts\100-quick-test.cmd

REM 5. Verifikation: Sind App und Java-Sync synchron?
scripts\500-verify-sync.cmd

REM 6. Änderungen in App übertragen
REM    → app/.../FintsService.kt bearbeiten

REM 7. Erneute Verifikation
scripts\500-verify-sync.cmd

REM 8. App bauen (nur wenn alles OK)
scripts\200-build-debug.cmd

REM 9. Oder Kompletter Workflow (Test + Build + Install)
scripts\300-workflow.cmd install
```

**Gesamt-Zeit:** ~2-3 Minuten statt 5+ Minuten bei direktem App-Build!

---

## 📊 Performance-Vergleich

| Script | Build-Zeit | Test-Zeit | Features |
|--------|-----------|-----------|----------|
| `qt.cmd` | 10-15s | 10-20s | Minimal, schnellster Weg |
| `quick-test-fix.cmd` | 10-15s | 10-20s | Mit Fehlerdiagnose & Hilfe |
| `setup.cmd` | 10-15s | 10-20s | Einmalig, mit Config-Wizard |
| `start-java-sync.cmd` | 10-15s | 10-20s | Original, ohne Extras |

**Mit `--with-live` Parameter:** +40-60 Sekunden für Gradle Live-Test

---

## 🔧 Konfiguration

Alle Scripts nutzen `scripts\java-sync\config.properties`:

```properties
iban=DE89370400440532013000
userId=DEINE_NUTZERKENNUNG
pin=DEINE_PIN
blz=37040044
tanMethod=900
daysBack=30
debug=false
```

**Config bearbeiten:**
```cmd
notepad scripts\java-sync\config.properties
```

**Debug-Modus aktivieren:**
```properties
debug=true
```

---

## 🆘 Troubleshooting

### Script findet config.properties nicht

**Symptom:**
```
[X] config.properties nicht gefunden!
```

**Lösung:**
```cmd
scripts\setup.cmd  # Automatische Erstellung
```

---

### Java nicht gefunden

**Symptom:**
```
'java' is not recognized as an internal or external command
```

**Lösung:**
```cmd
REM Java aus Android Studio nutzen
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%

REM Prüfen
java -version

REM Nochmal testen
scripts\qt.cmd
```

---

### Build fehlschlägt

**Symptom:**
```
[X] Java-Sync Build fehlgeschlagen!
```

**Lösung:**
```cmd
REM Manueller Build mit mehr Logs
gradlew.bat :scripts:java-sync:jar

REM Gradle Cache löschen (falls korrupt)
gradlew.bat clean

REM Nochmal bauen
gradlew.bat :scripts:java-sync:jar
```

---

### Test schlägt fehl

**Symptom:**
```
[X] Java-Sync fehlgeschlagen (Exit Code: 1)
```

**Häufige Ursachen:**
1. **PIN/Nutzerkennung falsch**
   ```cmd
   notepad scripts\java-sync\config.properties
   ```

2. **Secure Go nicht bestätigt**
   - Handy prüfen
   - In App bestätigen
   - Nochmal testen

3. **Timeout**
   - Netzwerk-Verbindung prüfen
   - Später nochmal versuchen

4. **Debug-Modus aktivieren:**
   ```properties
   debug=true
   ```
   ```cmd
   scripts\qt.cmd
   ```

---

## 📝 Best Practices

1. **`qt.cmd` für schnelle Iterationen** - kürzester Weg
2. **`setup.cmd` nur einmal** - für initiales Setup
3. **`--with-live` nur bei Android-Problemen** - spart Zeit
4. **Debug-Modus bei Problemen** - ausführliche Logs
5. **Config nie committen** - enthält Credentials!

---

## 🔗 Weitere Dokumentation

- **Workflow-Details:** `../TESTING-WORKFLOW.md`
- **Cheatsheet:** `../QUICK-REFERENCE.md`
- **E2E-Tests:** `../BBBank-Sync-E2E-Test.md`
- **Java-Sync README:** `java-sync/README.md`

---

**Viel Erfolg! 🚀**
