package com.novikon.pace.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.novikon.pace.receivers.ReminderReceiver
import java.util.Calendar

object ReminderScheduler {

    // Identificadores únicos por día+franja para los PendingIntent.
    // Formato: dayIndex * 10 + franja (0=mañana, 1=tarde, 2=noche, 3=todo el día)
    // Ejemplo: Martes mañana = 1*10+0 = 10, Martes tarde = 1*10+1 = 11
    // Así nunca colisionan entre sí ni con las alarmas de días activos.
    private fun requestCode(dayIndex: Int, slot: Int) = dayIndex * 10 + slot

    fun scheduleReminders(
        context: Context,
        areRemindersEnabled: Boolean,
        activeDayIndices: Set<Int>,
        morningEnabled: Boolean,
        morningTime: String,
        afternoonEnabled: Boolean,
        afternoonTime: String,
        eveningEnabled: Boolean,
        eveningTime: String,
        allDayEnabled: Boolean,
        allDayTime: String
    ) {
        cancelReminders(context)

        if (!areRemindersEnabled || activeDayIndices.isEmpty()) return

        data class Slot(val enabled: Boolean, val time: String, val slotIndex: Int)

        val slots = listOf(
            Slot(morningEnabled, morningTime, 0),
            Slot(afternoonEnabled, afternoonTime, 1),
            Slot(eveningEnabled, eveningTime, 2),
            Slot(allDayEnabled, allDayTime, 3)
        )

        activeDayIndices.forEach { dayIndex ->
            slots.filter { it.enabled }.forEach { slot ->
                val (hour, minute) = slot.time.split(":").map { it.toIntOrNull() ?: 0 }
                scheduleForDay(context, dayIndex, hour, minute, slot.slotIndex)
            }
        }
    }

    private fun scheduleForDay(
        context: Context,
        dayIndex: Int,
        hour: Int,
        minute: Int,
        slotIndex: Int
    ) {
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
            set(Calendar.DAY_OF_WEEK, calendarDay)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("day_index", dayIndex)
            putExtra("slot_index", slotIndex)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(dayIndex, slotIndex),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

    fun cancelReminders(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 7 días × 4 franjas = 28 combinaciones posibles
        for (dayIndex in 0..6) {
            for (slotIndex in 0..3) {
                val intent = Intent(context, ReminderReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode(dayIndex, slotIndex),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
            }
        }
    }
}