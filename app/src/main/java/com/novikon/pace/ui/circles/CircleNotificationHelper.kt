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

object CircleNotificationHelper {

    private const val CHANNEL_ID = "pace_circle_chat"
    private const val CHANNEL_NAME = "Mensajes del círculo"

    fun showIfNeeded(
        context: Context,
        message: Message,
        currentUserId: String,
        circleName: String,
        circleId: String,
        targetActivityClass: Class<*>,
        isMuted: Boolean = false
    ) {
        if (message.senderId == currentUserId) return
        if (isMuted) return

        val title = context.getString(R.string.notif_new_message_in_circle, circleName)

        val body: String = when (message.type) {
            "SYSTEM" -> message.text.ifBlank { context.getString(R.string.notif_system_message) }
            "EVENT" -> "${message.senderName}: ${context.getString(R.string.notif_new_event, message.senderName, message.eventHabitName)}"
            "EVENT_START" -> context.getString(R.string.notif_event_started, message.eventHabitName)
            "PHOTO" -> "${message.senderName}: ${context.getString(R.string.notif_photo_sent)}"
            else -> {
                val sender = message.senderName.ifBlank { circleName }
                "$sender: ${message.text.ifBlank { "..." }}"
            }
        }

        showNotification(context, title, body, circleId, circleName, targetActivityClass)
    }

    private fun showNotification(
        context: Context,
        title: String,
        body: String,
        circleId: String,
        circleName: String,
        targetActivityClass: Class<*>
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(context)

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
        manager.notify(circleId, 2001, notification)
    }

    fun cancelNotification(context: Context, circleId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(circleId, 2001)
    }

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
