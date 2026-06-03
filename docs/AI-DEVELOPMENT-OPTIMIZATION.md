# AI-Development Optimization Framework
## Kleinere Modelle mit großem Output - Setup-Strategien für OpenCode

**Kontext:** MyBudgets Android App, ~10k Zeilen Kotlin, komplexe Banking-Integration

---

## 1. CODE QUALITY & KONSISTENZ (Automatisierung)

### 1.1 Pre-Commit Hooks + Linting (Skills-basiert)

**Problem:** Modelle schreiben Code der nicht Style/Konventionen folgt

**Lösung:**
- **EditorConfig**: `.editorconfig` für automatische Indentation/Länge
- **Detekt Rules**: Kotlin static analysis - regeln.yml mit strict rules
  - Maximalzeilenlänge: 120 chars
  - Naming conventions: Interfaces nicht `I`, Constants `CONSTANT`
  - Complexity: McCabe < 15
- **Pre-Commit Hook** (Git): Automatischer Check vor Commit
  ```powershell
  # .git/hooks/pre-commit
  ./gradlew.bat detekt
  if ($LASTEXITCODE -ne 0) { exit 1 }
  ```

**OpenCode Integration:**
- **Skill:** `code-quality-checks` - ruft `./gradlew detekt` auf
- **AGENTS.md Rule:** `"detekt_fixes": "required"` - AI muss Linting-Fehler fixen

### 1.2 Architecture Enforcement (AGENTS.md Rules)

**Problem:** Kleinere Modelle verstehen Architektur nicht → vermischen Layer

**Lösung - AGENTS.md Section:**
```markdown
## Architecture Layer Rules (NO TOUCH!)

- **data/model/*.kt** → @Entity, @Parcelable nur, keine Logik
- **data/db/*Dao.kt** → interface nur, @Query/@Insert/@Delete/@Update
- **data/repository/*.kt** → @Singleton, Business-Logik hier, Datenfluss nur abwärts
- **viewmodel/*.kt** → @HiltViewModel, StateFlow<State>, Input-Handling nur
- **ui/*.kt** → Fragment/Adapter nur, keine DB-Zugriffe
- **util/*.kt** → object stateless, keine Coroutines, pure functions

**Violation Punishment:**
- DAO mit Logik → AI MUSS refaktorieren sofort
- Fragment mit DB-Zugriff → AI kann nicht committen
- ViewModel mit UI-Imports → Auto-abort
```

### 1.3 Naming Conventions (Zentrale Register)

**Projekt-spezifische Naming Registry:**
```kotlin
// util/NamingConventions.kt (dokumentiert & prüfbar)
object NamingConventions {
    // Fragment: {Feature}Fragment
    // ViewModel: {Feature}ViewModel
    // Repository: {Entity}Repository (nicht XxxManager/XxxService)
    // Dialog: {Purpose}DialogFragment (nicht XxxDialog)
    // Adapter: {Entity}Adapter
    // Event: {Action}Event (sealed class)
    // State: {Feature}State (sealed class)
    
    // String resources: feature_semantic_name
    // Examples: "dashboard_total_balance", "transaction_edit_title"
}
```

**OpenCode Rule:**
```markdown
- Alle Klassen müssen Naming-Konventionen folgen
- Neue Dateien → automatisch Prefix prüfen
- Bei Violation → AI muss umbenennen
```

### 1.4 Code Templates (Boilerplate Reduktion)

**Problem:** Modelle generieren immer gleiche Boilerplate → Token-Verschwendung

**Lösung - Zentrale Templates:**
```kotlin
// templates/ViewModel.template.kt
@HiltViewModel
class ${FEATURE}ViewModel @Inject constructor(
    private val repo: ${ENTITY}Repository
) : ViewModel() {
    
    private val _state = MutableStateFlow<${FEATURE}State>(${FEATURE}State.Idle)
    val state: StateFlow<${FEATURE}State> = _state.asStateFlow()
    
    // TODO: Implement business logic
}

// templates/Fragment.template.kt
@AndroidEntryPoint
class ${FEATURE}Fragment : Fragment() {
    private var _binding: Fragment${FEATURE}Binding? = null
    private val binding get() = _binding!!
    private val viewModel: ${FEATURE}ViewModel by viewModels()
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = Fragment${FEATURE}Binding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // TODO: Handle state
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

**OpenCode Integration:**
- **Skill:** `android-templates` - provides templates
- **AI Instruction:** "Use templates from `skills/android-templates/` when creating new files"

---

## 2. KONTEXT MANAGEMENT (Effiziente Token-Nutzung)

### 2.1 Structured Project Index (automatisch generiert)

**Problem:** Modelle brauchen Context - aber full codebase read = zu viele Token

**Lösung - Automatischer Index:**
```markdown
# MyBudgets Project Index

