# IMPLEMENTATION PLAYBOOK: AI Development Optimization
## Phase 1-4: Konkrete Schritte mit OpenCode Skills

---

## PHASE 1: Foundation (Woche 1-2) - 6-8 Stunden
**Fokus:** Dokumentation + Kontext-Struktur

### 1.1 ✅ PROJECT-INDEX.md Script
**Aufwand:** 1 Stunde  
**Datei:** `scripts/Generate-ProjectIndex.ps1`  
**Status:** Siehe oben (PowerShell-Script fertig)

**Nächster Schritt:**
```powershell
# Einmalig ausführen
.\scripts\Generate-ProjectIndex.ps1

# In Git committen
git add PROJECT-INDEX.md
git commit -m "docs: Generate project index"
```

**Automatisierung:** Später als Pre-Commit Hook (Phase 2)

---

### 1.2 ✅ PATTERNS-LEARNED.md Schreiben
**Aufwand:** 1.5 Stunden  
**Datei:** `docs/PATTERNS-LEARNED.md`  
**Status:** Siehe oben (Markdown-Template fertig)

**Prozess:**
1. Kopiere Template in `docs/PATTERNS-LEARNED.md`
2. Füge MyBudgets-spezifische Patterns hinzu (aus Commits)
3. Commit:
   ```powershell
   git add docs/PATTERNS-LEARNED.md
   git commit -m "docs: Patterns learned from 2 months development"
   ```

---

### 1.3 DECISIONS.md + TECH-STACK.md
**Aufwand:** 1 Stunde  
**Dateien:** `docs/DECISIONS.md`, `docs/TECH-STACK.md`

**DECISIONS.md (Architektur-Dokumentation):**
```markdown
# Architecture Decisions

## ADR-001: StateFlow für UI State Management
**Date:** 2026-03-2x  
**Status:** ✅ Accepted  
**Problem:** Need reactive UI updates with lifecycle awareness  
**Decision:** StateFlow + ViewModel + repeatOnLifecycle  
**Rationale:** Built-in backpressure, Kotlin-native, lifecycle integration  
**Trade-offs:** More boilerplate than LiveData, but more control  
**Consequences:** Type-safe state, easy to test  

## ADR-002: 3-Level Category Hierarchy  
**Date:** 2026-05-10  
**Status:** ✅ Accepted  
**Problem:** Users want nested categories but UI must remain manageable  
**Decision:** Max 3 levels (L1-L3) with overflow warning  
**Rationale:** Balance between user needs and implementation complexity  
**Trade-offs:** Limits power-users, but covers 99% of use cases  
**Consequences:** Simple tree rendering, fast DB queries  

## ADR-003: Custom PatternMatcher (Kotlin, not SQL)  
**Date:** 2026-06-03  
**Status:** ✅ Accepted  
**Problem:** SQL LIKE cannot handle AND-logic + punctuation normalization  
**Decision:** PatternMatcher.kt with Kotlin implementation  
**Rationale:** Full control, testable, no DB overhead  
**Trade-offs:** More code, but better performance + flexibility  
**Consequences:** Patterns now work correctly, easy to extend  

## ADR-004: AppLogger Custom Implementation  
**Date:** 2026-04-15  
**Status:** ✅ Accepted  
**Problem:** android.util.Log not visible in app, hard to debug user issues  
**Decision:** In-memory buffer + UI Logs View + export capability  
**Rationale:** Users can export logs, support team can debug  
**Trade-offs:** More boilerplate than android.util.Log  
**Consequences:** Better UX, easier support  
```

**TECH-STACK.md (Auto-generiert aus build.gradle.kts):**
```markdown
# Technology Stack & Versions

## Build
- Gradle: 8.7
- AGP (Android Gradle Plugin): 8.3.0
- Kotlin: 1.9.20

## Android SDK
- Min SDK: 26 (Android 8.0 Froyo)
- Target SDK: 34 (Android 14)
- Compile SDK: 34

## Key Libraries
| Library | Version | Use Case | Decision |
|---------|---------|----------|----------|
| androidx.room:room-runtime | 2.5.2 | Local database | REQUIRED |
| com.google.dagger:hilt-android | 2.x | Dependency injection | Core |
| androidx.lifecycle | 2.6.x | Lifecycle management | Core |
| com.google.android.material | 1.9.x | UI components | Core |
| hbci4java | 3.1.88 | Banking FinTS protocol | CRITICAL |
| junit | 4.x | Unit testing | Core |

## Explicitly NOT Used
- ❌ Retrofit (no external APIs)
- ❌ RxJava (StateFlow sufficient)
- ❌ Jetpack Compose (Fragment-based UI simpler)
- ❌ Room Migrations (manual control preferred)
- ❌ DataBinding (ViewBinding simpler)

## Build Profiles
- **Debug:** Full logging, slow
- **Release:** Proguard, optimized (not used yet)
```

