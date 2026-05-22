# Historical Sync UX Optimization Plan

**Context:** BBBank limit = 150 TX per sync, TAN per request
**Goal:** Make multi-sync as efficient/painless as possible

---

## Current Pain Points

1. **Recurrence Dialog after EVERY sync** (even mid-loading)
2. **No clear "load more" indicator** - user must guess if more history exists
3. **PIN re-entry every 2 minutes** during multi-sync sessions
4. **No bulk-load mode** - must tap sync button repeatedly

---

## Optimizations

### 1. ✅ PIN Caching (Already Implemented)
**Status:** Working (2 min cache, resets on use)
**Current:** `FintsService.kt` Line 117-144
**Good enough:** Yes, 2 min sufficient for multi-sync

### 2. 🔧 Recurrence Detection - Defer Until End
**Change:** Only show dialog when:
- User explicitly stops loading (back button / cancel)
- No more gaps to fill (reached today or oldest available)
- NOT after every intermediate sync

**Implementation:**
```kotlin
// AccountViewModel
private var pendingRecurrenceCheck = false

fun onSyncSuccess(...) {
    if (hasMoreGaps() && !userCancelled) {
        pendingRecurrenceCheck = true  // Defer
        // Don't call checkForRecurringPatterns()
    } else {
        // Final sync or user stopped
        if (pendingRecurrenceCheck) {
            checkForRecurringPatterns()
        }
    }
}
```

**Files:**
- `AccountViewModel.kt`: Line ~250 (after sync success)
- `AccountDetailFragment.kt`: Line ~180 (pattern dialog trigger)

### 3. 🔧 Smart "Load More History" Button
**Change:** Show persistent button when gaps exist
- Button text: "Ältere Buchungen laden (bis DD.MM.YYYY)" 
- Shows next gap date
- Tapping triggers sync for that gap
- Button disappears when no gaps left

**UI:**
```kotlin
// AccountDetailFragment
binding.btnLoadMore.apply {
    visibility = if (viewModel.hasHistoricalGaps) VISIBLE else GONE
    text = "Ältere Buchungen laden (bis ${viewModel.nextGapDate})"
    setOnClickListener { viewModel.syncHistoricalGap() }
}
```

**Files:**
- `fragment_account_detail.xml`: Add button below transaction list
- `AccountDetailFragment.kt`: Observe gap state
- `AccountViewModel.kt`: Expose `hasHistoricalGaps`, `nextGapDate`

### 4. 🆕 Bulk Load Mode
**Change:** "Alle verfügbaren laden" option
- Show dialog: "X Lücken gefunden (ca. X TANs). Alle laden?"
- Loop syncs automatically until no gaps
- Show progress: "Lade Lücke 2/5..."
- User can cancel mid-bulk

**Implementation:**
```kotlin
// AccountViewModel
suspend fun bulkLoadHistory(onProgress: (current: Int, total: Int) -> Unit) {
    val gaps = syncIntervalRepo.getHistoricalGaps(accountId)
    for ((index, gap) in gaps.withIndex()) {
        onProgress(index + 1, gaps.size)
        val result = syncHistoricalGap(gap.startDate)
        if (result.isCancelled) break
    }
    checkForRecurringPatterns() // Only once at end
}
```

**Files:**
- `AccountViewModel.kt`: New `bulkLoadHistory()` function
- `AccountDetailFragment.kt`: Show bulk dialog, progress UI

### 5. 🆕 TAN Count Preview
**Change:** Before starting bulk load, estimate TAN count
- "Voraussichtlich X TANs erforderlich"
- Based on gap count (1 TAN ≈ 1 gap)
- User can decide if worth it

**Implementation:**
```kotlin
// SyncIntervalRepo
fun estimateTanCount(accountId: Long): Int {
    val gaps = getHistoricalGaps(accountId)
    return gaps.size // Simple: 1 TAN per gap
}
```