## Quick Navigation
- **Main Entry:** MainActivity.kt:45
- **Database:** Room (CategoryDao, TransactionDao, ...)
- **Main ViewModel:** TransactionViewModel, CategoryViewModel
- **Key Utils:** PatternMatcher.kt, DateFormatter.kt, CurrencyFormatter.kt

## File Structure Summary
- `data/model/` → 12 entities (Category, Transaction, CategoryPattern, ...)
- `data/db/` → 8 DAOs (all read-only queries)
- `data/repository/` → 8 repositories (business logic here)
- `ui/` → 6 features (transactions, categories, dashboard, settings, ...)
- `util/` → 10 helper objects (stateless)

## Critical Patterns
- **State Management:** StateFlow + ViewModel (never MutableStateFlow in UI)
- **Database Layer:** Room + Repositories only
- **Error Handling:** sealed States (Idle|Loading|Success|Error)
- **Logging:** AppLogger only (never android.util.Log)
- **Pattern Matching:** PatternMatcher (TEXT=AND-logic, IBAN=substring)

## Feature Ownership Map
| Feature | Files | ViewModel | Repo |
|---------|-------|-----------|------|
| Transactions | TransactionFragment, TransactionDetailFragment | TransactionViewModel | TransactionRepository |
| Categories | CategoriesFragment, CategoryAdapter | CategoryViewModel | CategoryRepository |
| Dashboard | DashboardFragment | DashboardViewModel | - |
| Banking | FintsService + camt parser | BankSyncViewModel | AccountRepository |
| Patterns | PatternPickerDialog, TransactionDetailFragment | TransactionViewModel | CategoryPatternRepository |

## Dependencies Graph
```
UI Layer → ViewModel ↓
ViewModel → Repository ↓
Repository → DAO + Database
```

## Known Gotchas
1. **AppLogger ONLY** - never android.util.Log (line 7-32 AGENTS.md)
2. **Pattern Text Matching** - AND-logic, punctuation → spaces (PatternMatcher.kt:21-26)
3. **Banking Integration** - NO TOUCH (FintsService.kt, camt/ package, CustomCamtParser)
4. **Fragment Lifecycle** - Always use repeatOnLifecycle + STARTED state
5. **Copy for Edits** - entity.copy(field=newValue) - never direct assignment
```

**Auto-Generate Script:**
```powershell
# scripts/generate-project-index.ps1
function Generate-ProjectIndex {
    $indexMd = @"
    # Project Index - $(Get-Date -Format 'yyyy-MM-dd HH:mm')
    ...
"@
    
    # Count files per directory
    $dirs = Get-ChildItem -Recurse | Where-Object {$_.Extension -eq '.kt'} | Group-Object Directory | Sort-Object Count -Descending
    
    # Generate file structure
    foreach ($dir in $dirs) {
        $indexMd += "`n## $($dir.Name) ($($dir.Count) files)`n"
    }
    
    Set-Content -Path "PROJECT-INDEX.md" -Value $indexMd
}
```

**OpenCode Integration:**
- **AGENTS.md Rule:** "Before starting work: read PROJECT-INDEX.md for context"
- **Skill:** `project-index` - generates & updates index automatically

### 2.2 Slim Context Passing (Task-spezifisch)

**Problem:** Telegramfrage lädt ganze AGENTS.md + Docs = Token-Overkill

**Lösung - Context Filter:**

```markdown
## AGENTS.md - Context Layers

### Layer 1 (Always)
- Architektur Rules (2KB)
- Naming Conventions (1KB)
- Critical Gotchas (1KB)

### Layer 2 (Only if Feature mentioned)
- Feature-specific rules
- Example: "Kategorie ändern" → Load nur CategoryViewModel rules

