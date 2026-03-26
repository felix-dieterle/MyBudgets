package de.mybudgets.app.ui.transactions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.mybudgets.app.R
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.databinding.ItemTransactionBinding
import de.mybudgets.app.util.CurrencyFormatter
import de.mybudgets.app.util.DateFormatter

class TransactionAdapter(
    private val onClick: (Transaction) -> Unit
) : ListAdapter<Transaction, TransactionAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tx: Transaction) {
            binding.tvDescription.text = tx.description.ifBlank { "Buchung" }
            binding.tvDate.text        = DateFormatter.formatDate(tx.date)
            val sign = if (tx.type == TransactionType.INCOME) "+" else "-"
            binding.tvAmount.text = "$sign${CurrencyFormatter.format(tx.amount)}"
            val color = when (tx.type) {
                TransactionType.INCOME   -> ContextCompat.getColor(binding.root.context, R.color.income_green)
                TransactionType.EXPENSE  -> ContextCompat.getColor(binding.root.context, R.color.expense_red)
                TransactionType.TRANSFER -> ContextCompat.getColor(binding.root.context, R.color.transfer_blue)
            }
            binding.tvAmount.setTextColor(color)
            binding.root.setOnClickListener { onClick(tx) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Transaction>() {
            override fun areItemsTheSame(a: Transaction, b: Transaction) = a.id == b.id
            override fun areContentsTheSame(a: Transaction, b: Transaction) = a == b
        }
    }
}
