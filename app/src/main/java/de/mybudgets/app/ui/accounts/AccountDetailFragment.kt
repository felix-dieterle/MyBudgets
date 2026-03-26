package de.mybudgets.app.ui.accounts

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.AccountType
import de.mybudgets.app.databinding.FragmentAccountDetailBinding
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.viewmodel.AccountViewModel
import androidx.lifecycle.*
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AccountDetailFragment : Fragment() {

    private var _binding: FragmentAccountDetailBinding? = null
    private val binding get() = _binding!!
    private val vm: AccountViewModel by viewModels()
    private var accountId: Long = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAccountDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        accountId = arguments?.getLong("accountId") ?: 0L

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.accounts.collect { list ->
                    list.find { it.id == accountId }?.let { showAccount(it, list) }
                }
            }
        }
    }

    private fun showAccount(acc: Account, allAccounts: List<Account>) {
        binding.tvAccountName.text    = acc.name
        binding.tvAccountBalance.text = CurrencyFormatter.format(acc.balance, acc.currency)
        binding.tvAccountIban.text    = if (acc.iban.isNotBlank()) "IBAN: ${acc.iban}" else ""
        val typeLabel = when (acc.type) {
            AccountType.CHECKING -> "Girokonto"
            AccountType.SAVINGS  -> "Sparkonto"
            AccountType.CASH     -> "Barkasse"
            AccountType.VIRTUAL  -> "Virtuelles Konto"
        }
        binding.tvAccountType.text = typeLabel

        if (acc.isVirtual && acc.parentAccountId != null) {
            val parent = allAccounts.find { it.id == acc.parentAccountId }
            if (parent != null) {
                binding.layoutLinkedAccount.visibility = View.VISIBLE
                binding.tvLinkedAccountName.text = parent.name
            }
        } else {
            binding.layoutLinkedAccount.visibility = View.GONE
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
