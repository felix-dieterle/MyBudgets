# Work in Progress - 2026-05-19

## Aktuelle Aufgaben

### 1. Big Sync: Multi-KUmsAllCamt mit Date-Windows ⚠️ IN ARBEIT

**Problem:** 
- Früher: KUmsAllCamt (150 TX) + KUmsZeitSEPA (150 TX) = ~300 TX pro Sync (~2 Jahre Daten)
- Jetzt: HBCI 2.2 broken → nur KUmsAllCamt → 150 TX pro Sync
- User muss 20× "Ältere Buchungen importieren" klicken → inakzeptabel

**Lösung:**
- Multiple KUmsAllCamt-Jobs mit unterschiedlichen Date-Windows in EINEM Dialog/TAN
- 5 Jobs → 5×150 = bis zu 750 TX pro Sync
- 20 Klicks → ~4 Klicks

**Architektur-Überlegungen:**
- `fetchAccountStatement()` nutzt aktuell "first success wins" Pattern
- Für Multi-Job: Alle KUmsAllCamt-Jobs parallel adden, execute, merge, dedup
- WICHTIG: `enddate` Parameter nutzen für non-overlapping windows:
  ```
  Job 1: fromDate=earliest-365d, toDate=earliest-1
  Job 2: fromDate=earliest-730d, toDate=earliest-366d
  Job 3: fromDate=earliest-1095d, toDate=earliest-731d
  etc.
  ```
- Ohne `enddate`: Overlap-Problem (KUmsAllCamt returnt immer die 150 NEUESTEN ab fromDate)

**TODO:**
1. ✅ Code-Analyse abgeschlossen
2. ⏳ `continueSyncOlder()` umbauen: 5 Date-Windows berechnen
3. ⏳ `fetchAccountStatement()` umbauen: Multiple KUmsAllCamt mit enddate
4. ⏳ Result-Merging + Dedup nach remoteId
5. ⏳ Fallback: Wenn Multi-Job fehlschlägt → Single-Job retry

**Dateien betroffen:**
- `AccountViewModel.kt` (continueSyncOlder)
- `FintsService.kt` (fetchAccountStatement, buildJobAttempts, extractFlatData)

---

### 2. UX: ± Toggle vor Min/Max-Filter ⏸️ PENDING

**Anforderung:**
- Vor den Min/Max-Betrags-Feldern: Toggle-Switch für Minus/Plus
- Default: Minus (Ausgaben)
- User kann zwischen "Ausgaben anzeigen" / "Einnahmen anzeigen" umschalten

**Implementation:**
- `TransactionViewModel.kt`: Neue StateFlow `_amountSign: MutableStateFlow<AmountSign>` (enum: NEGATIVE, POSITIVE, ALL)
- `fragment_transactions.xml`: ChipGroup/ToggleButton vor `til_amount_min`
- `TransactionsFragment.kt`: Binding + Filter-Logik
- Filter-Logik: `tx.amount` Vorzeichen prüfen basierend auf `AmountSign`

**Dateien betroffen:**
- `TransactionViewModel.kt`
- `fragment_transactions.xml`
- `TransactionsFragment.kt`

---

### 3. UX: Last-Sync-Date in Account-Overview ⏸️ PENDING

**Anforderung:**
- Kontoübersicht zeigt Saldo, aber nicht wann zuletzt gesynct wurde
- User sieht nicht ob Saldo aktuell oder 1 Monat alt ist
- Lösung: `lastSyncAt` Timestamp pro Konto anzeigen

**Implementation:**

#### DB-Migration:
```kotlin
// Account.kt
data class Account(
    ...
    val lastSyncAt: Long? = null  // epoch millis
)
```

Migration in `AppDatabase.kt`:
```sql
ALTER TABLE accounts ADD COLUMN lastSyncAt INTEGER DEFAULT NULL;
```

#### Sync-Update:
`AccountViewModel.syncBankTransactions()` nach erfolgreichem Sync:
```kotlin
repo.save(account.copy(
    balance = camtBalance,
    lastSyncAt = System.currentTimeMillis()
))
```

#### UI-Anzeige:
`item_account.xml`: Neues TextView unter `tv_account_type`:
```xml
<TextView
    android:id="@+id/tv_last_sync"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="2dp"
    android:textAppearance="?attr/textAppearanceBodySmall"
    android:alpha="0.6"
    tools:text="Letzter Sync: vor 2 Tagen" />
```

