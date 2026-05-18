# MyBudgets - Development Roadmap

**Last Updated:** 2026-05-12  
**Current Status:** ✅ BBBank Sync Working (v1.0.10)

---

## Vision

Budget-App mit Fokus auf:
- **Kontrolle:** Wo geht mein Geld hin?
- **Trends:** Steigt/sinkt eine Kategorie?
- **Forecast:** Was kommt die nächsten 3 Monate?
- **Mobile UX:** Schnell, thumb-friendly, offline-first

---

## Technical Foundations

### Transaction Deduplication
```kotlin
// Unique ID = SHA-256 hash of key fields
fun Transaction.generateId() = sha256("$accountNumber|$date|$amount|$otherParty|$usage")

// Room: onConflict = IGNORE
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertAll(transactions: List<Transaction>)
```

### Incremental Sync Strategy
- **Batch size:** 50 transactions per API call
- **Direction:** Newest first, then load older batches on demand
- **UI:** "Load 50 more (150 older available)" button
- **Rate limit:** Max 1 call per 5 seconds

### Data Model Extensions Needed
```kotlin
// Add to Transaction entity
val categoryId: String? = null
val notes: String? = null

// New entities
@Entity data class Category(id, name, emoji, level, parentId, color)
@Entity data class SyncMetadata(accountId, lastSyncDate, oldestTransactionDate)
```

---

## Milestone 1: Categories & Basic Charts (MVP)

**Goal:** User kann Transaktionen kategorisieren und Verteilung sehen

**Database:**
- [ ] Add `categoryId` column to `transactions` table (migration)
- [ ] Create `categories` table (id, name, emoji, level, parentId, color, sortOrder)
- [ ] Pre-populate with default categories (Food, Housing, Transport, Shopping, Lifestyle)
- [ ] Add indices: `transactions(categoryId)`, `categories(parentId)`

**UI: Category Management:**
- [ ] Screen: Category list (show hierarchy, expandable)
- [ ] Screen: Edit/add category (name, emoji picker, parent selector, color picker)
- [ ] Default categories: 5 level-1 + ~15 level-2 (see ROADMAP-UX-FEATURES.md line 57-87 for full list)

**UI: Transaction Categorization:**
- [ ] Transaction list: Show emoji + category name per transaction
- [ ] Transaction detail: Tap to open category picker (bottom sheet, hierarchical)
- [ ] Bulk action: Long-press → multi-select → "Categorize all"
- [ ] Save rule: "Auto-categorize future transactions from REWE as Food>Groceries"

**UI: Donut Chart (Home Screen):**
- [ ] Library: Add MPAndroidChart or Vico dependency
- [ ] Query: `SELECT categoryId, SUM(ABS(amount)) FROM transactions WHERE amount < 0 GROUP BY categoryId`
- [ ] Chart: Donut with level-1 categories (Food 35%, Housing 30%, etc.)
- [ ] Timeframe selector: This month / Last month / Last 3 months / Custom
- [ ] Interaction: Tap slice → drill down to level-2 categories

**Testing:**
- [ ] Unit test: Category hierarchy queries (get children, get path)
- [ ] Unit test: Transaction aggregation with filters
- [ ] UI test: Categorize transaction flow
- [ ] Manual test: Create category → assign to 10 transactions → see in donut chart

**Success Criteria:**
- User can assign categories in <5 taps
- Donut chart loads in <1 second for 1000 transactions
- Category changes reflect immediately in chart (optimistic UI)

---

## Milestone 2: Incremental Sync & Filters

**Goal:** User kann beliebig viele alte Transaktionen laden ohne Duplikate

**Database:**
- [ ] Create `sync_metadata` table (accountId, lastSyncDate, oldestTransactionDate, totalSynced)
- [ ] Add `syncBatchId` to transactions (UUID per sync run)

**Backend: Sync Service:**
- [ ] Method: `syncNewest(account, limit=50)` - Load newest transactions
- [ ] Method: `syncOlder(account, limit=50)` - Load next older batch
- [ ] Logic: Check if more available (compare oldestTransactionDate with API limit)
- [ ] Update `SyncMetadata` after each sync
- [ ] Deduplication: Filter by existing IDs before insert

**UI: Incremental Loading:**
- [ ] Transaction list footer: "Load 50 more (120 older available)" button
- [ ] Show progress: "Loading..." with spinner
- [ ] Success message: "Loaded 50 transactions back to 2025-12-15"
- [ ] End state: "No older transactions available" (disable button)

**UI: Transaction Filters:**
- [ ] Filter chip bar: [Category ▾] [Date ▾] [Amount ▾] [Search 🔍]
- [ ] Category filter: Multi-select, hierarchical (Level 1 OR Level 2)
- [ ] Date filter: Presets (This month, Last 3 months) + custom picker
- [ ] Amount filter: Min/max slider
- [ ] Search: Text input, fuzzy match on `usage` and `otherParty`
- [ ] Persist filter state (SharedPreferences)
- [ ] Show active filters: "Showing 23 of 456 transactions"

**Testing:**
- [ ] Unit test: Deduplication logic (same transaction synced twice → still 1 in DB)
- [ ] Unit test: SyncMetadata updates correctly
- [ ] Integration test: Sync 50 → sync older 50 → check oldestTransactionDate
- [ ] UI test: Apply filter → load more → filter still active

**Success Criteria:**
- Can load 1000 transactions in 20 batches without duplicates
- Filters work correctly with pagination
- Sync takes <3 seconds per batch (on WiFi)

---

