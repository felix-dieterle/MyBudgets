# Scripts Workflow - Visuelle Übersicht

```
┌─────────────────────────────────────────────────────────────────┐
│                    🎯 ERSTE EINRICHTUNG                         │
│                                                                 │
│  cd F:\CascadeProjects\MyBudgets                               │
│  scripts\setup.cmd                                             │
│                                                                 │
│  → Erstellt config.properties                                  │
│  → Öffnet Notepad für Credentials                             │
│  → Führt ersten Test aus                                       │
│  → Zeigt Hilfe für nächste Schritte                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    🔄 ENTWICKLUNGS-ZYKLUS                       │
└─────────────────────────────────────────────────────────────────┘
                              │
      ┌───────────────────────┴───────────────────────┐
      │                                               │
      ▼                                               ▼
┌──────────────────┐                        ┌──────────────────┐
│  Änderung machen │                        │  Code-Review    │
│                  │                        │  (optional)     │
│  - In IDE        │                        └──────────────────┘
│  - BbbankSync    │                                │
│  - FintsService  │                                │
└────────┬─────────┘                                │
         │                                          │
         ▼                                          │
┌─────────────────────────────────────────────────┐│
│  🚀 QUICK TEST (15 Sekunden)                    ││
│                                                  ││
│  scripts\qt.cmd                                  ││
│                                                  ││
│  → Build Java-Sync (~10s)                       ││
│  → Run Test (~5-10s)                            ││
│  → Zeigt Ergebnis                               ││
└───────────────┬──────────────────────────────────┘│
                │                                    │
       ┌────────┴────────┐                          │
       │                 │                          │
       ▼                 ▼                          │
┌────────────┐    ┌────────────┐                   │
│ ✅ Erfolg  │    │ ❌ Fehler  │                   │
└──────┬─────┘    └──────┬─────┘                   │
       │                 │                          │
       │                 └──────────────────────────┘
       │                    (Zurück zu Änderung)
       │
       ▼
┌─────────────────────────────────────────────────┐
│  🔍 OPTIONAL: Android-spezifischer Test         │
│                                                  │
│  scripts\qt.cmd --with-live                     │
│                                                  │
│  → Testet FintsService.kt direkt                │
│  → Mit Android SAXParserFactory etc.            │
│  → +40-60 Sekunden                              │
└───────────────┬─────────────────────────────────┘
                │
       ┌────────┴────────┐
       │                 │
       ▼                 ▼
┌────────────┐    ┌────────────┐
│ ✅ Erfolg  │    │ ❌ Fehler  │
└──────┬─────┘    └──────┬─────┘
       │                 │
       │                 └──────────────────────┐
       │                    (Android-Problem)   │
       ▼                                        │
┌─────────────────────────────────────────┐    │
│  📦 APP BAUEN (2-3 Minuten)             │    │
│                                          │    │
│  gradlew.bat assembleDebug              │    │
│                                          │    │
│  → APK in app\build\outputs\apk\debug\  │    │
└───────────────┬─────────────────────────┘    │
                │                               │
                ▼                               │
┌─────────────────────────────────────────┐    │
│  📱 DEVICE-TEST                         │    │
│                                          │    │
│  - APK installieren                     │    │
│  - App öffnen                           │    │
│  - Kontoauszug synchronisieren          │    │
│  - Secure Go bestätigen                 │    │
│  - Transaktionen prüfen                 │    │
└───────────────┬─────────────────────────┘    │
                │                               │
       ┌────────┴────────┐                     │
       │                 │                     │
       ▼                 ▼                     │
┌────────────┐    ┌────────────┐              │
│ ✅ Erfolg  │    │ ❌ Fehler  │              │
│            │    │            │              │
│ FERTIG! 🎉 │    └──────┬─────┘              │
└────────────┘           │                     │
                         └─────────────────────┘
                            (Zurück zu Änderung)
```

---

## 📊 Performance-Vergleich

### **ALT: Direkter App-Build bei jeder Iteration**
```
Iteration 1: Änderung → App-Build (3 min) → Test → Fehler
Iteration 2: Änderung → App-Build (3 min) → Test → Fehler
Iteration 3: Änderung → App-Build (3 min) → Test → Erfolg!
─────────────────────────────────────────────────────────
GESAMT: ~9 Minuten für 3 Iterationen
```

### **NEU: Quick Test vor App-Build**
```
Iteration 1: Änderung → qt.cmd (15s) → Fehler
Iteration 2: Änderung → qt.cmd (15s) → Fehler
Iteration 3: Änderung → qt.cmd (15s) → Erfolg!
Finaler Build: App-Build (3 min)
─────────────────────────────────────────────────────────
GESAMT: ~3:45 Minuten für 3 Iterationen + finaler Build

ZEITERSPARNIS: 58% schneller! ⚡
```

---

## 🎯 Script-Auswahl-Matrix

| Situation | Script | Warum? |
|-----------|--------|--------|
| **Erste Einrichtung** | `setup.cmd` | Interaktiver Config-Wizard |
| **Schnellster Test** | `qt.cmd` | Minimaler Overhead, 2 Zeichen |
| **Fehlerdiagnose** | `quick-test-fix.cmd` | Ausführliche Hilfe-Texte |
| **Android-Problem** | `qt.cmd --with-live` | Testet SAXParserFactory etc. |
| **Legacy/Kompatibilität** | `start-java-sync.cmd` | Original-Script |

---

## 🔑 Schlüssel-Dateien

```
F:\CascadeProjects\MyBudgets\
│
├── scripts\
│   ├── 📄 setup.cmd              ← Einmalige Einrichtung (interaktiv)
│   ├── 🚀 qt.cmd                 ← Schnellster Test (Alias)
│   ├── 📋 quick-test-fix.cmd     ← Vollständiger Test-Workflow
│   ├── 📦 start-java-sync.cmd    ← Original (einfach)
│   │
│   ├── 📖 README.md              ← Script-Dokumentation
│   │
│   └── java-sync\
│       ├── 🔐 config.properties       ← Credentials (NICHT committen!)
│       ├── 📄 config.properties.example
│       └── src\main\java\de\mybudgets\sync\
│           └── BbbankSync.java        ← Java-Test-Code
│
├── app\src\main\java\de\mybudgets\app\data\banking\
│   └── FintsService.kt            ← App-Banking-Service
│
├── 📖 TESTING-WORKFLOW.md         ← Detaillierte Workflow-Doku
├── 📖 QUICK-REFERENCE.md          ← Cheatsheet
└── 📖 BBBank-Sync-E2E-Test.md     ← E2E-Test-Protokoll
```

---

## 💡 Pro-Tipps

1. **Alias für noch schnelleren Zugriff:**
   ```cmd
   REM Füge zu deinem PATH hinzu:
   set PATH=%PATH%;F:\CascadeProjects\MyBudgets\scripts
   
   REM Dann von überall:
   qt
   ```

2. **Batch-Mehrfach-Tests:**
   ```cmd
   REM Test nach jeder Änderung
   qt && echo Test 1 OK
   REM Änderung...
   qt && echo Test 2 OK
   REM Änderung...
   qt && echo Test 3 OK
   ```

3. **Watch-Mode (manuell):**
   ```cmd
   :loop
   qt
   pause
   goto loop
   ```

4. **Parallel zum IDE nutzen:**
   - IDE (z.B. IntelliJ/Android Studio) in einem Fenster
   - CMD mit `qt.cmd` in anderem Fenster
   - Nach jeder Änderung: Alt+Tab → Enter (qt starten)

---

**Happy Testing! 🚀**
