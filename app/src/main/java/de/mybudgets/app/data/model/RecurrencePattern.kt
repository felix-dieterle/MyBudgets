package de.mybudgets.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User-defined recurrence pattern for marking transactions as recurring.
 * 
 * A transaction matches this pattern if ALL specified criteria match:
 * - keywords: Any keyword found in description (case-insensitive, OR logic)
 * - targetIban: Exact match with recipient IBAN
 * - amountMin/amountMax: Amount within range
 * - intervalDays: Expected interval between occurrences
 */
@Entity(tableName = "recurrence_patterns")
data class RecurrencePattern(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, // User-friendly name (e.g. "Spotify Subscription")
    val keywords: String? = null, // Comma-separated keywords (e.g. "SPOTIFY,Streaming")
    val targetIban: String? = null, // Exact IBAN match
    val amountMin: Double? = null, // Min amount (inclusive)
    val amountMax: Double? = null, // Max amount (inclusive)
    val intervalDays: Int? = null, // Expected interval (for validation, not matching)
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long? = null
)
