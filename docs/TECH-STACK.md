# Tech Stack - MyBudgets

**Generated from:** `gradle/libs.versions.toml` + `app/build.gradle.kts`  
**Last Updated:** 2026-06-03  
**Format:** Organized by category with rationale for each choice

---

## Build & Compilation

| Component | Version | Notes |
|-----------|---------|-------|
| **Android Gradle Plugin (AGP)** | 8.4.0 | Latest stable (AGP 9.x not yet adopted) |
| **Kotlin** | 1.9.23 | Latest stable (Kotlin 2.0 available but ecosystem not ready) |
| **Java Target** | 17 | Required by JAXB runtime (java.awt module needs Java 17) |
| **Gradle** | 8.7 | Specified in gradle-wrapper.properties |

**Rationale:**
- Java 17: Required by hbci4java's jaxb-runtime (module system)
- Kotlin 1.9.23: Stable, coroutine support mature
- AGP 8.4.0: Supports Java 17 fully

---

## Android Framework (UI & Lifecycle)

| Library | Version | Purpose |
|---------|---------|---------|
| **AndroidX Core-KTX** | 1.13.1 | Extension functions for Android APIs |
| **AndroidX AppCompat** | 1.7.0 | Backward compatibility (minSdk=26) |
| **Material Components** | 1.12.0 | Material Design 3 UI components |
| **AndroidX Navigation** | 2.7.7 | Fragment navigation + Safe Args |
| **AndroidX LifeCycle** | 2.8.0 | Lifecycle-aware components |

**Decision ADR-006:** Fragment binding pattern (ViewBinding mandatory)

```kotlin
// All UI requires ViewBinding
buildFeatures {
    viewBinding = true
}
```

**Rationale:**
- Material 1.12.0: Latest M3 (Material Design 3)
- Navigation: Safe Args for type-safe navigation
- LifeCycle: repeatOnLifecycle() for coroutine scope management

---

## Database & Persistence

| Library | Version | Purpose |
|---------|---------|---------|
| **AndroidX Room** | 2.6.1 | Object-relational mapping |
| **Room Compiler** | 2.6.1 | Code generation (@Entity, @Dao) |
| **AndroidX Security-Crypto** | 1.1.0-alpha06 | Encrypted SharedPreferences |
| **Gson** | 2.11.0 | JSON serialization |

**Decision ADR-010:** Non-nullable ID with 0L as default

```kotlin
@Entity
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L  // 0 = "not yet saved"
)
```

**Rationale:**
- Room 2.6.1: Supports Kotlin coroutines natively
- Security-Crypto: Encrypts sensitive data in SharedPreferences
- Gson: Used for pattern/transaction serialization

---

## Networking & REST API

| Library | Version | Purpose |
|---------|---------|---------|
| **Retrofit** | 2.11.0 | HTTP client for REST APIs |
| **Retrofit Gson Converter** | 2.11.0 | Gson adapter for Retrofit |
| **OkHttp Logging Interceptor** | 4.12.0 | HTTP request/response logging |

**Usage:**
- ❌ Not used for banking sync (FinTS/HBCI instead)
- ✅ Potential future: REST API for cloud sync

**Rationale:**
- Retrofit 2.11.0: Stable, coroutine support via suspend functions
- OkHttp: Logging interceptor for debugging (AppLogger integration)

---

## Dependency Injection

| Library | Version | Purpose |
|---------|---------|---------|
| **Hilt** | 2.51.1 | Dependency injection framework |
| **Hilt Compiler** | 2.51.1 | Code generation |
| **Hilt Work** | 1.2.0 | Hilt integration for WorkManager |

**Usage:**
```kotlin
@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val repo: TransactionRepository
) : ViewModel()

@AndroidEntryPoint
class TransactionFragment : Fragment()
```

**Rationale:**
- Hilt 2.51.1: Reduces boilerplate vs manual Dagger
- Hilt Work: Automatic injection in background workers

---

## Asynchronous Programming (Coroutines)

| Library | Version | Purpose |
|---------|---------|---------|
| **Kotlin Coroutines Android** | 1.8.1 | Coroutine dispatchers + lifecycle integration |
| **Kotlin Coroutines Test** | 1.8.1 | Test utilities (runBlockingTest, StandardTestDispatcher) |

**Decision ADR-001:** StateFlow + ViewModel for UI state

