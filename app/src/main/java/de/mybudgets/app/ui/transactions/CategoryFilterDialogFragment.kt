package de.mybudgets.app.ui.transactions

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import de.mybudgets.app.data.model.Category

class CategoryFilterDialogFragment : DialogFragment() {

    private var selectedIds = mutableSetOf<Long>()
    private var onSaveListener: ((Set<Long>) -> Unit)? = null
    private val expandedParents = mutableSetOf<Long>()
    private var allCategories: List<Category> = emptyList()
    private var searchQuery = ""
    private lateinit var treeContainer: LinearLayout

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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val mainContainer = LinearLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        mainContainer.addView(buildToolbar())

        val searchLayout = TextInputLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = "Suchen..."
            setPadding(16, 4, 16, 4)

            val input = TextInputEditText(requireContext()).apply {
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        searchQuery = s?.toString()?.trim() ?: ""
                        rebuildTree()
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
            }
            addView(input)
        }
        mainContainer.addView(searchLayout)

        treeContainer = LinearLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(treeContainer)
        }
        mainContainer.addView(scrollView)

        allCategories.filter { it.parentCategoryId == null }.forEach { autoExpandSelected(it) }

        rebuildTree()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Kategorien wählen")
            .setView(mainContainer)
            .setPositiveButton("OK") { _, _ ->
                onSaveListener?.invoke(selectedIds)
            }
            .setNegativeButton("Abbrechen", null)
            .create()
    }

    private fun buildToolbar(): LinearLayout {
        val toolbar = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(8, 8, 8, 4)
        }

        fun btn(text: String, onClick: () -> Unit) = MaterialButton(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            this.text = text
            setOnClickListener { onClick() }
            setPadding(4, 0, 4, 0)
            minHeight = 0
        }

        toolbar.addView(btn("Alle") {
            allCategories.forEach { selectedIds.add(it.id) }
            rebuildTree()
        })
        toolbar.addView(btn("Keine") {
            selectedIds.clear()
            rebuildTree()
        })
        toolbar.addView(btn("▼") {
            allCategories.filter { it.parentCategoryId == null }.forEach { expandAll(it) }
            rebuildTree()
        })
        toolbar.addView(btn("▲") {
            expandedParents.clear()
            rebuildTree()
        })

        return toolbar
    }

    private fun expandAll(cat: Category) {
        if (allCategories.any { it.parentCategoryId == cat.id }) {
            expandedParents.add(cat.id)
            allCategories.filter { it.parentCategoryId == cat.id }.forEach { expandAll(it) }
        }
    }

    private fun autoExpandSelected(cat: Category) {
        for (child in allCategories.filter { it.parentCategoryId == cat.id }) {
            if (selectedIds.contains(child.id) || hasSelectedDescendant(child)) {
                expandedParents.add(cat.id)
            }
            autoExpandSelected(child)
        }
    }

    private fun hasSelectedDescendant(cat: Category): Boolean =
        allCategories.any { it.parentCategoryId == cat.id && (selectedIds.contains(it.id) || hasSelectedDescendant(it)) }

    private fun rebuildTree() {
        treeContainer.removeAllViews()
        val visibleIds = if (searchQuery.isNotEmpty()) {
            val matching = allCategories
                .filter { it.name.contains(searchQuery, ignoreCase = true) }
                .map { it.id }
                .toMutableSet()
            matching.toSet().forEach { id ->
                var pid = allCategories.find { it.id == id }?.parentCategoryId
                while (pid != null) {
                    matching.add(pid)
                    pid = allCategories.find { it.id == pid }?.parentCategoryId
                }
            }
            matching
        } else null

        buildTree(treeContainer, null, 0, visibleIds)
    }

    private fun buildTree(container: LinearLayout, parentId: Long?, level: Int, visibleIds: Set<Long>?) {
        val children = allCategories.filter { it.parentCategoryId == parentId }
        for (cat in children) {
            val hasChildren = allCategories.any { it.parentCategoryId == cat.id }
            val matchesSearch = visibleIds == null || visibleIds.contains(cat.id)
            if (visibleIds != null && !matchesSearch) continue

            val row = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                setPadding(level * 24, 8, 8, 8)
                gravity = Gravity.CENTER_VERTICAL
            }

            if (hasChildren) {
                val expandBtn = TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(40, ViewGroup.LayoutParams.WRAP_CONTENT)
                    gravity = Gravity.CENTER
                    text = if (expandedParents.contains(cat.id)) "▼" else "►"
                    setOnClickListener {
                        if (expandedParents.contains(cat.id)) expandedParents.remove(cat.id)
                        else expandedParents.add(cat.id)
                        rebuildTree()
                    }
                }
                row.addView(expandBtn)
            } else {
                row.addView(TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(40, 1)
                })
            }

            val cb = AppCompatCheckBox(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                text = cat.name
            }

            if (hasChildren) {
                val state = getParentState(cat)
                when (state) {
                    ParentState.UNCHECKED -> cb.isChecked = false
                    ParentState.CHECKED -> cb.isChecked = true
                    ParentState.PARTIAL -> {
                        cb.isChecked = true
                        cb.alpha = 0.5f
                    }
                }
                cb.setOnClickListener {
                    val s = getParentState(cat)
                    if (s == ParentState.CHECKED) {
                        selectedIds.remove(cat.id)
                        setChildrenSelected(cat.id, false)
                    } else {
                        selectedIds.add(cat.id)
                        setChildrenSelected(cat.id, true)
                    }
                    rebuildTree()
                }

                val (sel, total) = getDescendantCounts(cat.id)
                row.addView(TextView(requireContext()).apply {
                    text = " [$sel/$total]"
                    textSize = 13f
                    setTextColor(0xFF888888.toInt())
                })
            } else {
                cb.isChecked = selectedIds.contains(cat.id)
                cb.setOnClickListener {
                    if (cb.isChecked) selectedIds.add(cat.id) else selectedIds.remove(cat.id)
                    rebuildTree()
                }
            }

            row.addView(cb)
            container.addView(row)

            val showChildren = if (visibleIds != null) {
                hasChildren && visibleIds.contains(cat.id)
            } else {
                expandedParents.contains(cat.id) || level == 0
            }
            if (showChildren) {
                buildTree(container, cat.id, level + 1, visibleIds)
            }
        }
    }

    private enum class ParentState { CHECKED, UNCHECKED, PARTIAL }

    private fun getParentState(cat: Category): ParentState {
        val children = allCategories.filter { it.parentCategoryId == cat.id }
        var checked = 0
        var total = 0
        for (child in children) {
            total++
            if (selectedIds.contains(child.id)) checked++
            val counts = getDescendantCounts(child.id)
            checked += counts.first
            total += counts.second
        }
        if (checked == 0) return ParentState.UNCHECKED
        if (checked == total) return ParentState.CHECKED
        return ParentState.PARTIAL
    }

    private fun getDescendantCounts(catId: Long): Pair<Int, Int> {
        var sel = 0
        var total = 0
        for (child in allCategories.filter { it.parentCategoryId == catId }) {
            total++
            if (selectedIds.contains(child.id)) sel++
            val sub = getDescendantCounts(child.id)
            sel += sub.first
            total += sub.second
        }
        return sel to total
    }

    private fun setChildrenSelected(catId: Long, selected: Boolean) {
        for (child in allCategories.filter { it.parentCategoryId == catId }) {
            if (selected) selectedIds.add(child.id) else selectedIds.remove(child.id)
            setChildrenSelected(child.id, selected)
        }
    }

    fun setOnSaveListener(listener: (Set<Long>) -> Unit) {
        onSaveListener = listener
    }
}
