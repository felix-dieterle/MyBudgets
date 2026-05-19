# Balance (Saldo) Update Analyse

**Stand:** 2026-05-19  
**Problem:** Saldo wird bei historischem Sync mit falschen Werten überschrieben

---

## Aktuelles Verhalten

### Code-Flow (AccountViewModel.kt:203-207)

```kotlin
val camtBalance = fintsService.lastCamtBalance
if (camtBalance != null) {
    repo.save(account.copy(balance = camtBalance))
    AppLogger.i(TAG, "syncBankTransactions: Saldo ${account.id} aktualisiert: $camtBalance")
}
```

**Herkunft von `lastCamtBalance`:**

1. **FintsService.kt:921** setzt `lastCamtBalance` beim CAMT-Parsing
2. **CustomCamtParser.kt:229-231** extrahiert Balance vom Typ `CLBD` (Closing Booked Balance)
3. **CAMT XML** enthält Balance für den Zeitraum der Transaktionen

---

## Problem-Szenarien

### Szenario 1: Normal-Sync (aktuell)

**Request:**
- fromDate = null (heute)
- Bank liefert: 150 neueste TX (2025-12-01 bis 2026-05-19)
- CAMT `<Bal><Cd>CLBD</Cd>`: Saldo am **2026-05-19** (heute) = 5.432,10 EUR

**Update:**
- `camtBalance = 5432.10`
- `account.balance = 5432.10` ✅ **KORREKT**

---

### Szenario 2: Historischer Sync (Problem!)

**Request:**
- fromDate = 2024-10-01 (vor 1,5 Jahren)
- Bank liefert: 150 TX (2024-10-01 bis 2025-03-01)
- CAMT `<Bal><Cd>CLBD</Cd>`: Saldo am **2025-03-01** = 3.215,50 EUR

**Update:**
- `camtBalance = 3215.50`
- `account.balance = 3215.50` ❌ **FALSCH!**

**Erwartung:** Saldo soll aktueller Wert bleiben (5.432,10 EUR), nicht historischer Wert!

---

### Szenario 3: Mehrfacher historischer Sync

**Initial:**
- `account.balance = 5432.10` (aus Normal-Sync)

**Hist-Sync #1:**
- fromDate = 2024-09-30
- CAMT Balance = 3100.00 (Sep 2024)
- `account.balance = 3100.00` ❌

**Hist-Sync #2:**
- fromDate = 2024-03-31
- CAMT Balance = 2800.00 (März 2024)
- `account.balance = 2800.00` ❌

**User sieht:** Saldo springt wild zwischen Werten hin und her!

---

## Ursache

### CAMT `<Bal>` Semantik

**CAMT.052 Balance Types:**
- `OPBD` = Opening Balance (Anfangssaldo)
- `CLBD` = Closing Booked Balance (Schlusssaldo **für den abgefragten Zeitraum**)
- `ITBD` = Interim Balance (Zwischensaldo)

**Wichtig:** `CLBD` ist **NICHT** der aktuelle Kontosaldo, sondern der Saldo **am Ende des abgefragten Zeitraums**!

**Beispiel:**
- Request: `startdate=2024-10-01`
- Bank liefert: TX von 2024-10-01 bis 2025-03-01
- `CLBD` = Saldo am 2025-03-01 (vor 14 Monaten!)

---

## Lösung: Balance nur bei aktuellem Sync aktualisieren

### Option 1: Balance nur bei fromDate=null aktualisieren

```kotlin
val camtBalance = fintsService.lastCamtBalance
// Nur bei Voll-Sync (fromDate=null) ist CLBD aktueller Kontosaldo
if (camtBalance != null && actualFromMillis == NO_FROM_DATE) {
    repo.save(account.copy(balance = camtBalance))
    AppLogger.i(TAG, "syncBankTransactions: Saldo ${account.id} aktualisiert: $camtBalance")
} else if (camtBalance != null) {
    AppLogger.i(TAG, "syncBankTransactions: Historischer Sync - Balance NICHT aktualisiert (CLBD=$camtBalance für fromDate=${Date(actualFromMillis)})")
}
```