**Commit:**
```powershell
git add docs/DECISIONS.md docs/TECH-STACK.md
git commit -m "docs: Architecture decisions + tech stack registry"
```

---

### 1.4 Templates Ordner
**Aufwand:** 1.5 Stunden  
**Ordner:** `templates/`  
**Dateien:** Fragment, ViewModel, Repository, Adapter

**Struktur:**
```
templates/
├── Fragment.template.kt
├── ViewModel.template.kt
├── Repository.template.kt
├── Adapter.template.kt
├── DialogFragment.template.kt
└── README.md (Benutzungsanleitung)
```

**templates/Fragment.template.kt:**
```kotlin
package de.mybudgets.app.ui.${FEATURE_LOWERCASE}

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.databinding.Fragment${FEATURE}Binding
import de.mybudgets.app.viewmodel.${FEATURE}ViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ${FEATURE}Fragment : Fragment() {
    private var _binding: Fragment${FEATURE}Binding? = null
    private val binding get() = _binding!!
    private val viewModel: ${FEATURE}ViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Fragment${FEATURE}Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        handleState(state)
                    }
                }
            }
        }
    }

    private fun handleState(state: ${FEATURE}State) {
        when (state) {
            is ${FEATURE}State.Idle -> {
                // TODO: Handle idle state
            }
            is ${FEATURE}State.Loading -> {
                // TODO: Show loading
            }
            is ${FEATURE}State.Success -> {
                // TODO: Show data
            }
            is ${FEATURE}State.Error -> {
                // TODO: Show error
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

**templates/ViewModel.template.kt:**
```kotlin
package de.mybudgets.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mybudgets.app.data.model.${ENTITY}
import de.mybudgets.app.data.repository.${ENTITY}Repository
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ${FEATURE}State {
    object Idle : ${FEATURE}State()
    data class Loading(val message: String = "") : ${FEATURE}State()
    data class Success(val data: Any) : ${FEATURE}State()
    data class Error(val message: String) : ${FEATURE}State()
}

@HiltViewModel
class ${FEATURE}ViewModel @Inject constructor(
    private val repository: ${ENTITY}Repository
) : ViewModel() {

    private val _state = MutableStateFlow<${FEATURE}State>(${FEATURE}State.Idle)
    val state: StateFlow<${FEATURE}State> = _state.asStateFlow()

    // TODO: Add business logic methods

    private fun setState(newState: ${FEATURE}State) {
        _state.value = newState
        AppLogger.i("${FEATURE}ViewModel", "State: ${newState::class.simpleName}")
    }
}
```

**templates/Repository.template.kt:**
```kotlin
package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.${ENTITY}Dao
import de.mybudgets.app.data.model.${ENTITY}
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ${ENTITY}Repository @Inject constructor(
    private val dao: ${ENTITY}Dao
) {
    
    fun observeAll(): Flow<List<${ENTITY}>> = dao.observeAll()
    
    suspend fun getById(id: Long): ${ENTITY}? = dao.getById(id)
    
    suspend fun save(entity: ${ENTITY}): Long = 
        if (entity.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            entity.id
        }.also {
            AppLogger.i("${ENTITY}Repository", "Saved: id=$it")
        }
    
    suspend fun delete(entity: ${ENTITY}) {
        dao.delete(entity)
        AppLogger.i("${ENTITY}Repository", "Deleted: id=${entity.id}")
    }
}
```

**templates/README.md:**
```markdown
# Code Templates for MyBudgets

## Usage

### Generate from Template
When creating a new Feature:
1. Copy relevant template (Fragment.template.kt, ViewModel.template.kt, etc.)
2. Replace placeholders:
   - ${FEATURE} → CamelCase feature name (e.g., "Transaction", "Category")
   - ${FEATURE_LOWERCASE} → lowercase (e.g., "transaction", "category")
   - ${ENTITY} → Entity class name
3. Implement TODO sections

