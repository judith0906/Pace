package com.novikon.pace.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.novikon.pace.utils.SettingsManager
import com.novikon.pace.utils.ReminderScheduler

// BroadcastReceiver que se ejecuta automáticamente cuando el móvil arranca.
// Es necesario porque AlarmManager pierde todas las alarmas programadas
// cuando el dispositivo se apaga o reinicia.
//
// Android lo despierta cuando detecta el evento BOOT_COMPLETED,
// y nosotros aprovechamos para reprogramar los recordatorios.
// Para que funcione debe estar registrado en el AndroidManifest.xml
// con el permiso RECEIVE_BOOT_COMPLETED.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            val settingsManager = SettingsManager(context)

            if (settingsManager.areRemindersEnabled) {
                ReminderScheduler.scheduleReminders(
                    context = context,
                    areRemindersEnabled = settingsManager.areRemindersEnabled,
                    activeDayIndices = settingsManager.activeDayIndices,
                    morningEnabled = settingsManager.morningReminderEnabled,
                    morningTime = settingsManager.morningReminderTime,
                    afternoonEnabled = settingsManager.afternoonReminderEnabled,
                    afternoonTime = settingsManager.afternoonReminderTime,
                    eveningEnabled = settingsManager.eveningReminderEnabled,
                    eveningTime = settingsManager.eveningReminderTime,
                    allDayEnabled = settingsManager.allDayReminderEnabled,
                    allDayTime = settingsManager.allDayReminderTime
                )
            }
        }
    }
}