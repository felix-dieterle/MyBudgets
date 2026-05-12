# MyBudgets - UX & Features Roadmap

**Status:** Brainstorming Phase  
**Last Updated:** 2026-05-12  
**Core Philosophy:** Minimal API calls, excellent mobile UX, actionable budget insights

---

## 🎯 Core Goals

1. **Budget Control** - Know exactly where money is going
2. **Trend Analysis** - See if spending increases/decreases over time
3. **Forecasting** - Predict future expenses based on historical data
4. **Hierarchical Categories** - Industry-standard categorization (Food → Groceries → Supermarket)
5. **Mobile-First UX** - Fast, intuitive, thumb-friendly

---

## 🏗️ Architecture Principles

### API Call Minimization
- **Sync Strategy:** Incremental loading (50 transactions per batch)
- **Duplicate Prevention:** Transaction hash (date + amount + usage + otherParty) as unique key
- **Caching:** Room database as single source of truth
- **Smart Sync:** Only fetch new data when explicitly requested or on app start if last sync > 24h

### Data Integrity
```kotlin
// Transaction unique identifier strategy
data class Transaction(
    @PrimaryKey val id: String = generateId(), // SHA-256 hash
    val externalId: String? = null, // Bank's transaction ID if available
    val bankCode: String, // "BBBank", "Sparkasse", etc.
    val accountNumber: String,
    val date: LocalDate,
    val amount: BigDecimal,
    val usage: String,
    val otherParty: String,
    val syncBatchId: String, // To track which sync brought this transaction
    val createdAt: Instant,
    val updatedAt: Instant
)

fun generateId(): String {
    val key = "$date|$amount|$usage|$otherParty|$accountNumber"
    return sha256(key)
}
```

---

## 📊 Feature Breakdown

### 1. Hierarchical Categories (HIGH PRIORITY)

**UI Pattern:** Industry-standard 3-level hierarchy
```
Level 1 (Main)     Level 2 (Sub)        Level 3 (Detail)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🍔 Food            → Groceries          → Supermarket (Aldi, Rewe)
                   → Restaurants        → Fast Food
                   → Delivery           → Lieferando, UberEats
                   
🏠 Housing         → Rent               → Monthly Rent
                   → Utilities          → Electricity, Water, Gas
                   → Internet/Phone     
                   
🚗 Transport       → Car                → Fuel, Insurance, Repairs
                   → Public Transport   → Bus, Train
                   → Ride-sharing       → Uber, Taxi
                   
💳 Shopping        → Clothing           
                   → Electronics        
                   → Household Items    
                   
🎉 Lifestyle       → Entertainment      → Cinema, Concerts
                   → Sports/Fitness     → Gym, Equipment
                   → Hobbies            
                   
💰 Income          → Salary             
                   → Freelance          
                   → Other Income       
                   
💸 Savings         → Emergency Fund     
                   → Investments        
                   → Goals              → Vacation, House, etc.
```

**Data Model:**
```kotlin
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String,
    val level: Int, // 1, 2, or 3
    val parentId: String? = null, // null for level 1
    val color: String, // Hex color for charts
    val sortOrder: Int = 0,
    val isDefault: Boolean = false, // User-created or default
    val isActive: Boolean = true // Soft delete
)

// Pre-populate database with sensible defaults
// Allow users to add/edit/hide categories
```

**UX Flow:**
1. **Transaction List:** Show emoji + Level 2 category name
   - Tap to expand → Show full path: "Food > Groceries > Aldi"
2. **Categorization Screen:**
   - Swipeable cards for quick categorization (Tinder-style)
   - Or: Bottom sheet with expandable hierarchy tree
   - Remember last category for same merchant (auto-suggest)
3. **Bulk Categorization:**
   - "All transactions from 'REWE' → Food > Groceries > Supermarket"
   - Apply to past + future transactions

**Smart Categorization:**
```kotlin
// Auto-categorization rules
data class CategoryRule(
    val merchantPattern: String, // Regex: ".*REWE.*", ".*ALDI.*"
    val categoryId: String,
    val confidence: Float // 0.0 - 1.0
)

// Machine Learning (Phase 2):
// - Learn from user's manual categorizations
// - Suggest categories based on amount patterns
// Example: Transactions ~€900-1000 monthly → likely rent
```

---

### 2. Charts & Visualizations (HIGH PRIORITY)

#### A) Donut Chart - Top-Level Overview

