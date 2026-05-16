package de.mybudgets.app.ui.recurringrules

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.R
import de.mybudgets.app.data.model.RecurringRule
import de.mybudgets.app.databinding.FragmentRecurringRulesBinding
import de.mybudgets.app.databinding.ItemRecurringRuleBinding
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.util.DateFormatter
import de.mybudgets.app.viewmodel.RecurringRuleViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecurringRulesFragment : Fragment() {

    private var _binding: FragmentRecurringRulesBinding? = null
    private val binding get() = _binding!!
    private val vm: RecurringRuleViewModel by viewModels()
    private lateinit var adapter: RuleAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentRecurringRulesBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = RuleAdapter(
            onToggle = { vm.toggleActive(it) },
            onDelete = { vm.delete(it) }
        )
        binding.rvRules.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.rules.collect { list ->
                    adapter.submitList(list)
                    binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

private class RuleAdapter(
    private val onToggle: (RecurringRule) -> Unit,
    private val onDelete: (RecurringRule) -> Unit
) : ListAdapter<RecurringRule, RuleAdapter.VH>(object : DiffUtil.ItemCallback<RecurringRule>() {
    override fun areItemsTheSame(a: RecurringRule, b: RecurringRule) = a.id == b.id
    override fun areContentsTheSame(a: RecurringRule, b: RecurringRule) = a == b
}) {
    inner class VH(val b: ItemRecurringRuleBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemRecurringRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val rule = getItem(position)
        val ctx = holder.itemView.context
        with(holder.b) {
            tvRuleName.text = rule.name
            switchActive.isChecked = rule.isActive

            val amount = rule.matchAmount?.let { CurrencyFormatter.format(it, "EUR") } ?: "–"
            val interval = if (rule.intervalDays % 30 == 0) "alle ${rule.intervalDays / 30} Mon." else "alle ${rule.intervalDays} Tage"
            tvRuleDetail.text = "$amount · $interval · \"${rule.matchKeyword}\""
            tvRuleMatched.text = if (rule.accountId != null) "Konto #${rule.accountId}" else "Alle Konten"

            root.setOnClickListener { onToggle(rule) }
            root.setOnLongClickListener {
                androidx.appcompat.app.AlertDialog.Builder(ctx)
                    .setTitle(rule.name)
                    .setMessage("Regel löschen?")
                    .setPositiveButton("Löschen") { _, _ -> onDelete(rule) }
                    .setNegativeButton("Abbrechen", null)
                    .show()
                true
            }
            switchActive.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != rule.isActive) onToggle(rule)
            }
        }
    }
}
