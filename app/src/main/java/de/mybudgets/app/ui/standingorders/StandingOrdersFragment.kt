package de.mybudgets.app.ui.standingorders

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.databinding.FragmentStandingOrdersBinding
import de.mybudgets.app.viewmodel.StandingOrderViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StandingOrdersFragment : Fragment() {

    private var _binding: FragmentStandingOrdersBinding? = null
    private val binding get() = _binding!!
    private val vm: StandingOrderViewModel by viewModels()
    private lateinit var adapter: StandingOrderAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentStandingOrdersBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = StandingOrderAdapter(
            onClick = { order ->
                val bundle = Bundle().apply { putLong("standingOrderId", order.id) }
                findNavController().navigate(R.id.action_standingOrdersFragment_to_addEditStandingOrderFragment, bundle)
            },
            onDelete = { order ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.so_delete_title))
                    .setMessage(getString(R.string.so_delete_message, order.recipientName))
                    .setPositiveButton(android.R.string.ok) { _, _ -> vm.delete(order) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        )

        binding.recyclerStandingOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerStandingOrders.adapter = adapter

        binding.fabAddStandingOrder.setOnClickListener {
            findNavController().navigate(R.id.action_standingOrdersFragment_to_addEditStandingOrderFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.orders.collect { orders ->
                    adapter.submitList(orders)
                    binding.emptyState.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerStandingOrders.visibility = if (orders.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
