# MyBudgets - UI/UX Structure

**Goal:** Budget control through minimal friction, maximum insight

---

## Navigation Flow

```
┌─────────────────────────────────────────────────────────────┐
│                      APP STRUCTURE                          │
└─────────────────────────────────────────────────────────────┘

                    [🏠 Dashboard]
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
   [💳 Accounts]    [📊 Transactions]   [⚙️ Settings]
        │                  │
        │                  ├─→ [Detail] → [Categorize]
        │                  │
        └─→ [Detail] ───┐  └─→ [Filters]
                        │
                        └─→ [🏦 Bank Sync]
```

---

## Screen Hierarchy

### 1. Dashboard (Home)
**Goal:** Instant overview - "Where am I financially?"

```
┌────────────────────────────────┐
│ 💰 Total Balance: €2,340.50   │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━    │  ← Progress bar: Budget status
│                                │
│ 📊 This Month                  │
│  🍔 Food      35%  €820        │
│  🏠 Housing   30%  €700        │
│  🚗 Transport 15%  €350        │
│  [See Donut Chart →]           │
│                                │
│ 📋 Recent Transactions         │
│  🛒 REWE Supermarket  -€45.32  │
│     Food > Groceries           │
│  ⛽ Shell Tankstelle  -€62.00  │
│     Transport > Fuel           │
│  [See All →]                   │
└────────────────────────────────┘
```

**Taps:**
- Chart → Detailed breakdown
- Transaction → Detail/Categorize
- "See All" → Transactions list

---

### 2. Transactions List
**Goal:** Find, filter, categorize quickly

```
┌────────────────────────────────┐
│ Transactions         [+ Add]   │
├────────────────────────────────┤
│ 🔍 Search...                   │
│ [Food ▾] [This Month ▾]        │ ← Filter chips
├────────────────────────────────┤
│ ┌────────────────────────────┐ │
│ │ 🛒 REWE Supermarket        │ │
│ │ Food > Groceries           │ │ ← Category visible!
│ │ May 11, 2026      -€45.32  │ │
│ └────────────────────────────┘ │
│                                │
│ ┌────────────────────────────┐ │
│ │ ⛽ Shell Tankstelle        │ │
│ │ Transport > Car > Fuel     │ │
│ │ May 10, 2026      -€62.00  │ │
│ └────────────────────────────┘ │
│                                │
│ [Load 50 More ↓]               │ ← Incremental loading
└────────────────────────────────┘
```

**Interactions:**
- Tap transaction → Detail screen
- Swipe left → Quick categorize (future)
- Swipe right → Add note (future)
- Filter chips → Bottom sheet with options

---

### 3. Transaction Detail / Categorize
**Goal:** Assign category in <5 taps

```
┌────────────────────────────────┐
│ ← REWE Supermarket             │
│                                │
│ Amount:  -€45.32               │
│ Date:    May 11, 2026          │
│ Account: BBBank Girokonto      │
│                                │
│ Category:                      │
│ ┌────────────────────────────┐ │
│ │ 🍔 Food > Groceries        │ │ ← Tap to change
│ └────────────────────────────┘ │
│                                │
│ Notes:                         │
│ ┌────────────────────────────┐ │
│ │ Weekly grocery shopping... │ │
│ └────────────────────────────┘ │
│                                │
│ [Save]                         │
└────────────────────────────────┘
```

**Category Picker (Bottom Sheet):**

```
┌────────────────────────────────┐
│ Select Category                │
├────────────────────────────────┤
│ ▼ 🍔 Food                      │
│    🛒 Groceries            ←   │ ← Selected
│    🍽️ Restaurants              │
│    🚚 Delivery                 │
│                                │
│ ▼ 🏠 Housing                   │
│    🔑 Rent                     │
│    💡 Utilities                │
│                                │
│ ▶ 🚗 Transport                 │
│ ▶ 💳 Shopping                  │
│ ▶ 🎉 Lifestyle                 │
│                                │
│ [✓ Save Rule for "REWE"]       │ ← Auto-categorize future
└────────────────────────────────┘
```