### Layer 3 (On-demand)
- Full file content (nur wenn user fragt)
- Dependency info (nur wenn refactoring)
```

**Implementation:**
```powershell
# Run question via Telegram
# ask-opencode.ps1 detects keywords → loads only relevant layers

$keywords = $question -split '\s+'
$context = "# Base Context`n"

if ($keywords -contains 'kategorie' -or $keywords -contains 'category') {
    $context += Get-Content AGENTS.md | Select-String -Pattern "Category.*" -Context 5
}

# Pass only relevant context to OpenCode API
```

### 2.3 Feature-Specific AGENTS.md Sections

**Split AGENTS.md by Feature:**

```markdown
# AGENTS.md - Global Rules

## Transaction Feature
- **ViewModel:** TransactionViewModel.kt:40-80
- **Key Pattern:** StateFlow<State> with sealed State
- **Do's:** Use `.copy()` for edits, validate before save
- **Dont's:** Never UI imports in ViewModel
- **Test Command:** `./gradlew.bat testDebugUnitTest`

## Category Feature
- **Critical:** CategoryRepository.moveCategory() uses db.withTransaction{}
- **Drag & Drop:** CategoryDragDropHelper validates before move
- **Patterns:** PatternMatcher must match ALL keywords (AND-logic)

## Banking Feature (NO TOUCH ZONE)
- FintsService.kt: Touch only for new features
- camt/ package: Only if new bank pattern detected
- CustomCamtParser: Proven since 2026-05-12, don't change
```

**OpenCode Rule:**
```markdown
When working on a feature, first check its section in AGENTS.md
Load only relevant context to save tokens
```

---

## 3. DOMAIN KNOWLEDGE (Projekt-spezifische Intelligenz)

### 3.1 Pattern Library (was funktioniert, was nicht)

**Datei: `docs/PATTERNS-LEARNED.md`**

```markdown
# Learned Patterns - Was funktioniert

## ✅ ERFOLGREICH

### Pattern 1: StateFlow + ViewModel
```kotlin
private val _state = MutableStateFlow<State>(State.Idle)
val state: StateFlow<State> = _state.asStateFlow()
```
- **Warum:** Lazy init, lifecycle-aware, backpressure
- **Wo:** Alle ViewModels
- **Nicht:** MutableStateFlow nach außen leaken

### Pattern 2: Repository.save() mit Conditional
```kotlin
fun save(e: Entity): Long = if (e.id == 0L) dao.insert(e) else { dao.update(e); e.id }
```
- **Warum:** Single entry point für insert/update
- **Wo:** Alle Repositories
- **Edge Case:** categoryId=0 bedeutet "neu", nicht null!

### Pattern 3: Pattern Matching - AND-Logic
```kotlin
fun matchTextPattern(patternValue: String, description: String): Boolean {
    val keywords = patternValue.split("|").map { normalize(it) }
    return keywords.all { kw → description.contains(kw) }
}
```
- **Warum:** Alle Keywords müssen da sein (user expectation)
- **Nicht:** OR-Logic (falsche Positives)

## ❌ FEHLGESCHLAGEN

### Anti-Pattern 1: SQL LIKE für Complex Matching
- **Warum failed:** Zu primitiv für AND-Logic, Satzzeichen-Handling
- **Stattdessen:** Kotlin-Logik in Repository

### Anti-Pattern 2: android.util.Log
- **Warum failed:** Logs tauchen nicht in App-UI auf
- **Stattdessen:** AppLogger nur

### Anti-Pattern 3: Fragment mit Direct DAO Access
- **Warum failed:** Keine Lifecycle-Sicherheit, schwer zu testen
- **Stattdessen:** ViewModel → Repository → DAO

## 🔥 GOTCHAS (Kleine Fehler → große Probleme)

### Gotcha 1: StateFlow.value vs .first()
- **Problem:** `.value` bei lazy StateFlow kann null/empty sein
- **Fix:** `.first()` wartet bis initialized
- **Test:** `categoryRepository.observeAll().first()` in Kotlin

### Gotcha 2: Fragment Binding Lifecycle
- **Problem:** binding nach onDestroyView benutzen → Crash
- **Fix:** `private var _binding: Binding? = null` + null check + set null in onDestroyView

