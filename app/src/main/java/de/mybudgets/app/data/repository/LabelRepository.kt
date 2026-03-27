package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.LabelDao
import de.mybudgets.app.data.model.Label
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LabelRepository @Inject constructor(
    private val dao: LabelDao
) {
    fun observeAll(): Flow<List<Label>> = dao.observeAll()

    suspend fun save(label: Label): Long =
        if (label.id == 0L) dao.insert(label) else { dao.update(label); label.id }

    suspend fun delete(label: Label) = dao.delete(label)

    /**
     * Removes duplicate label rows that share the same name, keeping the one with
     * the lowest id for each name. Safe to call on every app start.
     */
    suspend fun deduplicateByName() = dao.deleteDuplicates()
}
