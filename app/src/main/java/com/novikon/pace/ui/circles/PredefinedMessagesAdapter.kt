package com.novikon.pace.ui.circles

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R

data class PredefinedMessageOption(
    val templateKey: String,
    val fallbackText: String
)

class PredefinedMessagesAdapter(
    private val onClick: (PredefinedMessageOption) -> Unit
) : RecyclerView.Adapter<PredefinedMessagesAdapter.OptionViewHolder>() {

    private val items = mutableListOf<PredefinedMessageOption>()
    private var sectionEmoji: String = "✨"

    fun submitData(emoji: String, messages: List<PredefinedMessageOption>) {
        sectionEmoji = emoji
        items.clear()
        items.addAll(messages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_predefined_message, parent, false)
        return OptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
        holder.bind(items[position], sectionEmoji, onClick)
    }

    override fun getItemCount(): Int = items.size

    class OptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: CardView = itemView.findViewById(R.id.card_message_option)
        private val tvEmoji: TextView = itemView.findViewById(R.id.tv_option_emoji)
        private val tvText: TextView = itemView.findViewById(R.id.tv_option_text)

        fun bind(
            option: PredefinedMessageOption,
            emoji: String,
            onClick: (PredefinedMessageOption) -> Unit
        ) {
            tvEmoji.text = emoji
            tvText.text = option.fallbackText
            card.setOnClickListener { onClick(option) }
        }
    }
}