**Location:** Home screen, top section  
**Purpose:** Quick glance at spending distribution

```
┌──────────────────────────────┐
│  Spending Overview           │
│  May 2026: €2,340            │
│                              │
│      ╱───────╲              │
│    ╱           ╲            │
│   │   €2,340    │           │
│   │  this month │           │
│    ╲           ╱            │
│      ╲───────╱              │
│                              │
│  🍔 Food 35% (€820)         │
│  🏠 Housing 30% (€700)      │
│  🚗 Transport 15% (€350)    │
│  💳 Shopping 12% (€280)     │
│  🎉 Lifestyle 8% (€190)     │
│                              │
│  [Tap for details →]        │
└──────────────────────────────┘
```

**Interaction:**
- Tap slice → Drill down to Level 2 categories
- Tap again → Show Level 3 + transactions
- Pinch to change timeframe (month/quarter/year)

**Technical:**
- Library: MPAndroidChart or Vico (modern, Compose-friendly)
- Data: Aggregated from Room with `SUM(amount) GROUP BY categoryId`
- Performance: Pre-calculate in background, cache results

#### B) Trend Chart - Timeline View

**Location:** Home screen, middle section (scrollable)  
**Purpose:** Show spending trends over time

```
┌──────────────────────────────┐
│  Trends (Last 6 Months)      │
│                              │
│  €1000 │        ╱──╲        │
│        │       ╱    ╲       │
│   €800 │──────╱      ╲──    │
│        │                 ╲  │
│   €600 │                  ╲ │
│        │                   ╲│
│   €400 │──────────────────── │
│        Jan Feb Mar Apr May Jun│
│                              │
│  Select categories:          │
│  ☑️ Food  ☑️ Transport        │
│  ☐ Housing ☐ Shopping        │
│                              │
│  [Show Forecast →]           │
└──────────────────────────────┘
```

**Features:**
- Multi-line chart (up to 5 categories selected)
- Toggle between: Monthly / Weekly / Daily view
- Smooth animations when switching categories
- Highlight unusual spikes (e.g., "Transport +60% this month")

**Technical:**
- Query: `SELECT categoryId, SUM(amount), MONTH(date) FROM transactions WHERE date > ? GROUP BY categoryId, MONTH(date)`
- Update interval: Real-time (observe Flow from Room)

#### C) Forecast Chart - Predictive Insights

**Location:** Expandable from Trend Chart  
**Purpose:** Help users plan future spending

```
┌──────────────────────────────┐
│  Forecast (Next 3 Months)    │
│                              │
│  Based on last 6 months avg: │
│                              │
│  🍔 Food                     │
│  Current: €820/mo            │
│  Forecast: €850/mo (+3.7%)   │
│  ━━━━━━━━━━━━━━━━━━━ 85%    │
│  Next 3mo: €2,550            │
│                              │
│  🚗 Transport                │
│  Current: €350/mo            │
│  Forecast: €380/mo (+8.6%)   │
│  ━━━━━━━━━━━━━━━━━━━ 38%    │
│  Next 3mo: €1,140            │
│                              │
│  💡 Insight: Transport costs │
│  increasing. Consider bike?  │
│                              │
│  [Settings: Forecast Period] │
└──────────────────────────────┘
```

**Algorithms:**
1. **Simple Moving Average (MVP):**
   ```kotlin
   fun forecastNextMonths(categoryId: String, months: Int = 3): BigDecimal {
       val last6Months = getMonthlySpending(categoryId, months = 6)
       val average = last6Months.average()
       val trend = calculateTrend(last6Months) // Linear regression
       return average * months + trend
   }
   ```

2. **Advanced (Phase 2):**
   - Seasonal adjustments (e.g., higher food costs in December)
   - Weighted average (recent months weighted higher)
   - Anomaly detection (exclude one-time expenses)

**Settings:**
- Forecast period: 1-12 months (default: 3)
- Include/exclude categories from forecast
- Set budget limits → Show "on track" or "over budget" warnings

---

### 3. Transaction List Filters (HIGH PRIORITY)

**Location:** Transaction screen, top bar  
**UX Pattern:** Material Design chips + bottom sheet

