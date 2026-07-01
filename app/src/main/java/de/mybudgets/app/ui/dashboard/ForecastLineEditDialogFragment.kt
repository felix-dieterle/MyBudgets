package de.mybudgets.app.ui.dashboard

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.mybudgets.app.R
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.util.CategoryTreeBuilder
import de.mybudgets.app.viewmodel.ForecastLineConfig

class ForecastLineEditDialogFragment : DialogFragment() {

    private var config: ForecastLineConfig = ForecastLineConfig("", "", emptySet())
    private var allCategories: List<Category> = emptyList()
    private var allConfigs: List<ForecastLineConfig> = emptyList()
    private var currentIndex: Int = 0
    private var selectedCategoryIds = mutableSetOf<Long>()
    private var onSaveListener: ((ForecastLineConfig) -> Unit)? = null
    private var onDeleteListener: ((String) -> Unit)? = null
    private var onNavigateListener: ((Int) -> Unit)? = null
    private val expandedParents = mutableSetOf<Long>()
    private lateinit var labelInput: EditText

    companion object {
        fun newInstance(
            config: ForecastLineConfig,
            allCategories: List<Category>,
            allConfigs: List<ForecastLineConfig> = emptyList(),
            currentIndex: Int = 0
        ): ForecastLineEditDialogFragment {
            return ForecastLineEditDialogFragment().apply {
                this.config = config
                this.allCategories = allCategories
                this.allConfigs = allConfigs
                this.currentIndex = currentIndex
                this.selectedCategoryIds = config.categoryIds.toMutableSet()
            }
        }
    }

    fun setOnSaveListener(listener: (ForecastLineConfig) -> Unit) {
        onSaveListener = listener
    }

    fun setOnDeleteListener(listener: (String) -> Unit) {
        onDeleteListener = listener
    }

    fun setOnNavigateListener(listener: (Int) -> Unit) {
        onNavigateListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val scrollView = NestedScrollView(requireContext()).apply {
            isFillViewport = true
            clipToPadding = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val container = LinearLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(16)
        }

        labelInput = EditText(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = getString(R.string.forecast_line_label_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(config.label)
            setPadding(12)
        }
        container.addView(labelInput)

        // ── Navigation row: Previous / Next ──
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.recurring_purple)
        val disabledColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
        val navRow = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 0)
        }
        val prevBtn = TextView(requireContext()).apply {
            text = getString(R.string.forecast_line_prev)
            textSize = 14f
            setPadding(0, 8, 0, 8)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(if (currentIndex > 0) primaryColor else disabledColor)
            setOnClickListener {
                if (currentIndex > 0) { onNavigateListener?.invoke(currentIndex - 1); dismiss() }
            }
        }
        val nextBtn = TextView(requireContext()).apply {
            text = getString(R.string.forecast_line_next)
            textSize = 14f
            setPadding(0, 8, 0, 8)
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(if (currentIndex < allConfigs.size - 1) primaryColor else disabledColor)
            setOnClickListener {
                if (currentIndex < allConfigs.size - 1) { onNavigateListener?.invoke(currentIndex + 1); dismiss() }
            }
        }
        navRow.addView(prevBtn)
        navRow.addView(nextBtn)
        container.addView(navRow)

        val sectionHeader = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = getString(R.string.forecast_line_select_categories)
            setPadding(0, 16, 0, 8)
            textSize = 14f
        }
        container.addView(sectionHeader)

        CategoryTreeBuilder.buildInto(
            container = container,
            categories = allCategories,
            selectedIds = selectedCategoryIds,
            expandedParents = expandedParents,
            mode = CategoryTreeBuilder.SelectionMode.MULTI
        )

        scrollView.addView(container)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.forecast_line_edit_title)
            .setView(scrollView)
            .setPositiveButton(R.string.forecast_line_save) { _, _ ->
                val label = labelInput.text.toString().trim()
                if (label.isNotEmpty()) {
                    val updated = config.copy(
                        label = label,
                        categoryIds = selectedCategoryIds.toSet()
                    )
                    onSaveListener?.invoke(updated)
                }
            }
            .setNegativeButton(R.string.forecast_line_cancel, null)
            .setNeutralButton(R.string.forecast_line_delete) { _, _ ->
                onDeleteListener?.invoke(config.id)
            }
            .create()
    }
}
