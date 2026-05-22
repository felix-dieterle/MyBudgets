package de.mybudgets.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.data.repository.SyncSettingsRepository
import de.mybudgets.app.databinding.FragmentSyncSettingsBinding
import javax.inject.Inject

@AndroidEntryPoint
class SyncSettingsFragment : Fragment() {
    
    @Inject lateinit var syncSettings: SyncSettingsRepository
    
    private var _binding: FragmentSyncSettingsBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSyncSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Load current values
        binding.sliderSecuregoWait.value = syncSettings.secureGoWaitSeconds.toFloat()
        binding.sliderBulkDelay.value = syncSettings.bulkSyncDelaySeconds.toFloat()
        binding.sliderDnsRetryCount.value = syncSettings.dnsRetryCount.toFloat()
        binding.sliderDnsRetryDelay.value = syncSettings.dnsRetryDelaySeconds.toFloat()
        
        updateLabels()
        
        // SecureGo Wait
        binding.sliderSecuregoWait.addOnChangeListener { _, value, _ ->
            syncSettings.secureGoWaitSeconds = value.toInt()
            binding.tvSecuregoWait.text = "${value.toInt()}s"
        }
        
        // Bulk Delay
        binding.sliderBulkDelay.addOnChangeListener { _, value, _ ->
            syncSettings.bulkSyncDelaySeconds = value.toInt()
            binding.tvBulkDelay.text = "${value.toInt()}s"
        }
        
        // DNS Retry Count
        binding.sliderDnsRetryCount.addOnChangeListener { _, value, _ ->
            syncSettings.dnsRetryCount = value.toInt()
            binding.tvDnsRetryCount.text = value.toInt().toString()
        }
        
        // DNS Retry Delay
        binding.sliderDnsRetryDelay.addOnChangeListener { _, value, _ ->
            syncSettings.dnsRetryDelaySeconds = value.toInt()
            binding.tvDnsRetryDelay.text = "${value.toInt()}s"
        }
        
        // Reset Button
        binding.btnReset.setOnClickListener {
            syncSettings.resetToDefaults()
            binding.sliderSecuregoWait.value = 30f
            binding.sliderBulkDelay.value = 5f
            binding.sliderDnsRetryCount.value = 2f
            binding.sliderDnsRetryDelay.value = 3f
            updateLabels()
        }
    }
    
    private fun updateLabels() {
        binding.tvSecuregoWait.text = "${syncSettings.secureGoWaitSeconds}s"
        binding.tvBulkDelay.text = "${syncSettings.bulkSyncDelaySeconds}s"
        binding.tvDnsRetryCount.text = syncSettings.dnsRetryCount.toString()
        binding.tvDnsRetryDelay.text = "${syncSettings.dnsRetryDelaySeconds}s"
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
