package com.novikon.pace.ui.circles

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.novikon.pace.R
import com.novikon.pace.models.Circle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Adapter de circulos: muestra cada grupo en la lista principal de comunidades.
class CirclesAdapter(
    private val onCircleClick: (Circle) -> Unit
) : RecyclerView.Adapter<CirclesAdapter.CircleViewHolder>() {

    private val items = mutableListOf<Circle>()

// ViewHolder de circulo: mantiene referencias a vistas de cada tarjeta.
    class CircleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatarPrimary: ShapeableImageView = itemView.findViewById(R.id.iv_avatar_primary)
        val tvAvatarMore: TextView = itemView.findViewById(R.id.tv_avatar_more)
        val tvName: TextView = itemView.findViewById(R.id.tv_circle_name)
        val tvMembers: TextView = itemView.findViewById(R.id.tv_members_count)
        val tvLastMessage: TextView = itemView.findViewById(R.id.tv_last_message)
        val tvLastTime: TextView = itemView.findViewById(R.id.tv_last_message_time)
    }

// onCreateViewHolder: infla el layout de cada item y crea su ViewHolder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CircleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_circle, parent, false)
        return CircleViewHolder(view)
    }

// onBindViewHolder: vincula los datos del elemento actual con su vista.
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

        // Escalable: 1 avatar principal + badge +N para miembros extra
        val members = circle.memberCount.coerceAtLeast(1)
        val extras = (members - 1).coerceAtLeast(0)

        if (extras > 0) {
            holder.tvAvatarMore.visibility = View.VISIBLE
            holder.tvAvatarMore.text = "+$extras"
        } else {
            holder.tvAvatarMore.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onCircleClick(circle) }
    }

// getItemCount: devuelve la cantidad total de elementos a renderizar.
    override fun getItemCount(): Int = items.size
    fun submitList(newItems: List<Circle>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}