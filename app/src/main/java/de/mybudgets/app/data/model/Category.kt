package de.mybudgets.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentCategoryId: Long? = null,
    val color: Int = 0xFF9E9E9E.toInt(),
    val icon: String = "ic_category",
    /** Regex pattern applied to transaction description for auto-matching */
    val pattern: String = "",
    val level: Int = 1,
    val isDefault: Boolean = false
)
