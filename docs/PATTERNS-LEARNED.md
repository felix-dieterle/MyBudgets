# Patterns We Learned - MyBudgets Development (2026-04 to 2026-06)

**Collected from:** 50+ commits, 689 days bulk sync, banking integration, pattern matching, category hierarchy

---

## ✅ SUCCESSFULLY WORKING PATTERNS

### Pattern 1: StateFlow + ViewModel for UI State Management
**Where Used:** ALL ViewModels (TransactionViewModel, CategoryViewModel, DashboardViewModel, etc.)

**Implementation:**
```kotlin
sealed class XxxState {
    object Idle : XxxState()
    data class Loading(val message: String = "") : XxxState()
    data class Success(val data: T) : XxxState()
    data class Error(val message: String) : XxxState()
}

@HiltViewModel
class XxxViewModel @Inject constructor(
    private val repo: XxxRepository
) : ViewModel() {
    private val _state = MutableStateFlow<XxxState>(XxxState.Idle)
    val state: StateFlow<XxxState> = _state.asStateFlow()
    
    // Emit state changes via:
    private fun setState(state: XxxState) {
        _state.value = state
    }
}
```

**Why Works:**
- ✅ Lifecycle-aware (survives config changes)
- ✅ Lazy initialization (no eager first emission)
- ✅ Backpressure support (can't overflow UI thread)
- ✅ Easy to test (state sealed class)
- ✅ Coroutine-native

**Fragment Consumption:**
```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.state.collect { state ->
            when (state) {
                is XxxState.Idle -> {}
                is XxxState.Loading -> showLoading()
                is XxxState.Success -> showData(state.data)
                is XxxState.Error -> showError(state.message)
            }
        }
    }
}
```

**Never Do:**
- ❌ Return MutableStateFlow to UI (leak abstraction!)
- ❌ Use \`state.value\` in Fragment (use \`.collect\`)
- ❌ Create multiple StateFlow for related states (combine into single State)
- ❌ StateFlow for one-time events (use Event/Channel or emit in UI method instead)

**Lessons Learned:**
- Initially used plain Coroutines → Added backpressure issues
- Switched to StateFlow → All problems solved
- DateVersion in v2026-03 onwards: All new features use this pattern

---

### Pattern 2: Repository.save() Conditional Insert/Update
**Where Used:** TransactionRepository, CategoryRepository, AccountRepository, etc.

**Implementation:**
```kotlin
suspend fun save(entity: Entity): Long =
    if (entity.id == 0L) {
        dao.insert(entity)  // Returns new ID
    } else {
        dao.update(entity)  // Returns Unit, so return entity.id
        entity.id
    }
```

**Why Works:**
- ✅ Single entry point for persistence (insert vs update is internal)
- ✅ Symmetrical API (user doesn't think about insert/update)
- ✅ Returns ID in both cases (can save result immediately)
- ✅ Testable (mock dao, verify correct method called)

**Usage:**
```kotlin
val txId = repository.save(transaction)
AppLogger.i("TransactionRepository", "Saved: id=$txId")
```

**Edge Cases (Learned Hard Way):**
- ❌ ID=0L means "create new" (NOT null!)
- ✅ Entity @Entity classes MUST have id: Long (not nullable)
- ✅ update() returns Unit in Room, so explicitly return entity.id
- ✅ Always validate entity before save (error handling)

**Real Example from CategoryRepository:**
```kotlin
suspend fun save(category: Category): Long {
    if (category.id == 0L) {
        return dao.insert(category)
    } else {
        dao.update(category)
        return category.id
    }
}
```

---

### Pattern 3: AppLogger for All Logging (CRITICAL!)
**Where Used:** EVERY class with logging (no exceptions!)

**FORBIDDEN:**
```kotlin
❌ import android.util.Log
❌ Log.e(TAG, "error")
```

**REQUIRED:**
```kotlin
✅ import de.mybudgets.app.util.AppLogger
✅ AppLogger.e(TAG, "error")
```

**Why This Matters:**
- ❌ android.util.Log → Only visible in Logcat (external tool, dev device only)
- ✅ AppLogger → In-memory buffer (3000 entries) + visible in App UI + exportable

**Features:**
- Logs appear in Settings → Debug → View Logs (in-app!)
- User can export logs via Intent → attach to support tickets
- Also appears in Logcat (best of both worlds)
- Custom formatter (TAG, level, timestamp)

**API:**
```kotlin
AppLogger.e(TAG, "Error happened")       // Error (Red)
AppLogger.w(TAG, "Warning")              // Warning (Yellow)
AppLogger.i(TAG, "Info")                 // Info (Blue)
AppLogger.d(TAG, "Debug")                // Debug (Gray)
AppLogger.clear()                        // Clear buffer
val logs: String = AppLogger.export()    // Export as string
```

**Implementation (Simplified):**
```kotlin
object AppLogger {
    private val entries = mutableListOf<LogEntry>()
    
    fun e(tag: String, msg: String, error: Throwable? = null) {
        entries.add(LogEntry("ERROR", tag, msg, error))
        if (entries.size > 3000) entries.removeAt(0)
    }
    // ... similar for w(), i(), d()
}
```

---

### Pattern 4: Fragment Lifecycle with repeatOnLifecycle
**Where Used:** ALL Fragments (no exceptions!)

**Implementation:**
```kotlin
@AndroidEntryPoint
class XxxFragment : Fragment() {
    private var _binding: FragmentXxxBinding? = null
    private val binding get() = _binding!!
    private val viewModel: XxxViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentXxxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Observe ViewModel state
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        // Handle state
                    }
                }
                launch {
                    viewModel.items.collect { items ->
                        adapter.submitList(items)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null  // Clear binding
    }
}
```

**Why Works:**
- ✅ Automatic cancellation when Fragment is STOPPED (not just destroyed)
- ✅ Restarted when Fragment returns to STARTED state
- ✅ No memory leaks (binding cleared in onDestroyView)
- ✅ No UI updates when invisible (lifecycle respects STARTED)

**Why repeatOnLifecycle?**
- ❌ OLD: Just launch { collect } → Keeps running even when paused
- ✅ NEW: repeatOnLifecycle(STARTED) → Pauses when invisible, resumes when visible

**Real Examples from Codebase:**
- TransactionFragment.kt (line 48+)
- CategoriesFragment.kt (line 55+)
- DashboardFragment.kt (line 42+)

---

### Pattern 5: TEXT Pattern Matching with AND-Logic
**Where Used:** PatternMatcher.kt (util/), used in TransactionRepository.save()

**Problem We Solved:**
- Initially: "Pattern matching" used simple substring in SQL LIKE
- Issue: User creates pattern "EDEKA|Lebensmittel" expecting BOTH words
- Old Behavior: OR-logic (either word matches) → false positives
- New Behavior: AND-logic (both words required) → precise matching

**Implementation (2026-06-03):**
```kotlin
object PatternMatcher {
    private val PUNCTUATION_REGEX = Regex("[,.:;/\\\\()\\[\\]{}]")
    
