package de.mybudgets.app.data.repository

import de.mybudgets.app.data.db.${FEATURE_PASCAL}Dao
import de.mybudgets.app.data.model.${FEATURE_PASCAL}
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "${FEATURE_PASCAL}Repository"

/**
 * Repository for ${FEATURE} data access.
 *
 * **Responsibilities:**
 * - Abstraction layer between ViewModel and DAO
 * - Business logic (save vs insert/update decision)
 * - Error handling and logging
 * - Database transactions for multi-step operations
 *
 * **Layer Architecture:**
 * ```
 * Fragment → ViewModel → Repository → DAO → Database
 * ```
 *
 * **Key Patterns:**
 * 1. save(): Conditional insert/update (return ID in both cases)
 * 2. observeAll(): Flow for reactive updates
 * 3. withTransaction: Multi-step atomic operations
 * 4. AppLogger: All logging via AppLogger (not android.util.Log)
 *
 * **Testing:**
 * - Mock DAO in tests
 * - Verify correct DAO method called (insert vs update)
 * - No UI testing (Repository is pure business logic)
 */
@Singleton
class ${FEATURE_PASCAL}Repository @Inject constructor(
    private val dao: ${FEATURE_PASCAL}Dao
) {
    
    /**
     * Observe all items as Flow (reactive updates).
     *
     * **Usage in ViewModel:**
     * ```
     * viewModel.items = repository.observeAll().stateIn(...)
     * ```
     *
     * **Fragment Collection:**
     * ```
     * viewModel.items.collect { items ->
     *     adapter.submitList(items)
     * }
     * ```
     *
     * @return Flow that emits whenever database changes
     */
    fun observeAll(): Flow<List<${FEATURE_PASCAL}>> {
        AppLogger.d(TAG, "observeAll() called")
        return dao.observeAll()
    }

    /**
     * Fetch all items as single snapshot (not flow).
     *
     * **Usage:**
     * - One-time load (not reactive)
     * - Initial load in ViewModel
     * - Testing
     *
     * @return List of all items
     * @throws Exception if database operation fails
     */
    suspend fun fetch${FEATURE_PASCAL}(): List<${FEATURE_PASCAL}> {
        AppLogger.d(TAG, "fetch${FEATURE_PASCAL}() called")
        return try {
            val items = dao.getAll()
            AppLogger.i(TAG, "fetch${FEATURE_PASCAL}() success - ${items.size} items")
            items
        } catch (e: Exception) {
            AppLogger.e(TAG, "fetch${FEATURE_PASCAL}() failed", e)
            throw e
        }
    }

    /**
     * Get single item by ID.
     *
     * @param id Item ID
     * @return Item or null if not found
     */
    suspend fun getById(id: Long): ${FEATURE_PASCAL}}? {
        AppLogger.d(TAG, "getById($id) called")
        return try {
            dao.getById(id).also { item ->
                if (item != null) {
                    AppLogger.d(TAG, "getById($id) found")
                } else {
                    AppLogger.w(TAG, "getById($id) not found")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getById($id) failed", e)
            throw e
        }
    }

    /**
     * Save item (insert new or update existing).
     *
     * **Pattern:**
     * - If id == 0L: Insert new item (return generated ID)
     * - If id != 0L: Update existing item (return same ID)
     *
     * **Usage:**
     * ```
     * val item = ${FEATURE_PASCAL}(id = 0L, name = "New Item")
     * val newId = repository.save(item)  // Returns generated ID
     * 
     * // Later...
     * val updated = item.copy(name = "Updated")
     * val sameId = repository.save(updated)  // Returns item.id
     * ```
     *
     * **Why This Pattern?**
     * ✅ Single method (caller doesn't decide insert vs update)
     * ✅ Returns ID in both cases (can use immediately)
     * ✅ Logic centralized (not scattered across app)
     * ✅ Testable (mock insert and update paths)
     *
     * @param item Item to save
     * @return ID (generated for new, same for update)
     * @throws Exception if database operation fails
     */
    suspend fun save(item: ${FEATURE_PASCAL}): Long {
        AppLogger.d(TAG, "save() called - id=${item.id}")
        
        return try {
            val result = if (item.id == 0L) {
                // New item: insert
                dao.insert(item).also { newId ->
                    AppLogger.i(TAG, "save() inserted - new id=$newId")
                }
            } else {
                // Existing item: update
                dao.update(item)
                AppLogger.i(TAG, "save() updated - id=${item.id}")
                item.id
            }
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "save() failed", e)
            throw e
        }
    }

    /**
     * Delete single item.
     *
     * @param id Item ID to delete
     * @return Number of rows deleted (1 if found, 0 if not found)
     * @throws Exception if database operation fails
     */
    suspend fun delete(id: Long): Int {
        AppLogger.d(TAG, "delete($id) called")
        return try {
            val deleted = dao.deleteById(id)
            AppLogger.i(TAG, "delete($id) deleted $deleted rows")
            deleted
        } catch (e: Exception) {
            AppLogger.e(TAG, "delete($id) failed", e)
            throw e
        }
    }

    /**
     * Delete multiple items.
     *
     * @param ids List of item IDs to delete
     * @return Number of rows deleted
     * @throws Exception if database operation fails
     */
    suspend fun deleteAll(ids: List<Long>): Int {
        AppLogger.d(TAG, "deleteAll(${ids.size} items) called")
        return try {
            val deleted = dao.deleteByIds(ids)
            AppLogger.i(TAG, "deleteAll() deleted $deleted rows")
            deleted
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteAll() failed", e)
            throw e
        }
    }

    // TODO: Add repository-specific methods
    // Example patterns:
    
    /**
     * Example: Search with filtering.
     *
     * @param query Search query
     * @return Matching items
     */
    suspend fun search(query: String): List<${FEATURE_PASCAL}> {
        AppLogger.d(TAG, "search('$query') called")
        return dao.getAll().filter { item ->
            // TODO: Implement search logic
            // item.name.contains(query, ignoreCase = true)
            false
        }
    }

    /**
     * Example: Bulk operation with transaction.
     *
     * **Important:** Use database.withTransaction { } for multi-step operations
     * to ensure atomicity (all-or-nothing).
     *
     * @param items List of items to save
     */
    suspend fun saveAll(items: List<${FEATURE_PASCAL}>) {
        AppLogger.d(TAG, "saveAll(${items.size} items) called")
        // TODO: Use database.withTransaction if multiple operations
        // database.withTransaction {
        //     items.forEach { save(it) }
        // }
    }
}
