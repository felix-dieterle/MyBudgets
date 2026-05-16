package de.mybudgets.app.ui.categories

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.databinding.ItemCategoryPickerBinding

class CategoryPickerAdapter(
    private val onItemClick: (Category) -> Unit
) : ListAdapter<Category, CategoryPickerAdapter.VH>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCategoryPickerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        private val binding: ItemCategoryPickerBinding,
        private val onItemClick: (Category) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: Category) {
            binding.tvCategoryIcon.text = category.icon
            binding.tvCategoryName.text = category.name
            binding.tvChevron.isVisible = category.level < 3
            binding.root.setOnClickListener { onItemClick(category) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(old: Category, new: Category) = old.id == new.id
        override fun areContentsTheSame(old: Category, new: Category) = old == new
    }
}
