package de.mybudgets.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "labels")
data class Label(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int = 0xFF607D8B.toInt()
)
