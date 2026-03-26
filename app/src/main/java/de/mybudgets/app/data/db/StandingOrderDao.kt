package de.mybudgets.app.data.db

import androidx.room.*
import de.mybudgets.app.data.model.StandingOrder
import kotlinx.coroutines.flow.Flow

@Dao
interface StandingOrderDao {

    @Query("SELECT * FROM standing_orders ORDER BY nextExecutionDate ASC")
    fun observeAll(): Flow<List<StandingOrder>>

    @Query("SELECT * FROM standing_orders WHERE sourceAccountId = :accountId ORDER BY nextExecutionDate ASC")
    fun observeByAccount(accountId: Long): Flow<List<StandingOrder>>

    @Query("SELECT * FROM standing_orders WHERE isActive = 1 AND nextExecutionDate <= :now ORDER BY nextExecutionDate ASC")
    suspend fun getDueOrders(now: Long): List<StandingOrder>

    @Query("SELECT * FROM standing_orders WHERE id = :id")
    suspend fun getById(id: Long): StandingOrder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: StandingOrder): Long

    @Update
    suspend fun update(order: StandingOrder)

    @Delete
    suspend fun delete(order: StandingOrder)
}
