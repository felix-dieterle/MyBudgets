package de.mybudgets.app.ui.log

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.mybudgets.app.R
import de.mybudgets.app.databinding.ItemLogEntryBinding
import de.mybudgets.app.util.LogEntry
import de.mybudgets.app.util.LogLevel

class LogEntryAdapter : ListAdapter<LogEntry, LogEntryAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemLogEntryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLogEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)
        val ctx = holder.itemView.context
        with(holder.binding) {
            tvLogLevel.text   = entry.level.label
            tvLogTag.text     = entry.tag
            tvLogMessage.text = entry.message

            val color = when (entry.level) {
                LogLevel.DEBUG -> ContextCompat.getColor(ctx, R.color.log_debug)
                LogLevel.INFO  -> ContextCompat.getColor(ctx, R.color.log_info)
                LogLevel.WARN  -> ContextCompat.getColor(ctx, R.color.log_warn)
                LogLevel.ERROR -> ContextCompat.getColor(ctx, R.color.log_error)
            }
            tvLogLevel.setTextColor(color)
            tvLogTag.setTextColor(color)

            if (entry.throwable != null) {
                tvLogThrowable.visibility = View.VISIBLE
                tvLogThrowable.text = entry.throwable.stackTraceToString()
            } else {
                tvLogThrowable.visibility = View.GONE
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<LogEntry>() {
            override fun areItemsTheSame(a: LogEntry, b: LogEntry) = a.id == b.id
            override fun areContentsTheSame(a: LogEntry, b: LogEntry) = a == b
        }
    }
}