```
┌──────────────────────────────┐
│  Transactions                │
│  ╭──────────────────────────╮│
│  │  🔍 Search...            ││
│  ╰──────────────────────────╯│
│                              │
│  [All Categories ▾] [May ▾] │
│  [Amount: Any ▾]             │
│                              │
│  ┌────────────────────────┐ │
│  │ 🍔 REWE Supermarket    │ │
│  │ Food > Groceries       │ │
│  │ May 11, 2026   -€45.32 │ │
│  └────────────────────────┘ │
│                              │
│  ┌────────────────────────┐ │
│  │ ⛽ Shell Tankstelle    │ │
│  │ Transport > Car > Fuel │ │
│  │ May 10, 2026   -€62.00 │ │
│  └────────────────────────┘ │
│                              │
│  [Load 50 more ↓]           │
└──────────────────────────────┘
```

**Filter Options:**

1. **Category Filter (Hierarchical):**
   - Level 1: "Show all Food transactions"
   - Level 2: "Show only Groceries"
   - Level 3: "Show only Aldi"
   - Multi-select: "Food OR Transport"

2. **Date Range:**
   - Quick options: This month / Last month / Last 3 months / This year
   - Custom: Date picker (from/to)
   - Smart: "Last 30 days" (rolling window)

3. **Amount Range:**
   - Presets: < €10 / €10-50 / €50-100 / €100-500 / > €500
   - Custom: Slider with min/max

4. **Text Search:**
   - Search in: Merchant name, usage field, notes
   - Fuzzy matching: "rewe" matches "REWE SINGEN"

5. **Account Filter:**
   - If multiple bank accounts connected
   - "Show only BBBank" / "Show all accounts"

6. **Type Filter:**
   - Income / Expenses / Transfers
   - Categorized / Uncategorized

**Technical Implementation:**
```kotlin
data class TransactionFilter(
    val categoryIds: List<String> = emptyList(),
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val amountMin: BigDecimal? = null,
    val amountMax: BigDecimal? = null,
    val searchText: String? = null,
    val accountIds: List<String> = emptyList(),
    val types: List<TransactionType> = emptyList(),
    val onlyUncategorized: Boolean = false
)

// Room query
@Query("""
    SELECT * FROM transactions 
    WHERE (:categoryIds IS NULL OR categoryId IN (:categoryIds))
      AND (:dateFrom IS NULL OR date >= :dateFrom)
      AND (:dateTo IS NULL OR date <= :dateTo)
      AND (:amountMin IS NULL OR ABS(amount) >= :amountMin)
      AND (:amountMax IS NULL OR ABS(amount) <= :amountMax)
      AND (:searchText IS NULL OR usage LIKE '%' || :searchText || '%' OR otherParty LIKE '%' || :searchText || '%')
      AND (:onlyUncategorized = 0 OR categoryId IS NULL)
    ORDER BY date DESC
    LIMIT :limit OFFSET :offset
""")
fun getFilteredTransactions(
    categoryIds: List<String>?,
    dateFrom: String?,
    dateTo: String?,
    amountMin: String?,
    amountMax: String?,
    searchText: String?,
    onlyUncategorized: Boolean,
    limit: Int,
    offset: Int
): Flow<List<Transaction>>
```

**UX Details:**
- Filter state persists across app restarts (save to SharedPreferences)
- Active filters shown as chips with "X" to remove
- "Clear all filters" button when any filter active
- Filter count badge: "Transactions (234)" → "Transactions (12 filtered)"

---

### 4. Incremental Transaction Sync (HIGH PRIORITY)

**Goal:** Load historical transactions without overwhelming API or causing duplicates

**UX Flow:**
```
┌──────────────────────────────┐
│  Transactions                │
│                              │
│  Showing 50 of 200 available│
│                              │
│  [Most Recent Transactions]  │
│  May 11, 2026 ...            │
│  May 10, 2026 ...            │
│  ...                         │
│  Apr 15, 2026 ...            │
│                              │
│  ┌────────────────────────┐ │
│  │  Load 50 More          │ │
│  │  (150 older available) │ │
│  │  📅 Back to Mar 12     │ │
│  └────────────────────────┘ │
│                              │
│  ┌────────────────────────┐ │
│  │  Load All Remaining    │ │
│  │  (⚠️ May take 2-3 min) │ │
│  └────────────────────────┘ │
└──────────────────────────────┘
```

**Backend Strategy:**