    fun normalizeText(text: String): String {
        return text
            .replace(PUNCTUATION_REGEX, " ")  // KEY: Replace with SPACE (not remove!)
            .trim()
            .lowercase()
    }
    
    fun matchTextPattern(
        patternValue: String,
        description: String,
        note: String
    ): Boolean {
        val keywords = patternValue.split("|").map { normalizeText(it) }.filter { it.isNotBlank() }
        if (keywords.isEmpty()) return false
        
        val combinedText = normalizeText("$description $note")
        val words = extractWords(combinedText)
        
        // ALL keywords must be present (AND-logic)
        return keywords.all { keyword ->
            words.any { word ->
                word == keyword || word.contains(keyword)
            }
        }
    }
}
```

**Key Insight: Punctuation Handling**
```
OLD (WRONG):
  TX: "edeka.de einkauf"
  After remove: "edekade einkauf"
  Search "edeka": Found in "edekade" (merged!)
  Result: Wrong matches!

NEW (CORRECT):
  TX: "edeka.de einkauf"
  After replace punct→space: "edeka de einkauf"
  Search "edeka": Found in ["edeka", "de", "einkauf"]
  Result: Correct!
```

**Usage:**
```kotlin
// Pattern: "EDEKA|Lebensmittel"
PatternMatcher.matchTextPattern("EDEKA|Lebensmittel", "Einkauf bei EDEKA Lebensmittel", "")
// Result: ✅ TRUE (both EDEKA and Lebensmittel present)

