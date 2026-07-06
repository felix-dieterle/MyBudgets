package de.mybudgets.app.ui.transactions

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.mybudgets.app.data.model.Category

class CategoryFilterDialogFragment : DialogFragment() {

    private var selectedIds = mutableSetOf<Long>()
    private var onSaveListener: ((Set<Long>) -> Unit)? = null
    private val expandedParents = mutableSetOf<Long>()

    companion object {
        fun newInstance(
            allCategories: List<Category>,
            currentSelection: Set<Long>
        ): CategoryFilterDialogFragment {
            return CategoryFilterDialogFragment().apply {
                selectedIds = currentSelection.toMutableSet()
                this.allCategories = allCategories
            }
        }
    }

    private lateinit var allCategories: List<Category>

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val container = LinearLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(16)
        }

        buildHierarchicalUI(container, null, 0)

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(container)
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Kategorien wählen")
            .setView(scrollView)
            .setPositiveButton("OK") { _, _ ->
                onSaveListener?.invoke(selectedIds)
            }
            .setNegativeButton("Abbrechen", null)
            .create()
    }

    private fun buildHierarchicalUI(
        container: LinearLayout,
        parentId: Long?,
        level: Int
    ) {
        val children = if (parentId == null) {
            allCategories.filter { it.parentCategoryId == null }
        } else {
            allCategories.filter { it.parentCategoryId == parentId }
        }

        for (cat in children) {
            val row = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                setPadding(level * 24, 8, 8, 8)
            }

            // Expand/Collapse button (if has children)
            val hasChildren = allCategories.any { it.parentCategoryId == cat.id }
            if (hasChildren) {
                val expandBtn = TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        24,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = if (expandedParents.contains(cat.id)) "▼" else "►"
                    setOnClickListener {
                        if (expandedParents.contains(cat.id)) {
                            expandedParents.remove(cat.id)
                            text = "►"
                        } else {
                            expandedParents.add(cat.id)
                            text = "▼"
                        }
                        // Rebuild UI from root
                        container.removeAllViews()
                        buildHierarchicalUI(container, null, 0)
                    }
                }
                row.addView(expandBtn)
            } else {
                val spacer = TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(24, 1)
                }
                row.addView(spacer)
            }

            // Checkbox
            val checkbox = AppCompatCheckBox(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "${cat.name} (L${cat.level})"
                isChecked = selectedIds.contains(cat.id)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedIds.add(cat.id) else selectedIds.remove(cat.id)
                }
            }
            row.addView(checkbox)
            container.addView(row)

            // Show children if expanded
            if (expandedParents.contains(cat.id) || level == 0) {
                buildHierarchicalUI(container, cat.id, level + 1)
            }
        }
    }

    fun setOnSaveListener(listener: (Set<Long>) -> Unit) {
        onSaveListener = listener
    }
}
