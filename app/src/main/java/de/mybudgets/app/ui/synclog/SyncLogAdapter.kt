package de.mybudgets.app.ui.synclog

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.mybudgets.app.R
import de.mybudgets.app.data.model.SyncLogEntry
import de.mybudgets.app.data.repository.SyncLogRepository
import de.mybudgets.app.databinding.ItemSyncLogBinding
import java.text.DateFormat
import java.util.Date

/**
 * Ein Eintrag je Phase eines Sync-Laufs. Die Richtung steckt im Pfeil, ob der Schritt
 * geklappt hat in der Farbe (gruen/rot) - ein Blick genuegt, um den Lauf zu lesen.
 */
class SyncLogAdapter : ListAdapter<SyncLogEntry, SyncLogAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemSyncLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSyncLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)
        val ctx = holder.itemView.context
        val color = ContextCompat.getColor(
            ctx,
            if (entry.success) R.color.sync_status_up_to_date else R.color.sync_status_error
        )
        with(holder.binding) {
            ivSyncLogDirection.setImageResource(directionIcon(entry.direction))
            ivSyncLogDirection.imageTintList = ColorStateList.valueOf(color)
            tvSyncLogArea.text = entry.area
            tvSyncLogMessage.text = entry.message
            tvSyncLogMessage.visibility = if (entry.message.isBlank()) View.GONE else View.VISIBLE
            tvSyncLogTime.text = TIMESTAMP_FORMAT.format(Date(entry.timestamp))
        }
    }

    private fun directionIcon(direction: String): Int = when (direction) {
        SyncLogRepository.DIRECTION_PULL -> R.drawable.ic_sync_arrow_pull
        SyncLogRepository.DIRECTION_PUSH -> R.drawable.ic_sync_arrow_push
        SyncLogRepository.DIRECTION_BOTH -> R.drawable.ic_sync_syncing
        else -> R.drawable.ic_sync_up_to_date
    }

    companion object {
        private val TIMESTAMP_FORMAT: DateFormat =
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)

        private val DIFF = object : DiffUtil.ItemCallback<SyncLogEntry>() {
            override fun areItemsTheSame(a: SyncLogEntry, b: SyncLogEntry) = a.id == b.id
            override fun areContentsTheSame(a: SyncLogEntry, b: SyncLogEntry) = a == b
        }
    }
}
