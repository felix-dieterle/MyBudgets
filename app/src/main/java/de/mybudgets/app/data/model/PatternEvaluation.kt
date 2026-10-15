package de.mybudgets.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pattern_evaluations",
    indices = [Index(value = ["patternKey", "matcherVersion"], unique = true)]
)
data class PatternEvaluation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patternKey: String,
    val matcherVersion: String,
    val importance: Int = 0,
    val cuttability: Int = 0,
    val note: String = "",
    val categoryOverrideId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
