# Work in Progress - 2026-05-21

## Status Update

**Letzter Stand:** 2026-05-21 22:30

### Abgeschlossene Features (heute)

#### 1. ✅ KUmsAllCamt Job-Priority Änderung (BBBank Optimierung)

**Problem:** Historische Syncs holten nur ~150 Transaktionen

**Lösung:** Standalone Java-Test (MultiJobTest.java) zeigte:
- **Ein einzelner `KUmsAllCamt` Job holt ~1000 Transaktionen** (fast 2 Jahre)
- **Multi-Job Dialog ist NICHT nötig** für BBBank
- BBBank ignoriert Startdatum und liefert automatisch die letzten ~1000 TX

**Implementierung:**
- `FintsService.buildJobAttempts()`: **Job-Reihenfolge geändert**
  - **Vorher:** `KUmsZeitSEPA` → `KUmsAllCamt` → `KUmsAll` → `KUmsNew`
  - **Jetzt:** `KUmsAllCamt` → `KUmsZeitSEPA` → `KUmsAll` → `KUmsNew`
- Minimale Änderung: Nur 2 Zeilen in Job-Liste vertauscht
- Fallback-Logik bleibt erhalten

**Test-Ergebnis (MultiJobTest v1.0.7):**
- ✅ 989 Transaktionen mit EINER TAN
- ✅ TAN-Methode 946 (SecureGo plus Direktfreigabe) funktioniert
- ✅ HBCI 3.0 mit KUmsAllCamt erfolgreich

**Dateien:**
- `FintsService.kt`: Zeile 530-546 (buildJobAttempts)
- `scripts/java-sync/MultiJobTest.java`: v1.0.7 (Standalone Test)
- `scripts/400-multi-job-test.cmd`: Test-Wrapper

**Commit:** (noch nicht committed)

**Nächster Schritt:** APK bauen und in App testen

---

## Legacy Content (vor 22:00 Uhr)

#### 2. ✅ Interval-Based Sync v4 mit Button-Fix & Balance-Update
- **Gap-Detection funktioniert:** `getNextHistoricalSyncDate()` returned korrekt nächste Lücke
- **Button-Problem behoben:** `updateHistoricalSyncButtonState()` wird jetzt initial aufgerufen (nicht nur nach Sync)
- **Balance-Update verbessert:**
  - Normal-Sync: Update wenn TX ≤7 Tage alt
  - Historischer Sync: Update wenn keine neueren TX in DB existieren
- **Commit:** a7e3def (recurrence-confidence), 60b811d (PIN-cache), 496350f (interval-sync)
- **APK:** v1.0.45-309 deployed auf NAS

#### 2. ✅ Recurrence Pattern Management (Datenmodell)
- **RecurrencePattern Entity:** name, keywords, targetIban, amountMin/Max, intervalDays
- **Transaction.recurrencePatternId:** FK zu RecurrencePattern
- **RecurrencePatternMatcher:** Einheitliche Match-Logik (keywords OR, IBAN exact, amount range)
- **Migration 13→14:** DB-Schema erweitert
- **Commit:** Heute (noch nicht committed)
- **Status:** Datenmodell & Logik fertig, UI fehlt noch

---

## Erkenntnisse: Multi-Job vs. Single-Job (BBBank)

### ✅ RESOLVED: Multi-Job Dialog ist NICHT nötig!

**Test-Ergebnis (2026-05-21 22:00):**
- **Ein einzelner `KUmsAllCamt` Job holt ~1000 Transaktionen** (989 TX im Test)
- **Zeitraum:** Fast 2 Jahre Daten (2024-07 bis 2026-05)
- **TAN-Aufwand:** Nur EINE SecureGo Plus Bestätigung

**Warum Multi-Job getestet wurde:**
- Hibiscus nutzt Multi-Job-Dialoge (mehrere Jobs in EINER Session mit EINER TAN)
- Hypothese: Mehrere date-windowed Jobs könnten mehr als 150 TX holen

**Warum Multi-Job NICHT funktionierte (Commit e2e10e2):**
- BBBank verlangt für **jeden Job eine separate TAN** (3 Jobs = 3 TANs)
- Nur der erste Job lieferte Daten, andere wurden abgebrochen (User bestätigte nicht alle TANs)

**Warum Multi-Job NICHT nötig ist:**
- BBBank's `KUmsAllCamt` ignoriert das Startdatum
- Liefert automatisch die letzten ~1000 Transaktionen
- Deutlich mehr als das alte 150-TX-Limit

