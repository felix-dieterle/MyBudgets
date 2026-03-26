package de.mybudgets.app.ui.categories

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.mybudgets.app.data.model.Category
import de.mybudgets.app.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val onClick: (Category) -> Unit
) : ListAdapter<Category, CategoryAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(cat: Category) {
            binding.tvCategoryName.text  = cat.name
            binding.tvCategoryLevel.text = "L${cat.level}"
            binding.viewCategoryColor.setBackgroundColor(cat.color)
            binding.root.setOnClickListener { onClick(cat) }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, v: Int) =
        VH(ItemCategoryBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(a: Category, b: Category) = a.id == b.id
            override fun areContentsTheSame(a: Category, b: Category) = a == b
        }
    }
}
