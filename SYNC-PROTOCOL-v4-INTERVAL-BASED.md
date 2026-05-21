# Sync Protocol v4: Interval-Based Gap Detection

**Status:** Implementiert in v1.0.45-311 (2026-05-20)  
**Build:** `MyBudgets-v1.0.45-311-INTERVAL-SYNC-20260520-224531.apk`

## Evolution

- **v1:** Multi-Window Sync (fehlgeschlagen, revertiert)
- **v2:** 730-Tage-Chunks (Lücken wegen Vorwärts-Lesens)
- **v3:** Forward-Sync mit Anchor (keine Gap-Detection zwischen Syncs)
- **v4:** Interval-Tracking für perfekte Lücken-Erkennung ✅

## Problem mit v3

**Fehlende Persistenz:** `syncHistoricalEndDate` war nur im Memory → Verloren bei App-Neustart

**Beispiel:**
```
Session 1:
- Historischer Sync → 150 TX (01.01.2020 - 15.03.2020)
- syncHistoricalEndDate = 15.03.2020

App neu gestartet:
- syncHistoricalEndDate = NO_FROM_DATE
- Historischer Sync → 150 TX (01.01.2020 - 15.03.2020) WIEDER! (Duplikate)
```

## Lösung: Sync-Intervalle in DB speichern

### Neue Tabelle: `sync_intervals`

```kotlin
@Entity(tableName = "sync_intervals")
data class SyncInterval(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val startDate: Long,  // Älteste gelieferte TX
    val endDate: Long,    // Neueste gelieferte TX
    val isHistorical: Boolean,  // Normal-Sync vs. Historisch
    val timestamp: Long = System.currentTimeMillis()
)
```

### Gap-Detection-Algorithmus

```kotlin
fun getNextHistoricalSyncDate(accountId: Long): Long? {
    val intervals = getHistoricalForAccount(accountId).sortedBy { it.startDate }
    
    if (intervals.isEmpty()) {
        return 0L // Special marker: Start ohne fromDate
    }
    
    // Prüfe Lücken zwischen Intervallen
    for (i in 0 until intervals.size - 1) {
        val gapStart = intervals[i].endDate + 1 Tag
        val gapEnd = intervals[i+1].startDate - 1 Tag
        
        if (gapStart < gapEnd) {
            return gapStart // Lücke gefunden
        }
    }
    
    // Keine Lücken → Prüfe ob noch ältere Daten vor erstem Intervall
    if (intervals.first().startDate > SYNC_STOP_MILLIS) {
        return intervals.first().startDate - 1 Tag
    }
    
    return null // Alles vollständig
}
```

## Workflow

### Erster historischer Sync

```
1. Button "Ältere laden" gedrückt
2. getNextHistoricalSyncDate() → 0L (keine Intervalle)
3. syncBankTransactions(accountId, 0L, isHistorical=true)
4. fromDate = null (Bank entscheidet, liefert älteste)
5. Bank liefert: 150 TX (01.01.2020 - 15.03.2020)
6. Speichere: SyncInterval(start=01.01.2020, end=15.03.2020, isHistorical=true)
7. Button-Status update: canContinueSync = true
```

### Zweiter historischer Sync

```
1. Button "Ältere laden" gedrückt
2. getNextHistoricalSyncDate() → 16.03.2020 (end + 1 Tag)
3. syncBankTransactions(accountId, 16.03.2020, isHistorical=true)
4. fromDate = 16.03.2020
5. Bank liefert: 150 TX ab 16.03.2020 vorwärts → 16.03.2020 - 30.05.2020
6. Speichere: SyncInterval(start=16.03.2020, end=30.05.2020, isHistorical=true)
7. Button-Status update: canContinueSync = true
```

### Normal-Sync zwischendurch

```
1. Button "Bank-Sync" gedrückt
2. syncBankTransactions(accountId, NO_FROM_DATE, isHistorical=false)
3. DB hat TX → fromDate = neueste - 7 Tage
4. Bank liefert: 20 TX (13.05.2024 - 20.05.2024)
5. Speichere: SyncInterval(start=13.05.2024, end=20.05.2024, isHistorical=false)
6. Button "Ältere laden" bleibt aktiv (Lücke erkannt)
```

### Gap-Closing

```
DB Intervalle:
- [01.01.2020 - 15.03.2020, isHistorical=true]
- [16.03.2020 - 30.05.2020, isHistorical=true]
- [13.05.2024 - 20.05.2024, isHistorical=false]

getNextHistoricalSyncDate():
1. Sortiere: [01.01.2020-15.03.2020], [16.03.2020-30.05.2020], [13.05.2024-20.05.2024]
2. Prüfe Gap zwischen [1] und [2]: 16.03 == 16.03 → Kein Gap
3. Prüfe Gap zwischen [2] und [3]: 31.05.2020 < 12.05.2024 → GAP!
4. Return 31.05.2020

Button "Ältere laden":
→ fromDate = 31.05.2020
→ Bank liefert 150 TX vorwärts → schließt Lücke iterativ
```

### Vollständig geladen

