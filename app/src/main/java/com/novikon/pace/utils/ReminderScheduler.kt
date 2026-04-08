package com.novikon.pace.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.novikon.pace.receivers.ReminderReceiver
import java.util.Calendar

// Objeto encargado de programar y cancelar las alarmas de recordatorio.
// Usa AlarmManager — el sistema de Android para ejecutar código
// en un momento concreto, incluso si la app está cerrada.
//
// Es un "object" porque no necesita estado propio.
//
// Recibe los datos que necesita como parámetros en lugar de
// crear un SettingsManager internamente — así esta clase no depende
// de SettingsManager y es más fácil de reutilizar y entender.
object ReminderScheduler {

    // Programa las alarmas de recordatorio.
    // Primero cancela las alarmas anteriores para evitar duplicados,
    // luego programa una alarma por cada día activo.
    //
    // Parámetros:
    //   - context: necesario para acceder a AlarmManager
    //   - areRemindersEnabled: si los recordatorios están activados
    //   - activeDayIndices: conjunto de días activos (0=Lun ... 6=Dom)
    //   - reminderTime: hora en formato "HH:mm" (ej: "20:00")
    fun scheduleReminders(
        context: Context,
        areRemindersEnabled: Boolean,
        activeDayIndices: Set<Int>,
        reminderTime: String
    ) {
        // Cancelar siempre las alarmas anteriores antes de programar nuevas,
        // para evitar tener alarmas duplicadas o con horarios viejos
        cancelReminders(context)

        // Si los recordatorios están desactivados o no hay días activos,
        // no programamos nada
        if (!areRemindersEnabled || activeDayIndices.isEmpty()) {
            return
        }

        // Parsear la hora configurada (ej: "20:00" → hour=20, minute=0)
        val timeParts = reminderTime.split(":")
        val hour = timeParts[0].toIntOrNull() ?: 20
        val minute = timeParts[1].toIntOrNull() ?: 0

        // Programar una alarma por cada día activo
        activeDayIndices.forEach { dayIndex ->
            scheduleForDay(context, dayIndex, hour, minute)
        }
    }

    // Programa una alarma para un día concreto de la semana.
    // Si ese día ya pasó esta semana, la programa para la semana siguiente.
    private fun scheduleForDay(context: Context, dayIndex: Int, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Convertir nuestro índice (0=Lun, 1=Mar ... 6=Dom)
        // al formato que usa Calendar (Calendar.MONDAY, Calendar.TUESDAY...)
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

        // Calcular el momento exacto de la próxima ocurrencia
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, calendarDay)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Si ese momento ya pasó esta semana, saltar a la semana siguiente
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }

        // Intent que se lanzará cuando suene la alarma
        // Le pasamos el dayIndex para que ReminderReceiver pueda
        // reprogramar la alarma para la semana siguiente
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("day_index", dayIndex)
        }

        // PendingIntent — un "permiso" que le damos a Android para que
        // ejecute nuestro intent en el futuro, aunque la app esté cerrada.
        // Cada día tiene un requestCode único (dayIndex) para que no se sobreescriban.
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            dayIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // setExactAndAllowWhileIdle garantiza que la alarma suene
        // exactamente a la hora indicada, incluso si el móvil está
        // en modo ahorro de batería (disponible desde Android 6)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    // Cancela todas las alarmas programadas (una por cada día de la semana).
    // Se llama antes de reprogramar para evitar duplicados,
    // y cuando el usuario desactiva los recordatorios.
    fun cancelReminders(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (dayIndex in 0..6) {
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                dayIndex,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}