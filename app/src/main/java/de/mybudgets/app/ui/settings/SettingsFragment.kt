package de.mybudgets.app.ui.settings

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.databinding.FragmentSettingsBinding
import de.mybudgets.app.viewmodel.SettingsViewModel

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

        binding.btnSaveSettings.setOnClickListener {
            vm.backendUrl        = binding.etBackendUrl.text.toString().trim()
            vm.apiKey            = binding.etApiKey.text.toString().trim()
            vm.openrouterApiKey  = binding.etOpenrouterKey.text.toString().trim()
            vm.offlineMode       = binding.switchOfflineMode.isChecked
        }

        binding.btnLegal.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_legalFragment)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