### Gotcha 3: Satzzeichen bei Pattern Matching
- **Problem:** "edeka.de" nach `.replace(punct, "")` wird "edekade" (zusammengeklebt)
- **Fix:** `.replace(punct, " ")` (Leerzeichen statt Entfernen)

## 🎯 DESIGN DECISIONS (Warum so?)

### Decision 1: Kategorie Level 1-3
- **Warum nicht:** Unbegrenzte Tiefe?
  - Zu komplex für UI (Tree-Rendering)
  - DB-Performance bei tiefen Hierarchien
- **Lösung:** Feste 3 Level mit Overflow-Warnung

### Decision 2: 150 TX pro Sync Limit
- **Warum nicht:** Alles auf einmal?
  - BBBank API Limitation
  - Memory-Sicherheit (3000+ TX = RAM-Druck)
- **Lösung:** Bulk-Sequenzen mit Progress

### Decision 3: Pattern Confidence Score
- **Warum nicht:** Nur 0 oder 1?
  - Uncertainty quantifizieren (0.3=unwahrscheinlich, 0.9=sehr sicher)
  - Für future ML-Training
```

**OpenCode Integration:**
- **Skill:** `patterns-learned` - lädt bei jedem Task
- **AGENTS.md Rule:** "Konsultiere PATTERNS-LEARNED.md bevor du entscheidest"

### 3.2 Decision Journal (Retrospektive)

**Datei: `docs/DECISIONS.md`**

```markdown
# Architecture Decisions

## ADR-001: Why StateFlow + ViewModel (2026-04)
- **Problem:** Need reactive UI updates with lifecycle awareness
- **Options Considered:** 
  - LiveData (deprecated)
  - RxJava (overkill für diese App)
  - Plain Coroutines (keine backpressure)
- **Decision:** StateFlow
- **Rationale:** Built-in backpressure, Kotlin-native, lifecycle integration
- **Status:** ✅ Proven

## ADR-002: Why 3-Level Category Hierarchy (2026-05)
- **Problem:** Users want categories with subcategories
- **Options:**
  - Unlimited depth (too complex)
  - 3 levels max (implemented)
  - Flat list only (too simple)
- **Decision:** 3 levels with overflow warning
- **Rationale:** UI manageable, DB fast, covers 99% use cases
- **Status:** ✅ Working, but could limit power-users

## ADR-003: Why Custom PatternMatcher vs SQL LIKE (2026-06)
- **Problem:** TEXT patterns need AND-logic, not simple substring
- **Options:**
  - SQL LIKE (primitive)
  - Full-text search (overkill)
  - Custom Kotlin (implemented)
- **Decision:** Custom PatternMatcher
- **Rationale:** Full control, testable, no DB overhead
- **Status:** ✅ Working, easy to extend
```

**OpenCode Integration:**
- **Skill:** `decision-journal` - context für "warum wurde so entschieden?"
- Modelle verstehen constraints besser → bessere Entscheidungen

### 3.3 Technology Stack Registry

**Datei: `docs/TECH-STACK.md`**

```markdown
# Technology Stack & Versioning

## Core
- **Kotlin:** 1.9.20 (app/build.gradle.kts:15)
- **Android SDK:** Min 26 (Android 8), Target 34 (Android 14)
- **Gradle:** 8.7, AGP 8.3.0

## Database
- **Room:** androidx.room:room-runtime:2.5.2
- **Type:** SQLite (on-device)
- **Migrations:** Manual (no auto-migrations)

## DI
- **Hilt:** dagger.hilt:hilt-android:2.x
- **Scopes:** @Singleton, @ActivityRetention, no unscoped

## Async
- **Coroutines:** kotlinx-coroutines-android:1.7.x
- **Pattern:** viewModelScope.launch { repo.method() }
- **Exception:** CancellationException always re-throw

## UI
- **Material3:** com.google.android.material:material:1.9.x
- **ViewBinding:** Official (no DataBinding)
- **Navigation:** Jetpack Navigation (nav_graph.xml)

## Banking
- **FinTS/HBCI:** hbci4java:3.1.88
- **Protocol:** FinTS 3.0 primary, fallback 2.2 (BBBank)
- **Parsing:** Custom CAMT parser + HbciCamtPatcher

