# v1.0.44 - Bank-Sync Fix (CRITICAL)

**Datum:** 2026-05-15  
**Status:** Superseded by ROADMAP.md (2026-05-17) – KUmsAllCamt re-activated, Build-Fehler durch entfernen des unfertigen VirtualAccount-Codes gelöst in späteren Commits

## 🔴 KRITISCHER FIX: Bank-Sync repariert

### Problem (v1.0.43)
- **Fehler:** "Diese Bank unterstützt keinen HBCI-Kontoauszug-Abruf"
- **Root Cause:** KUmsAllCamt wurde in v1.0.43 entfernt (basierend auf veralteter AGENTS.md Info)
- **Folge:** Alle Fallback-Jobs (KUmsZeitSEPA, KUmsAll, KUmsNew) werden von BBBank NICHT unterstützt
- **Log:** logs-app15.txt (2026-05-15 17:16)

### Beweis: KUmsAllCamt funktioniert
- **logs-app16.txt (2026-05-12 19:48):** KUmsAllCamt erfolgreich → 150 Transaktionen
- **CustomCamtParser:** Extrahiert CAMT trotz SAX-Exception erfolgreich
- **Passport:** Neu erstellt am 2026-05-12 nach Clean Data

### Lösung: KUmsAllCamt RE-AKTIVIERT
**Datei:** `app/src/main/java/de/mybudgets/app/data/banking/FintsService.kt` (Zeile 309-327)

**BEFORE (v1.0.43):**
```kotlin
// ❌ DEAKTIVIERT
val jobAttempts = listOf(
    // JobAttempt("KUmsAllCamt", fromDate),  // ❌ DEAKTIVIERT
    JobAttempt("KUmsZeitSEPA", fromDate),
    JobAttempt("KUmsAll"),
    JobAttempt("KUmsNew"),
)
```

**AFTER (v1.0.44):**
```kotlin
// ✅ RE-ACTIVATED (proven working 2026-05-12)
val jobAttempts = listOf(
    JobAttempt("KUmsAllCamt", fromDate),  // ✅ FIRST - proven working
    JobAttempt("KUmsZeitSEPA", fromDate),
    JobAttempt("KUmsAll"),
    JobAttempt("KUmsNew"),
)
```

**Job-Sequenz:** KUmsAllCamt → KUmsZeitSEPA → KUmsAll → KUmsNew

### Warum CustomCamtParser funktioniert
1. **SAX-Exception wird gefangen** (hbci4java JAXB-Parser schlägt fehl)
2. **Fallback auf CustomCamtParser** (FintsService.kt:915)
3. **HbciCamtPatcher** repariert ungültiges XML
4. **CustomCamtParser** extrahiert Transaktionen erfolgreich
5. **150 TXs am 2026-05-12 beweisen:** Es funktioniert!

## Änderungen

### Code
- ✅ `FintsService.kt:316-323` - KUmsAllCamt als PRIMARY Job re-aktiviert
- ✅ `app/build.gradle.kts:29` - versionName: "1.0.43" → "1.0.44"

### Dokumentation
- ✅ `TEST-SCRIPTS-INVENTORY.md` - Test-Scripts Inventar erstellt
- ✅ `AGENTS.md` - HBCI-Version-Dokumentation korrigiert (220 → 300 für BBBank)
- ✅ `BBBank-Sync-Troubleshooting.md` - Widersprüche beseitigt
- ✅ `TODO.md` - Hybrid-Ansatz für Bank-Sync Fix dokumentiert

## Build-Status

**FAILED:** Compilation errors (VirtualAccount-Code unvollständig)

**Fehler:**
1. `TransactionDetailFragment.kt:233` - Type inference failure
2. `VirtualAccountPickerAdapter.kt:32` - Unresolved reference: bindingAdapterPosition
3. `VirtualAccountPickerBottomSheet.kt:14,26,58` - Unresolved reference: VirtualAccountWithBalance

**TODO:** VirtualAccount-Code entfernen oder vervollständigen (Feature nicht fertig in v1.0.43)

## Deployment

- [ ] Build erfolgreich
- [ ] APK deployed zu NAS: `\\secure-storage\home\Downloads\MyBudgets\MyBudgets-1.0.44.apk`
- [ ] Device-Test: Bank-Sync funktioniert
- [ ] Logs validieren: KUmsAllCamt erfolgreich

## Related Issues

- logs-app15.txt - Fehler v1.0.43 (2026-05-15)
- logs-app16.txt - Erfolg v1.0.42 (2026-05-12)
- Commit 126b71b - TEST-SCRIPTS-INVENTORY.md
- Commit 55478ce - TODO.md mit Hybrid-Ansatz

---

**Lessons Learned:**
- NIEMALS auf lokale Java-Sync-Scripts verlassen (können veraltet sein)
- IMMER Device-Logs analysieren (logs-app*.txt = Ground Truth)
- Dokumentation kann veraltet sein → Logs haben Beweiskraft