```
DB Intervalle: Lückenlose Kette von 01.01.2020 bis 20.05.2024

getNextHistoricalSyncDate():
1. Keine Gaps zwischen Intervallen
2. Erstes Intervall start = 01.01.2020
3. SYNC_STOP_MILLIS = 01.01.2000
4. 01.01.2020 > 01.01.2000 → Noch älter möglich
5. Return 31.12.2019

Weitere Iterationen bis Bank <150 TX liefert oder SYNC_STOP_MILLIS erreicht
```

## UI-Integration

### Button-Status

```kotlin
// AccountDetailFragment
private fun updateHistoricalSyncButtonState() {
    viewLifecycleOwner.lifecycleScope.launch {
        val canContinue = vm.canContinueSync(accountId)
        binding.btnHistoricalSync.isEnabled = canContinue
        binding.btnHistoricalSync.alpha = if (canContinue) 1.0f else 0.5f
    }
}

// Nach jedem Success-State aufrufen
is BankSyncState.Success -> {
    updateHistoricalSyncButtonState()
}
```

### Button-Click

```kotlin
binding.btnHistoricalSync.setOnClickListener {
    // Validierung (IBAN, userId)
    registerPinTanProviders()
    vm.continueSyncOlder(accountId) // Automatisch richtige Lücke
}
```

## DB-Migration

```sql
-- Migration 12→13
CREATE TABLE IF NOT EXISTS sync_intervals (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    accountId INTEGER NOT NULL,
    startDate INTEGER NOT NULL,
    endDate INTEGER NOT NULL,
    isHistorical INTEGER NOT NULL,
    timestamp INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS index_sync_intervals_accountId 
ON sync_intervals(accountId);
```

## Vorteile gegenüber v3

| Aspekt | v3 (Forward-Sync) | v4 (Interval-Based) |
|--------|-------------------|---------------------|
| **Persistenz** | ❌ Memory only → verloren bei Restart | ✅ DB-persistent |
| **Gap-Detection** | ❌ Heuristisch (avgTxPerDay) | ✅ Exakt (Intervall-Differenzen) |
| **Lücken schließen** | ⚠️ Manuell neu starten nötig | ✅ Automatisch nächste Lücke |
| **Duplikate** | ⚠️ Bei Restart möglich | ✅ Unmöglich (Deduplizierung) |
| **Normal + Historisch** | ⚠️ Separates Tracking | ✅ Vereinheitlicht |

## Test-Szenarien

### Szenario 1: Frisches Konto

```
1. Normal-Sync → [13.05.2024 - 20.05.2024, isHistorical=false]
2. Button "Ältere laden" → fromDate=null → [01.01.2020 - 15.03.2020, isHistorical=true]
3. Button "Ältere laden" → fromDate=16.03.2020 → [16.03.2020 - 30.05.2020, isHistorical=true]
4. ... iterativ bis Lücke zu Normal-Sync geschlossen
5. Button deaktiviert (wenn älteste == SYNC_STOP_MILLIS)
```

### Szenario 2: App-Restart während historischem Sync

```
Session 1:
- [01.01.2020 - 15.03.2020, isHistorical=true]
- [16.03.2020 - 30.05.2020, isHistorical=true]
- App geschlossen

Session 2:
- getNextHistoricalSyncDate() → 31.05.2020 (aus DB geladen!)
- Weiter ab 31.05.2020 → KEINE Duplikate
```

### Szenario 3: Mehrere Normal-Syncs vor historischem Sync

```
Tag 1: Normal-Sync → [19.05.2024 - 20.05.2024, isHistorical=false]
Tag 2: Normal-Sync → [20.05.2024 - 21.05.2024, isHistorical=false]
Tag 3: Historisch → fromDate=null → [01.01.2020 - 15.03.2020, isHistorical=true]

getNextHistoricalSyncDate():
- Prüft nur isHistorical=true Intervalle
- Findet Gap zwischen 15.03.2020 und 19.05.2024
- Return 16.03.2020
```

## Known Limitations

1. **SYNC_STOP_MILLIS (2000-01-01):** Hard-Coded-Limit für älteste Daten
2. **150 TX Limit:** Bank-seitig unveränderbar → Viele Iterationen bei hoher TX-Dichte
3. **Keine automatische Deduplizierung alter Intervalle:** `sync_intervals` Tabelle wächst unbegrenzt

## Future Improvements

1. **Intervall-Merging:** Benachbarte Intervalle automatisch mergen
2. **Cleanup-Job:** Alte Intervalle >1 Jahr löschen
3. **Progress-Indicator:** "X% historische Daten geladen" basierend auf Intervallen
4. **Multi-Account:** Gap-Detection pro Account isoliert ✅ (bereits implementiert)

---

**Version:** v4 (Interval-Based)  
**Build:** v1.0.45-311  
**Datum:** 2026-05-20  
**APK:** `MyBudgets-v1.0.45-311-INTERVAL-SYNC-20260520-224531.apk`  
**Location:** `\\secure-storage\home\Downloads\MyBudgets\`
