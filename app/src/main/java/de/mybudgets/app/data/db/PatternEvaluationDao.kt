package de.mybudgets.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.mybudgets.app.data.model.PatternEvaluation
import kotlinx.coroutines.flow.Flow

@Dao
interface PatternEvaluationDao {
    @Query("SELECT * FROM pattern_evaluations WHERE matcherVersion = :version")
    fun observeAll(version: String): Flow<List<PatternEvaluation>>

    @Query("SELECT * FROM pattern_evaluations WHERE matcherVersion = :version")
    suspend fun getAll(version: String): List<PatternEvaluation>

    @Query("SELECT * FROM pattern_evaluations WHERE patternKey = :key AND matcherVersion = :version LIMIT 1")
    suspend fun get(key: String, version: String): PatternEvaluation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(evaluation: PatternEvaluation): Long

    @Query("DELETE FROM pattern_evaluations WHERE patternKey = :key AND matcherVersion = :version")
    suspend fun delete(key: String, version: String)
}