### 6. 🆕 Background Sync During Loading
**Change:** Don't block UI during multi-sync
- Show loading overlay with progress
- Allow canceling
- User can navigate away (sync continues in background)

**Files:**
- Use WorkManager for background task?
- Or: Keep in ViewModel, show persistent notification

### 7. 🆕 Smart Gap Detection Hints
**Change:** Show visual gaps in transaction list
- Empty row: "--- Lücke: 120 Tage fehlen (01.01.2024 - 30.04.2024) ---"
- Tap to load that specific gap

**UI Benefits:**
- User sees WHERE gaps are
- Can prioritize (load recent gaps first)
- Visual feedback on progress

---

## Recommended Implementation Order

### Phase 1: Quick Wins (1-2 hours)
1. ✅ PIN Caching (already done)
2. 🔧 Defer recurrence detection until end
3. 🔧 Add "Load More History" button

### Phase 2: Bulk Loading (3-4 hours)
4. 🆕 Bulk load mode with progress
5. 🆕 TAN count preview

### Phase 3: Polish (2-3 hours)
6. 🆕 Visual gap indicators in list
7. 🆕 Background sync with notification

---

## Additional Ideas

### A. Session-Based TAN Caching (DANGEROUS)
**Idea:** Cache TAN for 5-10 minutes for bulk loads
**Risk:** Security issue if app stolen during session
**Verdict:** ❌ Don't implement (PIN cache sufficient)

### B. Predict Historical Range
**Idea:** Ask user "Wie weit zurück?" before first sync
- User picks: "3 Monate" / "1 Jahr" / "Alles"
- App calculates needed syncs upfront

**Pros:** User knows commitment upfront
**Cons:** User may not know answer

### C. Offline Interval Estimation
**Idea:** Show estimated missing days in UI
- "Letzte Buchung: 15.07.2024 (vor 676 Tagen)"
- "Geschätzt ~4 Syncs für vollständige Historie"

**Files:** `AccountDetailFragment.kt`, `SyncIntervalRepo.kt`

### D. Auto-Continue After Sync
**Idea:** After successful sync, show toast: "Weiter laden? (noch X Lücken)" with YES/NO buttons
**Pros:** Zero extra taps for user who wants full history
**Cons:** May feel pushy

### E. Desktop/Web Companion Tool
**Idea:** Bulk-load history on PC (faster, less tedious)
**Pros:** One-time setup, then mobile app syncs deltas only
**Cons:** Out of scope (needs backend/export)

---

## Testing Plan

1. **Scenario 1: Single Gap**
   - New account, sync once → 150 TX
   - Check: No recurrence dialog (gap still exists)
   - Tap "Load More" → sync again
   - Check: Recurrence dialog shown (no more gaps)

2. **Scenario 2: Bulk Load**
   - New account with ~500 days missing
   - Estimate: 3-4 gaps (TAN preview shows "3 TANs")
   - Start bulk load → progress bar updates
   - Cancel after 2 syncs → verify stop, show dialog
   - Resume → complete → final recurrence dialog

3. **Scenario 3: PIN Cache**
   - Start bulk load (3 syncs)
   - Verify: PIN asked only once (at first sync)
   - Wait 3 minutes idle → next sync asks PIN again

---

## Files to Modify

**Core Logic:**
- `AccountViewModel.kt`: Bulk load, gap detection, deferred recurrence
- `SyncIntervalRepo.kt`: Gap estimation, TAN count preview
- `FintsService.kt`: (No changes - PIN cache already works)

**UI:**
- `fragment_account_detail.xml`: Add "Load More" button, bulk dialog
- `AccountDetailFragment.kt`: Button logic, dialog handling
- `item_transaction.xml`: (Optional) Gap indicator layout

---

**Created:** 2026-05-22 20:25
**Status:** Planning phase - no implementation yet
**Priority:** Phase 1 (defer recurrence + load more button) = CRITICAL for UX
