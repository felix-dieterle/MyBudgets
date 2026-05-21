# Sync Protocol v3: Forward-Sync-Strategie

**Status:** Implementiert in v1.0.45-310 (2026-05-20)

## Problem mit v2 (730-Tage-Chunks)

**Bug:** Bank liefert bei `fromDate` immer **150 TX VORWÄRTS**, nicht rückwärts!

**Beispiel:**
```
DB: Neueste TX = 20.05.2024
Älteste TX = 02.07.2022

Historischer Sync: fromDate = 20.05.2024 - 730 Tage = 20.05.2022
→ Bank liefert: 20.05.2022 bis 01.07.2022 (150 TX vorwärts)
→ LÜCKE: 02.07.2022 bis 20.05.2024 fehlt!
```

## Lösung: Forward-Sync ab ältester TX

### Neue Strategie

**Historischer Sync = Vorwärts-Laden von ältesten TX**

1. **Start:** `fromDate = Date(0)` (1970-01-01) → Bank liefert **150 älteste TX überhaupt**
   - z.B. 01.01.2020 (Kontoöffnung) bis 15.03.2020
   - Anchor: `syncHistoricalEndDate = 15.03.2020` (neueste gelieferte TX)

2. **"Weiter laden":** `fromDate = syncHistoricalEndDate + 1 Tag`
   - z.B. 16.03.2020 → Bank liefert 150 TX ab 16.03.2020 vorwärts
   - z.B. 16.03.2020 bis 30.05.2020
   - Anchor: `syncHistoricalEndDate = 30.05.2020`

3. **Wiederholen bis Lücke geschlossen**

### Gap Detection (Smart Start)

**Bei erneutem historischen Sync:** Prüfe ob Lücke zwischen ältester und neuester TX existiert

```kotlin
val gapDays = (newestTx - oldestTx) / (24 * 60 * 60 * 1000)
val txCount = txRepo.getTransactionCountForAccount(accountId)
val avgTxPerDay = txCount.toDouble() / gapDays

if (avgTxPerDay < 0.5 && gapDays > 30) {
    // Gap exists → Starte bei ältester TX + 1 Tag (Lücke schließen)
    fromDate = Date(oldestTx + 24 * 60 * 60 * 1000)
} else {
    // Kein Gap → Starte bei Date(0) (älteste TX überhaupt)
    fromDate = Date(0)
}
```

**Kriterium:** < 0.5 TX/Tag über >30 Tage Zeitraum = wahrscheinlich Lücke

## Implementation Details

### Zwei Anchors

```kotlin
syncHistoricalEndDate: Long  // Für historischen Sync (vorwärts)
// Kein separater Anchor für Normal-Sync nötig (nutzt "neueste - 7 Tage")
```

### Sync-Modi

| Modus | Trigger | fromDate | Beschreibung |
|-------|---------|----------|--------------|
| **Normal-Sync** | Button "Bank-Sync" | Neueste TX - 7 Tage | Aktuelle Daten |
| **Voll-Sync** | Normal-Sync + DB leer | `null` | Alle TX |
| **Historisch-Start** | Button "Ältere laden" + kein Gap | `Date(0)` | Älteste überhaupt |
| **Historisch-Gap** | Button "Ältere laden" + Gap | Älteste TX + 1 Tag | Lücke schließen |
| **Historisch-Continue** | Button "Weiter" | `syncHistoricalEndDate + 1 Tag` | Iterativ vorwärts |

### Button-Zustand

```kotlin
canContinueSync: Boolean 
    = syncHistoricalEndDate != NO_FROM_DATE 
    && syncHistoricalEndDate > SYNC_STOP_MILLIS

// Button "Weiter zurück" nur aktiviert wenn:
// - Letzter historischer Sync lieferte neue TX
// - Ende noch nicht erreicht (> 2000-01-01)
```

### UI-Transparenz

**Loading-Message:**
- Normal: "Lade ab DD.MM.YYYY..."
- Voll: "Erstmaliger Sync - lade alle Buchungen..."
- Historisch-Start: "Lade älteste Buchungen..."
- Historisch-Gap: "Schließe Lücke ab DD.MM.YYYY..."
- Historisch-Continue: "Lade ab DD.MM.YYYY..."

**Success-Snackbar:**
```
150 neue Buchungen importiert
(01.01.2020 - 15.03.2020)
```

## Vorteile gegenüber v2

| Aspekt | v2 (730-Tage-Chunks) | v3 (Forward-Sync) |
|--------|----------------------|-------------------|
| **Lückenlos** | ❌ Lücken bei variierender TX-Dichte | ✅ Perfekte Anschlüsse |
| **Vorhersagbar** | ⚠️ 0-150 TX (unsicher) | ✅ Immer 150 TX (oder Ende erreicht) |
| **Effizient** | ⚠️ Überlappung verschwendet | ✅ Keine Überlappung |
| **Gap-Handling** | ❌ Keine automatische Erkennung | ✅ Smart Gap Detection |

## Test-Szenarien

### Szenario 1: Leere DB

```
DB: Leer

Historischer Sync:
1. fromDate = Date(0) → 150 TX (01.01.2020 - 15.03.2020)
2. fromDate = 16.03.2020 → 150 TX (16.03.2020 - 30.05.2020)
3. fromDate = 31.05.2020 → 50 TX (31.05.2020 - 20.05.2024)
4. fromDate = 21.05.2024 → 0 TX (Ende erreicht)

Ergebnis: 350 TX, lückenlos
```

### Szenario 2: Gap zwischen alt und neu

```
DB:
- Neueste: 20.05.2024 (Normal-Sync)
- Älteste: 01.01.2023 (Normal-Sync -7 Tage)
- Gap: 01.01.2020 - 31.12.2022 fehlt

Historischer Sync:
1. Gap Detection → avgTxPerDay = 0.2 < 0.5 → Gap erkannt
2. fromDate = 02.01.2023 → 150 TX (02.01.2023 - 01.04.2023)
3. fromDate = 02.04.2023 → ... (iterativ vorwärts)
4. Bis Lücke geschlossen

Dann separater Run:
1. fromDate = Date(0) → Älteste überhaupt laden
```

## Known Limitations

1. **Gap Detection heuristisch:** avgTxPerDay < 0.5 kann false positives geben bei niedrig frequentierten Konten
2. **Kein automatischer Lückenschluss:** User muss nach Gap-Closing manuell erneut "Ältere laden" drücken für älteste TX
3. **150 TX Limit bleibt:** Bank-seitig unveränderbar

## Next Steps

**User-Testing:** Prüfen ob:
1. Historischer Sync lückenlos funktioniert
2. Gap Detection zuverlässig arbeitet
3. UI-Messages klar verständlich sind
4. Button-Zustand korrekt aktualisiert wird

---

**Version:** v3 (Forward-Sync)  
**Build:** v1.0.45-310  
**Datum:** 2026-05-20
