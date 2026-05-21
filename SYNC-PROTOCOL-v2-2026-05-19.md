# Sync-Ablauf v2 - Smart Sync mit KUmsZeitSEPA

**Stand:** 2026-05-19 22:00  
**Version:** v1.0.45-308+  
**Änderungen:** Smart Normal-Sync, KUmsZeitSEPA Priority, 730-Tage-Chunks

---

## Grundkonzepte v2

### 1. Normal-Sync (Button "Synchronisieren")
- **Trigger:** User klickt "Synchronisieren" in Account-Detail
- **Parameter:** `fromDateMillis = NO_FROM_DATE` (= -1L)
- **Logik:**
  - **DB leer:** Voll-Sync (fromDate=null) → Bank liefert ALLE TX
  - **DB hat TX:** Smart-Sync (fromDate = neueste TX - 7 Tage) → nur neue TX
- **FinTS Job:**
  - Primary: **KUmsZeitSEPA** (MT940, HBCI 2.2, oft >150 TX)
  - Fallback: KUmsAllCamt (CAMT, FinTS 3.0, max 150 TX)
- **Anchor Update:** `syncLastFromDate = fromDate` (das gesendete fromDate)

### 2. Historischer Sync (Button "Ältere Buchungen importieren")
- **Trigger:** User klickt "Ältere Buchungen" → DatePicker
- **Parameter:** `fromDateMillis = userSelectedDate`
- **FinTS Job:** KUmsZeitSEPA → KUmsAllCamt (gleiche Fallback-Kette)
- **Anchor Update:** `syncLastFromDate = fromDate` (wenn neue TX)

### 3. Kontinuierlicher Historischer Sync (Button "Weiter zurück")
- **Trigger:** Nach erfolgreichem Historisch-Sync mit >0 TX → Snackbar "Weiter laden"
- **Parameter:** `fromDateMillis = earliestTxInDB - 730 Tage`
- **FinTS Job:** KUmsZeitSEPA → KUmsAllCamt
- **Anchor Update:**
  - Mit neuen TX: `syncLastFromDate = fromDate` (Button bleibt aktiv)
  - Ohne neue TX: `syncLastFromDate = NO_FROM_DATE` (Button deaktiviert)

### 4. KUmsZeitSEPA vs KUmsAllCamt (BBBank)

| Job | Format | HBCI | TX-Limit | Verhalten ohne startdate | Verhalten mit startdate |
|-----|--------|------|----------|-------------------------|------------------------|
| **KUmsZeitSEPA** | MT940 | 2.2 | **>150** (unklar, oft unbegrenzt) | Alle verfügbaren TX | Alle TX ab startdate |
| **KUmsAllCamt** | CAMT.052 | 3.0 | **150** (hart) | 150 neueste TX | 150 älteste ab startdate (vorwärts) |

---

## Ablauf-Szenarien mit neuer Logik

### Szenario A: Erste Installation (leere DB)

**Initial-Zustand:**
- DB: leer
- syncLastFromDate: NO_FROM_DATE

**Schritt 1: Normal-Sync**
```
Auto-Modus: DB leer → Voll-Sync
fromDate = null (NO_FROM_DATE)
→ Bank: KUmsZeitSEPA ohne startdate
→ Bank liefert: ALLE verfügbaren TX (z.B. 2000+ TX ab Kontoöffnung)
→ newTx: alle (z.B. 2000 TX)
→ syncLastFromDate = null (bleibt NO_FROM_DATE für Voll-Sync)
→ UI: "2000 neue Buchungen (01.01.2020 - 19.05.2026)" ✅
```

**✅ Perfekt:** User hat mit einem Klick alle Daten!

---

### Szenario B: App hat bereits Daten (Normal-Betrieb)

**Initial-Zustand:**
- DB: TX bis 31.12.2025
- Heute: 19.05.2026
- syncLastFromDate: NO_FROM_DATE (nach App-Neustart)

**Schritt 1: Normal-Sync**
```
Auto-Modus: DB hat TX → Smart-Sync
newestTxInDB = 31.12.2025
fromDate = 24.12.2025 (newestTx - 7 Tage)
→ Bank: KUmsZeitSEPA mit startdate=24.12.2025
→ Bank liefert: TX ab 24.12.2025 (24.12.2025 bis 19.05.2026)
→ existingRemoteIds: [... alle TX bis 31.12.2025]
→ newTx: TX von 01.01.2026 bis 19.05.2026 (~140 TX)
→ syncLastFromDate = 24.12.2025
→ UI: "140 neue Buchungen (24.12.2025 - 19.05.2026)" ✅
```

**✅ Funktioniert:** Nur neue TX geladen, 7-Tage-Overlap schließt Lücken.

**Schritt 2: Historischer Sync (falls User will)**
```
User klickt "Ältere Buchungen" → wählt 01.01.2024
fromDate = 01.01.2024
→ Bank: KUmsZeitSEPA mit startdate=01.01.2024
→ Bank liefert: TX ab 01.01.2024 bis 31.12.2024 (möglicherweise >150 TX!)
→ existingRemoteIds: [... TX bis 31.12.2025]
→ newTx: TX von 01.01.2024 bis 31.12.2024 (~365 TX)
→ syncLastFromDate = 01.01.2024
→ UI: "365 neue Buchungen (01.01.2024 - 31.12.2024)" ✅
→ Button "Weiter zurück" erscheint
```

**✅ Großer Fortschritt:** Ein Klick lädt ganzes Jahr (wenn KUmsZeitSEPA kein Limit hat).

