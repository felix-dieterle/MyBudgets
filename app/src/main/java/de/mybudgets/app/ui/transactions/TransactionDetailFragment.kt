package de.mybudgets.app.ui.transactions

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.databinding.FragmentTransactionDetailBinding
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.util.DateFormatter
import de.mybudgets.app.viewmodel.TransactionViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TransactionDetailFragment : Fragment() {

    private var _binding: FragmentTransactionDetailBinding? = null
    private val binding get() = _binding!!
    private val vm: TransactionViewModel by viewModels()
    private var transactionId: Long = 0L

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentTransactionDetailBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        transactionId = arguments?.getLong("transactionId") ?: 0L
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.transactions.collect { list ->
                    list.find { it.id == transactionId }?.let { show(it) }
                }
            }
        }
    }

    private fun show(tx: Transaction) {
        binding.tvTxDescription.text = tx.description.ifBlank { "Buchung" }
        binding.tvTxAmount.text      = CurrencyFormatter.format(tx.amount)
        binding.tvTxDate.text        = DateFormatter.formatDate(tx.date)
        binding.tvTxType.text        = tx.type.name
        binding.tvTxNote.text        = tx.note.ifBlank { "—" }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
