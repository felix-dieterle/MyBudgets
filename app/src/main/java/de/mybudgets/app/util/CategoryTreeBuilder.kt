package de.mybudgets.app.util

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.view.setPadding
import de.mybudgets.app.data.model.Category

object CategoryTreeBuilder {
    enum class SelectionMode { SINGLE, MULTI }

    fun buildInto(
        container: LinearLayout,
        categories: List<Category>,
        selectedIds: MutableSet<Long>,
        expandedParents: MutableSet<Long>,
        mode: SelectionMode,
        parentId: Long? = null,
        level: Int = 0
    ) {
        container.removeAllViews()
        addChildren(container, categories, selectedIds, expandedParents, mode, parentId, level)
    }

    private fun addChildren(
        container: LinearLayout,
        categories: List<Category>,
        selectedIds: MutableSet<Long>,
        expandedParents: MutableSet<Long>,
        mode: SelectionMode,
        parentId: Long?,
        level: Int
    ) {
        val ctx = container.context
        for (cat in categories.filter { it.parentCategoryId == parentId }) {
            val hasChildren = categories.any { it.parentCategoryId == cat.id }
            val isExpanded = expandedParents.contains(cat.id) || level == 0

            val row = LinearLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                setPadding(level * 28, 6, 6, 6)
            }

            if (hasChildren) {
                row.addView(TextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(48, LinearLayout.LayoutParams.WRAP_CONTENT)
                    gravity = Gravity.CENTER
                    textSize = 12f
                    text = if (isExpanded) "\u25BC" else "\u25B6"
                })
            } else {
                row.addView(TextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(48, 1)
                })
            }

            val checkbox = AppCompatCheckBox(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = cat.name
                isChecked = selectedIds.contains(cat.id)
                tag = cat.id
            }
            row.addView(checkbox)
            container.addView(row)

            val childContainer = LinearLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                visibility = if (hasChildren && isExpanded) View.VISIBLE else View.GONE
            }
            container.addView(childContainer)

            if (hasChildren) {
                (row.getChildAt(0) as? TextView)?.setOnClickListener {
                    val expanded = expandedParents.contains(cat.id)
                    if (expanded) {
                        expandedParents.remove(cat.id)
                        (row.getChildAt(0) as? TextView)?.text = "\u25B6"
                    } else {
                        expandedParents.add(cat.id)
                        (row.getChildAt(0) as? TextView)?.text = "\u25BC"
                    }
                    childContainer.visibility = if (expandedParents.contains(cat.id)) View.VISIBLE else View.GONE
                }

                addChildren(childContainer, categories, selectedIds, expandedParents, mode, cat.id, level + 1)
            }

            when (mode) {
                SelectionMode.SINGLE -> {
                    checkbox.setOnClickListener {
                        if (checkbox.isChecked) {
                            selectedIds.clear()
                            selectedIds.add(cat.id)
                        } else {
                            selectedIds.remove(cat.id)
                        }
                        syncCheckboxStates(container, selectedIds)
                    }
                }
                SelectionMode.MULTI -> {
                    checkbox.setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedIds.add(cat.id) else selectedIds.remove(cat.id)
                    }
                }
            }
        }
    }

    private fun syncCheckboxStates(root: ViewGroup, ids: Set<Long>) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is AppCompatCheckBox) {
                child.isChecked = ids.contains(child.tag as? Long)
            }
            if (child is ViewGroup) syncCheckboxStates(child, ids)
        }
    }
}