`AccountAdapter.kt`:
```kotlin
fun bind(acc: Account) {
    ...
    binding.tvLastSync.text = if (acc.lastSyncAt != null) {
        "Letzter Sync: ${formatRelativeTime(acc.lastSyncAt)}"
    } else {
        "Noch nicht synchronisiert"
    }
    binding.tvLastSync.visibility = if (acc.iban.isNotBlank()) View.VISIBLE else View.GONE
}
```

Util-Funktion `DateFormatter.formatRelativeTime(millis: Long)`:
- "vor 5 Minuten"
- "vor 2 Stunden"
- "vor 3 Tagen"
- "vor 2 Wochen"

**Dateien betroffen:**
- `Account.kt` (data class)
- `AppDatabase.kt` (migration)
- `AccountViewModel.kt` (update lastSyncAt after sync)
- `item_account.xml` (layout)
- `AccountAdapter.kt` (binding)
- `DateFormatter.kt` (neue Util-Funktion)

---

## User-Kontext aus Conversation

**User sagte:**
1. "was ich nicht verstehe, ich erinnere mich dass früher einmal sehr viel mehr transaktionen geladen wurden, ich meine etwa 2 jahre 2024+2025. das wäre doch wieder sinnvoll oder nicht. was spricht für kleinere syncs wenn ich dann 20 mal syncen muss?"

   → **Answer:** Früher gab's 300 TX (KUmsAllCamt + KUmsZeitSEPA). Jetzt HBCI 2.2 broken. Lösung: Multi-KUmsAllCamt mit Date-Windows.

2. "ausserdem, vor dem min-max filter wäre ein mini schalter zum umschalten von minus zu plus beträgen gut, default auf minus."

   → **Answer:** Toggle für Ausgaben/Einnahmen Filter.

3. "bei der konten übersicht werden konten mit saldo angezeigt, hier wäre noch gut den stand(datum) anzuzeigen bis zu dem zuletzt abgefragt wurde, vielleicht brauchen wir im hintergrund allgemein eine historie der zeiträume die bereits gesynct wurden? verstehst du was ich meine? so sieht man dass zb. der letzte sync vor einem monat war und der saldo also nicht mehr aktuell ist."

   → **Answer:** `lastSyncAt` Timestamp speichern + in Kontoübersicht anzeigen.

4. "siehst du meine letzten 3 kommentare und ihre reihenfolge?"

   → **Answer:** Ja, alle 3 Kommentare sind klar.

---

## Nächste Schritte (Priorisiert)

1. **HIGH:** Multi-KUmsAllCamt implementieren (Aufgabe 1)
2. **MEDIUM:** ± Toggle implementieren (Aufgabe 2)
3. **MEDIUM:** Last-Sync-Date implementieren (Aufgabe 3)
4. **MEDIUM:** Build + APK + Deploy

---

## Offene Architektur-Fragen

### Multi-KUmsAllCamt: Bank-Support?

**Risiko:** BBBank könnte multiple same-type jobs ablehnen.

**Mitigation:**
- Try-Catch: Wenn Multi-Job Dialog fehlschlägt → Fallback auf Single-Job
- Log-Analyse: Nach erstem Deploy prüfen ob Multi-Jobs funktionieren

### KUmsAllCamt `enddate` Parameter Support?

**hbci4java:** `GVKUmsAllCamt` unterstützt `startdate`, `enddate`, `maxentries`

**BBBank:** Muss getestet werden. Falls `enddate` nicht supported:
- Plan B: Nur `startdate` nutzen, aber mit GROSSEN Steps (365d statt 1d)
- Akzeptieren dass Overlap existiert, Dedup via remoteId

---

## Code-Locations (Quick Reference)

```
Sync-Logic:
  AccountViewModel.kt:77-90     → continueSyncOlder()
  AccountViewModel.kt:136-223   → syncBankTransactions()
  FintsService.kt:230-373       → fetchAccountStatement()
  FintsService.kt:375-393       → buildJobAttempts()

Transaction-Filter:
  TransactionViewModel.kt:30-31  → _amountMin, _amountMax
  fragment_transactions.xml:167  → Amount filter layout
  TransactionsFragment.kt        → Filter binding

Account-Display:
  AccountAdapter.kt:18-30        → bind() method
  item_account.xml:20-32         → Account name/type/balance layout
  Account.kt:9-38               → Data class

Database:
  TransactionDao.kt:39          → getEarliestDateForAccount()
  AccountDao.kt                 → Account CRUD
```

---

**Status:** Ready for fresh session. Alle Infos dokumentiert.