## Milestone 3: Trend Chart & Forecast

**Goal:** User sieht Trends über Zeit und bekommt 3-Monats-Forecast

**UI: Trend Chart (Home Screen):**
- [ ] Chart type: Multi-line chart (time on X-axis, amount on Y-axis)
- [ ] Aggregation: Monthly (default), weekly, or daily (selector)
- [ ] Category selector: Checkboxes for top 5 categories + "More..."
- [ ] Query: `SELECT categoryId, MONTH(date), SUM(amount) GROUP BY categoryId, MONTH(date)`
- [ ] Visualization: Smooth lines, color-coded per category
- [ ] Highlight: Show trend direction (+15% ↑ or -8% ↓ since last month)

**UI: Forecast Section:**
- [ ] Algorithm: Simple moving average (last 6 months) + linear regression for trend
- [ ] Display: "Next 3 months forecast: Food €850/mo (+3.7%)"
- [ ] Confidence: Show range if variance is high (€800-900)
- [ ] Settings: Configurable forecast period (1-12 months)

**Backend: Analytics Service:**
- [ ] Method: `getTrendData(categoryIds, fromDate, toDate, groupBy: Period)`
- [ ] Method: `getForecast(categoryId, months: Int): ForecastResult`
- [ ] Cache results: Invalidate on new transactions
- [ ] Background calculation: Pre-compute on sync

**Testing:**
- [ ] Unit test: Forecast algorithm (mock data with known trend → verify output)
- [ ] Unit test: Trend calculation (group by month/week/day)
- [ ] UI test: Select category → trend chart updates
- [ ] Manual test: Add transactions in pattern → forecast should reflect

**Success Criteria:**
- Trend chart renders in <2 seconds
- Forecast is within ±10% accuracy for stable categories
- UI updates smoothly when switching categories

---

## Milestone 4: Smart Features & Polish

**Goal:** App fühlt sich intelligent und polished an

**Auto-Categorization:**
- [ ] Create `category_rules` table (merchantPattern regex, categoryId, confidence)
- [ ] UI: "All future transactions from REWE → Food>Groceries" (save rule)
- [ ] Backend: Apply rules on new transactions automatically
- [ ] Suggestion: "5 transactions from Shell, categorize as Transport>Car>Fuel?"

**Recurring Transactions:**
- [ ] Detect: Transactions with same amount ±5% every 28-32 days
- [ ] UI: "Recurring detected: Rent €700 every 1st" (mark as recurring)
- [ ] Budget planning: Separate fixed vs variable costs

**Budget Goals:**
- [ ] Create `budget_goals` table (categoryId, monthlyLimit, startDate)
- [ ] UI: Set budget per category (e.g., "Food: €800/month")
- [ ] Progress bar: Show €656/€800 (82%, green)
- [ ] Notification: Warning at 80%, 100%, 110%

**UX Polish:**
- [ ] Swipe gestures: Left = categorize, Right = add note
- [ ] Animations: Smooth transitions (300ms fade/slide)
- [ ] Haptic feedback: On categorization, button press
- [ ] Skeleton screens: Show while loading data
- [ ] Empty states: Nice illustrations when no data

**Performance:**
- [ ] Pagination: Use Paging 3 library for transaction list
- [ ] Indexing: Add indices for slow queries
- [ ] Background work: Use WorkManager for daily sync
- [ ] Memory: Profile with Android Studio profiler

**Testing:**
- [ ] Unit test: Auto-categorization rules (regex matching)
- [ ] Unit test: Recurring detection algorithm
- [ ] Performance test: Load 10,000 transactions → measure scroll FPS
- [ ] Accessibility test: TalkBack, large fonts

**Success Criteria:**
- 80%+ transactions auto-categorized correctly
- Smooth 60fps scrolling with 5000+ transactions
- Common tasks completable in <5 taps

---

## Out of Scope (For Now)

- Multi-currency support
- Cloud sync / multi-device
- Receipt scanning (OCR)
- Investment tracking
- Shared budgets (multi-user)
- Integration mit anderen Banken (focus: BBBank MVP)

Diese Features kommen **nach** erfolgreicher MVP-Validierung mit echten Usern.

---

## Current Sprint

**Active Milestone:** None (Planning Phase)  
**Next Step:** Start Milestone 1 - Setup database migrations for categories

**Decision Needed:**
- UI Framework: Jetpack Compose oder XML Views?
- Chart Library: MPAndroidChart (mature) oder Vico (modern, Compose)?
- Min SDK: Bleiben bei 26 oder auf 24 senken für mehr Geräte?

---

## Notes for AI Implementation

**Beim Start eines Features:**
1. Prüfe existierenden Code (FintsService.kt, Database, ViewModels)
2. Erstelle DB Migration ZUERST (test mit unit test)
3. Backend/Repository logic DANN
4. UI/ViewModel ZULETZT
5. Manual test before marking done

**Code Style:**
- Kotlin idioms (data classes, sealed classes, Flow)
- Dependency Injection via Hilt
- Room for database
- Coroutines für async operations
- AppLogger für logging (schon vorhanden)

**Testing Strategy:**
- Unit tests für business logic (deduplication, forecast, etc.)
- Integration tests für Database + Repository
- UI tests nur für kritische flows (categorization, sync)
- Manual testing via Debug APK auf echtem Gerät

**Git Commits:**
- Feature branches: `feature/milestone-1-categories`
- Commit message format: `feat(categories): Add category database schema`
- PR nach jedem Milestone, nicht nach jedem Feature

---

**Let's build! 🚀**