**Flow:**
1. Tap category → Bottom sheet opens
2. Expand hierarchy (collapsible)
3. Tap to select
4. Optional: "Apply to all REWE transactions"
5. Done

---

### 4. Charts (Future - Milestone 3)
**Goal:** See trends, forecast spending

```
┌────────────────────────────────┐
│ Spending Trends                │
├────────────────────────────────┤
│ [Month ▾] [Last 6 Months]      │
│                                │
│  €1000 │        ╱──╲           │
│        │       ╱    ╲          │
│   €800 │──────╱      ╲──       │
│        │                 ╲     │
│   €600 │                  ╲    │
│        Jan Feb Mar Apr May Jun │
│                                │
│ Select:                        │
│ ☑️ Food  ☑️ Transport          │
│ ☐ Housing ☐ Shopping           │
│                                │
│ 💡 Forecast (Next 3 Months):   │
│  🍔 Food: €850/mo (+3.7%)      │
│  🚗 Transport: €380/mo (+8.6%) │
└────────────────────────────────┘
```

---

## User Journeys

### Journey 1: "Where did my money go this month?"
```
Dashboard → Tap "This Month" chart
         → See breakdown by category
         → Tap "Food" slice
         → See all Food transactions
         → Review/adjust categories
```
**Time:** ~15 seconds

---

### Journey 2: "Categorize new transactions"
```
Dashboard → "Recent Transactions"
         → Tap uncategorized transaction
         → Tap "Category" field
         → Select from hierarchy
         → Optional: "Apply to all from this merchant"
         → Save
```
**Time:** ~8 seconds per transaction

---

### Journey 3: "Sync bank & auto-categorize"
```
Accounts → Tap account
        → "Sync" button
        → 50 new transactions loaded
        → Auto-categorized: 42 (84%)
        → Manual review: 8 transactions
```
**Time:** ~2 minutes (mostly automatic)

---

### Journey 4: "Check if I'm overspending" (Future)
```
Dashboard → Budget Goals section
         → See: Food 82% (€656/€800) ✅ On track
         →      Transport 117% (€350/€300) ⚠️ Over
         → Tap Transport → See breakdown
         → Identify: Fuel increased by 15%
```
**Time:** ~10 seconds

---

## Design Principles

### 1. **Thumb-Friendly**
- Primary actions in bottom 60% of screen
- FAB for main action (+ Add Transaction)
- Bottom nav bar (Home / Transactions / Charts / Settings)

### 2. **Visual Hierarchy**
```
Large:   Amounts, Category Emojis
Medium:  Transaction descriptions, Category names
Small:   Dates, metadata
```

### 3. **Color Coding**
- 🟢 Green: Income, under budget
- 🔴 Red: Expenses, over budget
- 🔵 Blue: Transfers, neutral info
- 🟡 Yellow: Warnings (approaching limit)

### 4. **Offline-First**
- All data in local DB
- Sync in background (WorkManager)
- Show "Last synced: 2 hours ago"
- Works without internet

### 5. **Progressive Disclosure**
- Level 1: Category emoji only (🍔)
- Level 2: + Name (🍔 Food)
- Level 3: Full path (🍔 Food > Groceries > Supermarket)

---

## Information Architecture

```
Budget Control
    ↓
Categorization (Core Feature)
    ↓
Hierarchical Structure
    ↓
├─ Level 1: Main (Food, Housing, Transport)
├─ Level 2: Sub (Groceries, Rent, Car)
└─ Level 3: Detail (Supermarket, Fuel)
    ↓
Analytics
    ↓
├─ Donut Chart (Distribution)
├─ Trend Chart (Timeline)
└─ Forecast (Prediction)
    ↓
Insights
    ↓
├─ Budget Goals (On track? Over?)
├─ Recurring Detection (Fixed costs)
└─ Smart Alerts (Unusual activity)
```

---

## Current Status (Milestone 1 Progress)

✅ Database: Categories + indices
✅ UI: Transaction list shows emoji + category name
⏳ UI: Category picker (next)
⏳ Auto-categorization (pattern matching)
⏳ Charts & analytics (Milestone 3)

---

**Last Updated:** 2026-05-12