**Lösung:** Job-Priority geändert - `KUmsAllCamt` erste Wahl statt zweite

---

### ~~Multi-Job-Dialog (BBBank Limitation)~~ [VERALTET]

**Frage heute:** "Warum braucht Hibiscus nicht für jeden Chunk TAN?"

**Antwort:** Hibiscus nutzt **Multi-Job-Dialoge** (mehrere Jobs in EINER Session):
```java
HBCIDialog dialog = handler.newDialog();
dialog.addJob(job1);  // Chunk 1
dialog.addJob(job2);  // Chunk 2
dialog.execute();     // EINE TAN für alle!
```

**Unser Problem:** Wir öffnen für jeden Chunk eine neue Session → neue TAN!

**Aber:** Multi-Job wurde bereits versucht und **fehlgeschlagen** (Commit e2e10e2):
- **Problem:** BBBank liefert nur für **ersten Job** Daten
- **Root Cause unklar:** Evtl. Bank-Limitation oder falscher `enddate`-Parameter
- **Dokumentiert in:** `WORK-IN-PROGRESS.md` Zeile 5-39 (alte Version)

**Status:** ⚠️ **UNRESOLVED** - Multi-Job funktioniert nicht bei BBBank

---

## Offene Aufgaben

### 1. ⏳ Recurrence Pattern UI (NEW)

**Anforderung:**
- Transaction Detail: Recurrence-Section mit Edit/Remove
- "Als wiederkehrend markieren" Button → Pattern-Dialog
- Pattern ändern → Auto-Scan + Vorschläge-Dialog

**Implementation Plan:**
1. **RecurrencePatternEditDialog:** Pattern erstellen/bearbeiten (Keywords, IBAN, Betrag-Range)
2. **RecurrenceMatchConfirmDialog:** Gefundene Matches mit Checkboxes
3. **Transaction Detail UI:** Recurrence-Section hinzufügen
4. **Auto-Scan nach Pattern-Änderung:** DB scannen, Matches vorschlagen

**Dateien betroffen:**
- `AddEditTransactionFragment.kt` (Recurrence-Section)
- `RecurrencePatternEditDialog.kt` (neu)
- `RecurrenceMatchConfirmDialog.kt` (neu)
- `TransactionViewModel.kt` (Pattern-CRUD)

**Status:** Datenmodell fertig, UI fehlt

---

### 2. ⚠️ Multi-KUmsAllCamt mit Date-Windows (BLOCKED)

**Problem:** 
- Früher: KUmsAllCamt (150 TX) + KUmsZeitSEPA (150 TX) = ~300 TX pro Sync
- Jetzt: HBCI 2.2 broken → nur KUmsAllCamt → 150 TX pro Sync
- User muss 20× "Ältere Buchungen importieren" klicken

**Lösung (geplant):**
- Multiple KUmsAllCamt-Jobs mit unterschiedlichen Date-Windows in EINEM Dialog
- 5 Jobs → 5×150 = bis zu 750 TX pro Sync

**Status:** ❌ **FAILED** - Bereits versucht (Commit e2e10e2)
- BBBank liefert nur für ersten Job Daten
- Grund unklar (Bank-Limitation? Parameter-Fehler?)

**Alternative Lösungen:**
1. **enddate Parameter testen:** Evtl. falsch genutzt?
2. **Andere Job-Typen mischen:** KUmsAllCamt + KUmsAll + KUmsNew parallel?
3. **Session-Reuse:** Statt neue Session pro Chunk?

**Dateien betroffen:**
- `AccountViewModel.kt` (continueSyncOlder)
- `FintsService.kt` (fetchAccountStatement, buildJobAttempts)

---

### 3. ⏸️ UX: ± Toggle vor Min/Max-Filter (PENDING)

**Anforderung:**
- Vor den Min/Max-Betrags-Feldern: Toggle-Switch für Minus/Plus
- Default: Minus (Ausgaben)

**Implementation:**
- `TransactionViewModel.kt`: `_amountSign: MutableStateFlow<AmountSign>`
- `fragment_transactions.xml`: ChipGroup/ToggleButton vor `til_amount_min`
- `TransactionsFragment.kt`: Binding + Filter-Logik

**Status:** PENDING

---

### 4. ⏸️ UX: Last-Sync-Date in Account-Overview (PENDING)

**Anforderung:**
- Kontoübersicht zeigt wann zuletzt gesynct wurde
- "Letzter Sync: vor 2 Tagen"

