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

    suspend fun save(label: Label): Long {
        if (label.id != 0L) {
            dao.update(label)
            return label.id
        }
        // Insert with IGNORE conflict strategy: returns -1 if the name already exists.
        // Fall back to the existing row in that case (handles both the normal path and
        // any concurrent save calls for the same name).
        val newId = dao.insert(label)
        if (newId != -1L) return newId
        return dao.findByName(label.name)!!.id
    }

    suspend fun delete(label: Label) = dao.delete(label)

    /**
     * Removes duplicate label rows that share the same name, keeping the one with
     * the lowest id for each name. Safe to call on every app start.
     */
    suspend fun deduplicateByName() = dao.deleteDuplicates()
}