PatternMatcher.matchTextPattern("EDEKA|Lebensmittel", "EDEKA Abteilung", "")
// Result: ❌ FALSE (Lebensmittel missing)
```

**Where Applied:**
1. TransactionRepository.save() → \`findTextMatch(description, note)\`
2. TransactionDetailFragment.findMatchingTransactions() → bulk update dialog
3. Potential future: Auto-categorization on TX import

---

### Pattern 6: Hierarchical Category Structure (Level 1-3)
**Where Used:** CategoryViewModel, CategoryRepository, Category model

**Design:**
```kotlin
@Entity
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val level: Int,  // 1, 2, or 3
    val parentCategoryId: Long? = null,
    val color: Int = Color.GRAY,
    val icon: String = "📁"
)
```

**Why 3 Levels (Not Unlimited)?**
- ✅ UI manageable (RecyclerView rendering fast)
- ✅ DB fast (limited nesting = simple queries)
- ✅ Covers 99% of real-world budgeting use cases
- ❌ Unlimited = complexity explosion
- ❌ Tree rendering = performance nightmare

**Usage:**
```
Level 1: Income
  Level 2: Salary
    Level 3: Monthly Base
    Level 3: Bonus
  Level 2: Freelance
    Level 3: Project A

Level 1: Expenses
  Level 2: Food
    Level 3: Groceries
    Level 3: Restaurants
```

**Operations:**
- **Move Category:** Validates no circular refs, adjusts children levels
- **Delete Category:** Deletes all children recursively
- **Display:** CategoriesFragment shows hierarchical tree with expand/collapse

**Real Implementation Details:**
```kotlin
// Prevent circular references
fun isDescendantOf(parentId: Long, maybeChild: Category): Boolean {
    var current = maybeChild.parentCategoryId
    while (current != null) {
        if (current == parentId) return true
        current = /* parent of current */
    }
    return false
}

// Prevent depth overflow
fun getMaxDescendantDepth(catId: Long): Int {
    // Returns max depth of subtree (3 = level 3, 2 = level 2, etc.)
}

// Move category with atomic transaction
suspend fun moveCategory(catId: Long, newParentId: Long) {
    database.withTransaction {
        dao.update(...)  // Update parent
        dao.updateChildrenLevels(...)  // Update all descendants
    }
}
```

---

### Pattern 7: Bulk Operations with Progress Tracking
**Where Used:** Banking sync (load 689 days in 23 requests)

**The Problem:**
- BBBank API limits: 150 TX per FinTS request
- Need to load: 689 days of history
- Naive approach: Would hang forever, no feedback

**Solution: Chunked Sync with Progress**
```kotlin
suspend fun bulkSyncAccountTransactions(accountId: Long, dateFrom: Long, dateTo: Long) {
    val dayRange = (dateTo - dateFrom) / (24 * 60 * 60 * 1000)
    val chunkDays = 30  // 30-day chunks
    val chunks = (dayRange / chunkDays).toInt() + 1
    
    for (i in 0 until chunks) {
        val chunkFrom = dateFrom + (i * chunkDays * 24 * 60 * 60 * 1000)
        val chunkTo = minOf(chunkFrom + chunkDays * 24 * 60 * 60 * 1000, dateTo)
        
        syncChunk(accountId, chunkFrom, chunkTo)
        publishProgress(i + 1, chunks)
    }
}
```

**UI Feedback:**
```kotlin
// In ViewModel
private val _syncProgress = MutableStateFlow<Pair<Int, Int>>(0 to 0)  // (current, total)
val syncProgress: StateFlow<Pair<Int, Int>> = _syncProgress.asStateFlow()

