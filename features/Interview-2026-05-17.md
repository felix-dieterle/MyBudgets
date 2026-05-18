# Interview 2026-05-17: Nutzer-Ziele & Pain Points

## 1. Kern-Bedürfnis: Dashboard als Kontrollzentrum

"Bin ich mit meinen Zielen im Track?" – Die App muss auf einen Blick zeigen:
- **Gibt es einen Einbruch** im Verhältnis Ausgaben/Einnahmen?
- **Dringender Handlungsbedarf** in den nächsten 3 Monaten?
- **Prognose**: Was kommt auf mich zu, was bleibt übrig?

→ Kein reines Statistik-Tool, sondern **Frühwarnsystem + Entscheidungshilfe**

## 2. Kern-Features (abgeleitet)

### 2.1 Virtuelle Töpfe (Envelope Budgeting + Glättung)
**Problem:** Unregelmäßige Zahlungen (Steuer, Versicherung, Haushaltsgeräte) verzerren den monatlichen Cashflow.

**Lösung:**
- Pro "Topf" ein virtuelles Konto, auf das monatlich 1/n der erwarteten Jahreskosten fließt
- **Beispiel Steuer:** Vierteljährlich 1000€ → monatlich 333€ auf Steuer-Topf → bei realer Abbuchung automatisch vom Topf gedeckt
- **Beispiel Haushaltsgeräte:** 700€/Jahr erwartet → monatlich 58€ auf Haushaltsgeräte-Topf
- **Effekt:** Auf dem Hauptkonto bleibt der "wirklich verfügbare" Betrag sichtbar

**Kritische Abläufe:**
1. **Match-Logik** beim Sync: Reale Buchung erkennen → virtuelles Konto belasten
   - Matching über `matchKeyword` (Text) + `matchIban` (Empfänger)
   - `matchAmount` mit Toleranz-Range für Betragsabweichungen
   - Prio-Regel optional (Sidefeature)
2. **Topf-Befüllung:** Monatlich per virtuellem Dauerauftrag vom Hauptkonto auf Topf

### 2.2 Frühwarnsystem & Prognose

**Zwei Ebenen:**

**A. Hard Facts (regelbasiert) – Pflicht**
- Bekannte RecurringRules (Miete, Netflix, Steuer, Versicherung)
- Geplante virtuelle Überweisungen
- Forecast 3 Monate in die Zukunft, auf Basis dieser Regeln
- Warnung wenn Hauptkonto in den nächsten 3 Monaten ins Minus rutscht
- Harte Warnung (rot) bei Echtzeit-Überziehung

**B. Weiche Prognose (arithmetisch) – on top**
- **Kategorie-Durchschnitt** (letzte 3-6 Monate) als Füllwert für Lücken zwischen Regeln
  - Z.B. Lebensmittel 450€/Monat → nächster Monat ~450€
- **Trend-Indikator** als Beobachtung: "Lebensmittel +12% zum Vorjahresmonat"
  - Optional: Ausgaben-Trend nach Kategorie im Dashboard zeigen

**Kein ML:**
- Keine saisonalen Muster auf Einzeltransaktion-Ebene
- Keine Wetter-/Feiertags-Logik
- Keine Unterscheidung "einmalig" vs. "regelmäßig"

### 2.3 Budget/Spielraum auf Hauptkonto

**Aktuell:** Überschuss/Einnahme-Überschuss bleibt auf Hauptkonto.
**Low-Prio:** Automatische Verteilung nach Regel (z.B. 50% Urlaub, 30% Sparen, 20% bleibt).

## 3. RecurringRule – Refinements aus Interview

Aktuelle Entity um `matchIban` + Toleranz ergänzen:

```kotlin
val matchIban: String? = null                      // Optional: IBAN-basiertes Matching
val matchAmountTolerance: Double? = null            // Optional: ±Toleranz (z.B. 10.0 = ±10€)
```

**Intervall-Unterstützung (bereits vorhanden):**
- `intervalDays` als Integer erlaubt alle Werte: 30 (monatlich), 90 (vierteljährlich), 365 (jährlich), etc.
- `RecurringPatternDetector` erkennt bereits quarterly detection
- `intervalLabel()` formatiert korrekt "alle X Tage" / "alle X Monate"

**Match-Logik** (Reihenfolge absteigende Prio):
1. `matchIban` + `matchKeyword` + `matchAmount` (±Toleranz) → Volltreffer
2. `matchIban` + `matchKeyword` → Treffer
3. `matchKeyword` + `matchAmount` (±Toleranz) → Treffer
4. `matchKeyword` → Basismatch

## 4. Warnungs-Stufen

| Stufe | Beschreibung | UI |
|-------|-------------|-----|
| Info | Prognose 3 Monate voraus zeigt Minus | Gelber Hinweis |
| Warnung | <30 Tage vor erwartetem Minus | Orange, Handlungsdruck |
| Kritisch | Realkonto aktuell im Minus | Rot, harte Warnung |
| Info | Virtuelles Konto im Minus | Hinweis (keine harte Warnung) |

## 5. Offene Punkte / Später

- Einnahmen-Überschuss automatisch verteilen
- manuelle vs. automatische Kategorisierung von Sonderfällen
- Saisonale Muster lernen (aktuell nicht priorisiert)

---

## 6. Roadmap & Meilensteine

### Phase 1 – Foundation ✅ (Done)
| Task | Status |
|------|--------|
| RecurringRule Entity + DAO + Repository | ✅ |
| RecurringPatternDialog → Rule-Editor | ✅ |
| Post-Sync Matching | ✅ |
| Advanced Filters (Datum + Betrag) | ✅ |
| RecurringRule Management UI (Liste) | ✅ |

### Phase 1b – RecurringRule Refinement (⬅️ Next)
| Task | Priority |
|------|----------|
| `matchIban` + `matchAmountTolerance` zu Entity hinzufügen | High |
| DB-Migration v11→v12 + DAO-Update | High |
| Dialog um IBAN-Feld + Toleranz-Eingabe erweitern | High |
| Match-Logik (AccountViewModel) um IBAN + Toleranz verbessert | High |

### Phase 2 – Envelope Budgeting / Virtuelle Töpfe (Kern-Feature)
| Task | Prio |
|------|------|
| Virtuelles Konto "Funded" Konzept: RecurringRule bekommt `targetVirtualAccountId` + `monthlyFundingAmount` | High |
| Monatliche virtuelle Überweisung automatisch erzeugen (per Worker/beim Sync) | High |
| Matching beim Sync: Reale Buchung gegen Regel → virtuelles Konto belasten (keine reale Buchung verändern) | High |
| Verfügbares Saldo = realBalance - Summe(virtuelle Belastungen) | High |

### Phase 3 – Dashboard als Kontrollzentrum
| Task | Prio |
|------|------|
| 3-Monats-Forecast im Dashboard (aus Rules + virtuelle Überweisungen) | High |
| Warnungs-Stufen (Info/Gelb → Warnung/Orange → Kritisch/Rot) | High |
| Kategorie-Durchschnitte (letzte 3-6 Monate) als weiche Prognose | Medium |
| Trend-Indikator pro Kategorie (MoM/YoY) | Medium |
| "Verfügbarer Betrag" im Account-Detail / Dashboard | Medium |

### Phase 4 – Polish
| Task | Prio |
|------|------|
| RecurringRule Edit-Screen (nicht nur Deactivate) | Medium |
| Intervall-Labels in UI verbessern (vierteljährlich, halbjährlich) | Low |
| Einnahmen-Überschuss automatisch verteilen (optional) | Low |

---

**Erstellt aus Interview 2026-05-17**
