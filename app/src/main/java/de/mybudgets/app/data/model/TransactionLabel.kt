package de.mybudgets.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "transaction_labels",
    primaryKeys = ["transactionId", "labelId"],
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Label::class,
            parentColumns = ["id"],
            childColumns = ["labelId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TransactionLabel(
    val transactionId: Long,
    val labelId: Long
)
