package de.mybudgets.app.ui.accounts

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.databinding.FragmentAccountsBinding
import de.mybudgets.app.viewmodel.AccountViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AccountsFragment : Fragment() {

    private var _binding: FragmentAccountsBinding? = null
    private val binding get() = _binding!!
    private val vm: AccountViewModel by viewModels()
    private lateinit var adapter: AccountAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAccountsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = AccountAdapter { account ->
            val bundle = Bundle().apply { putLong("accountId", account.id) }
            findNavController().navigate(R.id.action_accountsFragment_to_accountDetailFragment, bundle)
        }
        binding.rvAccounts.adapter = adapter

        binding.fabAddAccount.setOnClickListener {
            findNavController().navigate(R.id.action_accountsFragment_to_addEditAccountFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.accounts.collect { list ->
                    adapter.submitList(list)
                    binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
