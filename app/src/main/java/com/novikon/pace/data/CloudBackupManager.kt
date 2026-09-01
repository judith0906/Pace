package com.novikon.pace.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Copia de seguridad en la nube (Premium).
 *
 * Guarda un snapshot de los hábitos y registros del usuario bajo
 * `users/{uid}/backups/{timestamp}/` sin tocar los datos en vivo.
 * Restaurar vuelve a copiar ese snapshot a los nodos activos.
 *
 * Los datos NO se reconstruyen como objetos Kotlin: se leen y escriben
 * como Map<String, Any> crudos para conservar el esquema exacto.
 */
class CloudBackupManager {

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val THREE_MONTHS_MS = 3L * 30L * 24L * 60L * 60L * 1000L
    }

    private suspend fun readNodeRaw(path: String): Map<String, Any>? {
        val typeIndicator = object : GenericTypeIndicator<Map<String, Any>>() {}
        return suspendCancellableCoroutine { continuation ->
            database.getReference(path)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        continuation.resume(snapshot.getValue(typeIndicator))
                    }
                    override fun onCancelled(error: DatabaseError) {
                        continuation.resume(null)
                    }
                })
        }
    }

    private suspend fun writeNode(path: String, value: Any): Boolean {
        return suspendCancellableCoroutine { continuation ->
            database.getReference(path)
                .setValue(value)
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume(false)
                }
        }
    }

    /** Devuelve el timestamp de la copia más reciente o 0 si no hay ninguna. */
    suspend fun getLastBackupAt(): Long {
        val userId = auth.currentUser?.uid ?: return 0L
        return suspendCancellableCoroutine { continuation ->
            database.getReference("users/$userId/backups")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val last = snapshot.children
                            .mapNotNull { it.key?.toLongOrNull() }
                            .maxOrNull() ?: 0L
                        continuation.resume(last)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        continuation.resume(0L)
                    }
                })
        }
    }

    /** Devuelve los ids de los círculos a los que pertenece el usuario. */
    private suspend fun getUserCircleIds(userId: String): List<String> {
        return readNodeRaw("users/$userId/circles")?.keys?.toList() ?: emptyList()
    }

    private suspend fun getCircleName(circleId: String): String {
        return suspendCancellableCoroutine { continuation ->
            database.getReference("circles/$circleId/name")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        continuation.resume(snapshot.getValue(String::class.java) ?: "")
                    }
                    override fun onCancelled(error: DatabaseError) {
                        continuation.resume("")
                    }
                })
        }
    }

    /** Devuelve los mensajes de un círculo con timestamp >= cutoff, clave = id del mensaje. */
    private suspend fun getRecentCircleMessages(circleId: String, cutoff: Long): Map<String, Any> {
        val all = readNodeRaw("circles/$circleId/messages") ?: return emptyMap()
        return all.filter { (_, value) ->
            val msg = value as? Map<*, *>
            val ts = (msg?.get("timestamp") as? Number)?.toLong() ?: 0L
            ts >= cutoff
        }
    }

    /** Crea una copia de seguridad. Devuelve true si se guardó correctamente. */
    suspend fun createBackup(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        val timestamp = System.currentTimeMillis()

        val habits = readNodeRaw("users/$userId/habits") ?: emptyMap()
        val logs = readNodeRaw("users/$userId/habit_logs") ?: emptyMap()

        val basePath = "users/$userId/backups/$timestamp"
        val habitsOk = writeNode("$basePath/habits", habits)
        if (!habitsOk) return false

        val logsOk = writeNode("$basePath/habit_logs", logs)
        if (!logsOk) return false

        // ── Círculos: conversación de los últimos 3 meses ──────────────────────
        val cutoff = System.currentTimeMillis() - THREE_MONTHS_MS
        val circlesBackup = mutableMapOf<String, Any>()
        for (circleId in getUserCircleIds(userId)) {
            val recent = getRecentCircleMessages(circleId, cutoff)
            if (recent.isNotEmpty()) {
                circlesBackup[circleId] = mapOf(
                    "name" to getCircleName(circleId),
                    "messages" to recent
                )
            }
        }
        if (circlesBackup.isNotEmpty()) {
            val circlesOk = writeNode("$basePath/circles", circlesBackup)
            if (!circlesOk) return false
        }

        return writeNode("$basePath/createdAt", timestamp)
    }

    /** Restaura la copia más reciente sobre los datos activos. Devuelve true si tuvo éxito. */
    suspend fun restoreBackup(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        val last = getLastBackupAt()
        if (last <= 0L) return false

        val basePath = "users/$userId/backups/$last"
        val habits = readNodeRaw("$basePath/habits")
        val logs = readNodeRaw("$basePath/habit_logs")
        if (habits == null && logs == null) return false

        var ok = true
        if (habits != null) ok = writeNode("users/$userId/habits", habits)
        if (ok && logs != null) ok = writeNode("users/$userId/habit_logs", logs)

        // ── Círculos: reescribe los mensajes por su ID original (sin borrar) ───
        if (ok) {
            ok = restoreCircleMessages("$basePath/circles")
        }
        return ok
    }

    private suspend fun restoreCircleMessages(circlesPath: String): Boolean {
        val circles = readNodeRaw(circlesPath) ?: return true
        val updates = mutableMapOf<String, Any?>()
        circles.forEach { (circleId, value) ->
            val circle = value as? Map<*, *> ?: return@forEach
            val messages = circle["messages"] as? Map<*, *> ?: return@forEach
            messages.forEach { (msgId, msg) ->
                if (msgId != null) {
                    updates["circles/$circleId/messages/$msgId"] = msg
                }
            }
        }
        if (updates.isEmpty()) return true
        return writePaths(updates)
    }

    private suspend fun writePaths(updates: Map<String, Any?>): Boolean {
        return suspendCancellableCoroutine { continuation ->
            database.reference.updateChildren(updates)
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume(false)
                }
        }
    }
}