**Vorteil:** Einfach, klar
**Nachteil:** Bei fromDate nahe heute wird Balance trotzdem nicht aktualisiert

---

### Option 2: Balance nur bei "nahem" Datum aktualisieren

```kotlin
val camtBalance = fintsService.lastCamtBalance
if (camtBalance != null) {
    val isRecentSync = if (actualFromMillis == NO_FROM_DATE) {
        true // Voll-Sync = aktuell
    } else {
        val daysSinceFrom = (System.currentTimeMillis() - actualFromMillis) / (24 * 60 * 60 * 1000)
        daysSinceFrom <= 7 // Balance nur aktualisieren wenn fromDate < 7 Tage alt
    }
    
    if (isRecentSync) {
        repo.save(account.copy(balance = camtBalance))
        AppLogger.i(TAG, "syncBankTransactions: Saldo ${account.id} aktualisiert: $camtBalance")
    } else {
        AppLogger.i(TAG, "syncBankTransactions: Historischer Sync (${daysSinceFrom}d alt) - Balance NICHT aktualisiert")
    }
}
```

**Vorteil:** Flexibler, funktioniert auch bei fromDate nahe heute
**Nachteil:** Magic Number (7 Tage)

---

### Option 3: Balance-Datum aus CAMT extrahieren

**Problem:** CAMT `<Bal>` enthält kein explizites Datum für CLBD bei BBBank.

**Workaround:** Nutze neueste TX in Response als Balance-Datum

```kotlin
val camtBalance = fintsService.lastCamtBalance
if (camtBalance != null && transactions.isNotEmpty()) {
    val newestTxDate = transactions.maxOfOrNull { it.date } ?: 0L
    val daysSinceNewestTx = (System.currentTimeMillis() - newestTxDate) / (24 * 60 * 60 * 1000)
    
    if (daysSinceNewestTx <= 7) {
        repo.save(account.copy(balance = camtBalance))
        AppLogger.i(TAG, "syncBankTransactions: Saldo ${account.id} aktualisiert: $camtBalance (newestTx: ${Date(newestTxDate)})")
    } else {
        AppLogger.i(TAG, "syncBankTransactions: Historischer Sync (newestTx ${daysSinceNewestTx}d alt) - Balance NICHT aktualisiert")
    }
}
```

**Vorteil:** Nutzt tatsächliche TX-Daten statt fromDate-Parameter
**Nachteil:** Komplexer

---

## Empfehlung

**Option 1 (simpel)** für ersten Fix:

```kotlin
// Balance nur bei Voll-Sync (fromDate=null) aktualisieren
if (camtBalance != null && actualFromMillis == NO_FROM_DATE) {
    repo.save(account.copy(balance = camtBalance))
    AppLogger.i(TAG, "syncBankTransactions: Saldo ${account.id} aktualisiert: $camtBalance")
}
```

**Begründung:**
- Klar definiert: Voll-Sync = aktueller Saldo
- Kein Magic Number
- Historischer Sync ändert Balance nie → konsistent

**Edge Case:** Wenn User nur historische Syncs macht, wird Balance nie aktualisiert.
→ Akzeptabel, da User dann nie "Synchronisieren" klickt

---

## Test-Matrix

| Sync-Art | fromDate | CAMT CLBD | Balance Update? | Erwartetes Verhalten |
|----------|----------|-----------|-----------------|----------------------|
| Normal-Sync | null | 2026-05-19 Saldo | ✅ JA | Aktueller Saldo gesetzt |
| Hist-Sync #1 | 2024-10-01 | 2025-03-01 Saldo | ❌ NEIN | Balance bleibt unverändert |
| Hist-Sync #2 | 2024-03-31 | 2024-09-30 Saldo | ❌ NEIN | Balance bleibt unverändert |
| Normal-Sync (später) | null | 2026-05-20 Saldo | ✅ JA | Neuer aktueller Saldo |

