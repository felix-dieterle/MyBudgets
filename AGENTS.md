# MyBudgets - Projekt-spezifische Regeln

**Projekt:** MyBudgets - Android Budget-Tracking App mit FinTS/HBCI Banking-Integration  
**Repo:** https://github.com/felix-dieterle/MyBudgets

## Coding Standards

**Layer-Regel:** Daten fließen nur abwärts. UI kennt ViewModel, ViewModel kennt Repository, Repository kennt DAO. Keine Layer überspringen.

```
data/model/*.kt         → @Entity, keine Logik
data/db/*Dao.kt         → interface (abstract class nur bei @Transaction)
data/repository/*Repo.kt → @Singleton, Business-Logik hier
viewmodel/*ViewModel.kt → @HiltViewModel, stateIn(Lazily), keine UI-Imports
ui/<feature>/*Fragment.kt → @AndroidEntryPoint, ViewBinding
util/*.kt               → object, stateless helpers
worker/*Worker.kt       → @HiltWorker, CoroutineWorker
```

**ViewModel-Pattern:**
```kotlin
private val _state = MutableStateFlow<XxxState>(XxxState.Idle)
val state: StateFlow<XxxState> = _state
```
`SharingStarted.Lazily` für `stateIn`. States: `Idle | Loading(msg) | Success(data) | Error(msg)`. Keine MutableStates nach außen.

**Repository-save-Pattern:**
```kotlin
fun save(e: Xxx): Long = if (e.id == 0L) dao.insert(e) else { dao.update(e); e.id }
```

**Fragment-Lifecycle (100%):**
```kotlin
private var _binding: FragmentXxxBinding? = null
private val binding get() = _binding!!
override fun onDestroyView() { _binding = null }
// collect:
viewLifecycleOwner.lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { vm.state.collect { ... } }
    }
}
```

**Adapter (ListAdapter + DiffUtil):**
```kotlin
class XxxAdapter(private val onClick: (Xxx) -> Unit) :
    ListAdapter<Xxx, XxxAdapter.VH>(object : DiffUtil.ItemCallback<Xxx>() {
        override fun areItemsTheSame(a: Xxx, b: Xxx) = a.id == b.id
        override fun areContentsTheSame(a: Xxx, b: Xxx) = a == b
    }) {
    inner class VH(val b: ItemXxxBinding) : RecyclerView.ViewHolder(b.root)
    // onCreateViewHolder: VH(ItemXxxBinding.inflate(...))
    // onBindViewHolder: holder.bind(getItem(pos))
}
```

**Error-Handling:**
- ViewModel → sealed State + Fehler im Error-State → Fragment zeigt `Snackbar`
- `CancellationException` immer re-throwen
- Banking: `runCatching { ... }.onFailure { ... }`
- DB: Room wirft eigene Exceptions, kein try/catch nötig

**Null-Handling:**
- `categoryId: Long?` → immer `if (id != null)` prüfen, nie `!!`
- immer `?:` für Defaults statt null-checks

**Ressourcen-Naming:**
- strings: `feature_semantic_name` (dashboard_total_balance)
- layouts: `fragment_<feature>.xml`, `item_<entity>.xml`, `dialog_<purpose>.xml`
- colors: `feature_semantic` (income_green, expense_red)
- nav-actions: `action_source_to_target`

**CRUD-Fragmente:** Prefix `AddEdit` (AddEditTransactionFragment).
Bundle-Argumente: `putLong("id", entity.id)` (nie SafeArgs).

### Banking/Sync-Protokoll (NO TOUCH ZONE)

- **FintsService.kt** + `camt/`-Package + Banking-States (`BankSyncState`, `TransferState`) nie ändern.
- **CUstomCamtParser** (XmlPullParser) + **HbciCamtPatcher** (XML-Repair) + **CamtExtractionHelper** (JAXB-Intercept) sind bewährt – nur bei neuem Fehlermuster anfassen.
- **Java-Sync = Referenz** (`scripts/java-sync/BbbankSync.java`). App-Code (FintsService) muss 1:1 synchron sein (HBCI-Version, Jobs, Parameter).
- Sync-Test erst via `scripts/100-quick-test.cmd` (10-15s), dann App-Build.
- Verifikation: `scripts/500-verify-sync.cmd`.
- Bei BBBank: FinTS 3.0 ("300") Primary, Fallback 2.2 ("220").
- Job-Reihenfolge: `KUmsAllCamt` → `KUmsZeitSEPA` → `KUmsAll` → `KUmsNew`.

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

### UI/UX Structure
- **[UI-UX-STRUCTURE.md](./UI-UX-STRUCTURE.md)** - Navigation flow, user journeys, design principles

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
- BBBank: **FinTS 3.0 ("300")** als Primary (siehe FintsService.kt:531)
- Fallback auf HBCI 2.2 ("220") für andere Banken
- **Java-Sync ist Referenz** - App muss Code 1:1 synchron halten

**Job-Typen:**
- ✅ **KUmsAllCamt (CAMT):** Funktionierte 2026-05-12 mit CustomCamtParser (150 TXs)
- ✅ **Fallback-Jobs:** `KUmsZeitSEPA`, `KUmsAll`, `KUmsNew`
- ⚠️ **Stand 2026-05-15:** Alle Jobs schlagen fehl (Passport expired + mögliches Netzwerk-Problem)

**Custom Parser:**
- `CustomCamtParser.kt` extrahiert CAMT trotz SAX-Exception erfolgreich
- `HbciCamtPatcher.kt` repariert ungültiges XML von BBBank
- Bewährte Implementation seit 2026-05-12

**Referenz-Implementation:**
- `scripts/java-sync/src/BbbankSync.java` (hbci4java 3.1.88)
- **Regel:** App muss Code 1:1 mit Java-Sync synchron halten (HBCI-Version, Jobs, Parameter)
- **Dokumentation:** Siehe `TEST-SCRIPTS-INVENTORY.md` für Test-Ablauf

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
- **NAS (secure-storage):** `\\secure-storage\home\Downloads\MyBudgets\`
  
**Local Deployment Rules:**

- Always use `scripts/202-build-apk.cmd` when producing APKs for distribution. It matches CI and produces versioned filenames.
- When copying to NAS, use a new versioned filename (example: `MyBudgets-v<versionName>-<versionCode>-<timestamp>.apk`) instead of overwriting `MyBudgets-latest.apk`. The copy step sometimes does not overwrite remote files when using the same filename.
- The build script still writes `MyBudgets-latest.apk` for convenience, but for reliable installs pick the versioned file.

**Download-URLs:**
- **NAS (direkt):** `\\secure-storage\home\Downloads\MyBudgets\MyBudgets-latest.apk` (vom Handy via File-Manager)
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

**Zuletzt aktualisiert:** 2026-05-16