**Implementation:**
- `Account.lastSyncAt: Long?` (DB-Migration 14→15)
- `AccountViewModel.syncBankTransactions()` update lastSyncAt
- `AccountAdapter.kt` + `item_account.xml` Anzeige

**Status:** PENDING

---

## Debugging-Findings (heute)

### Button erscheint nicht nach historischem Sync

**Problem:** Button kommt nach erstem historischen Sync nicht (v1.0.45-308)

**Root Cause:** `updateHistoricalSyncButtonState()` wurde nur nach Sync aufgerufen, nicht initial beim Fragment-Start

**Fix:** `showAccount()` ruft jetzt initial `updateHistoricalSyncButtonState()` auf (Zeile 197-200)

**Deployed in:** v1.0.45-309

---

### TAN wird IMMER für CAMT-Jobs benötigt

**Problem:** BBBank fordert sofort TAN (kein PIN-only Versuch)

**Erkenntnis:** BBBank fordert bei `KUmsAllCamt` IMMER 2FA (PIN+TAN)
- Bank-Policy, nicht änderbar
- SecureGo Plus = Push-TAN (absichtlich nicht cachebar)

**Logging verbessert:** TAN-Method-Auswahl jetzt detailliert geloggt (═══-Boxen)

---

## Technische Details

### PIN-Cache (2026-05-20)
- **Sliding Window:** 2 Minuten, Timer reset bei jedem Zugriff
- **RAM-only:** Keine Disk-Persistierung
- **Auto-Invalidierung:** Bei falschem PIN sofort gelöscht
- **Manuell löschbar:** `invalidatePinCache()`

### Recurrence-Confidence-Formel (2026-05-20)
- **Exakte Beträge:** `conf = occ*0.4 + interval*0.4 + desc*0.2` (conf=0.8-1.0)
- **Ähnliche Beträge:** `conf = min(rawScore, 0.3)` (gedeckelt bei 0.3)
- **amountExactness:** Misst Abweichung innerhalb Gruppe (1.0=exakt, 0.0=5% Toleranz)

### Forward-Sync-Prinzip (BBBank KUmsAllCamt)
- **fromDate=null:** Bank liefert ÄLTESTE 150 TX
- **fromDate=X:** Bank liefert 150 TX AB X vorwärts
- **NIE rückwärts gehen!** (war Bug in v3)

---

## Nächste Session - Prioritäten

1. **HIGH:** Recurrence Pattern UI implementieren (Dialog + Transaction Detail)
2. **HIGH:** Button-Fix testen (v1.0.45-309 auf Device installieren)
3. **MEDIUM:** Multi-Job-Dialog nochmal analysieren (warum fehlgeschlagen?)
4. **LOW:** ± Toggle + Last-Sync-Date

---

## Code-Locations (Quick Reference)

```
Sync-Logic:
  AccountViewModel.kt:80-96      → continueSyncOlder()
  AccountViewModel.kt:136-340    → syncBankTransactions()
  FintsService.kt:230-440        → fetchAccountStatement()
  SyncIntervalRepository.kt:29-90 → getNextHistoricalSyncDate()

Recurrence-Pattern:
  RecurrencePattern.kt           → Entity (NEW)
  RecurrencePatternMatcher.kt    → Match-Logik (NEW)
  RecurrencePatternRepository.kt → CRUD (NEW)
  Transaction.kt:21              → recurrencePatternId (NEW)

Transaction-Filter:
  TransactionViewModel.kt:30-31  → _amountMin, _amountMax
  fragment_transactions.xml:167  → Amount filter layout

Account-Display:
  AccountAdapter.kt:18-30        → bind() method
  item_account.xml:20-32         → Account name/type/balance layout
  AccountDetailFragment.kt:197-200 → updateHistoricalSyncButtonState() initial call (NEW)

Database:
  AppDatabase.kt:25              → version = 14 (NEW)
  AppDatabase.kt:223-244         → MIGRATION_13_14 (NEW)
```

---

## Git Status

**Branch:** main (vermutlich)
**Letzter Commit:** a7e3def (recurrence-confidence)
**Uncommitted Changes:** Recurrence Pattern Feature (Datenmodell + Migration)

**Nächster Commit sollte enthalten:**
- RecurrencePattern.kt
- RecurrencePatternDao.kt
- RecurrencePatternRepository.kt
- RecurrencePatternMatcher.kt
- Transaction.kt (recurrencePatternId)
- AppDatabase.kt (Migration 13→14)

---

**Status:** Ready for next session. Kontext vollständig dokumentiert.