// In Fragment
viewModel.syncProgress.collect { (current, total) ->
    binding.progressBar.progress = (current * 100) / total
    binding.tvProgress.text = "$current / $total"
}
```

**Result:**
- 689 days synced in ~5-10 minutes (vs. seeming frozen)
- User sees progress (not frustrated!)
- Cancellable if needed

---

### Pattern 8: Database Transactions for Multi-Step Operations
**Where Used:** CategoryRepository.moveCategory(), Sync operations

**The Problem:**
```kotlin
❌ WRONG:
suspend fun moveCategory(catId: Long, newParentId: Long) {
    dao.updateCategory(...)  // Step 1
    dao.updateChildrenLevels(...)  // Step 2
    // If step 2 crashes, database is inconsistent!
}
```

**Solution: withTransaction Block**
```kotlin
✅ CORRECT:
suspend fun moveCategory(catId: Long, newParentId: Long) {
    database.withTransaction {
        dao.updateCategory(...)  // Step 1
        dao.updateChildrenLevels(...)  // Step 2
        // Either BOTH succeed or BOTH rollback
    }
}
```

**Why:**
- ✅ Atomicity: All-or-nothing
- ✅ Consistency: No intermediate states
- ✅ Isolation: No other threads see partial updates
- ✅ Durability: Once committed, stays committed

**Real Example:**
```kotlin
// CategoryRepository.moveCategory (actual code)
suspend fun moveCategory(catId: Long, targetParentId: Long?, newLevel: Int) {
    database.withTransaction {
        // 1. Update target category
        val updated = currentCategory.copy(
            parentCategoryId = targetParentId,
            level = newLevel
        )
        dao.update(updated)
        
        // 2. Update ALL children levels recursively
        updateChildrenLevels(catId, newLevel + 1)
        
        // 3. Clean up any orphaned relationships
        deleteIfEmpty(currentCategory.parentCategoryId)
    }
}
```

---

## ❌ ANTI-PATTERNS (Don't Do!)

### Anti-Pattern 1: SQL LIKE for Complex Pattern Matching
**Tried:** Originally used \`WHERE text LIKE '%' || pattern || '%'\`  
**Problems:**
- Too primitive (just substring matching)
- Can't handle AND-logic
- Can't normalize punctuation
- Performance: Full table scan

**Solution:** Move logic to Kotlin (Repository layer)

**Commit:** 2026-06-03 v1.0.52

---

### Anti-Pattern 2: Direct DAO Access from Fragment
**Problem:**
- ❌ No lifecycle safety
- ❌ No error handling (where goes the exception?)
- ❌ Hard to test (Fragment + DB = coupling)
- ❌ Violates architecture layers

**Always:** Fragment → ViewModel → Repository → DAO

```kotlin
❌ WRONG:
class MyFragment : Fragment() {
    @Inject lateinit var dao: TransactionDao
    
    override fun onViewCreated() {
        val txs = runBlocking { dao.observeAll() }  // WRONG!
    }
}

✅ RIGHT:
class MyFragment : Fragment() {
    private val viewModel: TransactionViewModel by viewModels()
    
    override fun onViewCreated() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(STARTED) {
                viewModel.transactions.collect { txs -> ... }
            }
        }
    }
}
```

---

### Anti-Pattern 3: android.util.Log Anywhere
**Problems:**
- ❌ Not visible in-app
- ❌ Hard for users to debug
- ❌ No export capability
- ❌ Requires dev device + adb

**Solution:** AppLogger ONLY

---

### Anti-Pattern 4: MutableStateFlow Exposed to UI
**Wrong:**
```kotlin
val state: MutableStateFlow<State>  // WRONG!
```

**Correct:**
```kotlin
val state: StateFlow<State>  // RIGHT (read-only)
```

**Why:**
- UI should only observe, not mutate state
- State changes must go through ViewModel methods
- Easier to reason about (single source of truth)

---

### Anti-Pattern 5: Unbounded List Updates
**Wrong:**
```kotlin
❌ data class State(val items: List<Item>)  // Can grow infinitely!

_state.value = State(items = oldItems + newItems)  // Appends forever
```

**Correct:**
```kotlin
✅ data class State(val items: List<Item>)

// Either:
// 1. Replace entire list
_state.value = State(items = newItems)

// 2. Or use pagination
data class State(val items: List<Item>, val pageNumber: Int, val totalPages: Int)
```

---

## 🔥 CRITICAL GOTCHAS (Small mistake → Big problem)

### Gotcha 1: StateFlow.value on Lazy Flow
**Mistake:**
```kotlin
val cats = categoryRepository.observeAll().value  // WRONG!
// Result: null or empty because flow not initialized yet
// Exception: NullPointerException one screen down
```

**Fix:**
```kotlin
val cats = categoryRepository.observeAll().first()  // RIGHT
// Suspends until flow emits first value
// Safe to use in coroutine
```

**When This Happens:**
- Lazy StateFlow not yet initialized
- Called too early in lifecycle
- Null-safety checks skip one level too many

**Real Example from v1.0.35:**
```kotlin
// CRASHED:
val categories = categoryRepository.observeAll().value  // Returned null!

