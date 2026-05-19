# Sync-Ablauf Protokoll & Analyse

**Stand:** 2026-05-19  
**Ziel:** Genaues Verständnis der beiden Sync-Mechanismen und deren Edge Cases

---

## Grundkonzepte

### 1. Normal-Sync (Button "Synchronisieren")
- **Trigger:** User klickt "Synchronisieren" in Account-Detail
- **Parameter:** `fromDateMillis = NO_FROM_DATE` (= -1L)
- **FinTS Job:** KUmsAllCamt ohne `startdate` → Bank liefert ~150 neueste Buchungen
- **Anchor Update:** `syncLastFromDate = newTx.minOfOrNull { it.date }` (früheste neue TX)

### 2. Historischer Sync (Button "Ältere Buchungen importieren")
- **Trigger:** User klickt "Ältere Buchungen" nach Normal-Sync
- **Parameter:** `fromDateMillis = syncLastFromDate - 1`
- **FinTS Job:** KUmsAllCamt mit `startdate=fromDate` → Bank liefert 150 älteste ab diesem Datum
- **Anchor Update:** `syncLastFromDate = actualFromMillis` (das gesendete fromDate)

### 3. KUmsAllCamt Verhalten (BBBank)
- **Ohne startdate:** Liefert ~150 **NEUESTE** Buchungen (bis heute)
- **Mit startdate:** Liefert ~150 **ÄLTESTE** ab startdate (vorwärts)

---

## Ablauf-Szenarien mit Beispieldaten

### Szenario A: Erste Installation (leere DB)

**Initial-Zustand:**
- DB: leer
- syncLastFromDate: NO_FROM_DATE

**Schritt 1: Normal-Sync**
```
fromDate = null (NO_FROM_DATE)
→ Bank: KUmsAllCamt ohne startdate
→ Bank liefert: 150 neueste TX (2025-12-01 bis 2026-05-19)
→ newTx: 150 (alle neu)
→ syncLastFromDate = 2025-12-01 (früheste neue TX)
→ UI: "150 neue Buchungen"
```

**Schritt 2: Historischer Sync ("Ältere Buchungen")**
```
fromDate = 2025-11-30 (syncLastFromDate - 1)
→ Bank: KUmsAllCamt mit startdate=2025-11-30
→ Bank liefert: 150 älteste ab 2025-11-30 (2025-11-30 rückwärts bis ~2025-06-01)
→ existingRemoteIds: [... 150 TX von Schritt 1]
→ newTx: ~150 (alle neu, ältere Periode)
→ syncLastFromDate = 2025-11-30 (das gesendete fromDate)
→ UI: "150 neue Buchungen"
```

**✅ Funktioniert**

---

### Szenario B: App hat bereits Daten (Normal-Betrieb)

**Initial-Zustand:**
- DB: TX bis 2025-12-31
- Heute: 2026-05-19
- syncLastFromDate: NO_FROM_DATE (nach App-Neustart)

**Schritt 1: Normal-Sync**
```
fromDate = null (NO_FROM_DATE)
→ Bank: KUmsAllCamt ohne startdate
→ Bank liefert: 150 neueste TX (2025-12-01 bis 2026-05-19)
→ existingRemoteIds: [... alle TX bis 2025-12-31]
→ newTx: nur TX von 2026-01-01 bis 2026-05-19 (~140 TX)
→ syncLastFromDate = 2026-01-01 (früheste NEUE TX)
→ UI: "140 neue Buchungen"
```

**❌ PROBLEM:** syncLastFromDate = 2026-01-01, aber Bank hat 150 TX ab 2025-12-01 geliefert!

**Schritt 2: Historischer Sync ("Ältere Buchungen")**
```
fromDate = 2025-12-31 (syncLastFromDate - 1)
→ Bank: KUmsAllCamt mit startdate=2025-12-31
→ Bank liefert: 150 älteste ab 2025-12-31 (vorwärts!)
→ Das sind TX von 2025-12-31 bis 2026-05-19 (alle schon in DB!)
→ newTx: 0
→ UI: "0 neue Buchungen"
```

