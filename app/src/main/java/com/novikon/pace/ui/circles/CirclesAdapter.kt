package com.novikon.pace.ui.circles

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import com.novikon.pace.models.Circle
import java.text.SimpleDateFormat
import java.util.*

// Adapter del RecyclerView de la lista de círculos.
// Usa ListAdapter con DiffUtil para que solo se redibujen
// los ítems que realmente cambiaron — más eficiente que notifyDataSetChanged().
//
// Cada ítem usa item_circle.xml, que ya tienes definido.
// El adapter rellena: nombre, número de miembros, último mensaje y hora.
class CirclesAdapter(
    private val onCircleClick: (Circle) -> Unit
) : ListAdapter<Circle, CirclesAdapter.CircleViewHolder>(CircleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CircleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_circle, parent, false)
        return CircleViewHolder(view)
    }

    override fun onBindViewHolder(holder: CircleViewHolder, position: Int) {
        holder.bind(getItem(position), onCircleClick)
    }

    class CircleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvCircleName: TextView = itemView.findViewById(R.id.tv_circle_name)
        private val tvMembersCount: TextView = itemView.findViewById(R.id.tv_members_count)

        // tv_last_message y tv_last_message_time son opcionales —
        // si no están en tu item_circle.xml actual, simplemente no harán nada
        private val tvLastMessage: TextView? = itemView.findViewById(R.id.tv_last_message)
        private val tvLastMessageTime: TextView? = itemView.findViewById(R.id.tv_last_message_time)

        fun bind(circle: Circle, onCircleClick: (Circle) -> Unit) {
            tvCircleName.text = circle.name
            tvMembersCount.text = buildMembersText(circle.memberCount)

            // Preview del último mensaje — solo si el layout lo tiene
            tvLastMessage?.apply {
                if (circle.lastMessage.isNotBlank()) {
                    text = circle.lastMessage
                    visibility = View.VISIBLE
                } else {
                    text = itemView.context.getString(R.string.no_messages_yet)
                    visibility = View.VISIBLE
                }
            }

            // Hora del último mensaje — solo si el layout lo tiene
            tvLastMessageTime?.apply {
                if (circle.lastMessageTime > 0) {
                    text = formatMessageTime(circle.lastMessageTime)
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }

            itemView.setOnClickListener { onCircleClick(circle) }
        }

        private fun buildMembersText(count: Int): String {
            return when (count) {
                1 -> "1 miembro"
                else -> "$count miembros"
            }
        }

        // Formatea el timestamp del último mensaje para mostrarlo en la lista:
        //   - Si es de hoy        → "14:32"
        //   - Si es de esta semana → "Lun", "Mar", etc.
        //   - Si es más antiguo   → "12/03"
        private fun formatMessageTime(timestamp: Long): String {
            val msgDate = Calendar.getInstance().apply { timeInMillis = timestamp }
            val now = Calendar.getInstance()

            return when {
                isSameDay(msgDate, now) -> {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
                }
                isThisWeek(msgDate, now) -> {
                    SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
                        .replaceFirstChar { it.uppercaseChar() }
                }
                else -> {
                    SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestamp))
                }
            }
        }

        private fun isSameDay(a: Calendar, b: Calendar): Boolean =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                    a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

        private fun isThisWeek(msgDate: Calendar, now: Calendar): Boolean {
            val diff = now.timeInMillis - msgDate.timeInMillis
            return diff < 7 * 24 * 60 * 60 * 1000L
        }
    }

    // DiffCallback: compara círculos por ID para detectar si un ítem
    // es el mismo objeto, y por contenido para saber si hay que redibujarlo.
    private class CircleDiffCallback : DiffUtil.ItemCallback<Circle>() {
        override fun areItemsTheSame(oldItem: Circle, newItem: Circle): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Circle, newItem: Circle): Boolean =
            oldItem == newItem
    }
}