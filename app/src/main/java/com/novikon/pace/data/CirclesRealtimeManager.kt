package com.novikon.pace.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.novikon.pace.models.Circle
import com.novikon.pace.models.Message
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Clase encargada de toda la comunicación con Firebase Realtime Database
// relacionada con los círculos y sus mensajes.
//
// Sigue el mismo patrón que RealtimeDatabaseManager:
//   - Funciones suspend para operaciones únicas (crear, enviar, etc.)
//   - Listeners en tiempo real para observar cambios (mensajes del chat)
//
// Estructura en Firebase:
// circles/{circleId}/
//   name, createdBy, createdAt
//   members/{userId} → true
//   messages/{messageId}/
//     text, senderId, senderName, timestamp
//
// users/{userId}/circles/{circleId} → true
//   (índice inverso para recuperar los círculos del usuario sin escanear todo)
class CirclesRealtimeManager {

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun getUserId(): String? = auth.currentUser?.uid

    fun getUserName(): String = auth.currentUser?.displayName
        ?: auth.currentUser?.email?.substringBefore("@")
        ?: "Usuario"

    // ── CREAR CÍRCULO ─────────────────────────────────────────────────────────

    // Crea un círculo nuevo en Firebase y añade al creador como primer miembro.
    // Usa push() para generar un ID único cronológico automáticamente.
    // Escribe en dos sitios a la vez (circles/ y users/.../circles/)
    // con un multi-path update para que sea atómico — o se guardan los dos
    // o no se guarda ninguno.
    // Devuelve el circleId si se creó correctamente, null si hubo error.
    suspend fun createCircle(name: String): String? {
        val userId = getUserId() ?: return null

        return suspendCancellableCoroutine { continuation ->
            val circlesRef = database.getReference("circles")
            val newCircleRef = circlesRef.push()
            val circleId = newCircleRef.key ?: run {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val timestamp = System.currentTimeMillis()

            // Multi-path update: escribe en circles/ y en users/.../circles/
            // de forma atómica — si falla uno falla todo
            val updates = mapOf(
                "circles/$circleId/name" to name,
                "circles/$circleId/createdBy" to userId,
                "circles/$circleId/createdAt" to timestamp,
                "circles/$circleId/members/$userId" to true,
                "users/$userId/circles/$circleId" to true
            )

            database.reference.updateChildren(updates)
                .addOnSuccessListener { continuation.resume(circleId) }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume(null)
                }
        }
    }

    // ── OBTENER CÍRCULOS DEL USUARIO ──────────────────────────────────────────