## Build & Testing
- **Unit Tests:** JUnit 4 (local tests)
- **Integration:** Manual testing only (no Espresso)
- **CI/CD:** Disabled (v332+, manual builds)
- **APK Distribution:** NAS + FTP (mama-razzi)

## Key Dependencies NOT Used
- ❌ RxJava (too heavy for this scope)
- ❌ Retrofit (only internal APIs)
- ❌ Room Migrations (manual control)
- ❌ DataBinding (ViewBinding simpler)
- ❌ Jetpack Compose (too new, Fragment-based UI better for now)
```

**OpenCode Integration:**
- **AGENTS.md Rule:** "Tech stack ist sakrosankt - keine neuen Dependencies ohne Begründung"
- **Pre-Commit Check:** Validate no unapproved libs in build.gradle.kts

---

## 4. TESTING & VERIFICATION (Automatisierung)

### 4.1 Unit Test Templates (schneller schreiben)

```kotlin
// tests/TransactionRepositoryTest.kt (Template)
class TransactionRepositoryTest {
    private lateinit var dao: TransactionDao
    private lateinit var repo: TransactionRepository
    
    @Before
    fun setup() {
        dao = mockk()
        repo = TransactionRepository(dao)
    }
    
    @Test
    fun `save with id=0 calls insert`() {
        // Given
        val tx = Transaction(id = 0L, ...)
        coEvery { dao.insert(any()) } returns 1L
        
        // When
        val result = runBlocking { repo.save(tx) }
        
        // Then
        coVerify { dao.insert(tx) }
        assertEquals(1L, result)
    }
}
```

**OpenCode Integration:**
- **Command:** `/generate-test TransactionRepository`
- AI generates test template + key cases

### 4.2 Build Verification (Pre-Push)

**Script: `scripts/verify-before-push.ps1`**

```powershell
# Linting
./gradlew.bat detekt

# Unit Tests
./gradlew.bat testDebugUnitTest

# Build
./gradlew.bat assembleDebug

# If all pass → OK to push
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ All checks passed - ready to push"
} else {
    Write-Host "❌ Checks failed - fix first"
    exit 1
}
```

**OpenCode Rule:**
```markdown
Before pushing: Run verify-before-push.ps1
If it fails, AI must fix immediately
```

### 4.3 Regression Testing (schnelle Feedback)

**Datei: `scripts/quick-test.cmd`**

```batch
@echo off
REM Quick smoke test - runs in <30 seconds
echo Testing critical paths...

REM Pattern Matching
echo Testing PatternMatcher...
.\gradlew.bat testDebugUnitTest --tests "*PatternMatcher*"

REM Category Hierarchy
echo Testing CategoryRepository...
.\gradlew.bat testDebugUnitTest --tests "*CategoryRepository*"

REM Build
echo Building APK...
.\gradlew.bat assembleDebug

if %ERRORLEVEL% EQU 0 (
    echo ✅ Smoke test passed
) else (
    echo ❌ Smoke test FAILED
    exit /b 1
)
```

---

## 5. INTEGRATION: Alles zusammen

### 5.1 OpenCode Skill Stack (vollständig)

```powershell
# .opencode/AGENTS.md

## Skills Loaded
- `code-quality-checks` → Detekt rules, linting
- `android-templates` → Fragment, ViewModel, Repository templates
- `project-index` → Navigation, dependencies, quick links
- `patterns-learned` → Do's, Don'ts, Gotchas
- `decision-journal` → Warum-Entscheidungen
- `tech-stack-registry` → Versions, libraries

## Context Hierarchy
1. Load Global AGENTS.md (2KB base rules)
2. Detect feature → Load feature-specific section (500B)
3. User asks specific file → Load (on-demand)
4. AI generates code → Apply templates + linting

## Token Optimization
- Base context: ~2KB (reusable across all tasks)
- Feature context: ~500B-2KB (added only if mentioned)
- Full file: only on-demand (30-50KB max)
- Total first task: ~5KB (vs. 100KB full codebase read)
- Subsequent tasks: +0-2KB (feature-specific only)
```

### 5.2 Workflow (Developer POV)

```
1. Developer: "Neue Transaction-Seite mit Daten-Filter"
   ↓
