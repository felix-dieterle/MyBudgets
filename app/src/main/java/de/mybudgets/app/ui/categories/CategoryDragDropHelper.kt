package de.mybudgets.app.ui.categories

import androidx.recyclerview.widget.ItemTouchHelper
import de.mybudgets.app.util.AppLogger
import androidx.recyclerview.widget.RecyclerView
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.DropResult

class CategoryDragDropHelper(
    private val adapter: CategoryAdapter,
    private val onValidate: (source: Category, target: Category?) -> DropResult,
    private val onDrop: (source: Category, target: Category?) -> Unit
) : ItemTouchHelper.Callback() {

    private var draggedItem: Category? = null
    private var targetItem: Category? = null
    private var lastValidationResult: DropResult? = null

    companion object {
        private const val TAG = "CategoryDragDrop"
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val sourcePos = viewHolder.adapterPosition
        val targetPos = target.adapterPosition

        AppLogger.e(TAG, "========== onMove CALLED ==========")
        AppLogger.e(TAG, "onMove: sourcePos=$sourcePos, targetPos=$targetPos")

        if (sourcePos == RecyclerView.NO_POSITION || targetPos == RecyclerView.NO_POSITION) {
            AppLogger.e(TAG, "onMove: INVALID POSITION (source=$sourcePos, target=$targetPos)")
            return false
        }

        val source = adapter.getCategoryAt(sourcePos)
        val targetCat = adapter.getCategoryAt(targetPos)

        AppLogger.e(TAG, "onMove: source=${source.name}(id=${source.id})")
        AppLogger.e(TAG, "onMove: target=${targetCat.name}(id=${targetCat.id})")

        draggedItem = source
        targetItem = targetCat

        // Validate drop
        val result = onValidate(source, targetCat)
        lastValidationResult = result

        AppLogger.e(TAG, "onMove: validation result=${result::class.simpleName}")
        when (result) {
            is DropResult.Valid -> AppLogger.e(TAG, "onMove: VALID - newLevel=${result.newLevel}")
            is DropResult.Warning -> AppLogger.e(TAG, "onMove: WARNING - ${result.message}")
            is DropResult.Invalid -> AppLogger.e(TAG, "onMove: INVALID - ${result.message}")
        }

        // Update visual feedback
        adapter.updateDropFeedback(sourcePos, targetPos, result)

        AppLogger.e(TAG, "========== onMove END ==========")
        // Don't allow visual reordering - we only show feedback
        return false
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)

        AppLogger.e(TAG, "========== onSelectedChanged CALLED ==========")
        AppLogger.e(TAG, "onSelectedChanged: actionState=$actionState (IDLE=0, DRAG=2)")

        when (actionState) {
            ItemTouchHelper.ACTION_STATE_DRAG -> {
                val pos = viewHolder?.adapterPosition ?: RecyclerView.NO_POSITION
                val cat = if (pos != RecyclerView.NO_POSITION) adapter.getCategoryAt(pos) else null
                AppLogger.e(TAG, "onSelectedChanged: DRAG START - ${cat?.name}, pos=$pos")

                // Mark dragging state (NO view manipulation, NO notify!)
                adapter.setDraggingState(true, pos)
                
                draggedItem = cat
            }
            ItemTouchHelper.ACTION_STATE_IDLE -> {
                AppLogger.e(TAG, "onSelectedChanged: DRAG END (IDLE)")
            }
        }
        AppLogger.e(TAG, "========== onSelectedChanged END ==========")
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        AppLogger.e(TAG, "========== clearView CALLED ==========")
        AppLogger.e(TAG, "clearView: Drag released")

        // Trigger drop action when user releases
        val source = draggedItem
        val target = targetItem
        val result = lastValidationResult

        AppLogger.e(TAG, "clearView: source=${source?.name}(id=${source?.id})")
        AppLogger.e(TAG, "clearView: target=${target?.name}(id=${target?.id})")
        AppLogger.e(TAG, "clearView: result=${result?.let { it::class.simpleName }}")

        // Only drop if target is different from source (user actually moved item)
        if (source != null && target != null && source.id != target.id && result != null) {
            when (result) {
                is DropResult.Valid, is DropResult.Warning -> {
                    AppLogger.e(TAG, "clearView: ✅ EXECUTING DROP - ${source.name} -> ${target.name}")
                    onDrop(source, target)
                }
                is DropResult.Invalid -> {
                    AppLogger.e(TAG, "clearView: ❌ DROP INVALID, not executing")
                }
            }
        } else {
            AppLogger.e(TAG, "clearView: ⚠️ NO DROP (source==target or missing data)")
            AppLogger.e(TAG, "clearView:   source!=null: ${source != null}")
            AppLogger.e(TAG, "clearView:   target!=null: ${target != null}")
            AppLogger.e(TAG, "clearView:   source.id != target.id: ${source?.id != target?.id}")
            AppLogger.e(TAG, "clearView:   result!=null: ${result != null}")
        }

        // Clear drag state (will trigger UI refresh)
        adapter.setDraggingState(false)
        draggedItem = null
        targetItem = null
        lastValidationResult = null
        
        AppLogger.e(TAG, "========== clearView END ==========")
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // No swipe actions
    }

    override fun isLongPressDragEnabled(): Boolean = true

    override fun isItemViewSwipeEnabled(): Boolean = false
}
