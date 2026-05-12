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
// Solo lanza notificación si el mensaje no es propio y la app está en segundo plano.
object CircleNotificationHelper {

    // Canal dedicado a mensajes del círculo — separado del canal de recordatorios.
    private const val CHANNEL_ID = "pace_circle_chat"
    private const val CHANNEL_NAME = "Mensajes del círculo"

    // Muestra una notificación si el mensaje no es propio.
    // La actividad ya comprueba el foreground antes de llamar a este método.
    fun showIfNeeded(
        context: Context,
        message: Message,
        currentUserId: String,
        circleName: String,
        circleId: String,
        targetActivityClass: Class<*>
    ) {
        // No notificar nunca mensajes propios
        if (message.senderId == currentUserId) return

        val title: String
        val body: String

        // Título = nombre del emisor o del círculo según el tipo de mensaje.
        // El nombre del creador/emisor aparece siempre que esté disponible.
        when (message.type) {
            "EVENT" -> {
                // Quién ha creado el evento y sobre qué hábito
                title = circleName
                body = context.getString(R.string.notif_new_event, message.senderName, message.eventHabitName)
            }
            "EVENT_START" -> {
                // Inicio automático de evento — no tiene emisor humano
                title = circleName
                body = context.getString(R.string.notif_event_started, message.eventHabitName)
            }
            "PHOTO" -> {
                // Nombre del que envía la foto como título (igual que WhatsApp)
                title = message.senderName.ifBlank { circleName }
                body = context.getString(R.string.notif_photo_sent)
            }
            "SYSTEM" -> {
                title = circleName
                body = message.text.ifBlank { context.getString(R.string.notif_system_message) }
            }
            else -> {
                // Mensaje de texto normal: título = nombre del emisor
                title = message.senderName.ifBlank { circleName }
                body = message.text.ifBlank { "..." }
            }
        }

        showNotification(context, title, body, circleId, circleName, targetActivityClass)
    }

    // Construye y lanza la notificación con el canal correcto.
    // Usa circleId como tag → una sola notificación por círculo, se reemplaza si llega otra.
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
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Tag = circleId: si ya existe una notificación de este círculo se reemplaza,
        // no se acumulan. Al entrar al chat se cancela con cancelNotification().
        manager.notify(circleId, 2001, notification)
    }

    // Cancela la notificación del círculo al entrar en su chat.
    // Llamar desde onResume de CircleChatActivity.
    fun cancelNotification(context: Context, circleId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(circleId, 2001)
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