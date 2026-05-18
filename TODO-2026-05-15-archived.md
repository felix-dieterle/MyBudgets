# MyBudgets - TODO

## 🔴 CRITICAL: Bank-Sync Fix (v1.0.44)

**Status:** 2026-05-15 - Alle Jobs schlagen fehl (Passport expired + Netzwerk?)

**Root Cause:**
1. Passport war 7 Tage alt (last update: 2026-05-08, heute: 2026-05-15) → expired
2. Passport GELÖSCHT zur Neu-Initialisierung
3. Java-Sync Test schlägt AUCH fehl (qt.cmd):
   - HBCI 2.2 ("220"): Signing Error "secfunc 999 ungültig"
   - HBCI 3.0 ("300"): FileNotFoundException (Netzwerk?)
   - JVM: "Auslagerungsdatei zu klein" (Java-Prozesse gekillt)

**Proven Working Config (2026-05-12, logs-app16.txt):**
- App Version: v1.0.42
- HBCI-Version: "300" (FinTS 3.0)
- Job: **KUmsAllCamt** (CAMT)
- Parser: **CustomCamtParser**
- Result: ✅ 150 Transaktionen erfolgreich

**Current Config (v1.0.43 - BROKEN):**
- KUmsAllCamt ENTFERNT (basierend auf veralteter AGENTS.md Info)
- Nur Fallback-Jobs: KUmsZeitSEPA, KUmsAll, KUmsNew
- Alle Jobs schlagen fehl

**Nächste Schritte (Hybrid-Ansatz):**

### Phase 1: Netzwerk/Bank-Problem abwarten
- [ ] 1-2 Tage warten, dann `qt.cmd` erneut testen
- [ ] Falls erfolgreich → Weiter mit Phase 2

### Phase 2: Python-Script validieren
- [ ] `new_bbbank-sync-debug.py` testen
- [ ] Zeigt ob Problem bei hbci4java oder Bank liegt
- [ ] In TEST-SCRIPTS-INVENTORY.md dokumentieren

### Phase 3: Passport-Only Test
- [ ] Java-Sync anpassen: Nur Session aufbauen, keine Jobs
- [ ] Prüfen ob Passport erstellt wird
- [ ] Separat testen ob Verbindung steht

### Phase 4: App v1.0.44 vorbereiten
- [ ] **KUmsAllCamt RE-AKTIVIEREN** (funktionierte 2026-05-12)
- [ ] CustomCamtParser behalten
- [ ] Vorsichtig deployen & testen
- [ ] Jeden Schritt in BBBank-Sync-E2E-Test.md dokumentieren

**WICHTIG:** Behutsam vorgehen, nicht raten, jeden Fehlschlag protokollieren.

---

## Bugs (Low Priority)

### Chart Long-Press Custom Select nicht aktiv
- **Problem:** Long-Press auf Kategorie im Chart löst Drill-Down aus statt Custom-Select
- **Expected:** Long-Press sollte Kategorie für Custom-Auswahl togglen
- **Current:** Long-Press triggert Drill-Down (wie normaler Tap)
- **Priority:** Low
- **Location:** `DashboardFragment.kt:330-347` (GestureDetector onLongPress)

### Kategorie-Hierarchie per Drag & Drop ändern
- **Feature:** Unterkategorien per Drag & Drop in andere Überkategorie verschieben
- **UX:** Long-Press + Drag in CategoriesFragment RecyclerView
- **Implementation:** ItemTouchHelper.Callback für Drag-Events
- **Logic:** `onMove()` prüft ob Ziel = Kategorie, dann `updateParent(draggedId, targetId)`
- **Priority:** Medium-Low
- **Location:** `CategoriesFragment.kt` + `CategoryTreeAdapter.kt`

---

## Features

### High Priority

#### 1. Virtuelle Konten - Core Implementation
- [ ] VirtualAccount Entity mit Feldern:
  - `id`, `name`, `icon`, `color`, `targetBalance`, `isActive`
  - `createdAt`, `updatedAt`
