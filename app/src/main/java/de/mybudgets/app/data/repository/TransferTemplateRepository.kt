package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.TransferTemplateDao
import de.mybudgets.app.data.model.TransferTemplate
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferTemplateRepository @Inject constructor(
    private val dao: TransferTemplateDao
) {
    fun observeAll(): Flow<List<TransferTemplate>> = dao.observeAll()

    suspend fun getById(id: Long): TransferTemplate? = dao.getById(id)

    suspend fun save(template: TransferTemplate): Long =
        if (template.id == 0L) dao.insert(template)
        else { dao.update(template); template.id }

    suspend fun delete(template: TransferTemplate) = dao.delete(template)
}
