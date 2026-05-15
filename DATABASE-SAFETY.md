# Database Safety & Migration Guide

## Aktuelle Sicherheitsmaßnahmen (Stand v1.0.13)

### ✅ Implementiert

1. **Vollständige Migrations-Kette (1→9)**
   - Alle Migrationen vorhanden in `AppDatabase.kt`
   - Kein Sprung möglich ohne Datenverlust
   
2. **Destructive Migration verboten**
   - `fallbackToDestructiveMigration()` explizit auskommentiert
   - Kommentar warnt vor Datenverlust
   
3. **DatabaseBackupHelper**
   - Manuelle Backups möglich
   - Automatisches Cleanup (max 5 Backups)
   - Restore-Funktion vorhanden
   - **Status:** Implementiert, aber noch nicht UI-integriert

### ⚠️ Noch nicht implementiert

1. **Automatisches Backup vor Migration**
   - Room bietet keinen Pre-Migration-Hook
   - Backup muss manuell vom User getriggert werden
   
2. **Export/Import als Sicherheitsnetz**
   - CSV/JSON Export aller Daten
   - Import bei Datenverlust
   
3. **Migration-Tests**
   - Keine automatischen Tests für Migrationen
   - Manuelles Testing bei jedem Schema-Change

## Zukünftige Migrationen: Best Practices

### Workflow für neue Migrationen

1. **Vor Entwicklung:**
   - Release-Notes: "Bitte vor Update Backup erstellen"
   - In App: Dialog zeigen "Update verfügbar, Backup empfohlen"

2. **Migration schreiben:**
   ```kotlin
   val MIGRATION_9_10 = object : Migration(9, 10) {
       override fun migrate(db: SupportSQLiteDatabase) {
           // IMMER mit IF NOT EXISTS arbeiten
           db.execSQL("ALTER TABLE transactions ADD COLUMN newField TEXT DEFAULT ''")
       }
   }
   ```

3. **Testen:**
   - App mit DB v9 installieren
   - Daten hinzufügen (Konten, Transaktionen)
   - Update auf v10 installieren
   - Prüfen: Alle Daten noch da?

4. **Rollback-Plan:**
   - Wenn Migration fehlschlägt → `DatabaseBackupHelper.restoreFromBackup()`
   - User muss App neu installieren + Backup restore

### Migration Patterns (Safe)

**✅ Safe (idempotent):**
```kotlin
// Neue Spalte mit DEFAULT
db.execSQL("ALTER TABLE transactions ADD COLUMN categoryId INTEGER DEFAULT NULL")

// Index mit IF NOT EXISTS
db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_category ON transactions(categoryId)")

// Neue Tabelle mit IF NOT EXISTS
db.execSQL("CREATE TABLE IF NOT EXISTS categories (...)")
```

**❌ Unsafe (nicht idempotent):**
```kotlin
// Ohne IF NOT EXISTS → Crash bei Re-Run
db.execSQL("CREATE INDEX idx_tx_category ON transactions(categoryId)")

// DROP ohne Check
db.execSQL("DROP TABLE old_table")  // Wenn nicht existiert → Crash

// RENAME ohne Fallback
db.execSQL("ALTER TABLE old_name RENAME TO new_name")
```

## Empfohlene Next Steps (Priorität)

### 🔴 High Priority

1. **Settings: "Backup erstellen" Button**
   - Ruft `DatabaseBackupHelper.createManualBackup()` auf
   - Zeigt Pfad an: `/data/data/de.mybudgets.app/files/db_backups/`
   - User kann per ADB ziehen: `adb pull /data/data/de.mybudgets.app/files/db_backups/`

2. **Settings: "Backup wiederherstellen"**
   - Liste aller Backups (`DatabaseBackupHelper.listBackups()`)
   - Warnung: "App wird geschlossen, Backup wird restored"
   - Nach Restore: App neu starten

3. **Export/Import (CSV oder JSON)**
   - Alle Transaktionen, Konten, Kategorien exportieren
   - Speichern auf `/sdcard/Download/MyBudgets-export-<timestamp>.json`
   - Bei Datenverlust: Import aus JSON

### 🟡 Medium Priority

4. **Update-Dialog mit Backup-Reminder**
   - Beim App-Start nach Update: "Neue Version installiert, Backup empfohlen?"
   - Button "Jetzt Backup erstellen"

5. **Migration Error Handling**
   - `try/catch` um Migrations
   - Bei Fehler: Log schreiben, User informieren
   - **Problem:** Room macht Migration intern, schwer zu catchen

### 🟢 Low Priority

6. **Automated Migration Tests**
   - Instrumentierte Tests mit echter DB
   - Test: DB v9 → Migrate → v10 → Prüfe Daten
   - Aufwendig, aber Gold-Standard

## Migration History Log

| Version | Migration | Changes | Date | Notes |
|---------|-----------|---------|------|-------|
| v9 | 8→9 | Added indices for categories/transactions | 2026-05-12 | Performance optimization |
| v8 | 7→8 | Added `categories` table | 2026-04-14 | Initial category support |
| v7 | 6→7 | Added `virtualAccountId` to transactions | - | Virtual accounts |
| v6 | 5→6 | Added `pattern` to accounts | - | Auto-matching |
| v5 | 4→5 | Added gamification badges | - | Badges system |
| v4 | 3→4 | Added `labels` and `transaction_labels` | - | Tagging system |
| v3 | 2→3 | Added `transfer_templates` | - | Quick transfers |
| v2 | 1→2 | Added `standing_orders` | - | Recurring payments |
| v1 | - | Initial schema | - | Base tables |

## Lessons Learned

### Data Loss Incident (v1.0.12)

**Problem:** User hatte DB v8, installierte v1.0.12 → Migration fehlte → DB dropped

**Root Cause:**
- User hatte sehr alte App-Version (möglicherweise v7 oder älter)
- v1.0.12 hatte nur MIGRATION_8_9, nicht 7→8
- Room findet keinen Path von v7→v9 → Drop DB

**Fix:**
- Alle Migrationen 1→9 hinzugefügt
- Kommentar "NEVER fallbackToDestructiveMigration"
- Backup-Helper erstellt

**Prevention:**
- Immer ALLE Migrationen behalten
- Niemals alte Migrationen löschen
- Backup vor Major Updates

---

**Status:** Grundlagen vorhanden, UI-Integration fehlt noch.
**Ziel:** v1.1.0 mit Backup/Restore UI in Settings.
