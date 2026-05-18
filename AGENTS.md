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
- **CustomCamtParser** (XmlPullParser) + **HbciCamtPatcher** (XML-Repair) + **CamtExtractionHelper** (JAXB-Intercept) sind bewährt – nur bei neuem Fehlermuster anfassen.
- **App-Code ist Quelle der Wahrheit**, Änderungen nur im App-Code (`FintsService.kt`, `camt/`).
- Bei BBBank: FinTS 3.0 ("300") Primary, Fallback 2.2 ("220").
- Job-Reihenfolge: `KUmsAllCamt` → `KUmsZeitSEPA` → `KUmsAll` → `KUmsNew`.
- Testen via App-Build: `scripts/202-build-apk.cmd`.

## UI/UX Standards

**Theme:** `Theme.Material3.DayNight.NoActionBar`. Karten via `MaterialCardView` (16dp padding/bottomMargin, eckig). Buttons: `MaterialButton` (filled = primary, `?attr/materialButtonOutlinedStyle` = secondary). Chips via `ChipGroup` (singleSelection=true, `Widget.Material3.Chip.Assist.Elevated` oder `.Filter`).

**Dialoge (3 Patterns):**
- **Einfach:** `MaterialAlertDialogBuilder(requireContext())` inline (Bestätigung, Löschen, Listen-Auswahl)
- **Komplex:** `DialogFragment` + ViewBinding + setter-basierter Listener (`setOnXxxListener {}`)
- **PIN/TAN:** `suspendCancellableCoroutine` in Top-Level-Funktionen (`PinTanDialogs.kt`). Provider vor Sync registrieren, in `onDestroyView` nullen.

**Formulare:** `ScrollView` → `LinearLayout(vertical)` → `TextInputLayout/TextInputEditText`.
- Validation: `etName.error = getString(R.string.error_*)` + `return@setOnClickListener`.
- Edit-Mode: `existing.copy(name = newName, ...)` (id/balance/createdAt nie überschreiben).
- DatePicker: `DatePickerDialog` + `Calendar` + `DateFormatter.formatDate(millis)`. Read-only-Feld via `focusable=false, clickable=true`.
- Spinner: `ArrayAdapter` mit `simple_spinner_item`/`simple_spinner_dropdown_item`.

**Listen:** `ListAdapter<T, VH>` + `DiffUtil.ItemCallback`. Click-Handler als Lambda-Konstruktor-Parameter. Formatter (CurrencyFormatter, DateFormatter) im ViewHolder direkt aufrufen.

**Leerzustände:** `LinearLayout` mit Emoji + `textAppearanceTitleLarge` + `textAppearanceBodyMedium(alpha=0.7)`, gesteuert via `visibility = if (list.isEmpty()) VISIBLE else GONE`.

**Loading: Button deaktivieren (btnSave.isEnabled = false)** – keine Spinner/ProgressBar.

**Fehler:** Transient → `Snackbar.make(view, msg, LENGTH_LONG).show()`. Feld-Validation → `EditText.error`.

**PIN/TAN-Lebenszyklus:**
```kotlin
fintsService.pinProvider = { bankName -> pinDialog(...) }
// in onDestroyView:
fintsService.pinProvider = null  // + tanProvider, decoupledConfirmProvider
```

**Zurück-Navigation:** `findNavController().navigateUp()` nach erfolgreichem Speichern. Vorwärts: `navigate(R.id.action_X_to_Y, Bundle().apply { putLong("id", entity.id) })`.

**Kategorie-Vorschlag:** 3-Button-Dialog (`Positive=Übernehmen, Negative=Überspringen, Neutral=Manuell`).

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
│   ├── build.cmd               # APK Build-Script
│   └── workflow.cmd            # Kompletter Test→Build→Install
└── keystore/                   # Debug Keystore
```

## Wichtige Dokumentation

### Roadmap & Planning
- **[ROADMAP.md](./ROADMAP.md)** - Aktuelle Phasen, Milestones, Prioritäten
- **[features/Interview-2026-05-17.md](./features/Interview-2026-05-17.md)** - Nutzer-Interview: Ziele, Pain Points, Feature-Ideen
- **[ROADMAP-2026-05-12-archived.md](./ROADMAP-2026-05-12-archived.md)** - Alte Roadmap (archiviert, enthält Out-of-Scope-Ideen)

## UI/UX Structure
- **[UI-UX-STRUCTURE.md](./UI-UX-STRUCTURE.md)** - Navigation flow, user journeys, design principles

### Banking & FinTS

**⚠️ KRITISCH - Bei BBBank-Sync-Problemen zuerst lesen:**
- **[BBBank-Sync-Troubleshooting.md](./BBBank-Sync-Troubleshooting.md)** - Bekannte Probleme, Lösungen, Lessons Learned
- **[BBBank-Sync-E2E-Test.md](./BBBank-Sync-E2E-Test.md)** - Vollständiges Test-Protokoll & Analyse

**Test & Development:**
- **[scripts/README.md](./scripts/README.md)** - Script-Übersicht

### BBBank-Spezifische Regeln

**HBCI-Version:**
- BBBank: **FinTS 3.0 ("300")** als Primary (siehe FintsService.kt:531)
- Fallback auf HBCI 2.2 ("220") für andere Banken

**Job-Typen:**
- ✅ **KUmsAllCamt (CAMT):** Funktionierte 2026-05-12 mit CustomCamtParser (150 TXs)
- ✅ **Fallback-Jobs:** `KUmsZeitSEPA`, `KUmsAll`, `KUmsNew`
- ⚠️ **Stand 2026-05-15:** Alle Jobs schlagen fehl (Passport expired + mögliches Netzwerk-Problem)

**Custom Parser:**
- `CustomCamtParser.kt` extrahiert CAMT trotz SAX-Exception erfolgreich
- `HbciCamtPatcher.kt` repariert ungültiges XML von BBBank
- Bewährte Implementation seit 2026-05-12

## Build & Deployment

### Lokal bauen

```bash
# APK bauen
scripts\200-build-debug.cmd

# Kompletter Workflow: Test → Build → Install
scripts\300-workflow.cmd
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

### Test-Credentials

- **Location:** `scripts/java-sync/config.properties` (lokal, nicht im Git)
- **Template:** `scripts/java-sync/config.properties.example`
- **Setup:** `scripts\001-setup.cmd` (interaktiver Config-Wizard)

## Bekannte Probleme & Fixes

### BBBank-Sync schlägt fehl

**Problem:** "Diese Bank unterstützt keinen HBCI-Kontoauszug-Abruf"

**Lösung:** Siehe **[BBBank-Sync-Troubleshooting.md](./BBBank-Sync-Troubleshooting.md)**

**Quick-Check:**
1. HBCI-Version in App = HBCI 2.2 mit Fallback auf 3.0? ✅
2. Job-Liste in App = `KUmsZeitSEPA → KUmsAll → KUmsNew`? ✅
3. CAMT aktiviert (KUmsAllCamt via CustomCamtParser)? ✅

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

**Zuletzt aktualisiert:** 2026-05-17
