package de.mybudgets.app.ui.categories

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.databinding.FragmentAddEditCategoryBinding
import de.mybudgets.app.viewmodel.CategoryViewModel

@AndroidEntryPoint
class AddEditCategoryFragment : Fragment() {

    private var _binding: FragmentAddEditCategoryBinding? = null
    private val binding get() = _binding!!
    private val vm: CategoryViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAddEditCategoryBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSave.setOnClickListener {
            val name = binding.etCategoryName.text.toString().trim()
            if (name.isBlank()) { binding.etCategoryName.error = "Name fehlt"; return@setOnClickListener }
            val cat = Category(
                name    = name,
                pattern = binding.etPattern.text.toString().trim()
            )
            vm.save(cat)
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
