package com.novikon.pace.ui.circles

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
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

// Adapter de mensajes: maneja multiples tipos de burbuja y estados de evento.
class MessagesAdapter(
    private val currentUserId: String,
    private val onJoinEvent: (Message) -> Unit,
    private val onDeclineEvent: (Message) -> Unit,
    private val onCaptureMoment: (Message) -> Unit,
    private val onDeleteMessage: (Message) -> Unit,
    // Callback para abrir una foto en pantalla completa al clicarla
    private val onPhotoClick: (String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class ChatItem {
        data class MessageItem(val message: Message) : ChatItem()
        data class DateHeader(val dateText: String) : ChatItem()
    }
    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_SYSTEM = 3
        private const val VIEW_TYPE_EVENT = 4
        private const val VIEW_TYPE_EVENT_START = 5
        private const val VIEW_TYPE_PHOTO = 6
        private const val VIEW_TYPE_DATE_HEADER = 7

        private const val EVENT_CAPTURE_WINDOW_MS = 60 * 60 * 1000L
        private fun formatTime(timestamp: Long): String {
            if (timestamp == 0L) return ""
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        private fun formatDateTime(timestamp: Long): String {
            if (timestamp == 0L) return "-"
            return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        private fun resolveTemplateText(message: Message, context: Context): String {
            val key = message.messageTemplateKey
            if (key.isBlank()) return message.text

            val resId = context.resources.getIdentifier(key, "string", context.packageName)
            if (resId == 0) return message.text

            return runCatching {
                if (message.messageTemplateParams.isEmpty()) {
                    context.getString(resId)
                } else {
                    context.getString(resId, *message.messageTemplateParams.toTypedArray())
                }
            }.getOrElse { message.text }
        }
    }

    private val items = mutableListOf<ChatItem>()
    private fun formatDateHeader(timestamp: Long): String {
        val msgCal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val todayCal = java.util.Calendar.getInstance()
        val yesterdayCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }

        return when {
            msgCal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
                    msgCal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR) ->
                itemView_context?.getString(R.string.date_header_today) ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))

            msgCal.get(java.util.Calendar.YEAR) == yesterdayCal.get(java.util.Calendar.YEAR) &&
                    msgCal.get(java.util.Calendar.DAY_OF_YEAR) == yesterdayCal.get(java.util.Calendar.DAY_OF_YEAR) ->
                itemView_context?.getString(R.string.date_header_yesterday) ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))

            else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }
    private var itemView_context: Context? = null
    private val ioExecutor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    fun addMessage(message: Message) {
        // Comprobar si hay que insertar separador de fecha
        val lastMessage = items.filterIsInstance<ChatItem.MessageItem>().lastOrNull()?.message
        val needsHeader = if (lastMessage == null) {
            true
        } else {
            val lastCal = java.util.Calendar.getInstance().apply { timeInMillis = lastMessage.timestamp }
            val newCal = java.util.Calendar.getInstance().apply { timeInMillis = message.timestamp }
            lastCal.get(java.util.Calendar.DAY_OF_YEAR) != newCal.get(java.util.Calendar.DAY_OF_YEAR) ||
                    lastCal.get(java.util.Calendar.YEAR) != newCal.get(java.util.Calendar.YEAR)
        }

        if (needsHeader && message.timestamp > 0L) {
            val dateText = formatDateHeader(message.timestamp)
            items.add(ChatItem.DateHeader(dateText))
            notifyItemInserted(items.size - 1)
        }

        items.add(ChatItem.MessageItem(message))
        notifyItemInserted(items.size - 1)
    }

    fun removeMessage(messageId: String) {
        val idx = items.indexOfFirst { it is ChatItem.MessageItem && it.message.id == messageId }
        if (idx == -1) return
        items.removeAt(idx)
        notifyItemRemoved(idx)
        // Si el item anterior era un DateHeader y el siguiente también lo es (o no existe), eliminar el header huérfano
        if (idx > 0 && items.getOrNull(idx - 1) is ChatItem.DateHeader) {
            if (idx >= items.size || items[idx] is ChatItem.DateHeader) {
                items.removeAt(idx - 1)
                notifyItemRemoved(idx - 1)
            }
        }
    }

    fun updateMessage(message: Message) {
        val idx = items.indexOfFirst { it is ChatItem.MessageItem && it.message.id == message.id }
        if (idx == -1) return
        items[idx] = ChatItem.MessageItem(message)
        notifyItemChanged(idx)
    }

    fun refreshTemporalStates() {
        notifyDataSetChanged()
    }
    override fun getItemViewType(position: Int): Int {
        return when (val item = items[position]) {
            is ChatItem.DateHeader -> VIEW_TYPE_DATE_HEADER
            is ChatItem.MessageItem -> {
                val msg = item.message
                when (msg.type) {
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
        }
    }

// onCreateViewHolder: infla el layout de cada item y crea su ViewHolder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        itemView_context = parent.context
        return when (viewType) {
            VIEW_TYPE_SENT -> SentMessageViewHolder(
                inflater.inflate(R.layout.item_message_sent, parent, false),
                onDeleteMessage
            )
            VIEW_TYPE_RECEIVED -> ReceivedMessageViewHolder(
                inflater.inflate(R.layout.item_message_received, parent, false)
            )
            VIEW_TYPE_EVENT -> EventMessageViewHolder(
                inflater.inflate(R.layout.item_message_event, parent, false),
                currentUserId,
                onJoinEvent,
                onDeclineEvent
            )
            VIEW_TYPE_EVENT_START -> EventStartViewHolder(
                inflater.inflate(R.layout.item_message_event_start, parent, false),
                currentUserId,
                onCaptureMoment
            )
            VIEW_TYPE_PHOTO -> PhotoMessageViewHolder(
                inflater.inflate(R.layout.item_message_photo, parent, false),
                ioExecutor,
                mainHandler,
                onPhotoClick
            )
            VIEW_TYPE_DATE_HEADER -> DateHeaderViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_system, parent, false)
            )
            else -> SystemMessageViewHolder(
                inflater.inflate(R.layout.item_message_system, parent, false)
            )
        }
    }

