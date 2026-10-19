package de.mybudgets.app.ui.synclog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.databinding.FragmentSyncLogBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SyncLogFragment : Fragment() {

    private var _binding: FragmentSyncLogBinding? = null
    private val binding get() = _binding!!

    private val vm: SyncLogViewModel by viewModels()
    private lateinit var adapter: SyncLogAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSyncLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SyncLogAdapter()
        binding.rvSyncLog.adapter = adapter

        binding.btnSyncLogClear.setOnClickListener {
            vm.clear()
            Snackbar.make(binding.root, R.string.sync_log_cleared, Snackbar.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.entries.collect { entries ->
                    adapter.submitList(entries)
                    binding.tvSyncLogCount.text = getString(R.string.sync_log_count, entries.size)
                    binding.tvSyncLogEmpty.visibility =
                        if (entries.isEmpty()) View.VISIBLE else View.GONE
                    binding.btnSyncLogClear.isEnabled = entries.isNotEmpty()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
