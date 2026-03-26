package de.mybudgets.app.data.api.dto

data class TransactionDto(
    val id: Long = 0,
    val accountId: Long = 0,
    val virtualAccountId: Long? = null,
    val amount: Double = 0.0,
    val description: String = "",
    val date: Long = 0,
    val type: String = "EXPENSE",
    val categoryId: Long? = null,
    val note: String = "",
    val isRecurring: Boolean = false,
    val recurringIntervalDays: Int = 0,
    val remoteId: String? = null,
    val createdAt: Long = 0
)
