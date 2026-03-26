package de.mybudgets.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "standing_orders")
data class StandingOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceAccountId: Long,
    val recipientName: String,
    val recipientIban: String,
    val recipientBic: String = "",
    val amount: Double,
    val purpose: String = "",
    val intervalDays: Int = 30,
    val firstExecutionDate: Long,
    val lastExecutionDate: Long? = null,
    val nextExecutionDate: Long,
    val isActive: Boolean = true,
    val sentToBank: Boolean = false,
    val remoteId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
