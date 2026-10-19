package de.mybudgets.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein Eintrag im Sync-Protokoll: eine Phase eines Sync-Laufs (z. B. Upload der Buchungen).
 *
 * [runId] gruppiert alle Phasen desselben Laufs (Startzeit des Laufs in Millisekunden),
 * [direction] ist "PULL", "PUSH", "BOTH" oder "NONE", [area] das Klartext-Etikett
 * ("Buchungen", "Konten", ...). [itemCount] ist die Zahl der bewegten Datensaetze.
 */
@Entity(tableName = "sync_log", indices = [Index(value = ["timestamp"])])
data class SyncLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val runId: Long,
    val phase: String,
    val direction: String,
    val area: String,
    val itemCount: Int,
    val success: Boolean,
    val message: String = ""
)
