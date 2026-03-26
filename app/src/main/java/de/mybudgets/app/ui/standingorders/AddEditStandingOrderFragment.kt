package de.mybudgets.app.ui.standingorders

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.data.banking.FintsService
import de.mybudgets.app.data.model.StandingOrder
import de.mybudgets.app.databinding.FragmentAddEditStandingOrderBinding
import de.mybudgets.app.ui.transfers.pinDialog
import de.mybudgets.app.ui.transfers.tanDialog
import de.mybudgets.app.viewmodel.StandingOrderState
import de.mybudgets.app.viewmodel.StandingOrderViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class AddEditStandingOrderFragment : Fragment() {

    private var _binding: FragmentAddEditStandingOrderBinding? = null
    private val binding get() = _binding!!
    private val vm: StandingOrderViewModel by viewModels()

    @Inject lateinit var fintsService: FintsService

    private var firstExecutionDate: Long = System.currentTimeMillis()
    private var lastExecutionDate: Long? = null
    private val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    private var editOrderId: Long = 0L

    private val intervalOptions = listOf(7 to "Wöchentlich", 14 to "Alle 2 Wochen", 30 to "Monatlich",
        90 to "Vierteljährlich", 180 to "Halbjährlich", 365 to "Jährlich")

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAddEditStandingOrderBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editOrderId = arguments?.getLong("standingOrderId", 0L) ?: 0L

        // Interval spinner
        val intervalLabels = intervalOptions.map { it.second }
        binding.spinnerInterval.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, intervalLabels)
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerInterval.setSelection(2) // default: monthly

        // Source account spinner
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.accounts.collect { accounts ->
                    val labels = accounts.map { a ->
                        val suffix = if (a.isVirtual) " (virtuell)" else ""
                        "${a.name}$suffix"
                    }
                    binding.spinnerSourceAccount.adapter =
                        ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
                            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

                    // Pre-fill if editing
                    if (editOrderId != 0L) {
                        vm.orders.value.find { it.id == editOrderId }?.let { order ->
                            prefillForm(order, accounts.indexOfFirst { it.id == order.sourceAccountId })
                        }
                    }
                }
            }
        }

        // Date pickers
        firstExecutionDate = System.currentTimeMillis()
        binding.tvFirstDate.text = dateFmt.format(Date(firstExecutionDate))
        binding.tvFirstDate.setOnClickListener { pickDate { ts -> firstExecutionDate = ts; binding.tvFirstDate.text = dateFmt.format(Date(ts)) } }
        binding.tvLastDate.setOnClickListener { pickDate { ts -> lastExecutionDate = ts; binding.tvLastDate.text = dateFmt.format(Date(ts)) } }
        binding.btnClearLastDate.setOnClickListener { lastExecutionDate = null; binding.tvLastDate.text = getString(R.string.so_no_end_date) }

        // State observer
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    when (state) {
                        is StandingOrderState.Loading -> binding.btnSave.isEnabled = false
                        is StandingOrderState.Success -> {
                            binding.btnSave.isEnabled = true
                            Snackbar.make(view, state.message, Snackbar.LENGTH_LONG).show()
                            vm.resetState()
                            findNavController().navigateUp()
                        }
                        is StandingOrderState.Error -> {
                            binding.btnSave.isEnabled = true
                            Snackbar.make(view, state.message, Snackbar.LENGTH_LONG).show()
                            vm.resetState()
                        }
                        else -> binding.btnSave.isEnabled = true
                    }
                }
            }
        }

        binding.btnSave.setOnClickListener { saveOrder() }
    }

    private fun prefillForm(order: StandingOrder, accountPos: Int) {
        binding.etSoRecipientName.setText(order.recipientName)
        binding.etSoRecipientIban.setText(order.recipientIban)
        binding.etSoRecipientBic.setText(order.recipientBic)
        binding.etSoAmount.setText(order.amount.toString())
        binding.etSoPurpose.setText(order.purpose)
        if (accountPos >= 0) binding.spinnerSourceAccount.setSelection(accountPos)
        firstExecutionDate = order.firstExecutionDate
        lastExecutionDate = order.lastExecutionDate
        binding.tvFirstDate.text = dateFmt.format(Date(firstExecutionDate))
        binding.tvLastDate.text = lastExecutionDate?.let { dateFmt.format(Date(it)) }
            ?: getString(R.string.so_no_end_date)
        val intervalPos = intervalOptions.indexOfFirst { it.first == order.intervalDays }
        if (intervalPos >= 0) binding.spinnerInterval.setSelection(intervalPos)
    }

    private fun saveOrder() {
        val accounts = vm.accounts.value
        val account = accounts.getOrNull(binding.spinnerSourceAccount.selectedItemPosition)
            ?: run { Snackbar.make(requireView(), getString(R.string.error_select_account), Snackbar.LENGTH_SHORT).show(); return }

        val name    = binding.etSoRecipientName.text.toString().trim()
        val iban    = binding.etSoRecipientIban.text.toString().trim().replace(" ", "")
        val bic     = binding.etSoRecipientBic.text.toString().trim()
        val amtStr  = binding.etSoAmount.text.toString().trim()
        val purpose = binding.etSoPurpose.text.toString().trim()
        val interval = intervalOptions[binding.spinnerInterval.selectedItemPosition].first

        if (name.isBlank()) { binding.etSoRecipientName.error = getString(R.string.error_required); return }
        if (iban.isBlank())  { binding.etSoRecipientIban.error = getString(R.string.error_required); return }
        val amount = amtStr.toDoubleOrNull()
        if (amount == null || amount <= 0) { binding.etSoAmount.error = getString(R.string.error_invalid_amount); return }

        val sendToBank = binding.switchSendToBank.isChecked
        if (sendToBank) {
            fintsService.pinProvider = { bankName ->
                pinDialog(requireActivity(), getString(R.string.transfer_pin_title, bankName))
            }
            fintsService.tanProvider = { challenge ->
                tanDialog(requireActivity(), getString(R.string.transfer_tan_title, challenge))
            }
        }

        val order = StandingOrder(
            id                = editOrderId,
            sourceAccountId   = account.id,
            recipientName     = name,
            recipientIban     = iban,
            recipientBic      = bic,
            amount            = amount,
            purpose           = purpose,
            intervalDays      = interval,
            firstExecutionDate = firstExecutionDate,
            lastExecutionDate  = lastExecutionDate,
            nextExecutionDate  = firstExecutionDate
        )

        vm.save(order, sendToBank = sendToBank)
    }

    private fun pickDate(onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            cal.set(y, m, d, 0, 0, 0)
            onPicked(cal.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    override fun onDestroyView() {
        fintsService.pinProvider = null
        fintsService.tanProvider = null
        super.onDestroyView()
        _binding = null
    }
}
