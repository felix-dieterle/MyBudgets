# Category Pattern Assignment — User Stories

## Overview

Category patterns let the user define rules that auto-assign categories to matching transactions on import. Two user-facing flows exist:

1. **Retroactive** — User manually categorizes a transaction and optionally creates a pattern from it
2. **Automatic** — During sync, `TransactionRepository.save()` matches incoming transactions against saved patterns

---

## US1: Manual Category Assignment

**As a** user viewing a transaction detail  
**I want to** pick a category from a hierarchical list  
**So that** I can classify the transaction for budgeting

**Acceptance:**

- Transaction detail shows a "Categorize" button (if `categoryId == null`) or current category name
- Tapping opens a category picker (hierarchical radio-button list, top-level expandable)
- Saving assigns `categoryId` to the transaction
- The transaction immediately appears with the category color/name in lists
- "Create Pattern" is offered after successful categorization

---

## US2: Create Pattern from Transaction (Without matchedName)

**As a** user who just categorized a transaction  
**I want to** create a keyword or IBAN pattern  
**So that** future transactions from the same sender are auto-categorized

**Acceptance:**

- After categorizing, a dialog shows pattern options:
  - **IBAN** (if available in the transaction note) — matches all transactions from that recipient
  - **TEXT** — matches a set of keywords from the description (chips, multi-select)
  - **No pattern** — just categorize this transaction once
- Selecting a category is required before saving
- On save:
  - Transaction gets `categoryId` assigned
  - A `CategoryPattern` row is created with `patternType`, `patternValue`, `categoryId`
  - A success message confirms: "Future transactions matching this pattern will be auto-categorized"

---

## US3: Create Pattern with matchedName

**As a** user who wants cleaner transaction descriptions in lists  
**I want to** set a custom display name when creating a pattern  
**So that** matched transactions show my name instead of the raw bank description

**Acceptance:**

- The pattern creation dialog has an optional text field "Anzeigename (optional)"
- When filled:
  - The current transaction's `description` is overwritten with the custom name
  - Future auto-matched transactions also get this custom `description`
  - Bank description is lost on match (intentional — user wants the clean name)
- When left empty:
  - The bank's original `description` is preserved (no change)
  - Existing patterns keep `matchedName = ""` and are unaffected

**Example UX:**

- Bank description: `V.12121423 EDEKA MÜNCHEN`
- matchedName: `EDEKA`
- In all lists/dashboard the transaction shows "EDEKA" as description

---

## US4: Bulk Update Existing Transactions on Pattern Creation

**As a** user creating a new pattern  
**I want to** optionally apply the same category to existing uncategorized transactions that match the pattern  
**So that** I don't have to manually categorize them one by one

**Acceptance:**

- After pattern creation, the system finds all existing transactions matching the pattern
- Three buttons presented:
  - **"Update Conflicts"** — reassigns ALL matching transactions (even those with a different category already assigned) to the new category
  - **"Keep Existing"** — assigns only uncategorized transactions, skips already-categorized ones
  - **Cancel** — does nothing
- Conflict dialog lists:
  - Count of uncategorized matches
  - Count already matching the selected category
  - Count with a different category (grouped by category name)
- If matchedName was set, it is applied to all updated transactions' descriptions

---

## US5: Auto-Match on Sync/Import

**As a** user importing new bank transactions  
**I want to** see them automatically categorized based on saved patterns  
**So that** I don't have to manually categorize recurring transactions

**Acceptance:**

- During `TransactionRepository.save()`, before insert:
  - If `categoryId` is already set → skip (manual assignment takes priority)
  - If `description` matches any pattern → assign `pattern.categoryId`
  - If `matchedName` is non-blank → overwrite `description` with it
  - AI fallback (`TransactionAiHelper.suggestCategoryId`) runs only if no pattern matched
- After insert, the pattern's `usageCount` is incremented
- Multiple patterns for the same description: first match wins (order by `usageCount DESC`)

---

## US6: Pattern Management (View/Toggle/Delete)

**As a** user who wants to review or clean up patterns  
**I want to** see a list of all saved patterns  
**So that** I can disable outdated ones or delete incorrect ones

**Acceptance:**

- Settings/Patterns screen shows all patterns in a list
- Each row displays:
  - Pattern type icon (IBAN / TEXT)
  - Pattern value
  - Target category name + color
  - Usage count
  - matchedName (if set)
- Toggle switch to enable/disable per pattern
- Swipe-to-delete or delete button with confirmation dialog

---

## US7: Pattern Suggestion on Categorization

**As a** user categorizing a transaction  
**I want to** see keyword chips automatically extracted from the description  
**So that** I can quickly create a TEXT pattern without typing

**Acceptance:**

- The pattern dialog analyzes the description:
  - Splits on `.`, `-`, `/` and whitespace
  - Lowercases and deduplicates
  - Removes stop words (`der`, `die`, `das`, `von`, `mit`, etc.)
  - Removes words shorter than 3 characters
- Resulting keywords are shown as toggle chips in a `ChipGroup`
- User selects one or more keywords
- On save, selected keywords are joined with `|` as `patternValue`

---

## Data Model

```kotlin
CategoryPattern(
    id: Long,
    categoryId: Long,       // FK → Category
    patternType: String,    // "IBAN" | "TEXT" | "HYBRID"
    patternValue: String,   // IBAN string OR keyword1|keyword2
    confidence: Double,     // 0.0-1.0
    usageCount: Int,
    lastUsed: Long?,
    createdAt: Long,
    matchedName: String     // custom display name (empty = use bank description)
)
```

## Auto-Match Logic (Priority Order)

```
1. categoryId already set on transaction → skip
2. description matches a TEXT pattern (first by usageCount DESC)
3. description matches a HYBRID pattern (IBAN + TEXT)
4. note contains IBAN matching an IBAN pattern
5. AI fallback (TransactionAiHelper)
```

## MatchedName Flow

```
Pattern saved with matchedName="EDEKA"
       ↓
On save: if pattern match AND matchedName is not blank
       ↓
transaction.description = "EDEKA"  (replaces bank description)
       ↓
In lists/dashboard → shows "EDEKA" instead of "V.12121423 EDEKA MÜNCHEN"
```

## Recurring vs One-Time Patterns

- All patterns are persistent (no "apply once" concept yet)
- To stop auto-matching: disable or delete the pattern
- RecurringRule matching happens separately (see `RecurringRule` entity)
