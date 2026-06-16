package com.novikon.pace.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.novikon.pace.constants.PrefsConstants
import com.novikon.pace.models.DailyHabitLog
import com.novikon.pace.models.Habit
import kotlin.coroutines.resume

// Clase intermediaria entre la UI y Firebase.
// La UI nunca habla directamente con Firebase — siempre pasa por aquí.
//
// Estrategia de datos:
//   - Guardar: siempre en local (caché) Y en Firebase
//   - Leer: primero Firebase, si falla usa el caché local como respaldo
//   - Esto permite que la app funcione sin internet y que el historial
//     se conserve si el usuario cambia de móvil
//
// Todos los hábitos (predefinidos y personalizados) se guardan en Firebase.
class HabitsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PrefsConstants.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val databaseManager = RealtimeDatabaseManager()

    companion object {
        private const val KEY_SELECTED_HABITS = "selected_habits"
        private const val KEY_HABIT_LOGS = "habit_logs"
        private const val KEY_HABITS_CONFIGURED = "habits_configured"
        private const val KEY_CURRENT_USER_ID = "current_user_id"
    }

    // ── GESTIÓN DE USUARIO ────────────────────────────────────────────────────

    // Guarda el id del usuario actual y limpia el caché si
    // cambió de usuario — para no mezclar datos de distintos usuarios.
    fun setCurrentUserId(userId: String) {
        val previousUserId = getCurrentUserId()

        if (previousUserId != userId && previousUserId.isNotEmpty()) {
            clearLocalCache()
        }

        prefs.edit { putString(KEY_CURRENT_USER_ID, userId) }
    }

    // Devuelve el identificador del usuario activo guardado en el dispositivo.
    fun getCurrentUserId(): String {
        return prefs.getString(KEY_CURRENT_USER_ID, "") ?: ""
    }

    // Elimina todos los datos del caché local del usuario actual.
    // Se llama cuando se detecta un cambio de usuario.
    private fun clearLocalCache() {
        prefs.edit {
            remove(KEY_SELECTED_HABITS)
            remove(KEY_HABIT_LOGS)
            remove(KEY_HABITS_CONFIGURED)
        }
    }

    // ── HÁBITOS SELECCIONADOS ─────────────────────────────────────────────────

    // Guarda los hábitos seleccionados en local Y en Firebase.
    // Es suspend para que la Activity use lifecycleScope — sin CoroutineScope huérfanos.
    // Devuelve true si Firebase confirmó el guardado, false si hubo error.
    suspend fun saveSelectedHabits(habits: List<Habit>): Boolean {
        // Guardar en caché local primero — así funciona aunque no haya internet
        prefs.edit {
            putString(KEY_SELECTED_HABITS, gson.toJson(habits))
            putBoolean(KEY_HABITS_CONFIGURED, true)
        }

        // Guardar en Firebase — tanto predefinidos como personalizados
        return databaseManager.saveHabits(habits)
    }

    // Recupera los hábitos desde Firebase.
    // Si Firebase falla o devuelve vacío, usa el caché local como respaldo.
    suspend fun getSelectedHabitsAsync(): List<Habit> {
        return try {
            val habitsFromFirebase = databaseManager.getHabits()

            if (habitsFromFirebase.isNotEmpty()) {
                // Actualizar caché local con los datos de Firebase
                prefs.edit {
                    putString(KEY_SELECTED_HABITS, gson.toJson(habitsFromFirebase))
                    putBoolean(KEY_HABITS_CONFIGURED, true)
                }
                habitsFromFirebase
            } else {
                // Firebase vacío — usar caché local como respaldo
                getSelectedHabitsFromCache()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Error de red o Firebase — usar caché local como respaldo
            getSelectedHabitsFromCache()
        }
    }

    // Devuelve los hábitos del caché local sin consultar Firebase.
    // Se usa como respaldo cuando Firebase no está disponible.
    fun getSelectedHabitsFromCache(): List<Habit> {
        val json = prefs.getString(KEY_SELECTED_HABITS, null) ?: return emptyList()
        val type = object : TypeToken<List<Habit>>() {}.type
        return gson.fromJson(json, type)
    }

    // ── REGISTRO DIARIO ───────────────────────────────────────────────────────

    // Guarda el registro de un hábito en un día concreto en local Y en Firebase.
    // Es suspend para que la Activity use lifecycleScope — sin CoroutineScope huérfanos.
    // Devuelve true si Firebase confirmó el guardado, false si hubo error.
    suspend fun logHabit(habitId: String, date: String, isDone: Boolean): Boolean {
        // Recuperar metadatos del log existente si ya fue inicializado,
        // para no sobreescribir nombre/emoji/duración con vacíos al marcar/desmarcar
        val existingLog = getHabitLogsForDate(date).find { it.habitId == habitId }

        // Si no hay log existente, buscar metadatos en los hábitos guardados
        val habit = if (existingLog?.habitName?.isNotEmpty() == true) null
        else getSelectedHabitsFromCache().find { it.id == habitId }

        val log = DailyHabitLog(
            habitId = habitId,
            date = date,
            isDone = isDone,
            timestamp = System.currentTimeMillis(),
            source = "MANUAL",
            eventId = "",
            habitName = existingLog?.habitName?.ifEmpty { habit?.name ?: "" } ?: (habit?.name ?: ""),
            habitEmoji = existingLog?.habitEmoji?.ifEmpty { habit?.emoji ?: "" } ?: (habit?.emoji ?: ""),
            habitDuration = existingLog?.habitDuration?.ifEmpty { habit?.duration ?: "" } ?: (habit?.duration ?: ""),
            isEventHabit = false
        )
        return logHabit(log)
    }

    // Inicializa los registros del día actual en Firebase con todos los hábitos
    // en estado no completado. Se llama al abrir DailyHabitsActivity si el día
    // no tiene logs todavía. Así el historial siempre tiene la lista completa
    // de hábitos de ese día, aunque el usuario no toque ninguno.
    suspend fun initializeDayLogsIfNeeded(date: String): Boolean {
        val existingLogs = getHabitLogsForDate(date).filter { !it.isEventHabit }
        if (existingLogs.isNotEmpty()) return true // ya inicializado

        val habits = getSelectedHabitsAsync()
        if (habits.isEmpty()) return false

        var allSaved = true
        habits.forEach { habit ->
            val log = DailyHabitLog(
                habitId = habit.id,
                date = date,
                isDone = false,
                timestamp = System.currentTimeMillis(),
                source = "MANUAL",
                eventId = "",
                habitName = habit.name,
                habitEmoji = habit.emoji,
                habitDuration = habit.duration,
                isEventHabit = false
            )
            val saved = logHabit(log)
            if (!saved) allSaved = false
        }
        return allSaved
    }

    // Devuelve todos los registros del caché local.
    fun getHabitLogs(): List<DailyHabitLog> {
        val json = prefs.getString(KEY_HABIT_LOGS, null) ?: return emptyList()
        val type = object : TypeToken<List<DailyHabitLog>>() {}.type
        return gson.fromJson(json, type)
    }

    // Devuelve los registros de un día concreto filtrando el caché local.
    fun getHabitLogsForDate(date: String): List<DailyHabitLog> {
        return getHabitLogs().filter { it.date == date }
    }

    // ── SINCRONIZACIÓN ────────────────────────────────────────────────────────

    // Descarga todos los datos del usuario desde Firebase y actualiza el caché local.
    // Se llama al arrancar la app en MainActivity para asegurarse de que
    // el caché está actualizado, especialmente si el usuario cambió de móvil.
    suspend fun syncFromFirebase(): Boolean {
        return try {
            // Sincronizar hábitos
            val habits = databaseManager.getHabits()
            if (habits.isNotEmpty()) {
                prefs.edit {
                    putString(KEY_SELECTED_HABITS, gson.toJson(habits))
                    putBoolean(KEY_HABITS_CONFIGURED, true)
                }
            }

            // Sincronizar registros diarios
            val logs = databaseManager.getHabitLogs()
            if (logs.isNotEmpty()) {
                prefs.edit { putString(KEY_HABIT_LOGS, gson.toJson(logs)) }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ── CONFIGURACIÓN INICIAL ─────────────────────────────────────────────────

    // Comprueba si el usuario ya tiene hábitos configurados consultando Firebase.
    // Si Firebase falla, usa el caché local como respaldo.
    // Se usa en MainActivity para saber si hay que mostrar la pantalla
    // de selección de hábitos la primera vez.
    suspend fun areHabitsConfiguredAsync(): Boolean {
        return try {
            databaseManager.getHabits().isNotEmpty()
        } catch (e: Exception) {
            prefs.getBoolean(KEY_HABITS_CONFIGURED, false)
        }
    }

    // Actualiza el registro diario en caché local para ese hábito y fecha.
    // Si ya existe, lo reemplaza para evitar duplicados en el mismo día.
    private fun saveLogToLocalCache(log: DailyHabitLog) {
        val logs = getHabitLogs().toMutableList()
        logs.removeAll { it.habitId == log.habitId && it.date == log.date }
        logs.add(log)
        prefs.edit { putString(KEY_HABIT_LOGS, gson.toJson(logs)) }
    }

    // Guarda un registro diario completo tanto en caché local como en Firebase.
    // Se usa cuando ya tenemos construido el objeto DailyHabitLog.
    suspend fun logHabit(log: DailyHabitLog): Boolean {
        saveLogToLocalCache(log)
        return databaseManager.saveHabitLog(log)
    }

    // Crea un registro histórico cuando el usuario se une a un evento grupal.
    // Convierte el hábito del evento en una entrada del historial diario.
    suspend fun logJoinedEventToHistory(
        eventId: String,
        habitLabel: String,
        scheduledAtMillis: Long,
        eventTimeZoneId: String
    ): Boolean {
        val tzId = if (eventTimeZoneId.isBlank()) {
            java.util.TimeZone.getDefault().id
        } else {
            eventTimeZoneId
        }

        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone(tzId)
        }
        val eventDate = dateFormat.format(java.util.Date(scheduledAtMillis))

        // Parse simple: "💪 Hacer ejercicio" -> emoji + nombre
        val trimmed = habitLabel.trim()
        val firstSpace = trimmed.indexOf(' ')
        val emoji = if (firstSpace > 0) trimmed.substring(0, firstSpace) else "📅"
        val name = if (firstSpace > 0 && firstSpace + 1 < trimmed.length) {
            trimmed.substring(firstSpace + 1)
        } else {
            trimmed.ifBlank { "Evento" }
        }

        val eventHabitId = "event_join_$eventId"

        val log = DailyHabitLog(
            habitId = eventHabitId,
            date = eventDate,
            isDone = true,
            timestamp = System.currentTimeMillis(),
            source = "EVENT_JOIN",
            eventId = eventId,
            habitName = name,
            habitEmoji = emoji,
            habitDuration = "Evento",
            isEventHabit = true
        )

        return logHabit(log)
    }

    // Elimina registros del caché local por id de hábito.
    // Si se pasa fecha, borra solo ese día; si no, borra todos los días del hábito.
    private fun removeLogFromLocalCache(habitId: String, date: String? = null) {
        val logs = getHabitLogs().toMutableList()
        if (date == null) {
            logs.removeAll { it.habitId == habitId }
        } else {
            logs.removeAll { it.habitId == habitId && it.date == date }
        }
        prefs.edit { putString(KEY_HABIT_LOGS, gson.toJson(logs)) }
    }

    // Elimina del historial local y remoto la marca de un evento al que se unió.
    // Se usa cuando el usuario cambia su respuesta y deja de participar.
    suspend fun removeJoinedEventFromHistory(eventId: String): Boolean {
        val eventHabitId = "event_join_$eventId"

        // 1) quitar de caché local
        removeLogFromLocalCache(eventHabitId, null)

        // 2) quitar de Firebase
        val userId = databaseManager.getUserId() ?: return false
        val logRef = com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("users/$userId/habit_logs")

        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            logRef.orderByChild("habitId").equalTo(eventHabitId)
                .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        if (!snapshot.exists()) {
                            cont.resume(true)
                            return
                        }

                        val updates = mutableMapOf<String, Any?>()
                        snapshot.children.forEach { child ->
                            child.key?.let { key ->
                                updates["users/$userId/habit_logs/$key"] = null
                            }
                        }

                        com.google.firebase.database.FirebaseDatabase.getInstance()
                            .reference
                            .updateChildren(updates)
                            .addOnSuccessListener { cont.resume(true) }
                            .addOnFailureListener { cont.resume(false) }
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        cont.resume(false)
                    }
                })
        }
    }

    // Elimina todos los logs de un día concreto del caché local y de Firebase.
