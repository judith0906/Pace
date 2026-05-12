package com.novikon.pace.ui.circles

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.novikon.pace.R
import com.novikon.pace.models.Message

// Helper para mostrar notificaciones tipo WhatsApp del chat del círculo.
// Se lanza cada vez que llega un mensaje nuevo de otro usuario u evento.
object CircleNotificationHelper {

    // Canal dedicado a mensajes del círculo — separado del canal de recordatorios.
    private const val CHANNEL_ID = "pace_circle_chat"
    private const val CHANNEL_NAME = "Mensajes del círculo"

    // Muestra una notificación si el mensaje no es propio y la app está en segundo plano.
    fun showIfNeeded(
        context: Context,
        message: Message,
        currentUserId: String,
        circleName: String,
        circleId: String,
        targetActivityClass: Class<*>
    ) {
        // No notificar mensajes propios
        if (message.senderId == currentUserId) return

        val title: String
        val body: String

        // Construye título y cuerpo según el tipo de mensaje
        when (message.type) {
            "EVENT" -> {
                title = circleName
                body = context.getString(R.string.notif_new_event, message.senderName, message.eventHabitName)
            }
            "EVENT_START" -> {
                title = circleName
                body = context.getString(R.string.notif_event_started, message.eventHabitName)
            }
            "PHOTO" -> {
                title = message.senderName.ifBlank { circleName }
                body = context.getString(R.string.notif_photo_sent)
            }
            "SYSTEM" -> {
                title = circleName
                body = message.text.ifBlank { context.getString(R.string.notif_system_message) }
            }
            else -> {
                // Mensaje normal de texto o template (join/decline de evento, etc.)
                title = message.senderName.ifBlank { circleName }
                body = message.text.ifBlank { "..." }
            }
        }

        showNotification(context, title, body, circleId, circleName, targetActivityClass)
    }

    // Construye y lanza la notificación con el canal correcto.
    private fun showNotification(
        context: Context,
        title: String,
        body: String,
        circleId: String,
        circleName: String,
        targetActivityClass: Class<*>
    ) {
        ensureChannel(context)

        // Al pulsar la notificación se abre el chat del círculo correspondiente
        val intent = Intent(context, targetActivityClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(CircleChatActivity.EXTRA_CIRCLE_ID, circleId)
            putExtra(CircleChatActivity.EXTRA_CIRCLE_NAME, circleName)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            circleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pace_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            // Vibración y sonido por defecto, igual que WhatsApp
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Usamos el circleId como tag y un ID fijo por canal, así cada círculo
        // tiene su propia notificación (se agrupa por círculo, no por mensaje)
        manager.notify(circleId, 2001, notification)
    }

    // Crea el canal de notificaciones si no existe (requerido desde Android 8).
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mensajes y eventos de tus círculos"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}