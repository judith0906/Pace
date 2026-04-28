package com.novikon.pace.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.novikon.pace.models.DailyHabitLog
import com.novikon.pace.models.Habit
import com.novikon.pace.models.HabitCategory
import com.novikon.pace.models.TimeOfDay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Clase encargada de comunicarse directamente con Firebase Realtime Database.
// Es la única clase de la app que sabe cómo leer y escribir en Firebase —
// el resto de clases usan HabitsManager y SettingsManager, que usan esta clase internamente.
//
// Todas sus funciones son suspend — se ejecutan en una coroutine
// y devuelven el resultado cuando Firebase responde.
//
// La estructura en Firebase es:
// users/{userId}/
//   habits/      → hábitos seleccionados por el usuario
//   habit_logs/  → registros diarios (clave: {date}_{habitId})
//   settings/    → ajustes del usuario (tema, idioma, recordatorios...)
//   profile/     → datos de perfil (displayName, email)
//   devices/     → dispositivos con sesión activa (clave: deviceId)
class RealtimeDatabaseManager {

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Devuelve el id del usuario autenticado actualmente,
    // o null si no hay ninguno. Se usa como clave raíz en Firebase.
    fun getUserId(): String? = auth.currentUser?.uid

    // ── HÁBITOS ───────────────────────────────────────────────────────────────

