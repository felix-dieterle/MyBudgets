package de.mybudgets.app.ui.categories

import android.os.Bundle
import de.mybudgets.app.util.AppLogger
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.data.model.DropResult
import de.mybudgets.app.databinding.FragmentCategoriesBinding
import de.mybudgets.app.viewmodel.CategoryViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CategoriesFragment : Fragment() {

    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!
    private val vm: CategoryViewModel by viewModels()
    private lateinit var adapter: CategoryAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    companion object {
        private const val TAG = "CategoriesFragment"
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCategoriesBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        adapter = CategoryAdapter(
            onClick = { /* TODO: navigate to edit */ },
            onDragFeedback = { source, target, result -> updateDragBanner(source, target, result) }
        )
        binding.rvCategories.adapter = adapter

        // Setup drag & drop
        val dragDropHelper = CategoryDragDropHelper(
            adapter = adapter,
            onValidate = { source, target -> vm.validateDrop(source, target) },
            onDrop = { source, target -> handleDrop(source, target) }
        )
        itemTouchHelper = ItemTouchHelper(dragDropHelper)
        itemTouchHelper.attachToRecyclerView(binding.rvCategories)

        binding.fabAddCategory.setOnClickListener {
            findNavController().navigate(R.id.action_categoriesFragment_to_addEditCategoryFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.categories.collect { adapter.submitList(it) }
            }
        }
    }

    private fun updateDragBanner(source: de.mybudgets.app.data.model.Category?, target: de.mybudgets.app.data.model.Category?, result: DropResult?) {
        if (source == null || result == null) {
            // Hide banner
            binding.dragFeedbackBanner.visibility = android.view.View.GONE
            return
        }

        // Show banner
        binding.dragFeedbackBanner.visibility = android.view.View.VISIBLE

        // Set source
        binding.dragBannerSource.text = "${getCategoryIcon(source)} ${source.name}"

        // Set target + icon based on result
        when (result) {
            is DropResult.Valid -> {
                binding.dragBannerIcon.setImageResource(android.R.drawable.ic_menu_send)
                binding.dragBannerIcon.setColorFilter(android.graphics.Color.parseColor("#4CAF50"))
                binding.dragFeedbackBanner.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
                
                binding.dragBannerTarget.text = if (target == null) {
                    "→ wird Top-Level (Level 1)"
                } else {
                    "→ wird Kind von \"${target.name}\" (Level ${result.newLevel})"
                }
            }
            is DropResult.Warning -> {
                binding.dragBannerIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                binding.dragBannerIcon.setColorFilter(android.graphics.Color.parseColor("#FFC107"))
                binding.dragFeedbackBanner.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF8E1"))
                
                binding.dragBannerTarget.text = "⚠️ ${result.message}"
            }
            is DropResult.Invalid -> {
                binding.dragBannerIcon.setImageResource(android.R.drawable.ic_delete)
                binding.dragBannerIcon.setColorFilter(android.graphics.Color.parseColor("#F44336"))
                binding.dragFeedbackBanner.setCardBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
                
                binding.dragBannerTarget.text = "❌ ${result.message}"
            }
        }
    }

    private fun getCategoryIcon(category: de.mybudgets.app.data.model.Category): String {
        // Simple icon mapping based on name (could be expanded)
        return when {
            category.name.contains("Food", ignoreCase = true) -> "🍎"
            category.name.contains("Housing", ignoreCase = true) -> "🏠"
            category.name.contains("Transport", ignoreCase = true) -> "🚗"
            category.name.contains("Lifestyle", ignoreCase = true) -> "💼"
            else -> "📁"
        }
    }

    private fun handleDrop(source: de.mybudgets.app.data.model.Category, target: de.mybudgets.app.data.model.Category?) {
        AppLogger.e(TAG, "========== handleDrop CALLED ==========")
        AppLogger.e(TAG, "handleDrop: source=${source.name}(id=${source.id}, L${source.level}, parent=${source.parentCategoryId})")
        AppLogger.e(TAG, "handleDrop: target=${target?.name}(id=${target?.id}, L${target?.level})")
        
        val result = vm.validateDrop(source, target)
        AppLogger.e(TAG, "handleDrop: validation result=${result::class.simpleName}")

        when (result) {
            is DropResult.Valid -> {
                AppLogger.e(TAG, "handleDrop: ✅ VALID - calling vm.moveCategory()")
                vm.moveCategory(source, target?.id)
                val message = if (target == null) {
                    "✅ ${source.name} → Top-Level"
                } else {
                    "✅ ${source.name} → ${target.name}"
                }
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                AppLogger.e(TAG, "handleDrop: Snackbar shown: $message")
            }
            is DropResult.Warning -> {
                AppLogger.e(TAG, "handleDrop: ⚠️ WARNING - calling vm.moveCategory()")
                vm.moveCategory(source, target?.id)
                Snackbar.make(binding.root, "⚠️ ${result.message}", Snackbar.LENGTH_SHORT).show()
                AppLogger.e(TAG, "handleDrop: Snackbar shown: ${result.message}")
            }
            is DropResult.Invalid -> {
                AppLogger.e(TAG, "handleDrop: ❌ INVALID - NOT calling vm.moveCategory()")
                Snackbar.make(binding.root, "❌ ${result.message}", Snackbar.LENGTH_SHORT).show()
                AppLogger.e(TAG, "handleDrop: Snackbar shown: ${result.message}")
            }
        }
        AppLogger.e(TAG, "========== handleDrop END ==========")
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