```kotlin
data class SyncMetadata(
    val accountId: String,
    val lastSyncDate: Instant,
    val oldestTransactionDate: LocalDate, // We have data back to this date
    val apiOldestAvailable: LocalDate?, // Bank says data goes back to this date
    val totalSynced: Int,
    val totalAvailable: Int? // If bank provides this info
)

class TransactionSyncService {
    
    suspend fun syncNewest(account: Account, limit: Int = 50): SyncResult {
        // Standard sync: Get newest transactions
        val lastSync = syncMetadataDao.getByAccount(account.id)
        
        val transactions = fintsService.fetchTransactions(
            account = account,
            fromDate = lastSync?.oldestTransactionDate ?: LocalDate.now().minusMonths(3),
            toDate = null, // "until today"
            maxResults = limit
        )
        
        val newTransactions = deduplicateAndInsert(transactions)
        
        updateSyncMetadata(account.id, transactions)
        
        return SyncResult(
            newCount = newTransactions.size,
            duplicateCount = transactions.size - newTransactions.size,
            oldestAvailable = checkIfMoreAvailable(account)
        )
    }
    
    suspend fun syncOlder(account: Account, limit: Int = 50): SyncResult {
        // Incremental backfill: Load older transactions
        val metadata = syncMetadataDao.getByAccount(account.id)
            ?: error("No sync metadata - run syncNewest first")
        
        val toDate = metadata.oldestTransactionDate.minusDays(1)
        val fromDate = toDate.minusMonths(3) // Go back 3 months at a time
        
        val transactions = fintsService.fetchTransactions(
            account = account,
            fromDate = fromDate,
            toDate = toDate,
            maxResults = limit
        )
        
        if (transactions.isEmpty()) {
            // We've reached the end
            metadata.apiOldestAvailable = fromDate
            syncMetadataDao.update(metadata)
            return SyncResult(message = "No older transactions available")
        }
        
        val newTransactions = deduplicateAndInsert(transactions)
        
        // Update metadata: We now have data back to this date
        metadata.oldestTransactionDate = transactions.minOf { it.date }
        syncMetadataDao.update(metadata)
        
        return SyncResult(
            newCount = newTransactions.size,
            oldestDate = metadata.oldestTransactionDate,
            moreAvailable = transactions.size == limit // Probably more available
        )
    }
    
    private suspend fun deduplicateAndInsert(transactions: List<Transaction>): List<Transaction> {
        val existingIds = transactionDao.getIdsByHashList(
            transactions.map { it.generateId() }
        ).toSet()
        
        val newTransactions = transactions.filter { 
            it.generateId() !in existingIds 
        }
        
        if (newTransactions.isNotEmpty()) {
            transactionDao.insertAll(newTransactions)
        }
        
        return newTransactions
    }
    
    private suspend fun checkIfMoreAvailable(account: Account): Boolean {
        // Option 1: Ask FinTS API if more transactions available
        // Option 2: If we got exactly 'limit' results, assume more available
        // Option 3: Check metadata.apiOldestAvailable
        val metadata = syncMetadataDao.getByAccount(account.id)
        return metadata?.apiOldestAvailable?.let { 
            metadata.oldestTransactionDate > it 
        } ?: true // Assume more available if we don't know
    }
}
```

**API Call Optimization:**

1. **Batch Size:** 50 transactions per call (configurable)
2. **Rate Limiting:** Max 1 call per 5 seconds to avoid bank blocking
3. **Date Range Strategy:**
   - Initial sync: Last 90 days
   - Incremental: Go back 90 days per batch
   - Max history: 2 years (or whatever bank allows)
4. **Caching:** Store raw response to retry parsing without re-fetching
5. **Background Sync:**
   - Use WorkManager for periodic sync (daily at 6am)
   - Only sync if on WiFi + battery > 20%

**Duplicate Prevention Strategy:**

```kotlin
// Transaction ID generation (deterministic hash)
fun Transaction.generateId(): String {
    val key = buildString {
        append(accountNumber)
        append("|")
        append(date.toString())
        append("|")
        append(amount.toPlainString())
        append("|")
        append(otherParty.trim().lowercase())
        append("|")
        append(usage.trim().lowercase().take(50)) // First 50 chars
    }
    return sha256(key)
}

// Room entity
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["id"], unique = true), // Primary key
        Index(value = ["externalId"], unique = false), // Bank's ID (not always unique across accounts)
        Index(value = ["accountId", "date"]), // Fast date range queries
        Index(value = ["categoryId"]) // Fast category filtering
    ]
)
data class Transaction(
    @PrimaryKey val id: String, // Our generated hash ID
    val externalId: String? = null, // Bank's transaction ID (if available)
    // ... other fields
)

// Insert with conflict resolution
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertAll(transactions: List<Transaction>)

// If we need to update existing (e.g., user changed category)
@Transaction
suspend fun upsertTransaction(transaction: Transaction) {
    val existing = getById(transaction.id)
    if (existing != null) {
        // Preserve user modifications (category, notes)
        val merged = transaction.copy(
            categoryId = existing.categoryId ?: transaction.categoryId,
            notes = existing.notes ?: transaction.notes
        )
        update(merged)
    } else {
        insert(transaction)
    }
}
```