```kotlin
@HiltViewModel
class TransactionViewModel @Inject constructor(...) : ViewModel() {
    private val _state = MutableStateFlow<TxState>(TxState.Idle)
    val state: StateFlow<TxState> = _state.asStateFlow()
}

// Fragment consumption
viewLifecycleOwner.lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.state.collect { state -> ... }
    }
}
```

**Rationale:**
- Coroutines 1.8.1: Stable, full lifecycle integration
- StateFlow: Native to coroutines (vs LiveData/RxJava)
- repeatOnLifecycle(): Pauses collection when Fragment not visible

**NOT Used:**
- ❌ RxJava: Overkill for this complexity
- ❌ LiveData: Deprecated (Google recommends StateFlow)

---

## Background Jobs

| Library | Version | Purpose |
|---------|---------|---------|
| **AndroidX WorkManager** | 2.9.0 | Periodic background sync jobs |
| **Hilt Work** | 1.2.0 | Dependency injection in Workers |

**Usage:**
```kotlin
class BankingSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val bankingService: BankingService
) : CoroutineWorker(context, params)
```

**Rationale:**
- WorkManager 2.9.0: Handles OS doze/battery optimization
- Guaranteed execution (vs plain Coroutines)
- Future: Can schedule periodic banking sync

---

## Banking Integration (FinTS/HBCI)

| Library | Version | Purpose |
|---------|---------|---------|
| **hbci4java** | 3.1.88 | FinTS/HBCI banking protocol implementation |
| **java-awt-stub.jar** | (custom) | Provides java.awt.Image for jaxb-runtime on Java 17 |

**Critical Constraints:**
- Java 17 required (module system needs java.desktop)
- Custom stub JAR: Avoids "package exists in another module" error
- Package excludes: Exclude duplicate META-INF files from hbci4j

**Usage:**
```kotlin
// FintsService.kt
class FintsService @Inject constructor(...) {
    suspend fun loadTransactions(
        dateFrom: Long,
        dateTo: Long
    ): List<Transaction>
}
```

**⚠️ NO TOUCH ZONE:**
- FintsService.kt: Proven stable since 2026-05-12
- CustomCamtParser.kt: Handles BBBank quirks (XML repair)
- Never modify unless new bank pattern discovered

**Rationale:**
- hbci4java 3.1.88: Only Java FinTS library with CAMT support
- No alternatives (Figo/Plaid require API keys + costs)

---

## Data Visualization

| Library | Version | Purpose |
|---------|---------|---------|
| **MPAndroidChart** | 3.1.0 | Line/Bar charts for expense dashboard |

**Usage:**
```kotlin
// Dashboard shows monthly expense breakdown
binding.chart.apply {
    data = LineData(entries)
    description.text = "Monthly Expenses"
}
```

**Rationale:**
- MPAndroidChart 3.1.0: Most popular Android charting library
- Lightweight (~500KB)
- Supports multiple chart types

---

## Testing

| Library | Version | Purpose |
|---------|---------|---------|
| **JUnit 4** | 4.13.2 | Unit test framework |
| **MockK** | 1.13.10 | Kotlin mocking library |
| **Robolectric** | 4.13 | Android runtime simulation for unit tests |
| **AndroidX Test Core** | 1.6.1 | Android test utilities |

**Strategy:**
- ✅ Unit tests: Business logic (Repository, ViewModel, formatters)
- ❌ Espresso: Too fragile (breaks on UI changes)
- ✅ Manual tests: Real device with real data (better feedback)

**Example Tests:**
```kotlin
@Test
fun testRepository_saveNewEntity_callsInsert() {
    val tx = Transaction(id = 0L, ...)
    coEvery { dao.insert(any()) } returns 1L
    
    val result = runBlocking { repo.save(tx) }
    
    coVerify { dao.insert(tx) }
    assertEquals(1L, result)
}

@Test
fun testPatternMatcher_andLogic() {
    assertTrue(PatternMatcher.matchTextPattern(
        "EDEKA|Lebensmittel",
        "Einkauf EDEKA Lebensmittel",
        ""
    ))
}
```

**Rationale:**
- MockK: Kotlin-native mocking (vs Mockito Java boilerplate)
- Robolectric: Tests without emulator (~100x faster)

---

## NOT Included (Intentionally)

