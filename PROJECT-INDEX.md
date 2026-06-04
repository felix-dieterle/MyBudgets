# MyBudgets Project Index
**Generated:** 2026-06-04 07:56:34  
**Total Files:** 111 | **Total Lines:** 12125 | **Version:** v1.0.10-better-extraction-build90-66-gfc86dde

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
| **Data Model** | data/model/ | 14 | @Entity classes, NO logic |
| **Database Access** | data/db/ | 11 | @Dao interfaces, queries only |
| **Business Logic** | data/repository/ | 12 | Repositories, logic here |
| **UI State** | viewmodel/ | 8 | @HiltViewModel, state management |
| **UI Presentation** | ui/ | 31 | Fragments, Adapters, no logic |
| **Utilities** | util/ | 13 | Stateless helpers, formatters |

## Critical Files (NO TOUCH Without Reason!)

### Banking Integration (PROVEN STABLE)
- data/banking/FintsService.kt - PIN/TAN protocol (BBBank: FinTS 3.0)
- data/banking/camt/ - XML parsing (custom + patched)
- data/banking/CustomCamtParser.kt - Stable since 2026-05-12
- **Rule:** Only modify if new bank pattern detected

### Pattern Matching (CRITICAL - RECENTLY FIXED)
- util/PatternMatcher.kt - AND-logic, punctuation handling (v1.0.53+)
- **Fixed:** 2026-06-03 Satzzeichen now replace with space (not removed)

### App Logger
- util/AppLogger.kt - In-app logging (ALWAYS use, never android.util.Log)
- **Displays:** In App Logs View + Logcat

## Feature Details

### Transactions Feature
- **Files:** ui/transactions/{TransactionFragment, TransactionDetailFragment, TransactionAdapter}
- **ViewModel:** TransactionViewModel.kt (line 40+)
- **Repository:** TransactionRepository.kt
- **Model:** Transaction, TransactionWithCategory
- **Key Pattern:** StateFlow<State> with sealed State
- **Test:** \./gradlew.bat testDebugUnitTest --tests "*Transaction*"\
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
- **Jobs:** KUmsAllCamt â†’ KUmsZeitSEPA â†’ KUmsAll â†’ KUmsNew
- **Special:** Bulk sync splits into 30-day chunks (689 days = 23 requests)

### Pattern Recognition Feature
- **Files:** data/model/CategoryPattern, util/PatternMatcher
- **Types:** TEXT (keywords with AND-logic), IBAN (substring)
- **Matching:** PatternMatcher.kt (new Kotlin implementation, not SQL)
- **Detection:** RecurringPatternDetector.kt (temporal patterns, 25% tolerance)

## Dependency Graph

\\\
UI Layer (Fragments)
    â†“ (uses)
ViewModel Layer (State Management)
    â†“ (uses)
Repository Layer (Business Logic)
    â†“ (uses)
DAO Layer (Database Access)
    â†“ (reads/writes)
SQLite Database
\\\

**RULE:** Never skip layers!
- âŒ Fragment directly calls DAO
- âŒ ViewModel directly accesses database
- âœ… Always: Fragment â†’ ViewModel â†’ Repository â†’ DAO

## Build & Test Commands

\\\ash
# Quick build
./gradlew.bat assembleDebug

# Unit tests
./gradlew.bat testDebugUnitTest

# Linting (Detekt)
./gradlew.bat detekt

# Full verification (once it exists)
./scripts/Verify-BeforePush.ps1
\\\

## Known Gotchas ðŸ”¥

### 1. StateFlow.value on Lazy Flow
âŒ WRONG: \categoryRepository.observeAll().value\  
âœ… RIGHT: \categoryRepository.observeAll().first()\

### 2. Fragment Binding After onDestroyView
âŒ WRONG: Keep binding as property and use it in onDestroyView  
âœ… RIGHT: \_binding = null\ before super.onDestroyView()

### 3. Pattern Text - Punctuation Handling
âŒ OLD: .replace(punct, "") â†’ "edeka.de" becomes "edekade" (wrong!)  
âœ… NEW: .replace(punct, " ") â†’ "edeka.de" becomes "edeka de" (correct!)

### 4. Database Transactions for Multi-Step Operations
âŒ WRONG: Update category, then update children (can leave inconsistent state)  
âœ… RIGHT: Wrap in \database.withTransaction { ... }\

### 5. AppLogger vs android.util.Log
âŒ WRONG: \ndroid.util.Log.e(...)\  
âœ… RIGHT: \AppLogger.e(...)\

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
| v1.0.53 | 2026-06-03 | Fixed pattern text matching: punctuation â†’ space (not remove) |
| v1.0.52 | 2026-06-03 | Pattern matching: AND-logic, all keywords must match |
| v1.0.51 | 2026-06-03 | Hierarchical category filter with expand/collapse |
| v1.0.50 | 2026-06-03 | Color/icon picker, recursive apply to children |

## Metrics

- **Code:** 12125 lines in 111 files
- **Avg File:** 109 lines/file
- **Largest Layer:** UI (31 files)

---

Generated by: \scripts/Generate-ProjectIndex.ps1\  
Next: See PATTERNS-LEARNED.md for dos & don'ts  
Questions: Check IMPLEMENTATION-PLAYBOOK.md