**User Feedback:**
```kotlin
// Show progress during sync
sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(
        val current: Int,
        val total: Int?,
        val message: String
    ) : SyncState()
    data class Success(
        val newTransactions: Int,
        val duplicates: Int,
        val message: String
    ) : SyncState()
    data class Error(val message: String) : SyncState()
}

// UI observes this
val syncState = MutableStateFlow<SyncState>(SyncState.Idle)

// Usage
syncState.value = SyncState.Syncing(
    current = 50,
    total = 200,
    message = "Loading older transactions..."
)
```

---

### 5. Additional UX Enhancements

#### A) Quick Actions (Swipe Gestures)

```
┌────────────────────────────┐
│ 🍔 REWE Supermarket        │  ← Swipe left: Categorize
│ Food > Groceries           │  → Swipe right: Add note
│ May 11, 2026   -€45.32     │
└────────────────────────────┘
```

**Actions:**
- Swipe left: Quick categorize (show category picker)
- Swipe right: Add note / Flag for review
- Long press: Multi-select mode (bulk actions)

#### B) Smart Notifications

```
📱 "You've spent €820 on Food this month"
   "That's 15% more than last month. Tap to see details."

📱 "New transaction: -€62.00 at Shell"
   "Categorize as Transport > Car > Fuel?"
```

**Types:**
- Daily summary (optional, configurable time)
- Budget warnings ("You're 80% of your Food budget")
- Unusual activity ("€500 transaction detected")
- Sync reminders ("Last sync: 3 days ago")

#### C) Budget Goals

```
┌──────────────────────────────┐
│  Budget Goals                │
│                              │
│  🍔 Food: €800/month         │
│  ━━━━━━━━━━━━━━━━━━━ 82%    │
│  €656 spent, €144 remaining  │
│  ✅ On track                  │
│                              │
│  🚗 Transport: €300/month    │
│  ━━━━━━━━━━━━━━━━━━━ 117%   │
│  €350 spent, -€50 over!      │
│  ⚠️ Over budget               │
│                              │
│  [+ Add Budget Goal]         │
└──────────────────────────────┘
```

**Features:**
- Set monthly budgets per category
- Visual progress bars (green/yellow/red)
- Notifications when approaching limit (80%, 100%)
- Suggested budgets based on past spending

#### D) Recurring Transactions Detection

```
🔍 "We noticed these transactions repeat monthly:"

┌────────────────────────────┐
│ 🏠 Rent: €700              │
│ Every 1st of the month     │
│ [Mark as Recurring]        │
└────────────────────────────┘

┌────────────────────────────┐
│ 📱 Vodafone: €35           │
│ Every 15th of the month    │
│ [Mark as Recurring]        │
└────────────────────────────┘
```

**Benefits:**
- Better forecasting (fixed costs vs variable)
- Alert if expected transaction missing
- Budget planning: "Fixed: €1,200/mo, Variable: €1,140/mo"

#### E) Export & Sharing

**Export Options:**
- CSV (for Excel/Google Sheets)
- PDF report (monthly summary)
- Share image (screenshot of charts for Instagram/Twitter)

**Share Examples:**
```
"My May 2026 spending breakdown 📊
🍔 Food: €820 (35%)
🏠 Housing: €700 (30%)
🚗 Transport: €350 (15%)
Total: €2,340
#budgeting #mybudgets"
```

---

## 🎨 UI/UX Design Principles

### Mobile-First Interactions

1. **Thumb-Friendly:**
   - Primary actions in bottom 60% of screen
   - FAB for main action (e.g., "Add manual transaction")
   - Bottom navigation bar (Home / Transactions / Charts / Settings)