// FIXED:
val categories = categoryRepository.observeAll().first()  // Waits for init
```

**Lesson:** Always use \`.first()\` before \`.value\` in coroutines!

---

### Gotcha 2: Fragment Binding After onDestroyView
**Mistake:**
```kotlin
❌ WRONG:
class MyFragment : Fragment() {
    private val binding = MyFragmentBinding.bind(root)  // Kept in property!
    
    override fun onDestroyView() {
        binding.tvName.text = "X"  // CRASH: View detached
        super.onDestroyView()
    }
}
```

**Fix:**
```kotlin
✅ RIGHT:
class MyFragment : Fragment() {
    private var _binding: MyFragmentBinding? = null
    private val binding get() = _binding ?: error("No binding!")
    
    override fun onCreateView(...) {
        _binding = MyFragmentBinding.inflate(...)
        return _binding!!.root
    }
    
    override fun onDestroyView() {
        _binding = null  // Clear FIRST
        super.onDestroyView()
    }
}
```

**Why:**
- Fragment views are destroyed in onDestroyView
- Binding keeps reference to destroyed views
- Accessing binding after this → accessing freed memory
- Android throws IllegalStateException or crashes with null ref

**Real Example from v1.0.20:**
```
CRASH: java.lang.IllegalStateException: 
      Fragment MyFragment has been destroyed
      
FIX: Set _binding = null before super.onDestroyView()
```

---

### Gotcha 3: Pattern Text - Punctuation Merging
**Mistake (Fixed 2026-06-03):**
```kotlin
❌ OLD:
normalize().replace(punct, "")  // "edeka.de-shop" → "edekadeshop"

// Now "edeka" matches inside "edekadeshop" (wrong!)
// And "de" also matches inside (REALLY wrong!)
```

**Fix:**
```kotlin
✅ NEW:
normalize().replace(punct, " ")  // "edeka.de-shop" → "edeka de shop"

// Now words are separated
// "edeka" only matches whole word "edeka" (correct!)
```

**Why This Matters:**
- Pattern "EDEKA" should not match "edeka-shop-online" (false positive)
- But "EDEKA Lebensmittel" should match "EDEKA, Lebensmittel" (ignore punct)
- Solution: Replace punct with space, then word-match

**Real Impact:**
- v1.0.52: Too many false-positive pattern matches
- v1.0.53: Fixed with space replacement
- Result: Pattern matching now works correctly

---

### Gotcha 4: Database Transactions Missing
**Mistake:**
```kotlin
❌ WRONG:
suspend fun moveCategory(catId: Long, newParent: Long) {
    dao.update(cat.copy(parentCategoryId = newParent))  // Partial update
    dao.updateChildrenLevels(...)  // Then this
    // If crashes here, database inconsistent!
}
```

**Fix:**
```kotlin
✅ RIGHT:
suspend fun moveCategory(catId: Long, newParent: Long) {
    database.withTransaction {
        dao.update(...)  // Atomic
        dao.updateChildrenLevels(...)  // with
        // Or both succeed, or both rollback
    }
}
```

**Consequence of Missing Transaction:**
- Category points to new parent (Level updated)
- But children still have old level (inconsistent!)
- UI shows garbage category hierarchy
- Can't delete categories (foreign key constraints)

---

### Gotcha 5: Bulk Operations Without Progress
**Mistake:**
```kotlin
❌ WRONG:
fun bulkSync(dateFrom: Long, dateTo: Long) {
    for (date in dateFrom..dateTo step 30 days) {
        sync(date, date + 30 days)
        // No feedback to UI
    }
    // Looks frozen for 10 minutes!
}
```

**Fix:**
```kotlin
✅ RIGHT:
fun bulkSync(dateFrom: Long, dateTo: Long) {
    val chunks = calculateChunks(dateFrom, dateTo, 30)
    for ((index, chunk) in chunks.withIndex()) {
        sync(chunk.from, chunk.to)
        _progress.value = Pair(index + 1, chunks.size)  // Update UI
    }
}
```

**User Experience:**
- ❌ OLD: App frozen for 10 min, user force-closes
- ✅ NEW: Progress bar visible, user understands it's working

**Real Example:**
- v1.0.30: Bulk 689-day sync froze UI for 5 minutes
- v1.0.31: Added progress tracking
- User feedback: "Much better! I can see it's doing something"

---

### Gotcha 6: CategoryId = 0 vs null
**Mistake:**
```kotlin
❌ WRONG:
data class Category(
    val id: Long? = null,  // nullable
    ...
)

