# UI Features - Wiederherstellungs-Plan

**Status:** 2026-05-15 - Alle Features existieren als lokale untracked Files (nie committed)

## 1. Auto-Kategorisierung (CategoryPattern)

**Standort:** `app/src/main/java/de/mybudgets/app/data/`
**Dateien:**
- `data/model/CategoryPattern.kt` - Entity: `category_patterns`-Tabelle (Room, FK auf `categories`)
- `data/db/CategoryPatternDao.kt` - DAO: insert/update/delete/query by IBAN/text-match

**Fehlt für Compile:**
- [ ] `CategoryPatternDao` in `AppDatabase.kt` registrieren (`abstract fun categoryPatternDao()`)
- [ ] Migration `category_patterns` Tabelle erstellen
- [ ] PatternService/Matcher (Business-Logik) fehlt
- [ ] UI-Einbindung in AddEditTransactionFragment

## 2. Pattern-Picker UI

**Standort:** `app/src/main/java/de/mybudgets/app/ui/`
**Dateien:**
- `transactions/PatternPickerDialogFragment.kt` - Dialog zum Auswählen/Erstellen von Patterns
- `categories/CategoryPickerAdapter.kt` - RecyclerView Adapter für Category-Auswahl

**Layouts:**
- `res/layout/dialog_pattern_picker.xml`
- `res/layout/bottom_sheet_category_picker.xml`
- `res/layout/item_category_picker.xml`
- `res/layout/item_top_category.xml`

**Fehlt für Compile:**
- [ ] In Navigation graph registrieren
- [ ] OnPatternSelectedListener in AddEditTransactionFragment einbinden

## 3. Wiederkehrende Buchungen erkennen (RecurringPatternDetector)

**Standort:** `app/src/main/java/de/mybudgets/app/util/RecurringPatternDetector.kt`
**Status:** ✅ Code vollständig, eigenständig

**Layouts:**
- `res/layout/dialog_recurring_settings.xml`

**Fehlt für Compile:**
- [ ] Keine Abhängigkeiten - kann direkt verwendet werden
- [ ] Aber: Kein UI-Einstiegspunkt (keine ViewModel-/Fragment-Integration)
- [ ] RecurringDauerauftrag als Entity speichern fehlt

## 4. Kategorie-Verwaltung erweitert

**Layouts:**
- `res/layout/fragment_category_management.xml`

**Fehlt für Compile:**
- [ ] CategoryManagementFragment.kt fehlt
- [ ] Navigation einrichten

## 5. Virtuelle Konten - Picker UI

**Layouts:**
- `res/layout/fragment_virtual_account_picker.xml`
- `res/layout/item_virtual_account_picker.xml`

**Status:** Backend existiert in committed code (PR #73). Layouts sind lokal neu.
**Fehlt für Compile:**
- [ ] VirtualAccountPickerFragment.kt fehlt
- [ ] Navigation einrichten

## 6. Database-Backup

**Standort:** `app/src/main/java/de/mybudgets/app/util/DatabaseBackupHelper.kt`
**Status:** ✅ Code vollständig, eigenständig
**Fehlt für Compile:**
- [ ] Keine UI/Settings-Einbindung
- [ ] Kein Auto-Backup vor Migration aktiviert

## 7. UI Icons (Drawables)

**Dateien:**
- `res/drawable/ic_add.xml`
- `res/drawable/ic_arrow_back.xml`
- `res/drawable/ic_delete.xml`
- `res/drawable/ic_edit.xml`
- `res/drawable/ic_expand_more.xml`
- `res/drawable/ic_info.xml`
- `res/drawable/circle_shape.xml`

**Status:** ✅ Vector-Drawables, referenzierbar
**Hinweis:** Einige Icons existieren bereits in `ic_launcher_*.xml` im Projekt

## 8. Dokumentation

- `CHANGELOG-v1.0.44.md` - ✅ Fertig
- `DATABASE-SAFETY.md` - ✅ Fertig

---

## Priorisierung für Integration

### Phase A: Sofort einsetzbar (nur Files hinzufügen)
1. **DatabaseBackupHelper** - Keine Abhängigkeiten, reine Utility
2. **RecurringPatternDetector** - Keine Abhängigkeiten
3. **Drawables** - Nur Resource-Dateien

### Phase B: Compile-Fix erforderlich
4. **CategoryPattern** + **CategoryPatternDao** - Brauchen AppDatabase-Registrierung + Migration
5. **CategoryPickerAdapter** + **PatternPickerDialogFragment** + Layouts - Brauchen Navigation/ViewModel

### Phase C: Fragment + Logik fehlt
6. **CategoryManagement** - Fragment fehlt komplett
7. **VirtualAccountPicker** - Fragment fehlt komplett

### Phase D: Integration
8. Auto-Kategorisierung in AddEditTransactionFragment einbinden
9. Recurring-Dialog mit PatternDetector verknüpfen
10. Backup-Button in Settings einbauen

---

**Letzte Aktualisierung:** 2026-05-15
