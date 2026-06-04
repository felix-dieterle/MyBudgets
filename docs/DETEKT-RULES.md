# Detekt Rules - MyBudgets

**Purpose:** Code quality linting with AI-friendly feedback (non-blocking).

**Philosophy:** Help AI understand code style WITHOUT breaking builds or preventing implementation.

**When to Run:**
- Optional: `./gradlew detekt` (before commit, for info)
- CI would run (if enabled)
- Not a blocker (AI can iterate even if violations exist)

---

## Setup

**Already in build.gradle.kts:**
```kotlin
plugins {
    id("io.gitlab.arturbosch.detekt")
}
```

**To run:**
```bash
cd MyBudgets
./gradlew detekt
```

**Output:** `build/reports/detekt/detekt.html` (open in browser)

---

## Rules Configuration

Create `app/detekt.yml` with rules tailored for MyBudgets patterns:

### Style Issues (Not Critical)
- Naming conventions: Classes `PascalCase`, functions `camelCase`
- Line length: Max 120 chars (not 80, to avoid excessive wrapping)
- TODO comments: Allowed (used throughout templates)

### Critical Issues (Quality Gates)
- No magic numbers (e.g., `if (id == 0L)` is exception, allowed)
- No unused imports
- No unused variables
- Function length: Max 50 lines (small functions encouraged)

### Android Specifics
- No `android.util.Log` calls (must use `AppLogger`)
- ViewBinding pattern: No direct view access outside binding.xxx
- Fragment lifecycle: `onDestroyView` must set binding to null

---

## Implementation (detekt.yml)

```yaml
---
build:
  maxIssues: 100  # Allow some violations (not a hard blocker)
  excludeCorrectable: false

config:
  validation: true
  warningsAsErrors: false  # Don't fail build on warnings
  checkBuildUponDefaultConfig: true

console-reports:
  active: true
  exclude:
    - 'ProjectStatisticsReport'
    - 'MetricsReport'

output-reports:
  active: true
  exclude: []
  include:
    - 'HtmlReport'

rules:
  active: true
  
  style:
    active: true
    MaxLineLength:
      active: true
      maxLineLength: 120  # More generous for readability
    MagicNumber:
      active: true
      ignoreNumbers:
        - '-1'
        - '0'
        - '1'
        - '2'
        - '3'
        - '24'  # Common DP size
        - '16'  # Common DP padding
        - '3000'  # AppLogger max entries
        - '689'  # Days in sync (domain constant)
      ignoreEnums: true
    NamingRules:
      active: true
      FunctionNaming:
        active: true
        functionPattern: '^([a-z$_][a-zA-Z$_0-9]*)|(`.*`)$'
      VariableNaming:
        active: true
        variablePattern: '^(_)?[a-z$_][a-zA-Z$_0-9]*$'
      ConstantNaming:
        active: true
        constantPattern: '^([A-Z_][A-Z0-9_]*|`.*`)$'
    UnusedImports:
      active: true
    UnusedVariable:
      active: true
    UnusedPrivateMember:
      active: true
  
  complexity:
    active: true
    LongMethod:
      active: true
      threshold: 50  # Functions >50 lines need refactoring
    ComplexMethod:
      active: true
      threshold: 15  # Cyclomatic complexity threshold
    LongParameterList:
      active: true
      threshold: 6  # Max 6 parameters
    TooManyFunctions:
      active: true
      thresholdInClasses: 15
      thresholdInInterfaces: 15
  
  coroutines:
    active: true
    GlobalCoroutineUsage:
      active: true
    RedundantSuspendModifier:
      active: true
  
  potential-bugs:
    active: true
    DuplicateCaseInWhenExpression:
      active: true
    EqualsWithHashCodeExist:
      active: true
    WhenExpressionOnNullableType:
      active: true
    NullPointerException:
      active: true

  exclusions:
    - '**/test/**'
    - '**/androidTest/**'
    - '**/build/**'
```

---

## Custom Rules for MyBudgets

### Rule: No android.util.Log

**Rationale:** All logging must go through AppLogger (in-app visibility).

**Configuration:**
```yaml
custom:
  NoAndroidUtilLog:
    active: true
    ruleSet: 'MyBudgetsRules'
```

**What to do if detected:**
```kotlin
❌ import android.util.Log
❌ Log.e(TAG, "message")

✅ import de.mybudgets.app.util.AppLogger
✅ AppLogger.e(TAG, "message")
```

### Rule: Fragment Binding Cleanup

**Rationale:** Fragment must set `_binding = null` in `onDestroyView()`.

**Template Check:** Templates enforce this, Detekt warns if missing.

---

## Running Detekt

**Full Analysis:**
```bash
./gradlew detekt
open build/reports/detekt/detekt.html
```

**Specific Module:**
```bash
./gradlew app:detekt
```

**CI Integration (Optional, Not Blocking):**
```bash
# Before GitHub Actions run
./gradlew detekt

# Generate report but DON'T fail build
exit 0
```

---

## AI Development: How to Use Detekt Output

**For AI:**
1. After generating code, run `./gradlew detekt`
2. Read `build/reports/detekt/detekt.html`
3. Learn from violations (style feedback)
4. Iterate: Fix violations if easy, skip if complex
5. No blocker (build still succeeds)

**Common Violations & Fixes:**

| Violation | Cause | Fix |
|-----------|-------|-----|
| `NamingRules` | `val myVariable` should be `myVariable` | Detekt auto-correctable in some IDEs |
| `UnusedImports` | Stale imports from copy-paste | `./gradlew detektFormat` (if enabled) |
| `LongMethod` | Function >50 lines | Split into smaller functions |
| `ComplexMethod` | Too many branches (if/when) | Extract logic to separate methods |
| `TooManyFunctions` | Class has 20+ functions | Split into smaller classes |

---

## Why This Approach Works for AI

✅ **Non-blocking:** AI can still build and test even with violations  
✅ **Feedback:** Violations visible in HTML report (learning tool)  
✅ **Iterative:** AI improves code quality over multiple passes  
✅ **No Silent Failures:** Violations clearly visible (not hidden in hook logs)  
✅ **Safe for Small Models:** Haiku can understand style feedback without being blocked  

---

## Future: Automated Formatting (Later)

If you want auto-formatting later:
- `detekt --autoCorrect` (fix style issues automatically)
- But NOT as pre-commit hook (too risky for AI)
- Manual: Run after AI generates code, before final review

---

**Last Updated:** 2026-06-04  
**Next:** Consider Phase 3 (Skills) or ad-hoc improvements as needed

