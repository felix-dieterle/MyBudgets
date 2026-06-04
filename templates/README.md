# Code Templates - MyBudgets

**Purpose:** Standardized boilerplate templates with comprehensive documentation and best practices baked in.

**Format:** Each template uses `${PLACEHOLDER}` syntax for easy substitution.

---

## Available Templates

### 1. Fragment.template.kt
**Pattern:** Fragment with ViewBinding + ViewModel + StateFlow collection

**Placeholders:**
- `${FEATURE}` - Feature name lowercase (e.g., `transaction`)
- `${FEATURE_PASCAL}` - Feature name PascalCase (e.g., `Transaction`)

**Key Components:**
- Private nullable binding with safety getter
- Lifecycle-aware ViewModel observation via `repeatOnLifecycle(STARTED)`
- State-based UI rendering (sealed State class)
- Proper onDestroyView cleanup
- AppLogger for all logging

**Usage:**
```bash
# Copy template
cp templates/Fragment.template.kt app/src/main/java/de/mybudgets/app/ui/transaction/TransactionFragment.kt

# Replace placeholders
sed -i 's/${FEATURE}/transaction/g' ...
sed -i 's/${FEATURE_PASCAL}/Transaction/g' ...
```

**Enforced Patterns:**
- ✅ ViewBinding with `_binding` nullable property
- ✅ `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(...) }`
- ✅ State observation with `when` statement
- ✅ `_binding = null` in `onDestroyView()`
- ✅ AppLogger for all logging

**Common Mistakes Prevented:**
- ❌ Accessing binding after onDestroyView → Error provides hint
- ❌ Coroutine not lifecycle-aware → template enforces repeatOnLifecycle
- ❌ android.util.Log → AppLogger required

---

### 2. ViewModel.template.kt
**Pattern:** @HiltViewModel with StateFlow + sealed State class

**Placeholders:**
- `${FEATURE_PASCAL}` - Feature name PascalCase
- `${FEATURE_PASCAL}Repository` - Repository class

**Key Components:**
- Private MutableStateFlow (mutation inside only)
- Public read-only StateFlow (for Fragment)
- Sealed State: Idle | Loading | Success | Error
- viewModelScope (auto-cancelled on clear)
- Repository injection
- Error handling with AppLogger

**Usage:**
```bash
cp templates/ViewModel.template.kt app/src/main/java/de/mybudgets/app/viewmodel/TransactionViewModel.kt
sed -i 's/${FEATURE_PASCAL}/Transaction/g' ...
```

**State Transitions:**
```
Initial: Idle
  ↓ (user action)
Loading (message: "Loading...")
  ↓ (success)
Success (data: List<T>)
  ↓ (retry)
Loading (message: "Loading...")
  
OR:
Loading
  ↓ (error)
Error (message: "Failed to load")
  ↓ (retry)
Loading
```

**Enforced Patterns:**
- ✅ `private val _state = MutableStateFlow<State>(State.Idle)`
- ✅ `val state: StateFlow<State> = _state.asStateFlow()`
- ✅ `viewModelScope.launch { ... }`
- ✅ Try/catch with AppLogger error logging
- ✅ Sealed State with all 4 states

**Common Mistakes Prevented:**
- ❌ Returning MutableStateFlow → Template exposes StateFlow only
- ❌ UI accessing _state directly → State is private
- ❌ Forgetting error handling → Template includes try/catch
- ❌ MainScope instead of viewModelScope → Auto-cleanup guaranteed

---

### 3. Repository.template.kt
**Pattern:** @Singleton Repository with DAO injection

**Placeholders:**
- `${FEATURE_PASCAL}` - Feature name PascalCase
- `${FEATURE_PASCAL}Dao` - DAO class

**Key Components:**
- @Singleton (one instance per app)
- DAO injection
- save() pattern (conditional insert/update)
- observeAll() Flow
- Error handling + AppLogger
- Multi-step transaction documentation

**Usage:**
```bash
cp templates/Repository.template.kt app/src/main/java/de/mybudgets/app/data/repository/TransactionRepository.kt
sed -i 's/${FEATURE_PASCAL}/Transaction/g' ...
```

**Core Methods:**
- `observeAll(): Flow<List<T>>` - Reactive observation
- `fetch${FEATURE}(): List<T>` - One-time snapshot
- `getById(id: Long): T?` - Single item
- `save(item: T): Long` - Insert or update (returns ID)
- `delete(id: Long): Int` - Delete single
- `deleteAll(ids: List<Long>): Int` - Delete multiple

**save() Pattern Explained:**
```kotlin
suspend fun save(item: ${FEATURE_PASCAL}): Long =
    if (item.id == 0L) {
        dao.insert(item)      // New item: return generated ID
    } else {
        dao.update(item)      // Existing: return same ID
        item.id
    }
```

