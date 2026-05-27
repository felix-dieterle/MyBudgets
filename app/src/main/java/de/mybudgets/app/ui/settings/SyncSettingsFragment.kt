package de.mybudgets.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.data.repository.SyncSettingsRepository
import de.mybudgets.app.data.repository.TransactionRepository
import de.mybudgets.app.databinding.FragmentSyncSettingsBinding
import de.mybudgets.app.ui.transactions.RecurringPatternDialog
import de.mybudgets.app.util.AppLogger
import de.mybudgets.app.util.RecurringPatternDetector
import de.mybudgets.app.viewmodel.AccountViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SyncSettingsFragment : Fragment() {
    
    @Inject lateinit var syncSettings: SyncSettingsRepository
    @Inject lateinit var txRepo: TransactionRepository
    private val viewModel: AccountViewModel by viewModels()
    
    private var _binding: FragmentSyncSettingsBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSyncSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Observe accounts (trigger lazy StateFlow)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.realAccounts.collect { /* just to activate flow */ }
        }
        
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
        
        // Undo Syncs
        binding.sliderUndoSyncs.addOnChangeListener { _, value, _ ->
            binding.tvUndoSyncs.text = value.toInt().toString()
        }
        
        binding.btnUndoSyncs.setOnClickListener {
            val count = binding.sliderUndoSyncs.value.toInt()
            val accounts = viewModel.realAccounts.value
            
            if (accounts.isEmpty()) {
                Snackbar.make(requireView(), "Keine Konten vorhanden", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Show account selection if multiple accounts
            val accountNames = accounts.map { "${it.name} (${it.iban.takeLast(4)})" }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Konto wählen")
                .setItems(accountNames) { _, which ->
                    val accountId = accounts[which].id
                    confirmUndoSyncs(accountId, count)
                }
                .show()
        }
        
        // Recurrence Check
        binding.btnRecurrenceCheck.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Muster neu erkennen")
                .setMessage("Wie sollen bestehende Marker behandelt werden?")
                .setPositiveButton("Neu erkennen (Reset)") { _, _ ->
                    runRecurrenceCheck(resetExisting = true)
                }
                .setNegativeButton("Nur neue markieren") { _, _ ->
                    runRecurrenceCheck(resetExisting = false)
                }
                .setNeutralButton("Abbrechen", null)
                .show()
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
    
    private fun confirmUndoSyncs(accountId: Long, count: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚠️ Wirklich löschen?")
            .setMessage("Die letzten $count Sync-Intervalle und alle zugehörigen Transaktionen werden unwiderruflich gelöscht!")
            .setPositiveButton("Löschen") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val deletedTxCount = viewModel.undoLastNSyncs(accountId, count)
                        Snackbar.make(
                            requireView(), 
                            "✅ $count Syncs gelöscht ($deletedTxCount Transaktionen)", 
                            Snackbar.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Snackbar.make(
                            requireView(), 
                            "❌ Fehler: ${e.message}", 
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
    
    private fun runRecurrenceCheck(resetExisting: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Get all transactions
                val allTxs = txRepo.observeAll().first()
                
                if (resetExisting) {
                    // Reset all isRecurring flags
                    for (tx in allTxs.filter { it.isRecurring }) {
                        txRepo.save(tx.copy(
                            isRecurring = false,
                            recurringIntervalDays = 0
                        ))
                    }
                }
                
                // Run pattern detection
                AppLogger.i("SyncSettingsFragment", "Running pattern detection on ${allTxs.size} transactions...")
                val patterns = RecurringPatternDetector.detectPatterns(allTxs)
                AppLogger.i("SyncSettingsFragment", "Found ${patterns.size} patterns")
                
                if (patterns.isEmpty()) {
                    Snackbar.make(
                        requireView(),
                        "❌ Keine wiederkehrenden Muster gefunden",
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@launch
                }
                
                // Show pattern selection dialog
                RecurringPatternDialog.newInstance(patterns)
                    .setOnApplyListener { rules ->
                        AppLogger.i("SyncSettingsFragment", "Saving ${rules.size} recurring rules...")
                        rules.forEach { rule -> viewModel.saveRecurringRule(rule) }
                        Snackbar.make(
                            requireView(),
                            "✅ ${rules.size} Muster als Regeln gespeichert",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    .setOnDismissListener {
                        AppLogger.i("SyncSettingsFragment", "Pattern dialog dismissed")
                    }
                    .show(childFragmentManager, "RecurringPatternDialog")
                    
            } catch (e: Exception) {
                AppLogger.e("SyncSettingsFragment", "runRecurrenceCheck failed", e)
                Snackbar.make(
                    requireView(),
                    "❌ Fehler: ${e.message}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
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
