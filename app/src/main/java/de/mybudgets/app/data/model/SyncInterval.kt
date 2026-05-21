package de.mybudgets.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_intervals")
data class SyncInterval(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val startDate: Long,  // Älteste tatsächlich gelieferte TX
    val endDate: Long,    // Neueste tatsächlich gelieferte TX
    val isHistorical: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