**Why?**
- ✅ Single API (no insert/update choice)
- ✅ Returns ID in both cases
- ✅ Caller can use immediately

**Enforced Patterns:**
- ✅ @Singleton scope
- ✅ AppLogger for all operations
- ✅ Try/catch with error logging
- ✅ save() pattern
- ✅ Flow for observeAll()

**Multi-Step Transactions (Example):**
```kotlin
suspend fun moveToCategory(itemId: Long, newCatId: Long) {
    database.withTransaction {
        dao.update(...)      // Step 1
        dao.updateChildren(...)  // Step 2
        // Both succeed or both rollback
    }
}
```

---

## Template Usage Workflow

### Step 1: Copy Template
```bash
cd MyBudgets
cp templates/Fragment.template.kt app/src/main/java/de/mybudgets/app/ui/myfeature/MyFeatureFragment.kt
```

### Step 2: Replace Placeholders
**Linux/Mac:**
```bash
sed -i 's/${FEATURE}/myfeature/g' app/src/.../MyFeatureFragment.kt
sed -i 's/${FEATURE_PASCAL}/MyFeature/g' app/src/.../MyFeatureFragment.kt
```

**Windows PowerShell:**
```powershell
$path = "app/src/.../MyFeatureFragment.kt"
(Get-Content $path) -replace '\$\{FEATURE\}', 'myfeature' | Set-Content $path
(Get-Content $path) -replace '\$\{FEATURE_PASCAL\}', 'MyFeature' | Set-Content $path
```

### Step 3: Customize Business Logic
- Fill in TODO comments with feature-specific logic
- Add additional methods as needed
- Extend StateFlow for additional data flows
- Update error handling for feature-specific exceptions

### Step 4: Test
```bash
./scripts/200-build-debug.cmd
./scripts/300-workflow.cmd  # Full test → build → install
```

---

## Documentation Embedded in Templates

Each template includes:
- KDoc comments explaining the pattern
- Code examples for common scenarios
- Why this pattern works (rationale)
- Common mistakes and how to avoid them
- Links to PATTERNS-LEARNED.md and DECISIONS.md

**Example:**
```kotlin
/**
 * Display and manage ${FEATURE} items.
 *
 * **State Management:**
 * - ViewModel holds StateFlow<${FEATURE_PASCAL}State>
 * - Fragment observes via repeatOnLifecycle(STARTED)
 * - State sealed class: Idle | Loading | Success(data) | Error(msg)
 *
 * **Lifecycle:**
 * - onCreateView: Inflate binding
 * - onViewCreated: Set up listeners, observe ViewModel
 * - onDestroyView: Clear binding (MUST NOT access binding after this)
 */
```

---

## Token Optimization via Templates

**Why Templates?**
- 📖 Documentation baked in (no separate context needed)
- 🎯 Boilerplate done (80% templates, 20% customization)
- ✅ Best practices enforced (right by default)
- 🔄 Consistent architecture (easier for AI to understand)
- ⚡ Faster development (less thinking, more coding)

**Example Benefit:**

**Without Templates:**
- Copy-paste from existing feature (200 lines)
- Maybe wrong pattern (android.util.Log instead of AppLogger)
- No documentation (have to ask AI or read PATTERNS-LEARNED.md)
- Search for subtle bugs (lifecycle issues, binding leaks)

**With Templates:**
- Copy template (200 lines + docs)
- Pattern enforced (AppLogger required, binding cleared)
- Documentation inline (KDoc explains everything)
- All best practices included (repeatOnLifecycle, sealed State)

**Context Savings:**
- ❌ Old: Need 50KB codebase context + PATTERNS-LEARNED.md (10KB)
- ✅ New: Copy template + customize TODO (done!)
- Result: 80% context reduction per feature

---

## Future Templates to Create

- [ ] Adapter.template.kt - ListAdapter + DiffUtil pattern
- [ ] Dialog.template.kt - DialogFragment with binding + state
- [ ] Worker.template.kt - CoroutineWorker with Hilt injection
- [ ] Dao.template.kt - Room DAO with common patterns
- [ ] Entity.template.kt - Room @Entity with best practices

---

## Maintenance

**When to Update Templates:**
- New pattern discovered (add to PATTERNS-LEARNED.md first)
- Bug found in pattern (fix template + all usages)
- DECISIONS.md changes (update docs in templates)

**Version Control:**
- Templates tracked in Git (but not in app APK)
- Changes committed with message: `chore: update templates - <reason>`

---

**Last Updated:** 2026-06-03  
**See Also:** PATTERNS-LEARNED.md, DECISIONS.md, TECH-STACK.md