**❌ FEHLER:** User kommt nicht an ältere Daten (vor 2025-12-01) ran!

---

### Szenario C: User klickt mehrfach "Ältere Buchungen"

**Initial-Zustand:**
- DB: TX ab 2024-10-01 (aus früheren Syncs)
- syncLastFromDate: 2024-10-01

**Schritt 1: Historischer Sync #1**
```
fromDate = 2024-09-30 (syncLastFromDate - 1)
→ Bank: KUmsAllCamt mit startdate=2024-09-30
→ Bank liefert: 150 älteste ab 2024-09-30 (2024-09-30 bis ~2025-03-01)
→ existingRemoteIds: [... TX ab 2024-10-01]
→ newTx: nur TX von 2024-09-30 (1 Tag = ~2 TX)
→ syncLastFromDate = 2024-09-30
→ UI: "2 neue Buchungen"
```

**Schritt 2: Historischer Sync #2**
```
fromDate = 2024-09-29
→ Bank: KUmsAllCamt mit startdate=2024-09-29
→ Bank liefert: 150 älteste ab 2024-09-29 (2024-09-29 bis 2025-02-28)
→ existingRemoteIds: [... TX ab 2024-09-30]
→ newTx: nur TX von 2024-09-29 (1 Tag = ~0-2 TX)
→ syncLastFromDate = 2024-09-29
→ UI: "0-2 neue Buchungen"
```

**❌ PROBLEM:** User muss hunderte Male klicken um 2 Jahre Daten zu holen!

---

### Szenario D: User hat nur alte Daten (2024), jetzt sync in 2026

**Initial-Zustand:**
- DB: TX nur aus 2024 (bis 2024-12-31)
- Heute: 2026-05-19
- syncLastFromDate: NO_FROM_DATE

**Schritt 1: Normal-Sync**
```
fromDate = null
→ Bank: KUmsAllCamt ohne startdate
→ Bank liefert: 150 neueste (2025-12-01 bis 2026-05-19)
→ existingRemoteIds: [... nur TX bis 2024-12-31]
→ newTx: alle 150 (alles neu aus 2025-2026)
→ syncLastFromDate = 2025-12-01
→ UI: "150 neue Buchungen"
```

**✅ Funktioniert, ABER:** Gap zwischen 2024-12-31 und 2025-12-01!

**Schritt 2: Historischer Sync ("Ältere Buchungen")**
```
fromDate = 2025-11-30
→ Bank: KUmsAllCamt mit startdate=2025-11-30
→ Bank liefert: 150 älteste ab 2025-11-30 (vorwärts!)
→ Alle schon in DB
→ newTx: 0
→ UI: "0 neue Buchungen"
```

**❌ FEHLER:** User kommt nicht an Daten von 2025-01-01 bis 2025-11-30 ran!

---

## Problem-Analyse

### Hauptproblem: Anchor-Logik bei Normal-Sync

**Code (Zeile 198-202):**
```kotlin
syncLastFromDate = if (actualFromMillis != NO_FROM_DATE) {
    actualFromMillis
} else {
    newTx.minOfOrNull { it.date } ?: NO_FROM_DATE
}
```

**Was passiert:**
- Normal-Sync ohne fromDate → Bank liefert 150 neueste
- syncLastFromDate = **früheste NEUE** TX (nicht früheste GELIEFERTE!)
- Historischer Sync startet bei der falschen Stelle

**Beispiel:**
- Bank liefert: TX von 2025-12-01 bis 2026-05-19 (150 TX)
- In DB: TX bis 2025-12-31
- Neue TX: 2026-01-01 bis 2026-05-19 (140 TX)
- syncLastFromDate = 2026-01-01 ❌
- **Richtig wäre:** syncLastFromDate = 2025-12-01 (früheste GELIEFERTE)

### Zweites Problem: KUmsAllCamt mit startdate geht VORWÄRTS