    // Guarda la lista de hábitos seleccionados en Firebase.
    // Usa setValue() que reemplaza t*do el nodo — si el usuario
    // cambia sus hábitos, los anteriores se eliminan automáticamente.
    // Devuelve true si se guardó correctamente, false si hubo error.
    suspend fun saveHabits(habits: List<Habit>): Boolean {
        val userId = getUserId() ?: return false

        return suspendCancellableCoroutine { continuation ->
            val habitsMap = habits.associate { habit ->
                habit.id to mapOf(
                    "id" to habit.id,
                    "name" to habit.name,
                    "emoji" to habit.emoji,
                    "duration" to habit.duration,
                    "category" to habit.category.name,
                    "timeOfDay" to habit.timeOfDay.name,
                    "isCustom" to habit.isCustom
                )
            }

            database.getReference("users/$userId/habits")
                .setValue(habitsMap)
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume(false)
                }
        }
    }

    // Recupera los hábitos guardados en Firebase para el usuario actual.
    // Construye cada Habit manualmente desde el snapshot porque Firebase
    // devuelve los datos como un Map, no como objetos Kotlin.
    // Devuelve una lista vacía si no hay hábitos o si hubo error.
    suspend fun getHabits(): List<Habit> {
        val userId = getUserId() ?: return emptyList()

        return suspendCancellableCoroutine { continuation ->
            database.getReference("users/$userId/habits")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val habits = mutableListOf<Habit>()

                        snapshot.children.forEach { habitSnapshot ->
                            try {
                                val habit = Habit(
                                    id = habitSnapshot.child("id").getValue(String::class.java) ?: "",
                                    name = habitSnapshot.child("name").getValue(String::class.java) ?: "",
                                    emoji = habitSnapshot.child("emoji").getValue(String::class.java) ?: "",
                                    duration = habitSnapshot.child("duration").getValue(String::class.java) ?: "",
                                    category = try {
                                        HabitCategory.valueOf(
                                            habitSnapshot.child("category").getValue(String::class.java) ?: "PHYSICAL"
                                        )
                                    } catch (e: IllegalArgumentException) {
                                        HabitCategory.PHYSICAL
                                    },
                                    timeOfDay = try {
                                        TimeOfDay.valueOf(
                                            habitSnapshot.child("timeOfDay").getValue(String::class.java) ?: "ALL_DAY"
                                        )
                                    } catch (e: IllegalArgumentException) {
                                        // Valor de franja horaria desconocido — usamos ALL_DAY como fallback
                                        // para que el hábito no desaparezca de la lista
                                        TimeOfDay.ALL_DAY
                                    },
                                    isCustom = habitSnapshot.child("isCustom").getValue(Boolean::class.java) ?: false
                                )
                                habits.add(habit)
                            } catch (e: Exception) {
                                // Si un hábito tiene datos corruptos lo ignoramos
                                // y continuamos con el siguiente
                                e.printStackTrace()
                            }
                        }

                        continuation.resume(habits)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        continuation.resumeWithException(error.toException())
                    }
                })
        }
    }

    // ── REGISTROS DIARIOS ─────────────────────────────────────────────────────

    // Guarda el registro de un hábito en un día concreto.
    // La clave del registro es "{date}_{habitId}" (ej: "2024-03-16_physical_1")
    // para que sea fácil identificarlo y actualizarlo.
    // Devuelve true si se guardó correctamente, false si hubo error.
    suspend fun saveHabitLog(log: DailyHabitLog): Boolean {
        val userId = getUserId() ?: return false

        return suspendCancellableCoroutine { continuation ->
            val logId = "${log.date}_${log.habitId}"

            val logMap = mapOf(
                "habitId" to log.habitId,
                "date" to log.date,
                "isDone" to log.isDone,
                "timestamp" to log.timestamp,

                "source" to log.source,
                "eventId" to log.eventId,
                "habitName" to log.habitName,
                "habitEmoji" to log.habitEmoji,
                "habitDuration" to log.habitDuration,
                "isEventHabit" to log.isEventHabit
            )

            database.getReference("users/$userId/habit_logs/$logId")
                .setValue(logMap)
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume(false)
                }
        }
    }

    // Recupera todos los registros diarios del usuario desde Firebase.
    // Se usa en HabitsManager.syncFromFirebase() para sincronizar
    // el caché local con los datos de Firebase al arrancar la app.
    // Devuelve una lista vacía si no hay registros o si hubo error.
    suspend fun getHabitLogs(): List<DailyHabitLog> {
        val userId = getUserId() ?: return emptyList()

        return suspendCancellableCoroutine { continuation ->
            database.getReference("users/$userId/habit_logs")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val logs = mutableListOf<DailyHabitLog>()

                        snapshot.children.forEach { logSnapshot ->
                            try {
                                val log = DailyHabitLog(
                                    habitId = logSnapshot.child("habitId").getValue(String::class.java) ?: "",
                                    date = logSnapshot.child("date").getValue(String::class.java) ?: "",
                                    isDone = logSnapshot.child("isDone").getValue(Boolean::class.java) ?: false,
                                    timestamp = logSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L,

                                    source = logSnapshot.child("source").getValue(String::class.java) ?: "MANUAL",
                                    eventId = logSnapshot.child("eventId").getValue(String::class.java) ?: "",
                                    habitName = logSnapshot.child("habitName").getValue(String::class.java) ?: "",
                                    habitEmoji = logSnapshot.child("habitEmoji").getValue(String::class.java) ?: "",
                                    habitDuration = logSnapshot.child("habitDuration").getValue(String::class.java) ?: "",
                                    isEventHabit = logSnapshot.child("isEventHabit").getValue(Boolean::class.java) ?: false
                                )
                                logs.add(log)
                            } catch (e: Exception) {
                                // Si un registro tiene datos corruptos lo ignoramos
                                // y continuamos con el siguiente
                                e.printStackTrace()
                            }
                        }

                        continuation.resume(logs)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        continuation.resumeWithException(error.toException())
                    }
                })
        }
    }

    // ── AJUSTES DE USUARIO ────────────────────────────────────────────────────

    // Guarda todos los ajustes del usuario en Firebase de una sola vez.
    // Usa setValue() sobre el nodo settings/ — reemplaza t*do el bloque
    // para mantener consistencia y evitar campos huérfanos.
    // Devuelve true si se guardó correctamente, false si hubo error.
    suspend fun saveSettings(
        themeMode: Int,
        language: String,
        remindersEnabled: Boolean,
        reminderTime: String,
        activeDayIndices: Set<Int>
    ): Boolean {
        val userId = getUserId() ?: return false

        return suspendCancellableCoroutine { continuation ->
            // Los Set<Int> no se pueden guardar directamente en Firebase —
            // los serializamos como String separado por comas (ej: "0,1,2,3,4")
            val settingsMap = mapOf(
                "themeMode" to themeMode,
                "language" to language,
                "remindersEnabled" to remindersEnabled,
                "reminderTime" to reminderTime,
                "activeDayIndices" to activeDayIndices.sorted().joinToString(",")
            )

            database.getReference("users/$userId/settings")
                .setValue(settingsMap)
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume(false)
                }
        }
    }

    // Recupera los ajustes del usuario desde Firebase.
    // Devuelve un Map con los valores, o null si no hay ajustes guardados.
    // El caller (SettingsManager) es responsable de interpretar cada campo
    // y aplicar los valores por defecto si alguno falta.
    suspend fun getSettings(): Map<String, Any>? {
        val userId = getUserId() ?: return null

        return suspendCancellableCoroutine { continuation ->
            database.getReference("users/$userId/settings")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) {
                            // El usuario no tiene ajustes guardados en Firebase todavía
                            continuation.resume(null)
                            return
                        }

                        val settings = mutableMapOf<String, Any>()

                        snapshot.child("themeMode").getValue(Int::class.java)
                            ?.let { settings["themeMode"] = it }
                        snapshot.child("language").getValue(String::class.java)
                            ?.let { settings["language"] = it }
                        snapshot.child("remindersEnabled").getValue(Boolean::class.java)
                            ?.let { settings["remindersEnabled"] = it }
                        snapshot.child("reminderTime").getValue(String::class.java)
                            ?.let { settings["reminderTime"] = it }
                        snapshot.child("activeDayIndices").getValue(String::class.java)
                            ?.let { settings["activeDayIndices"] = it }

                        continuation.resume(settings)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        // Si Firebase falla, devolvemos null para que
                        // SettingsManager use el caché local como respaldo
                        continuation.resume(null)
                    }
                })
        }
    }

    // ── DISPOSITIVOS ACTIVOS ──────────────────────────────────────────────────

    // Registra el dispositivo actual en Firebase al hacer login.
    // Usa el deviceId como clave para que cada dispositivo solo tenga
    // un registro — si el mismo dispositivo vuelve a hacer login,
    // simplemente actualiza su entrada en lugar de crear una nueva.
    // Devuelve true si se guardó correctamente, false si hubo error.
    suspend fun registerDevice(
        deviceId: String,
        deviceName: String,
        osVersion: String,
        loginTimestamp: Long
    ): Boolean {
        val userId = getUserId() ?: return false

        return suspendCancellableCoroutine { continuation ->
            val deviceMap = mapOf(
                "deviceId" to deviceId,
                "deviceName" to deviceName,
                "osVersion" to osVersion,
                "loginTimestamp" to loginTimestamp,
                "lastActiveTimestamp" to loginTimestamp
            )

            database.getReference("users/$userId/devices/$deviceId")
                .setValue(deviceMap)
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume(false)
                }
        }
    }

    // Actualiza el timestamp de última actividad del dispositivo actual.
    // Se llama al arrancar la app para que la lista de dispositivos
    // siempre refleje cuándo fue el último uso real.
    suspend fun updateDeviceLastActive(deviceId: String): Boolean {
        val userId = getUserId() ?: return false

        return suspendCancellableCoroutine { continuation ->
            database.getReference("users/$userId/devices/$deviceId/lastActiveTimestamp")
                .setValue(System.currentTimeMillis())
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume(false)
                }
        }
    }

    // Comprueba si un dispositivo concreto sigue registrado en Firebase.
    // Lo usa SplashActivity para detectar si fue eliminado remotamente.
    // Devuelve:
    //   true  → el dispositivo existe, sesión válida
    //   false → el dispositivo fue eliminado, hay que forzar login
    //   null  → no se pudo consultar Firebase (sin red), dejar pasar
    suspend fun isDeviceRegistered(deviceId: String): Boolean? {
        val userId = getUserId() ?: return false

        return suspendCancellableCoroutine { continuation ->
            database.getReference("users/$userId/devices/$deviceId")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        continuation.resume(snapshot.exists())
                    }

                    override fun onCancelled(error: DatabaseError) {
                        // Sin red — devolvemos null para no bloquear al usuario
                        continuation.resume(null)
                    }
                })
        }
    }

    // Recupera todos los dispositivos con sesión activa desde Firebase.
    // Devuelve una lista vacía si no hay dispositivos o si hubo error.
    suspend fun getActiveDevices(): List<Map<String, Any>> {
        val userId = getUserId() ?: return emptyList()

        return suspendCancellableCoroutine { continuation ->
            database.getReference("users/$userId/devices")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val devices = mutableListOf<Map<String, Any>>()

                        snapshot.children.forEach { deviceSnapshot ->
                            try {
                                val device = mutableMapOf<String, Any>()

                                deviceSnapshot.child("deviceId").getValue(String::class.java)
                                    ?.let { device["deviceId"] = it }
                                deviceSnapshot.child("deviceName").getValue(String::class.java)
                                    ?.let { device["deviceName"] = it }
                                deviceSnapshot.child("osVersion").getValue(String::class.java)
                                    ?.let { device["osVersion"] = it }
                                deviceSnapshot.child("loginTimestamp").getValue(Long::class.java)
                                    ?.let { device["loginTimestamp"] = it }
                                deviceSnapshot.child("lastActiveTimestamp").getValue(Long::class.java)
                                    ?.let { device["lastActiveTimestamp"] = it }

                                if (device.isNotEmpty()) devices.add(device)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        continuation.resume(devices)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        continuation.resume(emptyList())
                    }
                })
        }
    }

    // Elimina el registro de un dispositivo concreto de Firebase.
    // Se llama desde ActiveDevicesActivity cuando el usuario pulsa
    // "Cerrar sesión" en un dispositivo individual de la lista.
    // Devuelve true si se eliminó correctamente, false si hubo error.
    suspend fun removeDevice(deviceId: String, userId: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            database.getReference("users/$userId/devices/$deviceId")
                .removeValue()
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume(false)
                }
        }
    }

    // Elimina todos los dispositivos del usuario de Firebase de una vez.
    // Se llama cuando el usuario pulsa "Cerrar sesión en todos los dispositivos"
    // para que ningún otro dispositivo pueda seguir con la sesión activa.
    // Devuelve true si se eliminó correctamente, false si hubo error.
    suspend fun removeAllDevices(): Boolean {
        val userId = getUserId() ?: return false

        return suspendCancellableCoroutine { continuation ->
            database.getReference("users/$userId/devices")
                .removeValue()
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume(false)
                }
        }
    }
}