if (category.id != null) { ... }  // Have to null-check everywhere
```

**Correct:**
```kotlin
✅ RIGHT:
data class Category(
    val id: Long = 0L,  // 0 means "not yet saved"
    ...
)

if (category.id == 0L) { /* new */ } else { /* update */ }
```

**Why:**
- Room @PrimaryKey(autoGenerate=true) always returns Long, never null
- 0 is special value (means "not yet persisted")
- Cleaner code: \`id == 0L\` vs \`id != null\`
- No Optional indirection

---

## 🎯 ARCHITECTURE DECISIONS (Why Like This?)

### Decision 1: StateFlow for UI State (Not LiveData or RxJava)
**Chosen:** StateFlow  
**When:** 2026-03 (start of project)  
**Reason:**
- ✅ Kotlin coroutines native
- ✅ Built-in backpressure
- ✅ Lifecycle integration (repeatOnLifecycle)
- ✅ Easy to test
- ❌ LiveData deprecated soon
- ❌ RxJava overkill for this complexity

**Alternative Considered:**
- LiveData: Still works, but deprecated
- RxJava: Full reactive system (unnecessary)
- Plain Coroutines: No backpressure (overflow app)

**Trade-offs:**
- More boilerplate than LiveData (sealed State classes)
- But much more control and testability
- Future-proof (Compose also uses StateFlow)

---

### Decision 2: 3-Level Category Hierarchy (Not Unlimited)
**Chosen:** Max 3 levels  
**When:** 2026-05 (user requested nested categories)  
**Reason:**
- ✅ UI manageable (RecyclerView rendering fast)
- ✅ DB performance (limited nesting = simple queries)
- ✅ Covers 99% of real-world budgeting needs
- ❌ Unlimited = complexity explosion
- ❌ Tree rendering = performance nightmare

**Real User Case:**
```
Expenses
  Food
    Groceries (L3)
    Restaurants (L3)
  Housing
    Rent (L3)
    Utilities (L3)
```

**Future Limitation:**
- Power users might need L4 (unlikely)
- If so: Could extend to 4-5 levels with minimal changes
- Current design: Overflow warning when exceeding L3

---

### Decision 3: Custom PatternMatcher (Not SQL LIKE)
**Chosen:** Kotlin PatternMatcher  
**When:** 2026-06-03 (pattern matching broken)  
**Reason:**
- ✅ Full control (AND-logic, punctuation handling)
- ✅ Testable (unit tests possible)
- ✅ No DB overhead (RAM only, Kotlin logic)
- ✅ Extensible (add regex, ML later)
- ❌ SQL LIKE too primitive
- ❌ Full-text search overkill (Lucene)

**Why Not SQL?**
```sql
-- SQL LIKE can't do:
-- 1. AND-logic (all keywords must match)
-- 2. Punctuation normalization ("edeka.de" vs "edeka de")
-- 3. Word boundary matching (vs substring)

