package de.mybudgets.app.ui.transactions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.mybudgets.app.R
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.data.model.TransactionWithCategory
import de.mybudgets.app.databinding.ItemTransactionBinding
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.util.DateFormatter

class TransactionAdapter(
    private val onClick: (TransactionWithCategory) -> Unit
) : ListAdapter<TransactionWithCategory, TransactionAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TransactionWithCategory) {
            val tx = item.transaction
            val description = tx.description.ifBlank { "Buchung" }
            binding.tvDescription.text = description

            if (tx.isRecurring) {
                binding.ivRecurring.visibility = View.VISIBLE
                binding.ivRecurring.setOnClickListener {
                    val detail = if (tx.recurringIntervalDays > 0) {
                        "↻ alle ${tx.recurringIntervalDays} Tage"
                    } else {
                        "↻ wiederkehrend"
                    }
                    binding.root.performClick()
                }
            } else {
                binding.ivRecurring.visibility = View.GONE
            }

            if (item.category != null) {
                binding.tvCategory.text = "${item.category.icon} ${item.category.name}"
                binding.tvCategory.visibility = View.VISIBLE
            } else {
                binding.tvCategory.visibility = View.GONE
            }

            binding.tvDate.text = DateFormatter.formatDate(tx.date)
            val sign = if (tx.type == TransactionType.INCOME) "+" else "-"
            binding.tvAmount.text = "$sign${CurrencyFormatter.format(tx.amount)}"
            val color = when (tx.type) {
                TransactionType.INCOME   -> ContextCompat.getColor(binding.root.context, R.color.income_green)
                TransactionType.EXPENSE  -> ContextCompat.getColor(binding.root.context, R.color.expense_red)
                TransactionType.TRANSFER -> ContextCompat.getColor(binding.root.context, R.color.transfer_blue)
            }
            binding.tvAmount.setTextColor(color)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TransactionWithCategory>() {
            override fun areItemsTheSame(a: TransactionWithCategory, b: TransactionWithCategory) =
                a.transaction.id == b.transaction.id
            override fun areContentsTheSame(a: TransactionWithCategory, b: TransactionWithCategory) =
                a.transaction == b.transaction && a.category == b.category
        }
    }
}
