package com.novikon.pace.receivers

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.novikon.pace.R
import com.novikon.pace.ui.circles.CircleChatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PaceFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            com.novikon.pace.data.FCMTokenManager().saveToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data ?: return
        val title = data["title"] ?: return
        val body = data["body"] ?: return
        val circleId = data["circleId"] ?: return
        val circleName = data["circleName"] ?: ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel()

        val intent = Intent(this, CircleChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(CircleChatActivity.EXTRA_CIRCLE_ID, circleId)
            putExtra(CircleChatActivity.EXTRA_CIRCLE_NAME, circleName)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            circleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pace_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        NotificationManagerCompat.from(this).notify(circleId, 2001, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Mensajes del círculo",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mensajes y eventos de tus círculos"
                enableVibration(true)
            }
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "pace_circle_chat"
    }
}
