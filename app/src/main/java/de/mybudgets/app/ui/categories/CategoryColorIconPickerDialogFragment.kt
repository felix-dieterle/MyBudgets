package de.mybudgets.app.ui.categories

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.mybudgets.app.R
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.databinding.DialogCategoryColorIconBinding
import kotlinx.coroutines.launch

class CategoryColorIconPickerDialogFragment : DialogFragment() {

    private var _binding: DialogCategoryColorIconBinding? = null
    private val binding get() = _binding!!
    private var selectedColor: Int = 0xFF9E9E9E.toInt()
    private var selectedIcon: String = "📦"
    private var categoryId: Long = 0L
    private var categoryName: String = ""
    private var onSaveListener: ((color: Int, icon: String) -> Unit)? = null

    companion object {
        private const val ARG_CATEGORY_ID = "categoryId"
        private const val ARG_CATEGORY_NAME = "categoryName"
        private const val ARG_CATEGORY_COLOR = "categoryColor"
        private const val ARG_CATEGORY_ICON = "categoryIcon"

        fun newInstance(category: Category): CategoryColorIconPickerDialogFragment {
            return CategoryColorIconPickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_CATEGORY_ID, category.id)
                    putString(ARG_CATEGORY_NAME, category.name)
                    putInt(ARG_CATEGORY_COLOR, category.color)
                    putString(ARG_CATEGORY_ICON, category.icon)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categoryId = arguments?.getLong(ARG_CATEGORY_ID) ?: 0L
        categoryName = arguments?.getString(ARG_CATEGORY_NAME) ?: ""
        selectedColor = arguments?.getInt(ARG_CATEGORY_COLOR) ?: 0xFF9E9E9E.toInt()
        selectedIcon = arguments?.getString(ARG_CATEGORY_ICON) ?: "📦"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCategoryColorIconBinding.inflate(layoutInflater)
        
        setupColorPicker()
        setupIconPicker()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Category")
            .setView(binding.root)
            .setPositiveButton("Save") { _, _ ->
                onSaveListener?.invoke(selectedColor, selectedIcon)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun setupColorPicker() {
        val colors = listOf(
            0xFFFF6B6B.toInt(), // Red
            0xFFFFE66D.toInt(), // Yellow
            0xFF95E1D3.toInt(), // Light Teal
            0xFF4ECDC4.toInt(), // Teal
            0xFFAA96DA.toInt(), // Purple
            0xFFF38181.toInt(), // Pink
            0xFF5DADE2.toInt(), // Blue
            0xFF58D68D.toInt(), // Green
            0xFFFFB84D.toInt(), // Orange
            0xFF9E9E9E.toInt()  // Gray
        )
        
        binding.colorGrid.apply {
            removeAllViews()
            columnCount = 5
            for (color in colors) {
                val colorView = View(requireContext()).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 48.dpToPx()
                        height = 48.dpToPx()
                    }
                    setBackgroundColor(color)
                    setPadding(4.dpToPx())
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        selectedColor = color
                        binding.colorPreview.setBackgroundColor(color)
                    }
                }
                addView(colorView)
            }
        }
        binding.colorPreview.setBackgroundColor(selectedColor)
    }

    private fun setupIconPicker() {
        val commonEmojis = listOf(
            "📦", "🍔", "🏠", "🚗", "💳", "🎉", "🎓", "💪", "🏥", "📱",
            "✈️", "📺", "🎮", "🧸", "👔", "🌺", "🐶", "🍕", "☕", "🎵",
            "⚽", "🎬", "📚", "💼", "🛒", "✂️", "💇", "🚴", "🎾", "🧘"
        )
        
        binding.emojiGrid.apply {
            removeAllViews()
            columnCount = 5
            for (emoji in commonEmojis) {
                val emojiView = AppCompatTextView(requireContext()).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 48.dpToPx()
                        height = 48.dpToPx()
                    }
                    text = emoji
                    textSize = 24f
                    gravity = android.view.Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        selectedIcon = emoji
                        binding.iconPreview.text = emoji
                    }
                }
                addView(emojiView)
            }
        }
        binding.iconPreview.text = selectedIcon
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    fun setOnSaveListener(listener: (color: Int, icon: String) -> Unit) {
        onSaveListener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