// onBindViewHolder: vincula los datos del elemento actual con su vista.
override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (val item = items[position]) {
        is ChatItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item.dateText)
        is ChatItem.MessageItem -> {
            val message = item.message
            when (holder) {
                is SentMessageViewHolder -> holder.bind(message)
                is ReceivedMessageViewHolder -> holder.bind(message)
                is SystemMessageViewHolder -> holder.bind(message)
                is EventMessageViewHolder -> holder.bind(message)
                is EventStartViewHolder -> holder.bind(message)
                is PhotoMessageViewHolder -> holder.bind(message, currentUserId)
            }
        }
    }
}

// getItemCount: devuelve la cantidad total de elementos a renderizar.
    override fun getItemCount(): Int = items.size

// ViewHolder de mensaje enviado: muestra contenido publicado por el usuario actual.
    class SentMessageViewHolder(
        itemView: View,
        private val onDeleteMessage: (Message) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvText: TextView = itemView.findViewById(R.id.tv_message_text)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_message_time)
        fun bind(message: Message) {
            tvText.text = resolveTemplateText(message, itemView.context)
            tvTime.text = formatTime(message.timestamp)

            itemView.setOnLongClickListener { anchor ->
                val popup = PopupMenu(anchor.context, anchor)
                popup.menu.add(0, 1, 0, anchor.context.getString(R.string.delete_message))
                popup.setOnMenuItemClickListener { item ->
                    if (item.itemId == 1) {
                        onDeleteMessage(message)
                        true
                    } else {
                        false
                    }
                }
                popup.show()
                true
            }
        }
    }

// ViewHolder de mensaje recibido: pinta mensajes enviados por otros miembros.
    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSenderName: TextView = itemView.findViewById(R.id.tv_sender_name)
        private val tvText: TextView = itemView.findViewById(R.id.tv_message_text)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_message_time)
        fun bind(message: Message) {
            tvSenderName.text = message.senderName
            tvText.text = resolveTemplateText(message, itemView.context)
            tvTime.text = formatTime(message.timestamp)
        }
    }

// ViewHolder de sistema: renderiza avisos automáticos del chat.
    class SystemMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSystemMessage: TextView = itemView.findViewById(R.id.tv_system_message)
        fun bind(message: Message) {
            tvSystemMessage.text = resolveTemplateText(message, itemView.context)
        }
    }

