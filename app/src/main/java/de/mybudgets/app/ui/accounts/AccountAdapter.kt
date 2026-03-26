package de.mybudgets.app.ui.accounts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.databinding.ItemAccountBinding
import de.mybudgets.app.util.CurrencyFormatter

class AccountAdapter(
    private val onClick: (Account) -> Unit
) : ListAdapter<Account, AccountAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(acc: Account) {
            binding.tvAccountName.text    = acc.name
            binding.tvAccountBalance.text = CurrencyFormatter.format(acc.balance, acc.currency)
            binding.viewColor.setBackgroundColor(acc.color)
            binding.root.setOnClickListener { onClick(acc) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Account>() {
            override fun areItemsTheSame(a: Account, b: Account) = a.id == b.id
            override fun areContentsTheSame(a: Account, b: Account) = a == b
        }
    }
}
