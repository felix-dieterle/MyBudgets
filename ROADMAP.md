# MyBudgets – Development Roadmap

**Last Updated:** 2026-05-17  
**Source:** Nutzer-Interview (features/Interview-2026-05-17.md)

---

## Vision

Budget-App als **Frühwarnsystem + Entscheidungshilfe:**
- **"Bin ich mit meinen Zielen im Track?"** – auf einen Blick
- **Glättung** – unregelmäßige Kosten werden monatlich vorgehalten (virtuelle Töpfe)
- **Kontrolle** – verfügbarer Betrag auf Hauptkonto ist echt (nach Abzug aller Töpfe)
- **Prognose** – 3 Monate voraus, Hard Facts (Regeln) + weiche Indikatoren (Kategorie-ø)

---

## Phase 1 – Foundation ✅ (Done)

| Task | Status |
|------|--------|
| RecurringRule Entity + DAO + Repository | ✅ |
| DB-Migration v10→v11 | ✅ |
| Hilt-wired (DatabaseModule) | ✅ |
| RecurringPatternDialog → Rule-Editor | ✅ |
| Post-Sync Matching (Keyword + Amount) | ✅ |
| Advanced Filters (Date + Amount Range) | ✅ |
| RecurringRule Management UI (Liste + Toggle + Delete) | ✅ |
| RecurringPatternDetector (intervalDays, confidence, reasoning) | ✅ |
| Chart Swipe (ViewPager + PageIndicator) | ✅ |
| Saldo aus CAMT extrahiert | ✅ |
| Loading-Indicator + Sync Phasen-Text | ✅ |

---

## Phase 1b – RecurringRule Refinement (⬅️ Next)

**Ziel:** Matching zuverlässig machen für reale Szenarien

| Task | Prio |
|------|------|
| `matchIban: String?` zu RecurringRule | High |
| `matchAmountTolerance: Double?` (±Toleranz für Betragsabweichungen) | High |
| DB-Migration v11→v12 | High |
| Dialog: IBAN-Feld + Toleranz-Eingabe | High |
| Match-Logik in AccountViewModel: IBAN + Toleranz auswerten | High |
| Matching-Prio: IBAN+Keyword+Amount > IBAN+Keyword > Keyword+Amount > Keyword | Medium |

---

## Phase 2 – Envelope Budgeting / Virtuelle Töpfe

**Ziel:** Unregelmäßige Zahlungen glätten, verfügbaren Betrag korrekt anzeigen

| Task | Prio |
|------|------|
| RecurringRule erhält `targetVirtualAccountId: Long?` + `monthlyFundingAmount: Double?` | High |
| DB-Migration + DAO-Update | High |
| Sync-Integration: Virtuelle Überweisung monatlich automatisch erzeugen | High |
| Sync-Matching: Reale Buchung matched auf Rule → virtuelles Konto belasten (keine reale Buchung verändern) | High |
| Verfügbarer Betrag = realBalance - Summe(virtuelle Belastungen) | High |
| Dashboard/Account-Detail zeigt verfügbaren Betrag | Medium |
| Virtuelles Konto im Minus = sanfte Warnung | Medium |

---

## Phase 3 – Dashboard als Kontrollzentrum

**Ziel:** Frühwarnsystem, nicht nur Statistik

| Task | Prio |
|------|------|
| 3-Monats-Forecast aus Rules + virtuellen Überweisungen | High |
| Warnungs-Stufen (Info/Gelb → Warnung/Orange → Kritisch/Rot) | High |
| Kategorie-Durchschnitte (letzte 3-6 Monate) als weiche Prognose | Medium |
| Trend-Indikator pro Kategorie (MoM/YoY) | Medium |
| "Bin ich im Track?" – Gesamtindikator auf Dashboard | High |

---

## Phase 4 – Polish

| Task | Prio |
|------|------|
| RecurringRule Edit-Screen (nicht nur Deactivate) | Medium |
| Interval-Labels in UI (vierteljährlich, halbjährlich) | Low |
| Einnahmen-Überschuss automatisch verteilen (optional, sehr low) | Low |

---

## Out of Scope (vorerst)

- Multi-Währung
- Cloud-Sync / Multi-Device
- Beleg-Scan (OCR)
- Investment-Tracking
- Shared Budgets (Multi-User)
- Saisonale Muster auf Einzeltransaktion-Ebene (ML-Overkill)
- Kategorie-Budget-Limits (monatliche Grenzen pro Kategorie)
- Drag & Drop Kategorie-Hierarchie (CategoryTreeAdapter)

Diese Themen sind in `ROADMAP-2026-05-12-archived.md` dokumentiert und können später wieder aufgegriffen werden.

---

## Known Issues & Technical Debt

### Critical (vor jeder Phase prüfen)
| Issue | Status | Location |
|-------|--------|----------|
| BBBank-Sync: Passport expired / Netzwerk-Fehler | Unclear – muss auf Device getestet werden | `FintsService.kt` + Config siehe `BBBank-Sync-Troubleshooting.md` |
| `NoSuchFieldException: flatData` in CAMT-Injection | Nicht kritisch – TX-Extraktion funktioniert trotzdem | `CamtExtractionHelper.injectTransactions` |

### Phase-gebundene Bugs
| Phase | Issue | Prio | Location |
|-------|-------|------|----------|
| 1b | `matchAmount` ohne Toleranz zu starr – wird in Phase 1b behoben | High | `RecurringRule`, `AccountViewModel` |
| 4 | Chart Long-Press: Drill-Down statt Custom-Select | Low | `DashboardFragment.kt:330-347` (GestureDetector) |

### Technical Debt (fortlaufend)
| Task | Prio | Location |
|------|------|----------|
| Index auf `CategoryPattern.categoryId` fehlt (DB-Warning bei Build) | Low | `AppDatabase.kt` |
| Veraltete hbci4java API-Calls (deprecated) ersetzen | Low | `FintsService.kt:151,153,192,201,307,393` |
| `@OptIn(ExperimentalCoroutinesApi::class)` Annotationen ergänzen | Low | ViewModels (flatMapLatest, etc.) |
| Unbenutzte Lambda-Parameter zu `_` umbenennen | Low | diverse |

---

## Build & Deploy

- Jedes Feature → Commit → `.\gradlew.bat assembleDebug --no-daemon`
- APK nach NAS + mama-razzi (manuell nach Feature-Set)
- Version: `v<versionName>-<versionCode>-<timestamp>.apk`

---

## Alte Dokumente (archiviert)

- `ROADMAP-2026-05-12-archived.md` – Alte Roadmap vor Nutzer-Interview
- `TODO-2026-05-15-archived.md` – Alter TODO vor Neuausrichtung
