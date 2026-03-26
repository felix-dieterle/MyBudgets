package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.StandingOrderDao
import de.mybudgets.app.data.model.StandingOrder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StandingOrderRepository @Inject constructor(
    private val dao: StandingOrderDao
) {
    fun observeAll(): Flow<List<StandingOrder>> = dao.observeAll()
    fun observeByAccount(accountId: Long): Flow<List<StandingOrder>> = dao.observeByAccount(accountId)

    suspend fun getById(id: Long): StandingOrder? = dao.getById(id)
    suspend fun getDueOrders(now: Long = System.currentTimeMillis()): List<StandingOrder> = dao.getDueOrders(now)

    suspend fun save(order: StandingOrder): Long =
        if (order.id == 0L) dao.insert(order)
        else { dao.update(order); order.id }

    suspend fun delete(order: StandingOrder) = dao.delete(order)
}