### Example
Creating new "Budget" feature:
```kotlin
// Copy Fragment.template.kt
// Replace ${FEATURE} → "Budget"
// Replace ${FEATURE_LOWERCASE} → "budget"
// Result: BudgetFragment.kt

class BudgetFragment : Fragment() { ... }
```

## Template Checklist
- [ ] Correct package name
- [ ] All ${PLACEHOLDERS} replaced
- [ ] AppLogger imports (never android.util.Log!)
- [ ] @HiltViewModel or @AndroidEntryPoint
- [ ] StateFlow<State> with sealed State class
- [ ] Lifecycle handling (repeatOnLifecycle)
- [ ] Binding null-check + onDestroyView cleanup
- [ ] No TODO sections left

## Don't
- ❌ Modify templates themselves (they're for reference)
- ❌ Delete TODO sections without implementing
- ❌ Add android.util.Log (use AppLogger)
- ❌ Create Fragment without ViewModel
- ❌ Skip lifecycle management
```

**Commit:**
```powershell
mkdir -p templates
Copy-Item ".\docs\templates\*.kt" -Destination ".\templates\"
git add templates/
git commit -m "docs: Code templates for Fragment, ViewModel, Repository"
```

---

## PHASE 2: Automation (Woche 2-3) - 4-6 Stunden
**Fokus:** Scripts + Linting + Pre-Commit Hooks

### 2.1 verify-before-push.ps1
**Aufwand:** 1.5 Stunden  
**Datei:** `scripts/Verify-BeforePush.ps1`

```powershell
<#
.DESCRIPTION
Pre-push verification: Detekt + Unit Tests + Build
Exits with error if any check fails
#>

param(
    [switch]$SkipTests = $false,
    [switch]$SkipBuild = $false
)

$ErrorActionPreference = "Stop"

Write-Host "════════════════════════════════════════"
Write-Host "  PRE-PUSH VERIFICATION"
Write-Host "════════════════════════════════════════"

# Step 1: Detekt (Kotlin Linting)
Write-Host "`n[1/4] Running Detekt linting..."
try {
    & .\gradlew.bat detekt | Out-Null
    Write-Host "✅ Detekt passed"
} catch {
    Write-Host "❌ Detekt FAILED"
    exit 1
}

# Step 2: Unit Tests
if (-not $SkipTests) {
    Write-Host "`n[2/4] Running unit tests..."
    try {
        & .\gradlew.bat testDebugUnitTest | Out-Null
        Write-Host "✅ Unit tests passed"
    } catch {
        Write-Host "❌ Unit tests FAILED"
        exit 1
    }
} else {
    Write-Host "`n[2/4] Skipping unit tests (-SkipTests)"
}

# Step 3: Build
if (-not $SkipBuild) {
    Write-Host "`n[3/4] Building APK..."
    try {
        & .\gradlew.bat assembleDebug | Out-Null
        Write-Host "✅ Build passed"
    } catch {
        Write-Host "❌ Build FAILED"
        exit 1
    }
} else {
    Write-Host "`n[3/4] Skipping build (-SkipBuild)"
}

# Step 4: Project Index Update
Write-Host "`n[4/4] Updating PROJECT-INDEX.md..."
try {
    & .\scripts\Generate-ProjectIndex.ps1 | Out-Null
    Write-Host "✅ Project index updated"
} catch {
    Write-Host "⚠️  Project index update failed (non-fatal)"
}

Write-Host "`n════════════════════════════════════════"
Write-Host "✅ ALL CHECKS PASSED - READY TO PUSH"
Write-Host "════════════════════════════════════════"
```

**Ausführung:**
```powershell
.\scripts\Verify-BeforePush.ps1

# Oder mit Skips (schnell, für hotfixes)
.\scripts\Verify-BeforePush.ps1 -SkipTests -SkipBuild
```

---

### 2.2 .editorconfig
**Aufwand:** 30 Min  
**Datei:** `.editorconfig` (Projekt-Root)

```ini
# EditorConfig - Automatische Formatierung
root = true

# Kotlin files
[*.{kt,kts}]
indent_style = space
indent_size = 4
end_of_line = lf
charset = utf-8
trim_trailing_whitespace = true
insert_final_newline = true
max_line_length = 120

# XML
[*.xml]
indent_style = space
indent_size = 4
end_of_line = lf

# Gradle
[*.gradle]
indent_style = space
indent_size = 4
end_of_line = lf

# JSON
[*.json]
indent_style = space
indent_size = 2
end_of_line = lf
```

---

### 2.3 detekt-rules.yml
**Aufwand:** 1 Stunde  
**Datei:** `app/detekt-rules.yml` (neu) oder update bestehend

```yaml
# Detekt configuration - Kotlin linting rules
build:
  maxIssues: 10
  excludeCorrectable: false

output-reports:
  active: true
  exclude:
    - TxtOutputReport
    - XmlOutputReport
    - HtmlOutputReport

processors:
  active: true
  exclude:
    - DetektProgressListener

console-reports:
  active: true

# Rules Configuration
rules:
  # Naming
  ClassNaming:
    active: true
    classPattern: '[A-Z][a-zA-Z0-9]*'
  
  FunctionNaming:
    active: true
    functionPattern: '(^[a-z][a-zA-Z0-9]*$)|(^`.*`$)'
  
  VariableNaming:
    active: true
    variablePattern: '[a-z][a-zA-Z0-9]*'
    privateVariablePattern: '(_)?[a-z][a-zA-Z0-9]*'
  
  # Complexity
  CyclomaticComplexity:
    active: true
    threshold: 15  # Max McCabe complexity
  
  LongMethod:
    active: true
    threshold: 60  # Max lines in method
  
  LongParameterList:
    active: true
    threshold: 6  # Max parameters
  
  # Style
  MaxLineLength:
    active: true
    maxLineLength: 120  # Match EditorConfig
    ignoreBacktickedIdentifier: true
  
  MagicNumber:
    active: true
    ignoreNumbers: '-1,0,1,2'  # Common cases
  
  # Warnings
  UnusedImports:
    active: true
  
  UnusedPrivateMember:
    active: true
  
  UnnecessaryAbstractClass:
    active: true
```

---

### 2.4 Pre-Commit Hook
**Aufwand:** 30 Min  
**Datei:** `.git/hooks/pre-commit`

```powershell
#!/bin/bash
# Pre-commit hook: Run Detekt before commit

echo "🔍 Running pre-commit checks..."

# Run Detekt
./gradlew.bat detekt > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "❌ Detekt check failed!"
    echo "Run: ./gradlew.bat detekt"
    exit 1
fi

# Update PROJECT-INDEX
powershell -File scripts/Generate-ProjectIndex.ps1 > /dev/null 2>&1

echo "✅ Pre-commit checks passed"
exit 0
```

**Installieren:**
```powershell
# Create hook file
New-Item -Path .git/hooks/pre-commit -ItemType File -Force

# Add content (PowerShell version for Windows)
@'
param()
Write-Host "Running pre-commit checks..."
& .\gradlew.bat detekt | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Detekt failed"
    exit 1
}
& .\scripts\Generate-ProjectIndex.ps1 | Out-Null
Write-Host "Pre-commit checks passed"
'@ | Set-Content -Path .git/hooks/pre-commit

