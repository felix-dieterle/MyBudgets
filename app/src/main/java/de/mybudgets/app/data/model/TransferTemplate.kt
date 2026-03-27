package de.mybudgets.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_templates")
data class TransferTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceAccountId: Long,
    val recipientName: String,
    val recipientIban: String,
    val recipientBic: String = "",
    val amount: Double,
    val purpose: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