    // Recupera todos los círculos a los que pertenece el usuario actual.
    // Primero lee el índice users/{userId}/circles/ para obtener los IDs,
    // luego hace una consulta por cada ID para obtener los datos completos.
    // También lee el último mensaje de cada círculo para mostrarlo en la lista.
    // Devuelve lista vacía si no hay círculos o si hubo error.
    suspend fun getUserCircles(): List<Circle> {
        val userId = getUserId() ?: return emptyList()

        // Paso 1: obtener los IDs de círculos del usuario
        val circleIds = suspendCancellableCoroutine<List<String>> { continuation ->
            database.getReference("users/$userId/circles")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val ids = snapshot.children.mapNotNull { it.key }
                        continuation.resume(ids)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        continuation.resume(emptyList())
                    }
                })
        }

        if (circleIds.isEmpty()) return emptyList()

        // Paso 2: para cada ID recuperar los datos del círculo
        val circles = mutableListOf<Circle>()
        for (circleId in circleIds) {
            val circle = getCircleById(circleId) ?: continue
            circles.add(circle)
        }

        // Ordenar por último mensaje más reciente primero
        return circles.sortedByDescending { it.lastMessageTime.takeIf { t -> t > 0 } ?: it.createdAt }
    }

    // Recupera los datos completos de un círculo por su ID,
    // incluyendo el último mensaje para la preview de la lista.
    private suspend fun getCircleById(circleId: String): Circle? {
        return suspendCancellableCoroutine { continuation ->
            database.getReference("circles/$circleId")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) {
                            continuation.resume(null)
                            return
                        }

                        val name = snapshot.child("name").getValue(String::class.java) ?: ""
                        val createdBy = snapshot.child("createdBy").getValue(String::class.java) ?: ""
                        val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                        val memberCount = snapshot.child("members").childrenCount.toInt()

                        // Recuperar el último mensaje para la preview
                        val lastMsgSnapshot = snapshot.child("messages")
                        var lastMessage = ""
                        var lastMessageTime = 0L

                        if (lastMsgSnapshot.exists()) {
                            // Los mensajes están ordenados cronológicamente por su push() key —
                            // el último hijo es el mensaje más reciente
                            val lastMsg = lastMsgSnapshot.children.lastOrNull()
                            lastMessage = lastMsg?.child("text")?.getValue(String::class.java) ?: ""
                            lastMessageTime = lastMsg?.child("timestamp")?.getValue(Long::class.java) ?: 0L
                        }

                        continuation.resume(
                            Circle(
                                id = circleId,
                                name = name,
                                createdBy = createdBy,
                                createdAt = createdAt,
                                memberCount = memberCount,
                                lastMessage = lastMessage,
                                lastMessageTime = lastMessageTime
                            )
                        )
                    }

                    override fun onCancelled(error: DatabaseError) {
                        continuation.resume(null)
                    }
                })
        }
    }

    // ── OBSERVAR CÍRCULOS EN TIEMPO REAL ──────────────────────────────────────

    // Registra un listener que se dispara cada vez que cambia la lista
    // de círculos del usuario (se añade uno nuevo, se recibe un mensaje, etc.)
    // Devuelve el listener para que el Fragment pueda eliminarlo en onDestroyView.
    fun observeUserCircles(
        onCirclesChanged: (List<Circle>) -> Unit
    ): ValueEventListener {
        val userId = getUserId() ?: return object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val circleIds = snapshot.children.mapNotNull { it.key }

                if (circleIds.isEmpty()) {
                    onCirclesChanged(emptyList())
                    return
                }

                // Contador para saber cuándo hemos recibido todos los círculos
                val circles = mutableListOf<Circle>()
                var pendingCount = circleIds.size

                for (circleId in circleIds) {
                    database.getReference("circles/$circleId")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(circleSnapshot: DataSnapshot) {
                                if (circleSnapshot.exists()) {
                                    val name = circleSnapshot.child("name").getValue(String::class.java) ?: ""
                                    val createdBy = circleSnapshot.child("createdBy").getValue(String::class.java) ?: ""
                                    val createdAt = circleSnapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                                    val memberCount = circleSnapshot.child("members").childrenCount.toInt()

                                    val lastMsgSnapshot = circleSnapshot.child("messages")
                                    var lastMessage = ""
                                    var lastMessageTime = 0L
                                    if (lastMsgSnapshot.exists()) {
                                        val lastMsg = lastMsgSnapshot.children.lastOrNull()
                                        lastMessage = lastMsg?.child("text")?.getValue(String::class.java) ?: ""
                                        lastMessageTime = lastMsg?.child("timestamp")?.getValue(Long::class.java) ?: 0L
                                    }

                                    circles.add(
                                        Circle(
                                            id = circleId,
                                            name = name,
                                            createdBy = createdBy,
                                            createdAt = createdAt,
                                            memberCount = memberCount,
                                            lastMessage = lastMessage,
                                            lastMessageTime = lastMessageTime
                                        )
                                    )
                                }

                                pendingCount--
                                if (pendingCount == 0) {
                                    val sorted = circles.sortedByDescending {
                                        it.lastMessageTime.takeIf { t -> t > 0 } ?: it.createdAt
                                    }
                                    onCirclesChanged(sorted)
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                pendingCount--
                                if (pendingCount == 0) {
                                    onCirclesChanged(circles.sortedByDescending { it.createdAt })
                                }
                            }
                        })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onCirclesChanged(emptyList())
            }
        }

        database.getReference("users/$userId/circles").addValueEventListener(listener)
        return listener
    }

    // Elimina el listener de círculos del usuario.
    // Llamar en onDestroyView del Fragment para evitar memory leaks.
    fun removeUserCirclesListener(listener: ValueEventListener) {
        val userId = getUserId() ?: return
        database.getReference("users/$userId/circles").removeEventListener(listener)
    }

    // ── MENSAJES ──────────────────────────────────────────────────────────────

    // Envía un mensaje al círculo especificado.
    // Usa push() para generar un ID de mensaje único y cronológico.
    // El senderName se guarda en el propio mensaje para evitar consultas
    // adicionales al pintar cada burbuja del chat.
    // Devuelve true si se envió correctamente, false si hubo error.
    suspend fun sendMessage(circleId: String, text: String): Boolean {
        val userId = getUserId() ?: return false
        val userName = getUserName()

        return suspendCancellableCoroutine { continuation ->
            val messagesRef = database.getReference("circles/$circleId/messages")
            val newMsgRef = messagesRef.push()
            val messageId = newMsgRef.key ?: run {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }

            val messageMap = mapOf(
                "id" to messageId,
                "text" to text,
                "senderId" to userId,
                "senderName" to userName,
                "timestamp" to System.currentTimeMillis()
            )

            newMsgRef.setValue(messageMap)
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume(false)
                }
        }
    }

    // Observa los mensajes de un círculo en tiempo real.
    // Usa ChildEventListener en lugar de ValueEventListener para que
    // solo llegue cada mensaje nuevo — no toda la lista cada vez.
    // Devuelve el listener para eliminarlo en onDestroy de la Activity.
    fun observeMessages(
        circleId: String,
        onMessageAdded: (Message) -> Unit
    ): ChildEventListener {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val message = Message(
                        id = snapshot.child("id").getValue(String::class.java) ?: snapshot.key ?: "",
                        text = snapshot.child("text").getValue(String::class.java) ?: "",
                        senderId = snapshot.child("senderId").getValue(String::class.java) ?: "",
                        senderName = snapshot.child("senderName").getValue(String::class.java) ?: "",
                        timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    )
                    onMessageAdded(message)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) { error.toException().printStackTrace() }
        }

        database.getReference("circles/$circleId/messages")
            .orderByChild("timestamp")
            .addChildEventListener(listener)

        return listener
    }

    // Elimina el listener de mensajes de un círculo.
    // Llamar en onDestroy de CircleChatActivity para evitar memory leaks.
    fun removeMessagesListener(circleId: String, listener: ChildEventListener) {
        database.getReference("circles/$circleId/messages")
            .orderByChild("timestamp")
            .removeEventListener(listener)
    }

    // ── GESTIÓN DE MIEMBROS ───────────────────────────────────────────────────

    // Añade a otro usuario al círculo por su userId.
    // Escribe en circles/{circleId}/members/ y en users/{userId}/circles/
    // con un multi-path update atómico.
    // Devuelve true si se añadió correctamente, false si hubo error.
    suspend fun addMemberToCircle(circleId: String, targetUserId: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val updates = mapOf(
                "circles/$circleId/members/$targetUserId" to true,
                "users/$targetUserId/circles/$circleId" to true
            )

            database.reference.updateChildren(updates)
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    continuation.resume(false)
                }
        }
    }

    data class GroupInfo(
        val circleId: String,
        val name: String,
        val createdBy: String,
        val joinCode: String,
        val maxParticipants: Int,
        val memberIds: List<String>
    )

    private fun randomJoinCode(length: Int = 6): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

    private suspend fun generateUniqueJoinCode(): String {
        while (true) {
            val code = randomJoinCode()
            val exists = suspendCancellableCoroutine<Boolean> { cont ->
                database.getReference("circleJoinCodes/$code")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            cont.resume(snapshot.exists())
                        }
                        override fun onCancelled(error: DatabaseError) {
                            cont.resume(false)
                        }
                    })
            }
            if (!exists) return code
        }
    }

    suspend fun createCircle(name: String, maxParticipants: Int): String? {
        val userId = getUserId() ?: return null
        val joinCode = generateUniqueJoinCode()

        return suspendCancellableCoroutine { cont ->
            val newCircleRef = database.getReference("circles").push()
            val circleId = newCircleRef.key ?: run {
                cont.resume(null); return@suspendCancellableCoroutine
            }

            val ts = System.currentTimeMillis()
            val updates = mapOf(
                "circles/$circleId/name" to name,
                "circles/$circleId/createdBy" to userId,
                "circles/$circleId/createdAt" to ts,
                "circles/$circleId/joinCode" to joinCode,
                "circles/$circleId/maxParticipants" to maxParticipants,
                "circles/$circleId/members/$userId" to true,
                "users/$userId/circles/$circleId" to true,
                "circleJoinCodes/$joinCode" to circleId
            )

            database.reference.updateChildren(updates)
                .addOnSuccessListener { cont.resume(circleId) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    suspend fun joinCircleByCode(code: String): Boolean {
        val userId = getUserId() ?: return false

        val circleId = suspendCancellableCoroutine<String?> { cont ->
            database.getReference("circleJoinCodes/$code")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        cont.resume(snapshot.getValue(String::class.java))
                    }
                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(null)
                    }
                })
        } ?: return false

        return addMemberToCircleTransactional(circleId, userId)
    }

    private suspend fun addMemberToCircleTransactional(circleId: String, targetUserId: String): Boolean {
        return suspendCancellableCoroutine { cont ->
            val membersRef = database.getReference("circles/$circleId/members")
            val maxRef = database.getReference("circles/$circleId/maxParticipants")

            maxRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(maxSnap: DataSnapshot) {
                    val max = maxSnap.getValue(Int::class.java) ?: 6
                    membersRef.runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            val map = currentData.value as? Map<String, Any?> ?: emptyMap()
                            if (map.containsKey(targetUserId)) return Transaction.success(currentData)
                            if (map.size >= max) return Transaction.abort()

                            val mutable = map.toMutableMap()
                            mutable[targetUserId] = true
                            currentData.value = mutable
                            return Transaction.success(currentData)
                        }

                        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                            if (error != null || !committed) {
                                cont.resume(false)
                                return
                            }
                            database.getReference("users/$targetUserId/circles/$circleId")
                                .setValue(true)
                                .addOnSuccessListener { cont.resume(true) }
                                .addOnFailureListener { cont.resume(false) }
                        }
                    })
                }

                override fun onCancelled(error: DatabaseError) {
                    cont.resume(false)
                }
            })
        }
    }

    suspend fun getGroupInfo(circleId: String): GroupInfo? {
        return suspendCancellableCoroutine { cont ->
            database.getReference("circles/$circleId")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) {
                            cont.resume(null); return
                        }
                        val name = snapshot.child("name").getValue(String::class.java) ?: ""
                        val createdBy = snapshot.child("createdBy").getValue(String::class.java) ?: ""
                        val joinCode = snapshot.child("joinCode").getValue(String::class.java) ?: ""
                        val max = snapshot.child("maxParticipants").getValue(Int::class.java) ?: 6
                        val members = snapshot.child("members").children.mapNotNull { it.key }

                        cont.resume(
                            GroupInfo(
                                circleId = circleId,
                                name = name,
                                createdBy = createdBy,
                                joinCode = joinCode,
                                maxParticipants = max,
                                memberIds = members
                            )
                        )
                    }

                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(null)
                    }
                })
        }
    }

    suspend fun getMemberDisplayNames(userIds: List<String>): List<com.novikon.pace.models.CircleMember> {
        val result = mutableListOf<com.novikon.pace.models.CircleMember>()
        for (uid in userIds) {
            val name = suspendCancellableCoroutine<String> { cont ->
                database.getReference("users/$uid/profile/displayName")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            cont.resume(snapshot.getValue(String::class.java) ?: uid)
                        }
                        override fun onCancelled(error: DatabaseError) {
                            cont.resume(uid)
                        }
                    })
            }
            result.add(com.novikon.pace.models.CircleMember(uid, name))
        }
        return result
    }

    suspend fun updateMaxParticipants(circleId: String, newMax: Int): Boolean {
        val uid = getUserId() ?: return false
        val info = getGroupInfo(circleId) ?: return false
        if (info.createdBy != uid) return false
        if (newMax < info.memberIds.size) return false

        return suspendCancellableCoroutine { cont ->
            database.getReference("circles/$circleId/maxParticipants")
                .setValue(newMax)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    suspend fun removeMember(circleId: String, memberUid: String): Boolean {
        val uid = getUserId() ?: return false
        val info = getGroupInfo(circleId) ?: return false
        if (info.createdBy != uid) return false
        if (memberUid == info.createdBy) return false

        return suspendCancellableCoroutine { cont ->
            val updates = mapOf(
                "circles/$circleId/members/$memberUid" to null,
                "users/$memberUid/circles/$circleId" to null
            )
            database.reference.updateChildren(updates)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    suspend fun blockUser(targetUid: String): Boolean {
        val uid = getUserId() ?: return false
        return suspendCancellableCoroutine { cont ->
            database.getReference("users/$uid/blocked/$targetUid")
                .setValue(true)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }
}