2. OpenCode:
   - Lädt: Base Rules (2KB) + Transaction Feature (1KB)
   - Liest: PROJECT-INDEX.md (2KB) für schnelle Navigation
   - Prüft: PATTERNS-LEARNED.md für Best-Practices
   ↓
3. OpenCode generiert:
   - Fragment (aus android-templates)
   - ViewModel (aus android-templates)
   - Mit Detekt-Rules beachtet
   - Naming-Konventionen eingehalten
   ↓
4. Pre-Commit:
   - Detekt linting → Fehler?
   - Unit tests → Fehler?
   - Build → Fehler?
   ↓
5. Commit + Push (nur wenn alle Checks pass)
```

### 5.3 Emergency: Kleineres Modell wechseln

**Szenario:** Claude-Haiku statt Sonnet (Token-Reduktion 10x)

**Strategie:**
1. **Hyper-spezifische Prompts** (statt "implementiere X")
   - "Füge diese 3 Zeilen CODE nach Zeile 45 ein" (exakt)
   - "Rufe diese Funktion auf" (nicht "refactor")
2. **Maximal Kontext Pre-Selection**
   - Nur relevante 2-3 Files laden
   - Nicht komplette Module
3. **Template-Heavy Approach**
   - Modell generiert fast nichts → mostly copy-paste with tweaks
4. **Multi-Turn Tasks**
   - 1 Prompt = 1 kleine Änderung
   - Statt "implementiere ganze Feature" → 5 kleine Tasks

---

## 6. KONKRETE UMSETZUNG (Für MyBudgets jetzt)

### Phase 1: Foundation (diese Woche)
- [ ] `PROJECT-INDEX.md` generieren (scripts/generate-project-index.ps1)
- [ ] `docs/PATTERNS-LEARNED.md` schreiben (30 min, aus Erfahrung)
- [ ] `docs/DECISIONS.md` schreiben (20 min)
- [ ] `docs/TECH-STACK.md` auto-gen aus build.gradle.kts
- [ ] Template-Ordner: `templates/` mit ViewModel, Fragment, Repository

### Phase 2: Automation (nächste Woche)
- [ ] `scripts/verify-before-push.ps1` (detekt + test + build)
- [ ] `scripts/quick-test.cmd` (smoke tests)
- [ ] `.editorconfig` mit strict rules
- [ ] `app/detekt-rules.yml` konfigurieren

### Phase 3: Skills (später)
- [ ] `code-quality-checks` Skill entwickeln
- [ ] `android-templates` Skill
- [ ] `patterns-learned` Skill (markdown-basiert)
- [ ] `decision-journal` Skill

### Phase 4: Integration (kontinuierlich)
- [ ] AGENTS.md erweitern basierend auf neuen Learnings
- [ ] Decision Journal bei großen Changes updaten
- [ ] Pattern Library bei Bug-Fixes erweitern

---

## 7. METRIKEN: Wie messen?

**Vor dieser Optimierung:**
- Durchschnittliches Task: 50KB Context + 5-10 min
- Error Rate: ~15% (falsches Naming, Architecture-Violations)
- Token pro Feature: ~50-80K

**Nach Optimierung (Ziel):**
- Durchschnittliches Task: 5KB Context + 2-3 min
- Error Rate: ~5% (dank automatischer Checks)
- Token pro Feature: ~10-20K (4-8x Reduktion!)

**Messbar durch:**
```powershell
# metrics/token-usage.log
# 2026-06-03 | Task: "Add filter" | Context: 5KB | Tokens: 12K | Time: 2min | Errors: 0
# 2026-06-03 | Task: "Fix pattern" | Context: 3KB | Tokens: 8K | Time: 1min | Errors: 0
```

---

## Zusammenfassung: Die 3 Säulen

| Säule | Werkzeug | Effekt |
|-------|----------|--------|
| **Code Quality** | Detekt + Templates + Linting | Automatische Fehler-Prävention |
| **Kontext** | PROJECT-INDEX + Feature-Sections | 80% Token-Reduktion |
| **Domain Knowledge** | PATTERNS-LEARNED + DECISIONS | Bessere AI-Entscheidungen |

**Resultat:** Kleine Modelle (Haiku) können wie große Modelle (Sonnet) coden - mit richtiger Setup!