// ViewHolder de evento: presenta invitaciones/eventos y acciones de respuesta.
    class EventMessageViewHolder(
        itemView: View,
        private val currentUserId: String,
        private val onJoinEvent: (Message) -> Unit,
        private val onDeclineEvent: (Message) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_event_title)
        private val tvSchedule: TextView = itemView.findViewById(R.id.tv_event_schedule)
        private val tvJoined: TextView = itemView.findViewById(R.id.tv_event_joined)
        private val tvDeclined: TextView = itemView.findViewById(R.id.tv_event_declined)
        private val btnJoin: MaterialButton = itemView.findViewById(R.id.btn_event_join)
        private val btnDecline: MaterialButton = itemView.findViewById(R.id.btn_event_decline)
        fun bind(message: Message) {
            val context = itemView.context
            tvTitle.text = context.getString(R.string.event_title_format, message.eventHabitName)
            tvSchedule.text = context.getString(
                R.string.event_datetime_format,
                formatDateTime(message.eventScheduledAt)
            )

            val joined = if (message.eventJoinedNames.isEmpty()) {
                context.getString(R.string.event_none_placeholder)
            } else {
                message.eventJoinedNames.joinToString(", ")
            }

            val declined = if (message.eventDeclinedNames.isEmpty()) {
                context.getString(R.string.event_none_placeholder)
            } else {
                message.eventDeclinedNames.joinToString(", ")
            }

            tvJoined.text = if (message.eventJoinedNames.isEmpty())
                context.getString(R.string.event_none_placeholder)
            else
                message.eventJoinedNames.joinToString(", ")

            tvDeclined.text = if (message.eventDeclinedNames.isEmpty())
                context.getString(R.string.event_none_placeholder)
            else
                message.eventDeclinedNames.joinToString(", ")

            val isCreator = message.eventCreatedBy == currentUserId
            val alreadyJoined = message.eventJoinedIds.contains(currentUserId)
            val alreadyDeclined = message.eventDeclinedIds.contains(currentUserId)
            val eventStarted = message.eventScheduledAt > 0L && System.currentTimeMillis() >= message.eventScheduledAt

            if (isCreator || eventStarted) {
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

// ViewHolder de inicio de evento: representa cuando una actividad ya comenzó.
    class EventStartViewHolder(
        itemView: View,
        private val currentUserId: String,
        private val onCaptureMoment: (Message) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvText: TextView = itemView.findViewById(R.id.tv_event_started_text)
        private val btnCapture: MaterialButton = itemView.findViewById(R.id.btn_capture_moment)
        fun bind(message: Message) {
            tvText.text = resolveTemplateText(message, itemView.context)

            val now = System.currentTimeMillis()
            val canCaptureByMember = message.captureAllowedIds.contains(currentUserId)
            val withinCaptureWindow = message.timestamp > 0L && now <= (message.timestamp + EVENT_CAPTURE_WINDOW_MS)
            val canCapture = canCaptureByMember && withinCaptureWindow

            btnCapture.visibility = View.VISIBLE
            btnCapture.isEnabled = canCapture
            btnCapture.alpha = if (canCapture) 1f else 0.45f

            btnCapture.setOnClickListener {
                if (canCapture) onCaptureMoment(message)
            }
        }
    }

    // ViewHolder de foto: muestra capturas compartidas en el contexto del evento.
    class PhotoMessageViewHolder(
        itemView: View,
        private val ioExecutor: java.util.concurrent.ExecutorService,
        private val mainHandler: Handler,
        // Callback para pantalla completa al pulsar la imagen
        private val onPhotoClick: (String) -> Unit = {}
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvSender: TextView = itemView.findViewById(R.id.tv_photo_sender)
        private val ivPhoto: ImageView = itemView.findViewById(R.id.iv_photo_message)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_photo_time)
        // Contenedor raíz del item para controlar la alineación
        private val container: android.widget.LinearLayout = itemView as android.widget.LinearLayout

        fun bind(message: Message, currentUserId: String) {
            val isOwnPhoto = message.senderId == currentUserId

            // Nombre del emisor: solo visible si es de otro usuario
            tvSender.visibility = if (isOwnPhoto) View.GONE else View.VISIBLE
            tvSender.text = message.senderName
            tvTime.text = formatTime(message.timestamp)

            // Alinear el contenedor: mis fotos a la derecha, las ajenas a la izquierda
            container.gravity = if (isOwnPhoto) {
                android.view.Gravity.END
            } else {
                android.view.Gravity.START
            }

            ivPhoto.setImageDrawable(null)
            val url = message.photoUrl
            if (url.isBlank()) return

            // Abrir imagen en pantalla completa al pulsar
            ivPhoto.setOnClickListener { onPhotoClick(url) }

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

    class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvText: TextView = itemView.findViewById(R.id.tv_system_message)
        fun bind(dateText: String) {
            tvText.text = dateText
        }
    }
}