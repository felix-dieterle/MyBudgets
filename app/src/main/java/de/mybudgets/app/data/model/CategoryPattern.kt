package de.mybudgets.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

enum class PatternType {
    IBAN,      // Match by recipient IBAN
    TEXT,      // Match by keyword in usage/description
    HYBRID     // Match by IBAN + TEXT (highest confidence)
}

@Entity(
    tableName = "category_patterns",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CategoryPattern(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val patternType: String, // IBAN, TEXT, HYBRID
    val patternValue: String, // IBAN or text fragment, or "IBAN|TEXT" for HYBRID
    val confidence: Double = 0.7, // 0.0 - 1.0
    val usageCount: Int = 0,
    val lastUsed: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val matchedName: String = "",
    val amountMin: Double? = null,
    val amountMax: Double? = null,
    val filterIncome: Boolean? = null
)
