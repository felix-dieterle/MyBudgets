<#
.DESCRIPTION
Generates PROJECT-INDEX.md with auto-updated metrics, file structure, and quick-reference guides
Runs before every commit (can be manual or pre-commit hook)
#>

param([string]$OutputPath = "PROJECT-INDEX.md")

function Generate-ProjectIndex {
    # Get project metrics
    $allKt = @(Get-ChildItem -Path "app/src/main/java" -Filter "*.kt" -Recurse)
    $modelFiles = @(Get-ChildItem -Path "app/src/main/java/de/mybudgets/app/data/model" -Filter "*.kt" -Recurse).Count
    $daoFiles = @(Get-ChildItem -Path "app/src/main/java/de/mybudgets/app/data/db" -Filter "*Dao.kt" -Recurse).Count
    $repoFiles = @(Get-ChildItem -Path "app/src/main/java/de/mybudgets/app/data/repository" -Filter "*Repository.kt" -Recurse).Count
    $vmFiles = @(Get-ChildItem -Path "app/src/main/java/de/mybudgets/app/viewmodel" -Filter "*ViewModel.kt" -Recurse).Count
    $uiFiles = @(Get-ChildItem -Path "app/src/main/java/de/mybudgets/app/ui" -Filter "*.kt" -Recurse).Count
    $utilFiles = @(Get-ChildItem -Path "app/src/main/java/de/mybudgets/app/util" -Filter "*.kt" -Recurse).Count
    
    $totalLines = 0
    foreach ($file in $allKt) {
        $totalLines += (Get-Content $file.FullName | Measure-Object -Line).Lines
    }
    
    # Get version info
    $gitVersion = git describe --tags --always 2>$null
    $currentDate = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    
    $index = @"
# MyBudgets Project Index
**Generated:** $currentDate  
**Total Files:** $($allKt.Count) | **Total Lines:** $totalLines | **Version:** $gitVersion

## Quick Navigation

### Entry Points
- **MainActivity:** app/src/main/java/de/mybudgets/app/ui/MainActivity.kt
- **App Class:** app/src/main/java/de/mybudgets/app/MyBudgetsApp.kt
- **Nav Graph:** app/src/main/res/navigation/nav_graph.xml
- **Database:** app/src/main/java/de/mybudgets/app/data/db/AppDatabase.kt

### Quick Links to Features
| Feature | Fragment | ViewModel | Repository |
|---------|----------|-----------|-------------|
| **Transactions** | TransactionFragment | TransactionViewModel | TransactionRepository |
| **Categories** | CategoriesFragment | CategoryViewModel | CategoryRepository |
| **Dashboard** | DashboardFragment | DashboardViewModel | - |
| **Banking** | SyncFragment | BankSyncViewModel | AccountRepository |
| **Settings** | SettingsFragment | - | - |

### Utilities & Helpers
- **Logging:** util/AppLogger.kt (ALWAYS use, never android.util.Log)
- **Pattern Matching:** util/PatternMatcher.kt (TEXT=AND-logic, IBAN=substring)
- **Formatters:** util/DateFormatter.kt, util/CurrencyFormatter.kt
- **Recurring Patterns:** util/RecurringPatternDetector.kt

## Layer Architecture

| Layer | Directory | Files | Purpose |
|-------|-----------|-------|---------|
| **Data Model** | data/model/ | $modelFiles | @Entity classes, NO logic |
| **Database Access** | data/db/ | $daoFiles | @Dao interfaces, queries only |
| **Business Logic** | data/repository/ | $repoFiles | Repositories, logic here |
| **UI State** | viewmodel/ | $vmFiles | @HiltViewModel, state management |
| **UI Presentation** | ui/ | $uiFiles | Fragments, Adapters, no logic |
| **Utilities** | util/ | $utilFiles | Stateless helpers, formatters |

## Critical Files (NO TOUCH Without Reason!)

### Banking Integration (PROVEN STABLE)
- `data/banking/FintsService.kt` - PIN/TAN protocol (BBBank: FinTS 3.0)
- `data/banking/camt/` - XML parsing (custom + patched)
- `data/banking/CustomCamtParser.kt` - Stable since 2026-05-12
- **Rule:** Only modify if new bank pattern detected

### Pattern Matching (CRITICAL - RECENTLY FIXED)
- `util/PatternMatcher.kt` - AND-logic, punctuation handling (v1.0.53+)
- **Fixed:** 2026-06-03 Satzzeichen now replace with space (not removed)

### App Logger
- `util/AppLogger.kt` - In-app logging (ALWAYS use, never android.util.Log)
- **Displays:** In App Logs View + Logcat

## Feature Details

### Transactions Feature
- **Files:** ui/transactions/{TransactionFragment, TransactionDetailFragment, TransactionAdapter}
- **ViewModel:** TransactionViewModel.kt (line 40+)
- **Repository:** TransactionRepository.kt
- **Model:** Transaction, TransactionWithCategory
- **Key Pattern:** StateFlow<State> with sealed State
- **Test:** \`./gradlew.bat testDebugUnitTest --tests "*Transaction*"\`
- **Recent Changes:** Pattern text matching now uses AND-logic

### Categories Feature
- **Files:** ui/categories/{CategoriesFragment, CategoryAdapter, CategoryDragDropHelper}
- **ViewModel:** CategoryViewModel.kt
- **Repository:** CategoryRepository.kt
- **Model:** Category (hierarchical: Level 1-3 max)
- **Key Pattern:** Drag-drop with validation, hierarchical display
- **Features:** Edit color/icon, recursive apply to children, multi-select filter

### Dashboard Feature
- **Files:** ui/dashboard/DashboardFragment
- **ViewModel:** DashboardViewModel.kt
- **Charts:** Donut + Forecast (line chart with regression)
- **Aggregation:** Charts group by L1 (root) categories

### Banking/Sync Feature
- **Files:** data/banking/, ui/sync/
- **ViewModel:** BankSyncViewModel.kt
- **Repository:** AccountRepository.kt
- **Limitation:** 150 TX per request (BBBank limit)
- **Jobs:** KUmsAllCamt → KUmsZeitSEPA → KUmsAll → KUmsNew
- **Special:** Bulk sync splits into 30-day chunks (689 days = 23 requests)

### Pattern Recognition Feature
- **Files:** data/model/CategoryPattern, util/PatternMatcher
- **Types:** TEXT (keywords with AND-logic), IBAN (substring)
- **Matching:** PatternMatcher.kt (new Kotlin implementation, not SQL)
- **Detection:** RecurringPatternDetector.kt (temporal patterns, 25% tolerance)

## Dependency Graph

\`\`\`
UI Layer (Fragments)
    ↓ (uses)
ViewModel Layer (State Management)
    ↓ (uses)
Repository Layer (Business Logic)
    ↓ (uses)
DAO Layer (Database Access)
    ↓ (reads/writes)
SQLite Database
\`\`\`

**RULE:** Never skip layers!
- ❌ Fragment directly calls DAO
- ❌ ViewModel directly accesses database
- ✅ Always: Fragment → ViewModel → Repository → DAO

## Build & Test Commands

\`\`\`bash
# Quick build
./gradlew.bat assembleDebug

# Unit tests
./gradlew.bat testDebugUnitTest

# Linting (Detekt)
./gradlew.bat detekt

# Full verification (once it exists)
./scripts/Verify-BeforePush.ps1
\`\`\`

## Known Gotchas 🔥

### 1. StateFlow.value on Lazy Flow
❌ WRONG: \`categoryRepository.observeAll().value\`  
✅ RIGHT: \`categoryRepository.observeAll().first()\`

### 2. Fragment Binding After onDestroyView
❌ WRONG: Keep binding as property and use it in onDestroyView  
✅ RIGHT: \`_binding = null\` before super.onDestroyView()

### 3. Pattern Text - Punctuation Handling
❌ OLD: .replace(punct, "") → "edeka.de" becomes "edekade" (wrong!)  
✅ NEW: .replace(punct, " ") → "edeka.de" becomes "edeka de" (correct!)

### 4. Database Transactions for Multi-Step Operations
❌ WRONG: Update category, then update children (can leave inconsistent state)  
✅ RIGHT: Wrap in \`database.withTransaction { ... }\`

### 5. AppLogger vs android.util.Log
❌ WRONG: \`android.util.Log.e(...)\`  
✅ RIGHT: \`AppLogger.e(...)\`

## Context Loading Strategy

### For AI/OpenCode
When working on this project:

**If working on Transaction Feature:**
- Load: PROJECT-INDEX.md (this file)
- Load: PATTERNS-LEARNED.md > Transaction Pattern section
- Load: TransactionViewModel.kt (state management)

**If fixing Pattern Matching:**
- Load: PatternMatcher.kt (critical file)
- Load: PATTERNS-LEARNED.md > Pattern Matching section
- Load: DECISIONS.md > Why Custom PatternMatcher

**If modifying Banking:**
- Load: DECISIONS.md > NO TOUCH zone
- Load: BBBank-Sync-Troubleshooting.md (reference)
- Load: FintsService.kt (reference only)

**General Rule:** Load only relevant context to save tokens!

## Recent Changes (v1.0.50+)

| Version | Date | Change |
|---------|------|--------|
| v1.0.53 | 2026-06-03 | Fixed pattern text matching: punctuation → space (not remove) |
| v1.0.52 | 2026-06-03 | Pattern matching: AND-logic, all keywords must match |
| v1.0.51 | 2026-06-03 | Hierarchical category filter with expand/collapse |
| v1.0.50 | 2026-06-03 | Color/icon picker, recursive apply to children |

## Metrics

- **Code:** $totalLines lines in $($allKt.Count) files
- **Avg File:** $([math]::Round($totalLines / $($allKt.Count), 0)) lines/file
- **Largest Layer:** $(if ($uiFiles -gt $repoFiles) { "UI ($uiFiles files)" } else { "Repository ($repoFiles files)" })

---

Generated by: \`scripts/Generate-ProjectIndex.ps1\`  
Next: See PATTERNS-LEARNED.md for dos & don'ts  
Questions: Check IMPLEMENTATION-PLAYBOOK.md
"@
    
    Set-Content -Path $OutputPath -Value $index -Encoding UTF8
    Write-Host "✅ PROJECT-INDEX.md generated ($($allKt.Count) files, $totalLines lines)" -ForegroundColor Green
}

Generate-ProjectIndex