2. **One-Handed Operation:**
   - Swipe gestures for quick actions
   - Bottom sheets instead of dialogs
   - Avoid top-left hamburger menu (use bottom nav)

3. **Fast Loading:**
   - Skeleton screens while loading
   - Optimistic UI updates (show change immediately, sync in background)
   - Pagination (load 50 at a time, not all 1000 transactions)

4. **Offline-First:**
   - All data in local database
   - Sync in background
   - Show "last synced" timestamp
   - Queue actions when offline (e.g., categorization)

### Visual Hierarchy

```
┌──────────────────────────────┐
│  ← MyBudgets         🔔 ⚙️   │  ← Top bar (minimal)
├──────────────────────────────┤
│                              │
│  [PRIMARY CONTENT]           │  ← Main scrollable area
│  - Large, easy to read       │
│  - High contrast             │
│  - Clear actions             │
│                              │
│                              │
│                              │
│                              │
│                              │
│                              │
├──────────────────────────────┤
│  🏠  📊  💳  ⚙️              │  ← Bottom nav (always visible)
└──────────────────────────────┘
```

**Color Scheme:**
- **Green:** Positive (income, under budget)
- **Red:** Negative (expenses, over budget)
- **Blue:** Neutral (information, charts)
- **Yellow/Orange:** Warning (approaching limit)

**Typography:**
- Large numbers: 32sp (main amounts)
- Medium text: 16sp (labels, descriptions)
- Small text: 12sp (metadata, timestamps)
- High contrast: Dark text on light background (WCAG AA compliant)

### Animation & Feedback

- **Micro-interactions:** Button press ripples, card elevation on touch
- **Smooth transitions:** Fade/slide between screens (300ms)
- **Loading states:** Shimmer effect, not spinners
- **Success feedback:** Quick checkmark animation + haptic feedback
- **Error states:** Shake animation + red accent

---

## 📋 Implementation Phases

### Phase 1: MVP (Weeks 1-4)
**Goal:** Basic categorization + charts

- [x] Hierarchical categories (database + default data)
- [ ] Manual transaction categorization
- [ ] Basic donut chart (top-level categories)
- [ ] Simple transaction list with search
- [ ] Incremental sync (load more button)

**Success Criteria:**
- User can categorize transactions
- User can see spending breakdown in donut chart
- User can load historical transactions in batches

### Phase 2: Analytics (Weeks 5-8)
**Goal:** Trend analysis + forecasting

- [ ] Trend chart (spending over time)
- [ ] Category selector for trend chart
- [ ] Basic forecast (3-month moving average)
- [ ] Transaction filters (category, date, amount)
- [ ] Budget goals (set + track)

**Success Criteria:**
- User can see if spending is increasing/decreasing
- User gets basic forecast for next 3 months
- User can filter transactions effectively

### Phase 3: Intelligence (Weeks 9-12)
**Goal:** Smart features + automation

- [ ] Auto-categorization (rule-based)
- [ ] Recurring transaction detection
- [ ] Smart notifications (budget warnings)
- [ ] Improved forecast (seasonal adjustments)
- [ ] Export/sharing features

**Success Criteria:**
- 80%+ of transactions auto-categorized correctly
- User receives useful budget insights
- User can share budget summaries

### Phase 4: Polish (Weeks 13-16)
**Goal:** UX refinement + performance

- [ ] Swipe gestures for quick actions
- [ ] Animations + micro-interactions
- [ ] Performance optimization (large datasets)
- [ ] Accessibility improvements (TalkBack, large fonts)
- [ ] Multi-account support

**Success Criteria:**
- App feels fast and responsive
- User can complete common tasks in < 5 taps
- App handles 10,000+ transactions smoothly

---

## 🔧 Technical Debt & Considerations

### Database Schema Evolution

```kotlin
// Version 1 → 2: Add categories
@Database(
    entities = [Transaction::class, Category::class],
    version = 2
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
}

// Migration strategy
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE categories (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                emoji TEXT NOT NULL,
                level INTEGER NOT NULL,
                parentId TEXT,
                color TEXT NOT NULL,
                sortOrder INTEGER NOT NULL DEFAULT 0,
                isDefault INTEGER NOT NULL DEFAULT 0,
                isActive INTEGER NOT NULL DEFAULT 1
            )
        """)
        database.execSQL("""
            ALTER TABLE transactions 
            ADD COLUMN categoryId TEXT REFERENCES categories(id)
        """)
        // Insert default categories
        insertDefaultCategories(database)
    }
}
```

