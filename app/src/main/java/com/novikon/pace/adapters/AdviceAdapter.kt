package com.novikon.pace.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.databinding.ItemAdviceSectionBinding

data class AdviceSection(
    val icon: String,
    val title: String,
    val content: String
)

class AdviceAdapter : ListAdapter<AdviceSection, AdviceAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdviceSectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemAdviceSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(section: AdviceSection) {
            binding.tvSectionIcon.text = section.icon
            binding.tvSectionTitle.text = section.title
            binding.tvSectionContent.text = section.content
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AdviceSection>() {
        override fun areItemsTheSame(old: AdviceSection, new: AdviceSection) =
            old.title == new.title

        override fun areContentsTheSame(old: AdviceSection, new: AdviceSection) =
            old == new
    }
}
