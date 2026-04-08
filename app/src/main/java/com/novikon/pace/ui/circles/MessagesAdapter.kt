package com.novikon.pace.ui.circles

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import com.novikon.pace.models.Message
import java.text.SimpleDateFormat
import java.util.*

// Adapter del RecyclerView del chat.
// Usa dos tipos de ítem (VIEW_TYPE_SENT y VIEW_TYPE_RECEIVED)
// para mostrar las burbujas propias a la derecha y las ajenas a la izquierda,
// igual que cualquier aplicación de mensajería.
//
// Los mensajes se añaden uno a uno con addMessage() cuando llegan
// del listener en tiempo real — no se usa submitList() ni DiffUtil
// porque los mensajes de chat nunca se eliminan ni reordenan,
// siempre se añaden al final.
class MessagesAdapter(
    private val currentUserId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        // Formatea el timestamp del mensaje como "HH:mm" (ej: "14:32")
        // para mostrar la hora de cada burbuja del chat.
        fun formatTime(timestamp: Long): String {
            if (timestamp == 0L) return ""
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }

    private val messages = mutableListOf<Message>()

    // Añade un mensaje al final de la lista y notifica al RecyclerView.
    // Llamado desde CircleChatActivity cada vez que llega un onChildAdded de Firebase.
    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    // ── VIEW HOLDERS ──────────────────────────────────────────────────────────

    // Burbuja propia (enviada) — alineada a la derecha.
    // Solo muestra texto y hora — no muestra nombre porque el usuario
    // ya sabe que son sus propios mensajes.
    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvText: TextView = itemView.findViewById(R.id.tv_message_text)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_message_time)

        fun bind(message: Message) {
            tvText.text = message.text
            tvTime.text = formatTime(message.timestamp)
        }
    }

    // Burbuja ajena (recibida) — alineada a la izquierda.
    // Muestra nombre del remitente encima del texto para que se sepa
    // quién lo envió, especialmente en grupos con muchos miembros.
    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSenderName: TextView = itemView.findViewById(R.id.tv_sender_name)
        private val tvText: TextView = itemView.findViewById(R.id.tv_message_text)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_message_time)

        fun bind(message: Message) {
            tvSenderName.text = message.senderName
            tvText.text = message.text
            tvTime.text = formatTime(message.timestamp)
        }
    }
}