### Performance Considerations

1. **Large Datasets:**
   - Use `PagingSource` for transaction list (Paging 3 library)
   - Index critical columns (date, categoryId, amount)
   - Aggregate queries in background (coroutines)

2. **Chart Rendering:**
   - Pre-calculate aggregations (daily/monthly)
   - Cache chart data (invalidate on new transactions)
   - Use hardware acceleration for animations

3. **Sync Performance:**
   - Batch inserts (bulk insert 50 transactions at once)
   - Use transactions for atomicity
   - Background processing (WorkManager)

### Testing Strategy

1. **Unit Tests:**
   - Transaction ID generation (hash collision testing)
   - Deduplication logic
   - Forecast algorithms
   - Category hierarchy traversal

2. **Integration Tests:**
   - Room database migrations
   - FinTS sync flow
   - Filter queries

3. **UI Tests:**
   - Navigation flow
   - Categorization workflow
   - Chart interactions

---

## 🚀 Future Ideas (Post-MVP)

### Advanced Features

1. **Multi-Currency Support:**
   - Handle transactions in different currencies
   - Convert to base currency for aggregation
   - Show exchange rates

2. **Shared Budgets:**
   - Multiple users on same budget
   - Real-time sync via Firebase
   - Split transactions (e.g., "Julia paid, I owe 50%")

3. **AI-Powered Insights:**
   - "You spend 20% more on weekends"
   - "Your coffee habit costs €120/month"
   - "If you cut dining out by 25%, save €500/year"

4. **Investment Tracking:**
   - Connect to brokerage accounts
   - Track portfolio value
   - Net worth over time

5. **Bill Reminders:**
   - "Vodafone bill due in 3 days"
   - "Netflix subscription renews tomorrow"
   - Integration with calendar

6. **Receipt Scanning:**
   - OCR to extract amount, merchant, items
   - Attach receipt image to transaction
   - Search by receipt content

### Integrations

- **Banking:** Support more banks (Sparkasse, Volksbank, N26, etc.)
- **Payment Apps:** PayPal, Venmo, Apple Pay
- **Accounting:** Export to DATEV, Lexoffice
- **Automation:** IFTTT, Zapier webhooks

---

## 📝 Open Questions

1. **Category System:**
   - Should we allow more than 3 levels? (e.g., Food > Groceries > Supermarket > Aldi)
   - How to handle income categories? (Same hierarchy or separate?)
   - Default categories: Too many options overwhelm users?

2. **Sync Strategy:**
   - Should we auto-sync in background, or only when user requests?
   - How to handle bank connection errors? (Retry logic, user notification)
   - What if bank changes transaction IDs? (Re-sync all?)

3. **Forecast Accuracy:**
   - How to handle one-time expenses? (Detect and exclude from forecast)
   - Should we ask user to mark recurring vs one-time?
   - Confidence intervals: Show "€800 ± €50" or just "€800"?

4. **Data Privacy:**
   - Store data only locally, or offer cloud backup?
   - Encrypt database? (SQLCipher)
   - GDPR compliance: Export/delete all data

5. **Monetization (if applicable):**
   - Free with ads?
   - Freemium (basic free, advanced paid)?
   - One-time purchase vs subscription?
   - Enterprise version for businesses?

---

## 📚 References & Inspiration

### Similar Apps (Research)
- **YNAB (You Need A Budget):** Gold standard for budget categorization
- **Mint:** Automatic categorization, good charts
- **Splitwise:** Excellent UX for shared expenses
- **Emma:** Beautiful design, smart notifications
- **Wallet by BudgetBakers:** Great multi-currency support

### Design Resources
- **Material Design 3:** https://m3.material.io/
- **Human Interface Guidelines:** https://developer.apple.com/design/
- **Chart Libraries:**
  - MPAndroidChart: https://github.com/PhilJay/MPAndroidChart
  - Vico: https://github.com/patrykandpatrick/vico (Compose)

### Technical Resources
- **FinTS Spec:** https://www.hbci-zka.de/
- **Room Database:** https://developer.android.com/training/data-storage/room
- **Paging 3:** https://developer.android.com/topic/libraries/architecture/paging/v3-overview
- **Jetpack Compose:** https://developer.android.com/jetpack/compose

---

**Next Step:** Pick Phase 1 features and create detailed implementation tickets! 🚀
