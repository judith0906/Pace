package com.novikon.pace.ui.circles

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.novikon.pace.R
import com.novikon.pace.models.Message
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MessagesAdapter(
    private val currentUserId: String,
    private val onJoinEvent: (Message) -> Unit,
    private val onDeclineEvent: (Message) -> Unit,
    private val onCaptureMoment: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_SYSTEM = 3
        private const val VIEW_TYPE_EVENT = 4
        private const val VIEW_TYPE_EVENT_START = 5
        private const val VIEW_TYPE_PHOTO = 6

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
    private val ioExecutor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

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
        return when (msg.type) {
            "EVENT" -> VIEW_TYPE_EVENT
            "EVENT_START" -> VIEW_TYPE_EVENT_START
            "PHOTO" -> VIEW_TYPE_PHOTO
            "SYSTEM" -> VIEW_TYPE_SYSTEM
            else -> {
                if (msg.senderId == "system") VIEW_TYPE_SYSTEM
                else if (msg.senderId == currentUserId) VIEW_TYPE_SENT
                else VIEW_TYPE_RECEIVED
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> SentMessageViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false))
            VIEW_TYPE_RECEIVED -> ReceivedMessageViewHolder(inflater.inflate(R.layout.item_message_received, parent, false))
            VIEW_TYPE_EVENT -> EventMessageViewHolder(inflater.inflate(R.layout.item_message_event, parent, false), currentUserId, onJoinEvent, onDeclineEvent)
            VIEW_TYPE_EVENT_START -> EventStartViewHolder(inflater.inflate(R.layout.item_message_event_start, parent, false), currentUserId, onCaptureMoment)
            VIEW_TYPE_PHOTO -> PhotoMessageViewHolder(inflater.inflate(R.layout.item_message_photo, parent, false), ioExecutor, mainHandler)
            else -> SystemMessageViewHolder(inflater.inflate(R.layout.item_message_system, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
            is SystemMessageViewHolder -> holder.bind(message)
            is EventMessageViewHolder -> holder.bind(message)
            is EventStartViewHolder -> holder.bind(message)
            is PhotoMessageViewHolder -> holder.bind(message, currentUserId)
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

    class EventStartViewHolder(
        itemView: View,
        private val currentUserId: String,
        private val onCaptureMoment: (Message) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvText: TextView = itemView.findViewById(R.id.tv_event_started_text)
        private val btnCapture: MaterialButton = itemView.findViewById(R.id.btn_capture_moment)

        fun bind(message: Message) {
            tvText.text = message.text
            val canCapture = message.captureAllowedIds.contains(currentUserId)

            btnCapture.visibility = if (canCapture) View.VISIBLE else View.GONE
            btnCapture.setOnClickListener { onCaptureMoment(message) }
        }
    }

    class PhotoMessageViewHolder(
        itemView: View,
        private val ioExecutor: java.util.concurrent.ExecutorService,
        private val mainHandler: Handler
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvSender: TextView = itemView.findViewById(R.id.tv_photo_sender)
        private val ivPhoto: ImageView = itemView.findViewById(R.id.iv_photo_message)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_photo_time)

        fun bind(message: Message, currentUserId: String) {
            tvSender.visibility = if (message.senderId == currentUserId) View.GONE else View.VISIBLE
            tvSender.text = message.senderName
            tvTime.text = formatTime(message.timestamp)

            ivPhoto.setImageDrawable(null)
            val url = message.photoUrl
            if (url.isBlank()) return

            ioExecutor.execute {
                runCatching {
                    URL(url).openStream().use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }.onSuccess { bitmap ->
                    if (bitmap != null) {
                        mainHandler.post { ivPhoto.setImageBitmap(bitmap) }
                    }
                }
            }
        }
    }
}