- [ ] VirtualAccountDao mit Queries
- [ ] Repository & ViewModel
- [ ] UI: Liste virtueller Konten (Fragment + Adapter)
- [ ] UI: Virtuelle Konten erstellen/bearbeiten/löschen
- [ ] UI: Dashboard-Integration (Balance-Übersicht)

#### 2. Automatische Zuordnung zu Virtuellen Konten
- [ ] VirtualAccountRule Entity:
  - `id`, `virtualAccountId` (FK), `ruleType` (IBAN/Pattern/Category)
  - `matchValue` (z.B. "DE123456", ".*Miete.*", categoryId)
  - `priority`, `isActive`
- [ ] VirtualAccountRuleDao
- [ ] Matching-Engine: Transaction → VirtualAccount via Rules
- [ ] UI: Regel-Verwaltung (Liste + Add/Edit Dialog)
- [ ] Background-Job: Alle Transaktionen neu zuordnen bei Regel-Änderung

#### 3. Virtuelle Daueraufträge
- [ ] RecurringTransaction Entity:
  - `id`, `virtualAccountId` (FK optional)
  - `name`, `amount`, `interval` (monthly/weekly/yearly)
  - `startDate`, `endDate` (optional), `nextDueDate`
  - `isActive`, `categoryId` (FK optional)
- [ ] RecurringTransactionDao
- [ ] Scheduler: Prüft fällige Daueraufträge und erstellt Transaktionen
- [ ] UI: Daueraufträge-Liste + Add/Edit
- [ ] Notification: Erinnerung vor Fälligkeit

#### 4. Dashboard-Erweiterung für Virtuelle Konten
- [ ] Virtual Account Balance Cards (expandable)
- [ ] Upcoming Recurring Transactions (nächste 30 Tage)
- [ ] Budgetwarnungen (virtuelles Konto unter Target Balance)

---

## Medium Priority

### 5. Transaction Search/Filter
- [ ] Search-View in TransactionsFragment
- [ ] Filter nach: Date Range, Amount Range, Category, Account, Description
- [ ] DAO-Query mit dynamischen WHERE-Clauses
- [ ] Persist Filter-State (ViewModel SavedStateHandle)

### 6. Export/Import (Backup)
- [ ] Export DB to JSON (all tables)
- [ ] Import DB from JSON (with conflict resolution)
- [ ] UI: Settings → Export/Import
- [ ] File picker integration

### 7. Category Budget Goals
- [ ] Add `budgetAmount`, `budgetPeriod` (monthly/yearly) to Category
- [ ] UI: Edit Category → Budget-Felder
- [ ] Dashboard: Budget vs. Actual Chart per Category
- [ ] Warning wenn Budget überschritten

---

## Low Priority

### 8. Recurring Transaction Detection (Pattern Learning)
- [ ] ML/Rule-based Detection: Transaktionen mit gleichem Amount + ~gleicher Periode
- [ ] UI: "Dauerauftrag erkannt?" Dialog mit "Speichern" Option
- [ ] Background-Job: Periodisch nach Patterns scannen

### 9. Statistics-Erweiterung
- [ ] Year-over-Year Comparison
- [ ] Spending by Weekday/Time-of-Day
- [ ] Export Charts as Image

### 10. UI/UX Improvements
- [ ] Dark Mode
- [ ] Swipe-to-Delete in Lists
- [ ] Pull-to-Refresh
- [ ] Empty State Illustrations

---

## Technical Debt

- [ ] Add Index on `CategoryPattern.categoryId` (DB warning)
- [ ] Remove deprecated hbci4java API calls (FintsService.kt)
- [ ] Add @OptIn annotations for ExperimentalCoroutinesApi (ViewModels)
- [ ] Rename unused lambda parameters to `_`

---

**Last Updated:** 2026-05-15 (Critical Bank-Sync section added)