**Erwartung:** startdate = 2024-09-30 → liefert ältere Daten (rückwärts)
**Realität:** startdate = 2024-09-30 → liefert 150 älteste AB diesem Datum (vorwärts!)

**Resultat:** Wenn User auf "Ältere Buchungen" klickt, bekommt er immer wieder dieselben Daten.

---

## Lösungsansätze

### Option 1: Fix Anchor-Logik (Normal-Sync)

**Änderung:**
```kotlin
syncLastFromDate = if (actualFromMillis != NO_FROM_DATE) {
    actualFromMillis
} else {
    // Bei Voll-Sync: Früheste GELIEFERTE TX verwenden (nicht früheste neue!)
    transactions.minOfOrNull { it.date } ?: NO_FROM_DATE
}
```

**Vorteil:** Normal-Sync → Historischer Sync funktioniert lückenlos
**Nachteil:** Löst Problem C (viele Klicks) nicht

### Option 2: Historischer Sync mit größeren Sprüngen

**Änderung:**
```kotlin
fun continueSyncOlder(accountId: Long) {
    if (!canContinueSync) return
    viewModelScope.launch {
        val earliest = txRepo.getEarliestDateForAccount(accountId)
        if (earliest == null || earliest <= SYNC_STOP_MILLIS) { 
            syncLastFromDate = NO_FROM_DATE
            return@launch 
        }
        // Großer Sprung: -365 Tage statt -1 Tag
        val fromDate = earliest - 365L * 24 * 60 * 60 * 1000
        if (fromDate <= SYNC_STOP_MILLIS) { 
            syncLastFromDate = NO_FROM_DATE
            return@launch 
        }
        syncBankTransactions(accountId, fromDate)
    }
}
```

**Vorteil:** Weniger Klicks nötig
**Nachteil:** Kann Lücken lassen (150 TX Cap)

### Option 3: Normal-Sync lädt IMMER ab ältestem DB-Datum

**Konzept:**
```kotlin
fun syncBankTransactions(...) {
    val earliest = txRepo.getEarliestDateForAccount(accountId)
    val fromDate = if (earliest != null && earliest > SYNC_STOP_MILLIS) {
        // Sync ab frühestem existierenden Datum
        Date(earliest)
    } else {
        // Voll-Sync falls DB leer
        null
    }
    val syncResult = fintsService.fetchAccountStatement(account, fromDate)
}
```

**Vorteil:** Schließt Lücken automatisch
**Nachteil:** Lädt immer alle Daten seit ältestem Datum (overhead)

---

## Empfehlung

**Kombination Option 1 + 2:**

1. **Fix Anchor-Logik:** Normal-Sync verwendet früheste GELIEFERTE TX als Anchor
2. **Große Sprünge:** Historischer Sync springt 365 Tage zurück (nicht 1 Tag)

**Resultat:**
- Normal-Sync füllt Lücken nach vorne
- Historischer Sync holt schneller alte Daten
- User braucht ~4-5 Klicks für 2 Jahre Daten (statt 700+)

---

## Test-Matrix

| Szenario | Normal-Sync | Hist-Sync #1 | Hist-Sync #2 | Hist-Sync #3 |
|----------|-------------|--------------|--------------|--------------|
| **Leere DB** | 150 neue (2025-12-01 bis 2026-05-19) | 150 neue (2025-06-01 bis 2025-11-30) | 150 neue (2024-12-01 bis 2025-05-31) | 150 neue (2024-06-01 bis 2024-11-30) |
| **DB bis 2024** | 150 neue (2025-12-01 bis 2026-05-19) | 150 neue (2024-12-01 bis 2025-11-30) | 0-10 neue (Lücke 2024-12-31 bis 2025-12-01) | 150 neue (2023-12-01 bis 2024-11-30) |
| **DB aktuell** | 10 neue (letzte 10 Tage) | 140 neue (fehlende aus Bank-Response) | 150 neue (365 Tage zurück) | ... |