// Se llama en loadHabits() antes de reinicializar el día, para que los hábitos
// recién añadidos o deseleccionados queden reflejados correctamente.
// Devuelve true si Firebase confirmó el borrado, false si hubo error.
    suspend fun clearDayLogs(date: String): Boolean {
        // 1) Borrar del caché local
        val logs = getHabitLogs().toMutableList()
        logs.removeAll { it.date == date }
        prefs.edit { putString(KEY_HABIT_LOGS, gson.toJson(logs)) }

        // 2) Borrar de Firebase
        val userId = databaseManager.getUserId() ?: return false
        val logsRef = com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("users/$userId/habit_logs")

        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            logsRef.orderByChild("date").equalTo(date)
                .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        if (!snapshot.exists()) {
                            cont.resume(true)
                            return
                        }

                        val updates = mutableMapOf<String, Any?>()
                        snapshot.children.forEach { child ->
                            child.key?.let { key ->
                                updates["users/$userId/habit_logs/$key"] = null
                            }
                        }

                        com.google.firebase.database.FirebaseDatabase.getInstance()
                            .reference
                            .updateChildren(updates)
                            .addOnSuccessListener { cont.resume(true) }
                            .addOnFailureListener { cont.resume(false) }
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        cont.resume(false)
                    }
                })
        }
    }

    // Elimina todos los datos locales del usuario.
    // Se llama al cerrar sesión para no dejar datos huérfanos en el dispositivo.
    fun clearLocalData() {
        prefs.edit { clear() }
    }
}