package de.mybudgets.app.ui.categories

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.databinding.FragmentCategoriesBinding
import de.mybudgets.app.viewmodel.CategoryViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CategoriesFragment : Fragment() {

    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!
    private val vm: CategoryViewModel by viewModels()
    private lateinit var adapter: CategoryAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCategoriesBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = CategoryAdapter { /* TODO: navigate to edit */ }
        binding.rvCategories.adapter = adapter
        binding.fabAddCategory.setOnClickListener {
            findNavController().navigate(R.id.action_categoriesFragment_to_addEditCategoryFragment)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.categories.collect { adapter.submitList(it) }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
