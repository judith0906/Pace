package com.novikon.pace.ui.circles

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import com.novikon.pace.models.Circle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CirclesAdapter(
    private val onCircleClick: (Circle) -> Unit
) : RecyclerView.Adapter<CirclesAdapter.CircleViewHolder>() {

    private val items = mutableListOf<Circle>()

    class CircleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val membersAvatarView: CircleMembersAvatarView = itemView.findViewById(R.id.members_avatar_view)
        val tvName: TextView = itemView.findViewById(R.id.tv_circle_name)
        val tvMembers: TextView = itemView.findViewById(R.id.tv_members_count)
        val tvLastMessage: TextView = itemView.findViewById(R.id.tv_last_message)
        val tvLastTime: TextView = itemView.findViewById(R.id.tv_last_message_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CircleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_circle, parent, false)
        return CircleViewHolder(view)
    }

    override fun onBindViewHolder(holder: CircleViewHolder, position: Int) {
        val circle = items[position]
        val context = holder.itemView.context

        holder.tvName.text = circle.name
        holder.tvMembers.text = context.getString(R.string.members_count_format, circle.memberCount)
        holder.tvLastMessage.text = circle.lastMessage.ifBlank {
            context.getString(R.string.no_messages_yet)
        }
        holder.tvLastTime.text = if (circle.lastMessageTime > 0L) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(circle.lastMessageTime))
        } else {
            ""
        }

        holder.membersAvatarView.loadMembers(circle.memberPhotoUrls, circle.memberCount)
        holder.itemView.setOnClickListener { onCircleClick(circle) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<Circle>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onViewRecycled(holder: CircleViewHolder) {
        super.onViewRecycled(holder)
        holder.membersAvatarView.loadMembers(emptyList(), 0)
    }
}