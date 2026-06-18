package com.novikon.pace.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.novikon.pace.constants.Language
import com.novikon.pace.constants.PrefsConstants
import com.novikon.pace.data.RealtimeDatabaseManager
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper

// Clase encargada de gestionar todas las preferencias de la app.
// Estrategia de datos (igual que HabitsManager):
//   - Guardar: siempre en local (caché) Y en Firebase
//   - Leer: caché local como fuente inmediata, Firebase para sincronizar
//   - Esto permite que los ajustes funcionen sin internet y que se
//     restauren automáticamente si el usuario cambia de móvil
//
// El idioma del dispositivo es una excepción — se guarda solo en local
// porque depende del dispositivo, no del usuario. Al iniciar sesión,
// el idioma elegido en settings sobreescribe el del dispositivo.
class SettingsManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PrefsConstants.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val databaseManager = RealtimeDatabaseManager()

    companion object {
        private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
        private const val KEY_ACTIVE_DAY_INDICES = "active_day_indices"
        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_FIRST_INSTALL_DATE = "first_install_date"
        private const val KEY_DEVICE_LANGUAGE = "device_language"

        // Claves para cada franja horaria
        private const val KEY_MORNING_ENABLED = "reminder_morning_enabled"
        private const val KEY_MORNING_TIME = "reminder_morning_time"
        private const val KEY_AFTERNOON_ENABLED = "reminder_afternoon_enabled"
        private const val KEY_AFTERNOON_TIME = "reminder_afternoon_time"
        private const val KEY_EVENING_ENABLED = "reminder_evening_enabled"
        private const val KEY_EVENING_TIME = "reminder_evening_time"
        private const val KEY_ALLDAY_ENABLED = "reminder_allday_enabled"
        private const val KEY_ALLDAY_TIME = "reminder_allday_time"
    }

// ── RECORDATORIOS ─────────────────────────────────────────────────────────

    var areRemindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDERS_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_REMINDERS_ENABLED, value) }

    var morningReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_MORNING_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_MORNING_ENABLED, value) }

    var morningReminderTime: String
        get() = prefs.getString(KEY_MORNING_TIME, "08:00") ?: "08:00"
        set(value) = prefs.edit { putString(KEY_MORNING_TIME, value) }

    var afternoonReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_AFTERNOON_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_AFTERNOON_ENABLED, value) }

    var afternoonReminderTime: String
        get() = prefs.getString(KEY_AFTERNOON_TIME, "15:00") ?: "15:00"
        set(value) = prefs.edit { putString(KEY_AFTERNOON_TIME, value) }

    var eveningReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_EVENING_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_EVENING_ENABLED, value) }

    var eveningReminderTime: String
        get() = prefs.getString(KEY_EVENING_TIME, "21:00") ?: "21:00"
        set(value) = prefs.edit { putString(KEY_EVENING_TIME, value) }

    var allDayReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALLDAY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ALLDAY_ENABLED, value) }

    var allDayReminderTime: String
        get() = prefs.getString(KEY_ALLDAY_TIME, "09:00") ?: "09:00"
        set(value) = prefs.edit { putString(KEY_ALLDAY_TIME, value) }

    // ── DÍAS ACTIVOS ──────────────────────────────────────────────────────────

    var activeDayIndices: Set<Int>
        get() {
            if (isFirstRun()) {
                val defaultDays = setOf(0, 1, 2, 3, 4)
                activeDayIndices = defaultDays
                markFirstRunComplete()
                return defaultDays
            }
            return prefs.getStringSet(KEY_ACTIVE_DAY_INDICES, null)
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet()
                ?: setOf(0, 1, 2, 3, 4)
        }
        set(value) = prefs.edit {
            putStringSet(KEY_ACTIVE_DAY_INDICES, value.map { it.toString() }.toSet())
        }