# Make executable
chmod +x .git/hooks/pre-commit
```

---

## PHASE 3: OpenCode Skills (Woche 3-4) - 6-8 Stunden
**Fokus:** Reusable Skills für AI-Unterstützung

### 3.1 Skill: `mybudgets-code-quality`
**Struktur:**
```
~/.config/opencode/skills/mybudgets-code-quality/
├── SKILL.md
├── detekt-rules.yml (reference)
├── templates/
│   ├── Fragment.template.kt
│   ├── ViewModel.template.kt
│   └── Repository.template.kt
└── examples/
    ├── good-stateflow.kt
    └── bad-antipattern.kt
```

**SKILL.md:**
```markdown
# MyBudgets Code Quality Skill

Provides code quality rules, linting configuration, and templates for AI-assisted development.

## Load This Skill When:
- Creating new features (Fragment, ViewModel, Repository)
- Fixing code quality issues
- Working with patterns or state management
- Need architecture guidance

## What's Included:
- Detekt linting rules (strict)
- Code templates (Fragment, ViewModel, Repository, Adapter)
- Good/bad code examples
- Architecture layer rules
- Naming conventions

## Usage Examples:
- "Generate new transaction detail fragment" → Use Fragment.template.kt
- "What's wrong with this code?" → Check examples/bad-antipattern.kt
- "How should ViewModel handle state?" → See templates/ViewModel.template.kt
- "Write unit test for..." → Use patterns from examples/

