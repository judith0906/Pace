package com.novikon.pace.ui.circles

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.novikon.pace.R
import com.novikon.pace.models.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessagesAdapter(
    private val currentUserId: String,
    private val onJoinEvent: (Message) -> Unit,
    private val onDeclineEvent: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_SYSTEM = 3
        private const val VIEW_TYPE_EVENT = 4

        private fun formatTime(timestamp: Long): String {
            if (timestamp == 0L) return ""
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }

        private fun formatDateTime(timestamp: Long): String {
            if (timestamp == 0L) return "-"
            return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }

    private val messages = mutableListOf<Message>()

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateMessage(message: Message) {
        val idx = messages.indexOfFirst { it.id == message.id }
        if (idx == -1) return
        messages[idx] = message
        notifyItemChanged(idx)
    }

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when {
            msg.type == "EVENT" -> VIEW_TYPE_EVENT
            msg.senderId == "system" || msg.type == "SYSTEM" -> VIEW_TYPE_SYSTEM
            msg.senderId == currentUserId -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            }

            VIEW_TYPE_RECEIVED -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }

            VIEW_TYPE_EVENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_event, parent, false)
                EventMessageViewHolder(view, currentUserId, onJoinEvent, onDeclineEvent)
            }

            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_system, parent, false)
                SystemMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
            is SystemMessageViewHolder -> holder.bind(message)
            is EventMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvText: TextView = itemView.findViewById(R.id.tv_message_text)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_message_time)

        fun bind(message: Message) {
            tvText.text = message.text
            tvTime.text = formatTime(message.timestamp)
        }
    }

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

    class SystemMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSystemMessage: TextView = itemView.findViewById(R.id.tv_system_message)

        fun bind(message: Message) {
            tvSystemMessage.text = message.text
        }
    }

    class EventMessageViewHolder(
        itemView: View,
        private val currentUserId: String,
        private val onJoinEvent: (Message) -> Unit,
        private val onDeclineEvent: (Message) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_event_title)
        private val tvSchedule: TextView = itemView.findViewById(R.id.tv_event_schedule)
        private val tvParticipants: TextView = itemView.findViewById(R.id.tv_event_participants)
        private val btnJoin: MaterialButton = itemView.findViewById(R.id.btn_event_join)
        private val btnDecline: MaterialButton = itemView.findViewById(R.id.btn_event_decline)

        fun bind(message: Message) {
            tvTitle.text = "Evento: ${message.eventHabitName}"
            tvSchedule.text = "Fecha y hora: ${formatDateTime(message.eventScheduledAt)}"

            val joined = if (message.eventJoinedNames.isEmpty()) "-" else message.eventJoinedNames.joinToString(", ")
            val declined = if (message.eventDeclinedNames.isEmpty()) "-" else message.eventDeclinedNames.joinToString(", ")

            tvParticipants.text = "Van: $joined\nDeclinan: $declined"

            val isCreator = message.eventCreatedBy == currentUserId
            val alreadyJoined = message.eventJoinedIds.contains(currentUserId)
            val alreadyDeclined = message.eventDeclinedIds.contains(currentUserId)

            if (isCreator) {
                btnJoin.visibility = View.GONE
                btnDecline.visibility = View.GONE
            } else {
                btnJoin.visibility = View.VISIBLE
                btnDecline.visibility = View.VISIBLE

                btnJoin.isEnabled = !alreadyJoined
                btnDecline.isEnabled = !alreadyDeclined

                btnJoin.setOnClickListener { onJoinEvent(message) }
                btnDecline.setOnClickListener { onDeclineEvent(message) }
            }
        }
    }
}