// isFirstRun: evalua una condicion y devuelve true/false.
    private fun isFirstRun(): Boolean = prefs.getBoolean(KEY_FIRST_RUN, true)
    private fun markFirstRunComplete() {
        prefs.edit { putBoolean(KEY_FIRST_RUN, false) }
    }

    // ── HISTORIAL DE CONFIGURACIÓN SEMANAL ───────────────────────────────────

    fun saveWeeklyActiveDays(weekStartDate: String, dayIndices: Set<Int>) {
        val key = "weekly_config_$weekStartDate"
        prefs.edit {
            putStringSet(key, dayIndices.map { it.toString() }.toSet())
        }
    }
    fun getWeeklyActiveDays(weekStartDate: String): Set<Int>? {
        val key = "weekly_config_$weekStartDate"
        return prefs.getStringSet(key, null)
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
    }

    // ── FECHA DE PRIMERA INSTALACIÓN ─────────────────────────────────────────

    var firstInstallDate: String
        get() = prefs.getString(KEY_FIRST_INSTALL_DATE, "") ?: ""
        set(value) = prefs.edit { putString(KEY_FIRST_INSTALL_DATE, value) }

    // ── IDIOMA DEL DISPOSITIVO ────────────────────────────────────────────────

    // Detecta el idioma del dispositivo al primer arranque y lo guarda en local.
    // Si el idioma del dispositivo no está entre los disponibles, usa inglés.
    // Este valor persiste al cerrar sesión — es del dispositivo, no del usuario.
    fun initDeviceLanguageIfNeeded() {
        val savedLanguage = prefs.getString(KEY_DEVICE_LANGUAGE, null)
        if (savedLanguage == null) {
            // Primera vez — detectar idioma del dispositivo
            val deviceLocale = java.util.Locale.getDefault().language
            val supportedCodes = Language.values().map { it.code }
            val languageToUse = if (supportedCodes.contains(deviceLocale)) deviceLocale else "en"
            prefs.edit { putString(KEY_DEVICE_LANGUAGE, languageToUse) }
            LanguageHelper.changeLanguage(context, languageToUse)
        }
    }

    // ── SINCRONIZACIÓN CON FIREBASE ───────────────────────────────────────────

    // Guarda todos los ajustes del usuario en caché local Y en Firebase.
    // Es suspend para que la Activity use lifecycleScope.
    // El idioma del dispositivo no se incluye aquí — es local.
    // El idioma elegido por el usuario en settings sí se guarda en Firebase
    // para restaurarlo al iniciar sesión en otro dispositivo.
    suspend fun saveSettingsToFirebase() {
        databaseManager.saveSettings(
            themeMode = ThemeHelper.getThemeMode(context),
            language = LanguageHelper.getLanguageCode(context),
            remindersEnabled = areRemindersEnabled,
            activeDayIndices = activeDayIndices,
            morningEnabled = morningReminderEnabled,
            morningTime = morningReminderTime,
            afternoonEnabled = afternoonReminderEnabled,
            afternoonTime = afternoonReminderTime,
            eveningEnabled = eveningReminderEnabled,
            eveningTime = eveningReminderTime,
            allDayEnabled = allDayReminderEnabled,
            allDayTime = allDayReminderTime
        )
    }

    // Descarga los ajustes del usuario desde Firebase y los aplica al caché local.
    // Se llama al iniciar sesión para restaurar los ajustes del usuario
    // aunque haya cambiado de dispositivo.
    // Devuelve true si se sincronizó correctamente, false si hubo error o sin red.
    suspend fun syncSettingsFromFirebase(): Boolean {
        return try {
            val settings = databaseManager.getSettings() ?: return false

            // Aplicar tema
            (settings["themeMode"] as? Int)?.let { themeMode ->
                ThemeHelper.setThemeMode(context, themeMode)
            }

            // Aplicar idioma — el de Firebase tiene prioridad sobre el del dispositivo
            // porque el usuario lo eligió explícitamente en settings
            (settings["language"] as? String)?.let { language ->
                LanguageHelper.changeLanguage(context, language)
            }

            // Aplicar recordatorios
            (settings["remindersEnabled"] as? Boolean)?.let { enabled ->
                areRemindersEnabled = enabled
            }
            (settings["morningEnabled"] as? Boolean)?.let { morningReminderEnabled = it }
            (settings["morningTime"] as? String)?.let { morningReminderTime = it }
            (settings["afternoonEnabled"] as? Boolean)?.let { afternoonReminderEnabled = it }
            (settings["afternoonTime"] as? String)?.let { afternoonReminderTime = it }
            (settings["eveningEnabled"] as? Boolean)?.let { eveningReminderEnabled = it }
            (settings["eveningTime"] as? String)?.let { eveningReminderTime = it }
            (settings["allDayEnabled"] as? Boolean)?.let { allDayReminderEnabled = it }
            (settings["allDayTime"] as? String)?.let { allDayReminderTime = it }

            // Aplicar días activos — deserializar de "0,1,2,3,4" a Set<Int>
            (settings["activeDayIndices"] as? String)?.let { indicesStr ->
                if (indicesStr.isNotEmpty()) {
                    activeDayIndices = indicesStr.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .toSet()
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Sincroniza la fecha de primera instalación con Firebase.
    // Si Firebase tiene una fecha anterior a la local, usa la de Firebase
    // (el usuario reinstalό — la fecha real es la más antigua).
    // Si Firebase no tiene ninguna, sube la local.
    suspend fun syncFirstInstallDate(localDate: String): String {
        val remoteDate = databaseManager.getFirstInstallDate()

        return when {
            // Firebase tiene fecha — usar la más antigua entre Firebase y local
            remoteDate != null && remoteDate.isNotEmpty() -> {
                val earliest = if (remoteDate < localDate) remoteDate else localDate
                // Si la local era más reciente (reinstalación), actualizar local con la real
                if (earliest != localDate) {
                    firstInstallDate = earliest
                }
                earliest
            }
            // Firebase no tiene fecha — subir la local
            else -> {
                databaseManager.saveFirstInstallDate(localDate)
                localDate
            }
        }
    }
}