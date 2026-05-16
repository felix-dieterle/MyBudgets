package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.RecurringRuleDao
import de.mybudgets.app.data.model.RecurringRule
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringRuleRepository @Inject constructor(
    private val dao: RecurringRuleDao
) {
    fun observeActive(): Flow<List<RecurringRule>> = dao.observeActive()
    fun observeAll(): Flow<List<RecurringRule>> = dao.observeAll()
    suspend fun getActive(): List<RecurringRule> = dao.getActive()
    suspend fun getById(id: Long): RecurringRule? = dao.getById(id)
    suspend fun save(rule: RecurringRule): Long = if (rule.id == 0L) dao.insert(rule) else { dao.update(rule); rule.id }
    suspend fun deactivate(id: Long) = dao.deactivate(id)
    suspend fun delete(rule: RecurringRule) = dao.delete(rule)
}
