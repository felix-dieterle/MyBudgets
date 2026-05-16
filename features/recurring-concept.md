# Recurring Transactions – Concept

## 1. Current State

### Datenmodell

**Transaction** (Entity) hat zwei recurring-Felder:
- `isRecurring: Boolean` – Flag ob diese Buchung als wiederkehrend markiert ist
- `recurringIntervalDays: Int` – Vermuteter Abstand (z.B. 30 für monatlich)

**CategoryPattern** (separate Entity, existiert bereits):
- Verknüpft `patternValue` (IBAN, Text, Hybrid) mit einer `categoryId`
- Dient aktuell der **Auto-Kategorisierung** (nicht für Recurring-Erkennung)

### Erkennung (RecurringPatternDetector)

- Nach jedem Sync wird geprüft: Gibt es ≥3 Buchungen mit ähnlichem Betrag (±5%) in regelmäßigen Abständen (±3 Tage)?
- Gefundene Patterns werden im **RecurringPatternDialog** angezeigt
- Der User kann Patterns auswählen und "Übernehmen" → setzt `isRecurring = true` + `recurringIntervalDays`

### Status Quo Probleme

1. **Keine persistierten Regeln** – Nach "Übernehmen" existiert nur ein Flag auf einzelnen Transactions. Ein neuer Sync erkennt keine Verbindung zu bereits markierten Buchungen.
2. **Keine automatische Zuordnung** – Neue Sync-Transaktionen werden nicht gegen existierende RecurringRules geprüft
3. **Recurring und CategoryPattern sind entkoppelt** – Eigentlich gehören sie zusammen
4. **TransactionDetail-Recurring-Edit** – Ergibt keinen Sinn: das Intervall einer einzelnen Buchung zu ändern hilft nicht, das Pattern zu definieren

---

## 2. Gewünschtes Konzept (Vorschlag)

### RecurringRule (neue Entity)

```kotlin
@Entity(tableName = "recurring_rules")
data class RecurringRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                          // "Netflix", "Miete", ...
    val matchKeyword: String,                  // Text-Filter für description
    val matchAmount: Double? = null,           // optional: fester Betrag
    val intervalDays: Int,                     // 30, 90, 365, ...
    val categoryId: Long? = null,              // Auto-Kategorie (optional)
    val accountId: Long? = null,               // optional: nur auf einem Konto
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
```

### Erkennung wird zweistufig

1. **Sync-Time Detection** – Neue Transaktionen werden gegen aktive `RecurringRule`s gematcht (description enthält keyword + Betrag passt + Intervall passt)
   - Match → Transaction erhält `isRecurring = true`, optional `categoryId`
2. **Post-Sync Pattern Detector** – `RecurringPatternDetector` schlägt neue Regeln vor, wenn ≥3 Buchungen ein Muster ergeben aber noch keine Regel existiert

### Dialog

Der RecurringPatternDialog wird zum **Regel-Editor**:
- Erkannter Vorschlag → editable Keyword/Words/Vorschläge + optional IBAN aus TX → `RecurringRule` speichern
- Kategorie-Verknüpfung im gleichen Dialog
- Nach Speichern: Rule lebt und matched bei jedem zukünftigen Sync

---

## 3. Offene Fragen

- Soll eine RecurringRule auch **IBAN-basiert** matchen können (zusätzlich zu description-Keywords)?
- Soll der Dialog direkt eine Kategorie vorschlagen können (basierend auf CategoryPattern)?
- UI: Wo werden RecurringRules verwaltet? (Separater Screen? Im Account-Detail?)
- Was passiert wenn eine Regel matched aber der Betrag abweicht?