---

### Szenario C: User klickt mehrfach "Weiter zurück"

**Initial-Zustand:**
- DB: TX ab 01.01.2024 (aus früheren Syncs)
- syncLastFromDate: 01.01.2024
- User klickt "Weiter zurück"

**Schritt 1: Kontinuierlich-Historisch #1**
```
earliestTxInDB = 01.01.2024
fromDate = 01.01.2022 (earliest - 730 Tage = 2 Jahre)
→ Bank: KUmsZeitSEPA mit startdate=01.01.2022
→ Bank liefert: TX ab 01.01.2022 (möglicherweise >150 TX bis 31.12.2023)
→ existingRemoteIds: [... TX ab 01.01.2024]
→ newTx: TX von 01.01.2022 bis 31.12.2023 (~730 TX)
→ syncLastFromDate = 01.01.2022
→ UI: "730 neue Buchungen (01.01.2022 - 31.12.2023)" ✅
→ Button "Weiter zurück" bleibt aktiv
```

**Schritt 2: Kontinuierlich-Historisch #2**
```
earliestTxInDB = 01.01.2022
fromDate = 01.01.2020 (earliest - 730 Tage)
→ Bank: KUmsZeitSEPA mit startdate=01.01.2020
→ Bank liefert: TX ab 01.01.2020 (bis 31.12.2021)
→ newTx: TX von 01.01.2020 bis 31.12.2021 (~730 TX)
→ syncLastFromDate = 01.01.2020
→ UI: "730 neue Buchungen (01.01.2020 - 31.12.2021)" ✅
```

**Schritt 3: Kontinuierlich-Historisch #3**
```
earliestTxInDB = 01.01.2020
fromDate = 01.01.2018 (earliest - 730 Tage)
→ Bank: KUmsZeitSEPA mit startdate=01.01.2018
→ Bank liefert: 0 TX (Konto wurde erst 2020 eröffnet)
→ newTx: 0
→ syncLastFromDate = NO_FROM_DATE (Button deaktiviert)
→ UI: "0 neue Buchungen" ✅
→ Button "Weiter zurück" verschwindet
```

**✅ Perfekt:** 3 Klicks für 6 Jahre Historie, automatische Stop-Erkennung.

---

### Szenario D: User hat nur alte Daten, jetzt sync in 2026

**Initial-Zustand:**
- DB: TX nur aus 2024 (bis 31.12.2024)
- Heute: 19.05.2026
- syncLastFromDate: NO_FROM_DATE

**Schritt 1: Normal-Sync**
```
Auto-Modus: DB hat TX → Smart-Sync
newestTxInDB = 31.12.2024
fromDate = 24.12.2024 (newestTx - 7 Tage)
→ Bank: KUmsZeitSEPA mit startdate=24.12.2024
→ Bank liefert: TX ab 24.12.2024 (bis 19.05.2026)
→ newTx: TX von 01.01.2025 bis 19.05.2026 (~500 TX)
→ syncLastFromDate = 24.12.2024
→ UI: "500 neue Buchungen (24.12.2024 - 19.05.2026)" ✅
```

**✅ Gap geschlossen:** 7-Tage-Overlap verhindert Lücke.

---

## Vergleich: Alt vs Neu

| Szenario | v1 (Alt) | v2 (Neu) |
|----------|----------|----------|
| **Erstinstallation** | 150 TX → User muss manuell historisch laden | Alle TX auf einmal ✅ |
| **Normal-Sync (aktuelle Daten)** | 150 neueste → dedupliziert → 0 neue ❌ | Nur neue TX ab latest-7d ✅ |
| **Historisch laden (2 Jahre)** | 700+ Klicks (1 Tag/Klick) ❌ | 2-3 Klicks (730d/Klick) ✅ |
| **Lücken-Handling** | Möglich bei edge cases ⚠️ | 7-Tage-Overlap ✅ |
| **0 TX Detection** | Button bleibt aktiv ⚠️ | Button deaktiviert ✅ |
| **Zeitraum-Transparenz** | Nur in Logs | UI + Snackbar ✅ |

---

## Offene Fragen

1. **KUmsZeitSEPA TX-Limit:** Liefert BBBank wirklich >150 TX? → Test benötigt
2. **Fallback-Verhalten:** Was passiert wenn KUmsZeitSEPA fehlschlägt? → KUmsAllCamt (150 Cap)
3. **Performance:** Lädt KUmsZeitSEPA 2000+ TX in einem Request? → Monitoring

---

## Testing-Protokoll

### Test 1: Normal-Sync mit aktuellen Daten
- **Erwartung:** 0-10 neue TX (je nach Aktivität)
- **Zeitraum UI:** "Lade ab [latest-7d]..."
- **Ergebnis:** "X neue Buchungen ([von] - [bis])" ✅

### Test 2: Historisch laden (730 Tage zurück)
- **Erwartung:** >150 TX wenn KUmsZeitSEPA funktioniert
- **Fallback:** 150 TX wenn nur KUmsAllCamt
- **Zeitraum UI:** "Lade ab [earliest-730d]..."

### Test 3: Kontinuierlich "Weiter zurück"
- **Erwartung:** Button deaktiviert bei 0 neuen TX
- **Zeitraum UI:** Zeitraum in Snackbar sichtbar

---

**Nächste Schritte:**
1. User testet v308 und liefert Logs
2. Prüfen ob KUmsZeitSEPA >150 TX liefert
3. Bei Problemen: Fallback auf KUmsAllCamt + kleinere Chunks (180d statt 730d)
