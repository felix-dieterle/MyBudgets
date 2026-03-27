package de.mybudgets.app.ui.settings

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.work.*
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.databinding.FragmentSettingsBinding
import de.mybudgets.app.viewmodel.SettingsViewModel
import de.mybudgets.app.worker.BackendSyncWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val vm: SettingsViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.etBackendUrl.setText(vm.backendUrl)
        binding.etApiKey.setText(vm.apiKey)
        binding.etOpenrouterKey.setText(vm.openrouterApiKey)
        binding.switchOfflineMode.isChecked = vm.offlineMode

        updateSyncStatus()

        binding.btnSaveSettings.setOnClickListener {
            vm.backendUrl        = binding.etBackendUrl.text.toString().trim()
            vm.apiKey            = binding.etApiKey.text.toString().trim()
            vm.openrouterApiKey  = binding.etOpenrouterKey.text.toString().trim()
            vm.offlineMode       = binding.switchOfflineMode.isChecked
            Snackbar.make(view, getString(R.string.settings_saved), Snackbar.LENGTH_SHORT).show()
        }

        binding.btnManualSync.setOnClickListener {
            if (vm.backendUrl.isBlank()) {
                Snackbar.make(view, getString(R.string.settings_no_backend_url), Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val request = OneTimeWorkRequestBuilder<BackendSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(requireContext()).enqueue(request)
            Snackbar.make(view, getString(R.string.sync_started), Snackbar.LENGTH_SHORT).show()

            viewLifecycleOwner.lifecycleScope.launch {
                WorkManager.getInstance(requireContext())
                    .getWorkInfoByIdLiveData(request.id)
                    .asFlow()
                    .collect { info ->
                        if (info?.state == WorkInfo.State.SUCCEEDED) {
                            updateSyncStatus()
                        }
                    }
            }
        }

        binding.btnStandingOrders.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_standingOrdersFragment)
        }

        binding.btnLegal.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_legalFragment)
        }

        binding.btnLogConsole.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_logConsoleFragment)
        }
    }

    private fun updateSyncStatus() {
        val lastSync = vm.lastSyncTime
        binding.tvLastSync.text = if (lastSync == 0L) {
            getString(R.string.sync_never)
        } else {
            val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
            getString(R.string.sync_last, fmt.format(Date(lastSync)))
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

