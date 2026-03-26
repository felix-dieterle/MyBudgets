package de.mybudgets.app.ui.standingorders

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.mybudgets.app.data.model.StandingOrder
import de.mybudgets.app.databinding.ItemStandingOrderBinding
import de.mybudgets.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StandingOrderAdapter(
    private val onClick: (StandingOrder) -> Unit,
    private val onDelete: (StandingOrder) -> Unit
) : ListAdapter<StandingOrder, StandingOrderAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val b: ItemStandingOrderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(order: StandingOrder) {
            val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
            b.tvRecipientName.text = order.recipientName
            b.tvIban.text = formatIban(order.recipientIban)
            b.tvAmount.text = CurrencyFormatter.format(order.amount, "EUR")
            b.tvNextExecution.text = "Nächste Ausführung: ${fmt.format(Date(order.nextExecutionDate))}"
            b.tvInterval.text = intervalLabel(order.intervalDays)
            b.ivBankSent.visibility = if (order.sentToBank) android.view.View.VISIBLE else android.view.View.GONE
            b.root.setOnClickListener { onClick(order) }
            b.btnDelete.setOnClickListener { onDelete(order) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemStandingOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    private fun formatIban(iban: String): String =
        iban.chunked(4).joinToString(" ")

    private fun intervalLabel(days: Int): String = when (days) {
        7    -> "Wöchentlich"
        14   -> "Alle 2 Wochen"
        30   -> "Monatlich"
        90   -> "Vierteljährlich"
        180  -> "Halbjährlich"
        365  -> "Jährlich"
        else -> "Alle $days Tage"
    }

    companion object DiffCallback : DiffUtil.ItemCallback<StandingOrder>() {
        override fun areItemsTheSame(a: StandingOrder, b: StandingOrder) = a.id == b.id
        override fun areContentsTheSame(a: StandingOrder, b: StandingOrder) = a == b
    }
}