| Library | Why Not | Alternative |
|---------|---------|-------------|
| **RxJava** | Overkill (full reactive system) | StateFlow (simpler) |
| **Dagger (manual)** | Too verbose | Hilt (wrapper) |
| **Firebase** | Requires internet (offline-first app) | AppLogger (local) |
| **Sentry/Crashlytics** | Overkill | AppLogger + local logs |
| **Jetpack Compose** | Not ready for complexity | Fragments + ViewBinding |
| **Retrofit** | Using FinTS instead | Direct banking protocol |
| **Paging Library** | Overkill (limited TX count) | Manual pagination if needed |
| **DataStore** | Overkill | SharedPreferences (Security-Crypto) |
| **Jetpack MultiPlatform** | Not needed | Android-only app |

**Decision Philosophy:**
- Minimal dependencies (easier to maintain)
- Proven libraries only (no experimental)
- Focus on stability over features

---

## Dependency Graph (Simplified)

```
app/
├── Android Framework (Core, AppCompat, Material)
├── Room
│   └── Database + Persistence
├── Hilt + Work
│   └── DI + Background Jobs
├── Coroutines + StateFlow
│   └── Async + UI State
├── Navigation + LifeCycle
│   └── UI Navigation + Lifecycle Safety
├── hbci4java
│   └── Banking Integration (FinTS/HBCI)
├── Retrofit (Future REST API)
├── Gson (JSON Serialization)
├── MPAndroidChart (Dashboard Charts)
└── Test Libraries (JUnit, MockK, Robolectric)
```

---

## Compilation & Minification

**ProGuard/R8 Rules:**
```properties
# app/proguard-rules.pro
-keep class hbci4j.** { *; }
-keep class de.mybudgets.app.** { *; }
-dontwarn javax.xml.**
```

**Packaging Excludes:**
```kotlin
// Duplicate META-INF files from hbci4j
packaging {
    resources {
        excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/*.kotlin_module"
        )
    }
}
```

**Release Build:**
- ProGuard enabled (minifyEnabled = true)
- Obfuscation enabled
- Debug signing (no release keystore yet)

---

## Key Versions Summary

| Category | Min | Current | Max Considered |
|----------|-----|---------|-----------------|
| **Kotlin** | 1.9.x | 1.9.23 | 2.0.x (waiting) |
| **Java** | 17 | 17 | 21 (future) |
| **Min SDK** | 26 | 26 | 28 (future) |
| **Target SDK** | 34 | 34 | 35 (next) |
| **Room** | 2.6.x | 2.6.1 | 3.0.x (alpha) |
| **Coroutines** | 1.8.x | 1.8.1 | 2.0.x (future) |
| **Hilt** | 2.50.x | 2.51.1 | 2.52.x (next) |

---

## Update Strategy

**Cadence:**
- AGP: Every 4-6 months (match Kotlin release cycles)
- Kotlin: Every 6-8 months (wait for ecosystem catch-up)
- Libraries: Monthly security updates, quarterly feature updates
- Java: Only update for major security fixes (Java 17 → 21 in 1-2 years)

**Testing Before Update:**
```bash
# 1. Update version
# 2. Run build
./scripts/200-build-debug.cmd
# 3. Test on device
adb install app/build/outputs/apk/debug/app-debug.apk
# 4. Manual testing (category sync, pattern matching, charts)
# 5. If OK → commit
```

---

## CI/CD Pipeline

**Gradle Build:**
- AGP 8.4.0 with Gradle 8.7
- Parallel compilation (--parallel)
- Build cache enabled

**GitHub Actions:**
- Auto-build on push (disabled per v332+ decision)
- APK signed with debug keystore
- Currently manual trigger only

**Local Build Scripts:**
- `scripts/200-build-debug.cmd`: Build debug APK
- `scripts/202-build-apk.cmd`: Build versioned APK for distribution
- `scripts/300-workflow.cmd`: Full test → build → install

---

## Performance Characteristics

| Operation | Target | Current | Status |
|-----------|--------|---------|--------|
| **App Startup** | <2s | ~1.5s | ✅ Good |
| **Category Load** | <100ms | ~50ms | ✅ Good |
| **Pattern Match 500TX** | <100ms | ~50ms | ✅ Good |
| **Banking Sync 689 days** | <10min | ~5-10min | ✅ Good |
| **APK Size** | <50MB | ~35MB | ✅ Good |
| **Memory (Foreground)** | <100MB | ~60-80MB | ✅ Good |

**Bottlenecks (if needed):**
- [ ] Pattern matching: Could cache regex compilations
- [ ] Banking sync: Could parallelize CAMT parsing
- [ ] Chart rendering: Could use Canvas drawing (vs MPAndroidChart)

---

**Last Updated:** 2026-06-03  
**Next:** IMPLEMENTATION-PLAYBOOK.md Phase 1.4 (Template creation)
