package de.mybudgets.app.ui.categories

import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.ItemTouchHelper
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

        if (sourcePos == RecyclerView.NO_POSITION || targetPos == RecyclerView.NO_POSITION) {
            Log.d(TAG, "onMove: Invalid position (source=$sourcePos, target=$targetPos)")
            return false
        }

        val source = adapter.getCategoryAt(sourcePos)
        val targetCat = adapter.getCategoryAt(targetPos)

        draggedItem = source
        targetItem = targetCat

        // Validate drop
        val result = onValidate(source, targetCat)
        lastValidationResult = result

        Log.d(TAG, "onMove: ${source.name} -> ${targetCat.name}, result=${result::class.simpleName}")

        // Update visual feedback
        adapter.updateDropFeedback(sourcePos, targetPos, result)

        // Don't allow visual reordering - we only show feedback
        return false
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)

        when (actionState) {
            ItemTouchHelper.ACTION_STATE_DRAG -> {
                val pos = viewHolder?.adapterPosition ?: RecyclerView.NO_POSITION
                val cat = if (pos != RecyclerView.NO_POSITION) adapter.getCategoryAt(pos) else null
                Log.d(TAG, "onSelectedChanged: DRAG START - ${cat?.name}, pos=$pos")

                // Mark dragging state (NO view manipulation, NO notify!)
                adapter.setDraggingState(true, pos)
                
                draggedItem = cat
            }
            ItemTouchHelper.ACTION_STATE_IDLE -> {
                Log.d(TAG, "onSelectedChanged: DRAG END (IDLE)")
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        Log.d(TAG, "clearView: Drag released")

        // Trigger drop action when user releases
        val source = draggedItem
        val target = targetItem
        val result = lastValidationResult

        Log.d(TAG, "clearView: source=${source?.name}, target=${target?.name}, result=${result?.let { it::class.simpleName }}")

        // Only drop if target is different from source (user actually moved item)
        if (source != null && target != null && source.id != target.id && result != null) {
            when (result) {
                is DropResult.Valid, is DropResult.Warning -> {
                    Log.d(TAG, "clearView: Executing drop - ${source.name} -> ${target.name}")
                    onDrop(source, target)
                }
                is DropResult.Invalid -> {
                    Log.d(TAG, "clearView: Drop invalid, not executing")
                }
            }
        } else {
            Log.d(TAG, "clearView: No drop executed (source=target or missing data)")
        }

        // Clear drag state (will trigger UI refresh)
        adapter.setDraggingState(false)
        draggedItem = null
        targetItem = null
        lastValidationResult = null
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // No swipe actions
    }

    override fun isLongPressDragEnabled(): Boolean = true

    override fun isItemViewSwipeEnabled(): Boolean = false
}
