package de.mybudgets.app.ui.categories

import de.mybudgets.app.util.AppLogger
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.mybudgets.app.R
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.data.model.DropResult
import de.mybudgets.app.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val onClick: (Category) -> Unit,
    private val onLongClick: (Category) -> Boolean,
    private val onDragFeedback: (source: Category?, target: Category?, result: DropResult?) -> Unit,
    private val expandedCategories: Set<Long>
) : ListAdapter<Category, CategoryAdapter.VH>(DIFF) {

    private var isDragging = false
    private var currentDropTarget: Int? = null
    private var currentDropResult: DropResult? = null
    private var draggedItemPosition: Int? = null

    inner class VH(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(cat: Category) {
            binding.tvCategoryName.text  = cat.name
            binding.tvCategoryLevel.text = "L${cat.level}"
            binding.viewCategoryColor.setBackgroundColor(cat.color)
            binding.root.setOnClickListener { onClick(cat) }
            binding.root.setOnLongClickListener { 
                if (isDragging) false else onLongClick(cat)
            }

            // Expand/Collapse arrow for L1 categories
            if (cat.level == 1) {
                binding.tvExpandArrow.isVisible = true
                binding.tvExpandArrow.text = if (expandedCategories.contains(cat.id)) "▼" else "►"
            } else {
                binding.tvExpandArrow.isVisible = false
            }

            // Indentation based on level
            val indent = (cat.level - 1) * 32 // 32dp per level
            binding.root.setPadding(indent, binding.root.paddingTop, binding.root.paddingRight, binding.root.paddingBottom)

            // Make dragged item nearly invisible (alpha 0.3)
            if (adapterPosition == draggedItemPosition && isDragging) {
                binding.root.alpha = 0.3f
            } else {
                binding.root.alpha = 1f
            }

            // Target highlighting (no animation for now - might interfere with drag)
            val isTarget = adapterPosition == currentDropTarget
            if (isTarget) {
                binding.categoryContent.elevation = 8f
                binding.categoryContent.scaleX = 1.05f
                binding.categoryContent.scaleY = 1.05f
            } else {
                binding.categoryContent.elevation = 0f
                binding.categoryContent.scaleX = 1f
                binding.categoryContent.scaleY = 1f
            }

            // Hide item-level feedback (we use top banner now)
            binding.dropZoneIndicator.isVisible = false
            binding.dropFeedbackBanner.isVisible = false
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, v: Int) =
        VH(ItemCategoryBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    fun setDraggingState(dragging: Boolean, draggedPos: Int? = null) {
        // Set state WITHOUT any UI updates during drag
        isDragging = dragging
        draggedItemPosition = draggedPos
        
        if (dragging) {
            // Just store state, NO notify, NO banner update
            AppLogger.e("CategoryAdapter", "========== setDraggingState: DRAG START ==========")
            AppLogger.e("CategoryAdapter", "setDraggingState: dragging=true, pos=$draggedPos")
        } else {
            // Clear all drag state AFTER drag ends
            AppLogger.e("CategoryAdapter", "========== setDraggingState: DRAG END ==========")
            val oldTarget = currentDropTarget
            val oldDragged = draggedItemPosition
            currentDropTarget = null
            currentDropResult = null
            draggedItemPosition = null
            
            // Notify top banner
            onDragFeedback(null, null, null)
            
            // Refresh affected items AFTER drag ends
            if (oldTarget != null) {
                AppLogger.e("CategoryAdapter", "setDraggingState: Refreshing old target pos=$oldTarget")
                notifyItemChanged(oldTarget)
            }
            if (oldDragged != null) {
                AppLogger.e("CategoryAdapter", "setDraggingState: Refreshing old dragged pos=$oldDragged")
                notifyItemChanged(oldDragged)
            }
        }
    }



    fun updateDropFeedback(sourcePos: Int, targetPos: Int, result: DropResult) {
        AppLogger.e("CategoryAdapter", "updateDropFeedback: sourcePos=$sourcePos, targetPos=$targetPos, result=${result::class.simpleName}")
        
        // Clear previous target
        val oldTarget = currentDropTarget
        if (oldTarget != null && oldTarget != targetPos) {
            AppLogger.e("CategoryAdapter", "updateDropFeedback: Clearing old target pos=$oldTarget")
            notifyItemChanged(oldTarget)
        }
        
        // Haptic feedback on target change
        if (oldTarget != targetPos) {
            performTargetChangeHaptic()
        }
        
        // Set new target
        currentDropTarget = targetPos
        currentDropResult = result
        AppLogger.e("CategoryAdapter", "updateDropFeedback: Refreshing new target pos=$targetPos")
        notifyItemChanged(targetPos)
        
        // Update top banner
        val source = currentList.getOrNull(sourcePos)
        val target = currentList.getOrNull(targetPos)
        AppLogger.e("CategoryAdapter", "updateDropFeedback: Calling onDragFeedback(${source?.name}, ${target?.name}, ...)")
        onDragFeedback(source, target, result)
    }

    fun performSuccessHaptic() {
        // Will be called from Fragment with View context
    }

    fun performErrorHaptic() {
        // Will be called from Fragment with View context
    }

    private fun performTargetChangeHaptic() {
        // Light haptic on target change (called from adapter with RecyclerView context)
        currentList.firstOrNull()?.let {
            // Note: Needs View for haptic, will work from RecyclerView
        }
    }

    fun getCategoryAt(position: Int): Category = currentList[position]

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(a: Category, b: Category) = a.id == b.id
            override fun areContentsTheSame(a: Category, b: Category) = a == b
        }
    }
}