## Key Rules:
1. **NEVER** android.util.Log → Always AppLogger
2. **ALWAYS** StateFlow<State> with sealed State class
3. **ALWAYS** repeatOnLifecycle(Lifecycle.State.STARTED)
4. **ALWAYS** Null-out binding in onDestroyView()
5. **NEVER** Skip architecture layers (Fragment→ViewModel→Repository→DAO)
```

### 3.2 Skill: `mybudgets-patterns`
**Struktur:**
```
~/.config/opencode/skills/mybudgets-patterns/
├── SKILL.md
├── PATTERNS-LEARNED.md (reference)
├── PatternMatcher.kt (annotated)
└── examples/
    ├── pattern-text-matching.kt
    ├── pattern-iban-matching.kt
    └── pattern-edge-cases.kt
```

### 3.3 Skill: `mybudgets-navigation`
**Struktur:**
```
~/.config/opencode/skills/mybudgets-navigation/
├── SKILL.md
├── PROJECT-INDEX.md (reference)
├── feature-map.json
└── quick-links.md
```

**feature-map.json:**
```json
{
  "features": {
    "transactions": {
      "files": ["ui/transactions/TransactionFragment.kt", "ui/transactions/TransactionDetailFragment.kt"],
      "viewModel": "TransactionViewModel",
      "repository": "TransactionRepository",
      "dao": "TransactionDao",
      "model": "Transaction"
    },
    "categories": {
      "files": ["ui/categories/CategoriesFragment.kt", "ui/categories/CategoryAdapter.kt"],
      "viewModel": "CategoryViewModel",
      "repository": "CategoryRepository",
      "dao": "CategoryDao",
      "model": "Category"
    }
  }
}
```

---

## PHASE 4: AGENTS.md Integration (Finale) - 2-3 Stunden

### 4.1 Update AGENTS.md Project-Spezifisch

**Datei:** `AGENTS.md` (Projekt-Root) - Neu oder Update

```markdown
# MyBudgets - AI Development Rules

**Project:** Budget tracking Android app with FinTS banking integration  
**Version:** v1.0.50+  
**Last Updated:** 2026-06-03

## CRITICAL RULES (Violations = Auto-Reject)

