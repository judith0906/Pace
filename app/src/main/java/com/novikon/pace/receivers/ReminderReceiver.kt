package com.novikon.pace.receivers

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.novikon.pace.R
import com.novikon.pace.ui.main.MainActivity
import java.util.Calendar

// BroadcastReceiver que se ejecuta cuando llega la hora de un recordatorio.
// Android lo despierta automáticamente gracias a la alarma que programó
// ReminderScheduler con AlarmManager.
//
// Hace dos cosas:
//   1. Muestra la notificación al usuario
//   2. Reprograma la alarma para la semana siguiente
//      (porque setExactAndAllowWhileIdle no se repite solo)
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        // ID del canal de notificaciones — requerido desde Android 8.
        // Debe ser único dentro de la app.
        private const val CHANNEL_ID = "pace_reminders"

        // ID de la notificación — si usamos el mismo ID, Android
        // actualiza la notificación existente en lugar de crear una nueva.
        private const val NOTIFICATION_ID = 1001
    }
    override fun onReceive(context: Context, intent: Intent) {
        val dayIndex = intent.getIntExtra("day_index", -1)
        val slotIndex = intent.getIntExtra("slot_index", -1)
        val hour = intent.getIntExtra("hour", 9)
        val minute = intent.getIntExtra("minute", 0)

        createNotificationChannel(context)
        showNotification(context)

        if (dayIndex >= 0 && slotIndex >= 0) {
            reprogramAlarm(context, dayIndex, slotIndex, hour, minute)
        }
    }

    // Crea el canal de notificaciones.
    // Desde Android 8 (Oreo) es obligatorio crear un canal antes de
    // mostrar cualquier notificación. Si el canal ya existe, no pasa nada
    // — Android ignora la llamada sin error.
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH  // aparece en pantalla aunque esté bloqueada
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Construye y muestra la notificación.
    // Al pulsar la notificación, abre MainActivity.
    private fun showNotification(context: Context) {
        // Intent que se lanzará al pulsar la notificación
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // PendingIntent necesario para que Android pueda lanzar
        // el intent cuando el usuario pulse la notificación
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pace_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)  // la notificación desaparece al pulsar
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // Reprograma la alarma para exactamente una semana después.
    // Es necesario porque setExactAndAllowWhileIdle no se repite
    // automáticamente — hay que reprogramarla manualmente cada vez.
    private fun reprogramAlarm(context: Context, dayIndex: Int, slotIndex: Int, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val calendarDay = when (dayIndex) {
            0 -> Calendar.MONDAY
            1 -> Calendar.TUESDAY
            2 -> Calendar.WEDNESDAY
            3 -> Calendar.THURSDAY
            4 -> Calendar.FRIDAY
            5 -> Calendar.SATURDAY
            6 -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }

        val calendar = Calendar.getInstance().apply {
            add(Calendar.WEEK_OF_YEAR, 1)
            set(Calendar.DAY_OF_WEEK, calendarDay)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("day_index", dayIndex)
            putExtra("slot_index", slotIndex)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            dayIndex * 10 + slotIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }
}