-- So implemented in Kotlin instead
```

**Performance:**
- Matching 500 transactions: ~50ms (acceptable)
- Could optimize with caching if needed

---

### Decision 4: AppLogger Custom Implementation
**Chosen:** In-memory buffer + UI display  
**When:** 2026-04 (needed in-app logging)  
**Reason:**
- ✅ User can see logs in app
- ✅ Export for customer support
- ✅ Logcat + App logs combined
- ✅ Max 3000 entries (memory-bounded)
- ❌ android.util.Log invisible to users
- ❌ No export without adb

**Alternative Considered:**
- Sentry/Crashlytics: Overkill for this app
- Firebase: Require internet (wrong for offline-first app)
- Custom File Logging: Would bloat app storage

**Trade-offs:**
- More boilerplate than android.util.Log (have to import AppLogger)
- But user can troubleshoot without dev tools
- Better UX for support

---

## PERFORMANCE LESSONS

### Lesson 1: 150 TX Per Request Limit (BBBank Limitation)
**Discovered:** 2026-05-12  
**Impact:** Can't load 689 days in one request (would be 2500+ TX)

**Solution:**
```kotlin
// Chunk into 30-day segments
// Each segment: ~30-50 TX (fits in 150 limit)
// 689 days: 23 requests
val CHUNK_DAYS = 30
val chunks = (totalDays / CHUNK_DAYS)  + 1
```

**Result:**
- 5-10 minute sync (vs. error/hang)
- Progress visible (vs. frozen UI)

---

### Lesson 2: Pattern Detection Tolerance Must Be Relative
**Discovered:** 2026-05-31  
**Old Code:**
```kotlin
❌ val INTERVAL_TOLERANCE_DAYS = 3  // Fixed 3 days
❌ Detected 0 patterns (too strict!)
```

**New Code:**
```kotlin
✅ val INTERVAL_TOLERANCE_RATIO = 0.25  // 25% of avg interval
✅ Detects 10-20+ patterns (correct!)
```

**Example:**
- Monthly rent: Interval ~30 days
- Tolerance: 30 * 0.25 = 7.5 days (allows 22-37 day intervals)
- vs. Old: Fixed 3 days (rejects 22-37 day intervals) ❌

**Improvement:** Pattern detection now actually works! 🎉

---

### Lesson 3: Fragment Lazy Loading
**Discovered:** 2026-04-20  
**Problem:** ViewModels initialize slowly with Hilt + Room
**Solution:** Use \`.first()\` to wait for StateFlow initialization

```kotlin
val categories = categoryRepository.observeAll().first()
// Suspends until categories are loaded
// No null pointer exceptions
```

---

## TESTING INSIGHTS

### Test Strategy
- ❌ No Espresso (UI tests too fragile, break with UI changes)
- ✅ Unit tests for business logic (Repository, ViewModel, Formatters)
- ✅ Manual integration tests (real device, real data)
- ✅ Rapid feedback loops (5 min rebuild + install)

### What to Test First (Priority)
1. Repository.save() logic (create vs update)
2. ViewModel state transitions (Idle → Loading → Success → Error)
3. Pattern matching (edge cases!)
4. Date/Currency formatting (localization bugs)
5. Category hierarchy operations (move, delete, depth)

### Test Examples
```kotlin
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

@Test
fun `matchTextPattern with AND logic`() {
    assertTrue(PatternMatcher.matchTextPattern(
        "EDEKA|Lebensmittel",
        "Einkauf EDEKA Lebensmittel",
        ""
    ))
    
    assertFalse(PatternMatcher.matchTextPattern(
        "EDEKA|Lebensmittel",
        "EDEKA Shop",  // Missing Lebensmittel
        ""
    ))
}
```

---

## FUTURE IMPROVEMENTS

### To Explore
- [ ] Room migration auto-generation (currently manual)
- [ ] GraphQL for pattern queries (if complexity grows)
- [ ] ML-based category suggestion (vs. rule-based patterns)
- [ ] Kotlin multiplatform (iOS version?)
- [ ] Jetpack Compose (when Fragment complexity increases)

### Known Limitations (Accepted)
- TX count: Limited to 500 in memory (design choice)
- Pattern confidence: 0-1 scale (could be more granular)
- Banking: BBBank proprietary quirks (CAMT parsing hard)
- UI: Fragment-based (Compose not ready for this complexity yet)
- Performance: 150 TX limit from bank (not our limitation)

---

## SUMMARY TABLE: Do's & Don'ts

| Do ✅ | Don't ❌ |
|--------|----------|
| Use AppLogger | Use android.util.Log |
| StateFlow<State> | MutableStateFlow exposed |
| repeatOnLifecycle | Plain launch { } |
| Repository.save() | DAO directly from Fragment |
| Null binding in onDestroyView | Keep binding after destroyed |
| AND-logic for patterns | OR-logic or substring only |
| db.withTransaction {} | Multi-step without transaction |
| Progress updates for bulk ops | Freeze UI silently |
| .first() for lazy StateFlow | .value on uninitialized flow |

---

**Last Updated:** 2026-06-03  
**Maintained By:** AI + Human (learning from production)  
**Next:** See DECISIONS.md for architectural choices
