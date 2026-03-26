package de.mybudgets.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val virtualAccountId: Long? = null,
    val amount: Double,
    val description: String = "",
    val date: Long = System.currentTimeMillis(),
    val type: TransactionType,
    val categoryId: Long? = null,
    val note: String = "",
    val isRecurring: Boolean = false,
    val recurringIntervalDays: Int = 0,
    val remoteId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