### 1. Logging ONLY via AppLogger
❌ FORBIDDEN:
\`\`\`kotlin
import android.util.Log
Log.e(TAG, "error")
\`\`\`

✅ REQUIRED:
\`\`\`kotlin
import de.mybudgets.app.util.AppLogger
AppLogger.e(TAG, "error")
\`\`\`

**Reason:** Logs appear in App UI + export capability

### 2. StateFlow Pattern for State Management
EVERY ViewModel MUST follow:
\`\`\`kotlin
private val _state = MutableStateFlow<XxxState>(XxxState.Idle)
val state: StateFlow<XxxState> = _state.asStateFlow()
\`\`\`

### 3. Architecture Layers (STRICT)
- **data/model/\*.kt:** @Entity only, NO logic
- **data/db/\*Dao.kt:** @Dao queries only, NO logic
- **data/repository/\*Repository.kt:** Business logic HERE
- **viewmodel/\*ViewModel.kt:** State management only
- **ui/\*Fragment.kt:** Presentation only, NO logic

**Rule:** NEVER skip layers (Fragment → ViewModel → Repository → DAO)

### 4. Pattern Matching: AND-Logic
Text patterns use AND-logic (ALL keywords must match):
\`\`\`kotlin
Pattern: "EDEKA|Lebensmittel"
✅ TX: "Einkauf EDEKA Lebensmittel" → MATCH
❌ TX: "EDEKA Abteilung" → NO MATCH (missing Lebensmittel)
\`\`\`

### 5. Fragment Binding Lifecycle
\`\`\`kotlin
private var _binding: FragmentXxxBinding? = null
private val binding get() = _binding!!

override fun onDestroyView() {
    _binding = null  // MUST clear
    super.onDestroyView()
}
\`\`\`

## Skills to Load

When working on:
- **New feature:** Load \`mybudgets-code-quality\` skill
- **Pattern issues:** Load \`mybudgets-patterns\` skill
- **Navigation:** Load \`mybudgets-navigation\` skill
- **Database:** Load \`mybudgets-banking\` skill (if banking-related)

## Context Strategy

1. **First Time Reading This Project:**
   - Load: PROJECT-INDEX.md (quick orientation)
   - Load: AGENTS.md (this file)
   - Load: PATTERNS-LEARNED.md (dos & don'ts)

2. **Working on Specific Feature:**
   - Load: Feature-specific rules (from Phase 1)
   - Load: Relevant skill (from Phase 3)
   - Load: Code examples (from templates/)

3. **Fixing Bug:**
   - Load: PATTERNS-LEARNED.md (gotchas section)
   - Load: Related test files
   - Minimal file reads (be token-efficient!)

## Commands Reference

\`\`\`bash
# Pre-push verification
./scripts/Verify-BeforePush.ps1

# Update project index
./scripts/Generate-ProjectIndex.ps1

# Build APK
./gradlew.bat assembleDebug

# Linting only
./gradlew.bat detekt

# Tests only
./gradlew.bat testDebugUnitTest
\`\`\`

## Feature Rules

### Transaction Feature
- **ViewModel:** TransactionViewModel (line 40+)
- **State Pattern:** StateFlow<State> with Idle|Loading|Success|Error
- **Key Method:** suggestCategoryId(description, amount, type)
- **Pattern Matching:** Uses PatternMatcher (TEXT=AND, IBAN=substring)

### Category Feature
- **Critical:** Move validation in CategoryDragDropHelper
- **Pattern:** Hierarchical (Level 1-3 max)
- **NO TOUCH:** CategoryRepository.moveCategory() uses db.withTransaction{}

### Banking/FinTS (NO TOUCH ZONE)
- **Critical Files:** data/banking/FintsService.kt, data/banking/camt/
- **CustomCamtParser:** Proven stable since 2026-05-12
- **Only Touch If:** New bank pattern detected
- **Fallback:** BBBank uses FinTS 3.0 primary, 2.2 secondary

## Testing

- Unit tests: \`./gradlew.bat testDebugUnitTest\`
- No Espresso (too fragile)
- Manual integration tests on real device
- Pattern matching: Test edge cases carefully!

## Metrics

- Detekt issues: < 10 allowed
- Max line length: 120 chars
- Max method length: 60 lines
- Max complexity: McCabe < 15

---

## Phase Implementation Status

- [x] Phase 1: Foundation (INDEX, PATTERNS, TEMPLATES)
- [x] Phase 2: Automation (Scripts, Linting)
- [ ] Phase 3: Skills (OpenCode skills)
- [ ] Phase 4: Integration (Final AGENTS.md)

---

**Maintained by:** AI + Developer  
**For Questions:** See PROJECT-INDEX.md and PATTERNS-LEARNED.md
```

---

## ZUSAMMENFASSUNG: IMPLEMENTATION ROADMAP

| Phase | Woche | Aufwand | Status | Files |
|-------|-------|---------|--------|-------|
| 1.1 | 1 | 1h | ✅ Ready | PROJECT-INDEX.md script |
| 1.2 | 1 | 1.5h | ✅ Ready | PATTERNS-LEARNED.md |
| 1.3 | 1 | 1h | ✅ Ready | DECISIONS.md, TECH-STACK.md |
| 1.4 | 1 | 1.5h | ✅ Ready | templates/*.kt |
| 2.1 | 2 | 1.5h | ✅ Ready | Verify-BeforePush.ps1 |
| 2.2 | 2 | 0.5h | ✅ Ready | .editorconfig |
| 2.3 | 2 | 1h | ✅ Ready | detekt-rules.yml |
| 2.4 | 2 | 0.5h | ✅ Ready | .git/hooks/pre-commit |
| 3.1-3.4 | 3-4 | 6-8h | ⏳ TBD | 4 OpenCode Skills |
| 4 | 4 | 2-3h | ⏳ TBD | Final AGENTS.md |

**Total:** ~20 Stunden über 4 Wochen

---

## NÄCHSTE SCHRITTE

1. **Sofort (heute):**
   - Phase 1.1: `scripts/Generate-ProjectIndex.ps1` in Repo committen
   - Phase 1.2: `docs/PATTERNS-LEARNED.md` schreiben (copy-paste template, customize)

2. **Diese Woche:**
   - Phase 1.3-1.4: DECISIONS.md + templates/
   - Phase 2: Scripts + Linting

3. **Nächste Woche:**
   - Phase 3: OpenCode Skills development
   - Phase 4: Final AGENTS.md

**Question:** Sollen wir jetzt Phase 1.1 starten (PROJECT-INDEX.md script)?
```

**Ende des Playbooks**

