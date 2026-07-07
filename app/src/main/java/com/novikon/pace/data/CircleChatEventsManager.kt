package com.novikon.pace.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.novikon.pace.BuildConfig
import com.novikon.pace.R
import com.novikon.pace.models.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

// Gestor de eventos del chat: crea, actualiza y sincroniza eventos de circulo.
class CircleChatEventsManager(
    private val context: Context
) {

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Devuelve el id del usuario autenticado para asociar acciones del chat.
    fun getUserId(): String? = auth.currentUser?.uid

    // Obtiene el nombre visible del usuario para mostrarlo en mensajes y eventos.
    private fun getUserName(): String {
        return auth.currentUser?.displayName
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: context.getString(R.string.default_user)
    }

    // Crea un evento grupal y publica su tarjeta inicial en el chat.
    // Deja al creador apuntado automáticamente como participante.
    suspend fun createEvent(
        circleId: String,
        habitName: String,
        scheduledAtMillis: Long,
        eventTimeZoneId: String,
        eventDuration: String = "",
        eventDurationMs: Long = 0L
    ): Boolean {
        val uid = getUserId() ?: return false
        val userName = getUserName()

        return suspendCancellableCoroutine { cont ->
            val baseRef = database.reference
            val eventRef = baseRef.child("circles/$circleId/events").push()
            val eventId = eventRef.key ?: run {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }

            val messageRef = baseRef.child("circles/$circleId/messages").push()
            val messageId = messageRef.key ?: run {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }

            val now = System.currentTimeMillis()

            val updates = mutableMapOf<String, Any>(
                "circles/$circleId/events/$eventId/id" to eventId,
                "circles/$circleId/events/$eventId/habitName" to habitName,
                "circles/$circleId/events/$eventId/scheduledAt" to scheduledAtMillis,
                "circles/$circleId/events/$eventId/createdBy" to uid,
                "circles/$circleId/events/$eventId/createdByName" to userName,
                "circles/$circleId/events/$eventId/createdAt" to now,
                "circles/$circleId/events/$eventId/started" to false,
                "circles/$circleId/events/$eventId/joinedIds" to listOf(uid),
                "circles/$circleId/events/$eventId/joinedNames" to listOf(userName),
                "circles/$circleId/events/$eventId/declinedIds" to emptyList<String>(),
                "circles/$circleId/events/$eventId/declinedNames" to emptyList<String>(),
                "circles/$circleId/events/$eventId/timeZoneId" to eventTimeZoneId,
                "circles/$circleId/events/$eventId/eventDuration" to eventDuration,
                "circles/$circleId/events/$eventId/eventDurationMs" to eventDurationMs,

                "circles/$circleId/messages/$messageId/eventTimeZoneId" to eventTimeZoneId,
                "circles/$circleId/messages/$messageId/id" to messageId,
                "circles/$circleId/messages/$messageId/type" to "EVENT",
                "circles/$circleId/messages/$messageId/text" to context.getString(R.string.event_title_format, habitName),
                "circles/$circleId/messages/$messageId/senderId" to uid,
                "circles/$circleId/messages/$messageId/senderName" to userName,
                "circles/$circleId/messages/$messageId/timestamp" to now,
                "circles/$circleId/messages/$messageId/eventId" to eventId,
                "circles/$circleId/messages/$messageId/eventHabitName" to habitName,
                "circles/$circleId/messages/$messageId/eventScheduledAt" to scheduledAtMillis,
                "circles/$circleId/messages/$messageId/eventCreatedBy" to uid,
                "circles/$circleId/messages/$messageId/eventJoinedIds" to listOf(uid),
                "circles/$circleId/messages/$messageId/eventJoinedNames" to listOf(userName),
                "circles/$circleId/messages/$messageId/eventDeclinedIds" to emptyList<String>(),
                "circles/$circleId/messages/$messageId/eventDeclinedNames" to emptyList<String>(),
                "circles/$circleId/messages/$messageId/eventDuration" to eventDuration,
                "circles/$circleId/messages/$messageId/eventDurationMs" to eventDurationMs
            )

            baseRef.updateChildren(updates)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    // Registra si el usuario se une o declina un evento y sincroniza esa respuesta
    // tanto en el nodo del evento como en el mensaje que lo representa en el chat.
    suspend fun respondToEvent(
        circleId: String,
        messageId: String,
        eventId: String,
        join: Boolean
    ): Boolean {
        val uid = getUserId() ?: return false
        val userName = getUserName()

        return suspendCancellableCoroutine { cont ->
            val eventRef = database.getReference("circles/$circleId/events/$eventId")

            eventRef.addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        cont.resume(false)
                        return
                    }

                    val joinedIds = snapshot.child("joinedIds").children.mapNotNull { it.getValue(String::class.java) }.toMutableSet()
                    val joinedNames = snapshot.child("joinedNames").children.mapNotNull { it.getValue(String::class.java) }.toMutableSet()
                    val declinedIds = snapshot.child("declinedIds").children.mapNotNull { it.getValue(String::class.java) }.toMutableSet()
                    val declinedNames = snapshot.child("declinedNames").children.mapNotNull { it.getValue(String::class.java) }.toMutableSet()

                    joinedIds.remove(uid)
                    declinedIds.remove(uid)
                    joinedNames.remove(userName)
                    declinedNames.remove(userName)

                    if (join) {
                        joinedIds.add(uid)
                        joinedNames.add(userName)
                    } else {
                        declinedIds.add(uid)
                        declinedNames.add(userName)
                    }

                    val updates = mapOf<String, Any>(
                        "circles/$circleId/events/$eventId/joinedIds" to joinedIds.toList(),
                        "circles/$circleId/events/$eventId/joinedNames" to joinedNames.toList(),
                        "circles/$circleId/events/$eventId/declinedIds" to declinedIds.toList(),
                        "circles/$circleId/events/$eventId/declinedNames" to declinedNames.toList(),

                        "circles/$circleId/messages/$messageId/eventJoinedIds" to joinedIds.toList(),
                        "circles/$circleId/messages/$messageId/eventJoinedNames" to joinedNames.toList(),
                        "circles/$circleId/messages/$messageId/eventDeclinedIds" to declinedIds.toList(),
                        "circles/$circleId/messages/$messageId/eventDeclinedNames" to declinedNames.toList()
                    )

                    database.reference.updateChildren(updates)
                        .addOnSuccessListener { cont.resume(true) }
                        .addOnFailureListener { cont.resume(false) }
                }

                override fun onCancelled(error: DatabaseError) {
                    cont.resume(false)
                }
            })
        }
    }

    // Busca eventos cuya hora ya llegó y dispara su inicio automático.
    suspend fun checkAndStartDueEvents(circleId: String): Boolean {
        val now = System.currentTimeMillis()

        return suspendCancellableCoroutine { cont ->
            database.getReference("circles/$circleId/events")
                .orderByChild("scheduledAt")
                .endAt(now.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val dueEvents = snapshot.children.toList()
                        if (dueEvents.isEmpty()) {
                            cont.resume(true)
                            return
                        }

                        processDueEventsSequentially(circleId, dueEvents, 0) {
                            cont.resume(true)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(false)
                    }
                })
        }
    }

    // Procesa los eventos vencidos uno por uno para evitar colisiones de escritura.
    private fun processDueEventsSequentially(
        circleId: String,
        events: List<DataSnapshot>,
        index: Int,
        onDone: () -> Unit
    ) {
        if (index >= events.size) {
            onDone()
            return
        }

        val eventSnapshot = events[index]
        val eventId = eventSnapshot.key ?: run {
            processDueEventsSequentially(circleId, events, index + 1, onDone)
            return
        }

        val started = eventSnapshot.child("started").getValue(Boolean::class.java) ?: false
        if (started) {
            processDueEventsSequentially(circleId, events, index + 1, onDone)
            return
        }

        markEventStartedAndSendSystemMessage(circleId, eventId, eventSnapshot) {
            processDueEventsSequentially(circleId, events, index + 1, onDone)
        }
    }

    // Marca el evento como iniciado y publica el mensaje de sistema que avisa al grupo.
    private fun markEventStartedAndSendSystemMessage(
        circleId: String,
        eventId: String,
        eventSnapshot: DataSnapshot,
        onDone: () -> Unit
    ) {
        val startedRef = database.getReference("circles/$circleId/events/$eventId/started")

        startedRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val current = currentData.getValue(Boolean::class.java) ?: false
                if (current) return Transaction.abort()
                currentData.value = true
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null || !committed) {
                    onDone()
                    return
                }

                val habitName = eventSnapshot.child("habitName").getValue(String::class.java)
                    ?: context.getString(R.string.event_default_name)
                val joinedIds = eventSnapshot.child("joinedIds").children.mapNotNull { it.getValue(String::class.java) }
                val now = System.currentTimeMillis()

                val msgRef = database.getReference("circles/$circleId/messages").push()
                val msgId = msgRef.key ?: run {
                    onDone()
                    return
                }

                val eventDuration = eventSnapshot.child("eventDuration").getValue(String::class.java) ?: ""
                val eventDurationMs = eventSnapshot.child("eventDurationMs").getValue(Long::class.java) ?: 0L
                val eventScheduledAt = eventSnapshot.child("scheduledAt").getValue(Long::class.java) ?: now

                val messageMap = mapOf(
                    "id" to msgId,
                    "type" to "EVENT_START",
                    "text" to context.getString(R.string.event_system_started, habitName), // fallback
                    "messageTemplateKey" to "event_system_started",
                    "messageTemplateParams" to listOf(habitName),
                    "senderId" to "system",
                    "senderName" to "system",
                    "timestamp" to now,
                    "eventId" to eventId,
                    "eventHabitName" to habitName,
                    "eventScheduledAt" to eventScheduledAt,
                    "eventDuration" to eventDuration,
                    "eventDurationMs" to eventDurationMs,
                    "captureAllowedIds" to joinedIds
                )

                msgRef.setValue(messageMap)
                    .addOnCompleteListener { onDone() }
            }
        })
    }

    // Sube una foto de "momento del evento" y la envía al chat como mensaje tipo PHOTO.
    suspend fun sendPhotoMoment(
        circleId: String,
        eventId: String,
        bitmap: Bitmap
    ): Boolean {
        val uid = getUserId() ?: return false
        val userName = getUserName()
        // Calidad 95 para preservar detalle en las fotos del grupo
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            stream.toByteArray()
        }
        val objectPath = "circles/${circleId}/events/${eventId}/${uid}_${System.currentTimeMillis()}.jpg"
        val publicUrl = uploadToSupabase(bytes, objectPath) ?: return false
        return suspendCancellableCoroutine { cont ->
            val msgRef = database.getReference("circles/$circleId/messages").push()
            val msgId = msgRef.key ?: run {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
            val now = System.currentTimeMillis()
            val messageMap = mapOf(
                "id" to msgId,
                "type" to "PHOTO",
                "text" to context.getString(R.string.event_photo_label),
                "senderId" to uid,
                "senderName" to userName,
                "timestamp" to now,
                "eventId" to eventId,
                "photoUrl" to publicUrl
            )
            msgRef.setValue(messageMap)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    // Sube una imagen al storage y devuelve su URL pública para compartirla en el chat.
    private suspend fun uploadToSupabase(imageBytes: ByteArray, objectPath: String): String? {
        return withContext(Dispatchers.IO) {
            val baseUrl = BuildConfig.SUPABASE_URL.removeSuffix("/")
            val bucket = BuildConfig.SUPABASE_BUCKET
            val uploadUrl = "$baseUrl/storage/v1/object/$bucket/$objectPath"

            Log.d("SupabaseUpload", "URL=$uploadUrl")

            val conn = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                setRequestProperty("Content-Type", "image/jpeg")
            }

            try {
                conn.outputStream.use { it.write(imageBytes) }
                val code = conn.responseCode
                val errorBody = conn.errorStream?.bufferedReader()?.readText()
                Log.d("SupabaseUpload", "code=$code errorBody=$errorBody")

                if (code in 200..299) {
                    "$baseUrl/storage/v1/object/public/$bucket/$objectPath"
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("SupabaseUpload", "upload exception", e)
                null
            } finally {
                conn.disconnect()
            }
        }
    }

    // Escucha cambios en tiempo real del chat del círculo y notifica altas/cambios/bajas.
    fun observeMessages(
        circleId: String,
        onMessageAdded: (Message) -> Unit,
        onMessageChanged: (Message) -> Unit,
        onMessageRemoved: (String) -> Unit
    ): ChildEventListener {
        // Convierte el snapshot de Firebase al modelo Message usado por la UI.
        fun parseMessage(snapshot: DataSnapshot): Message {
            return Message(
                id = snapshot.child("id").getValue(String::class.java) ?: snapshot.key ?: "",
                text = snapshot.child("text").getValue(String::class.java) ?: "",
                senderId = snapshot.child("senderId").getValue(String::class.java) ?: "",
                senderName = snapshot.child("senderName").getValue(String::class.java) ?: "",
                timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L,
                type = snapshot.child("type").getValue(String::class.java) ?: "TEXT",
                eventId = snapshot.child("eventId").getValue(String::class.java) ?: "",
                eventHabitName = snapshot.child("eventHabitName").getValue(String::class.java) ?: "",
                eventScheduledAt = snapshot.child("eventScheduledAt").getValue(Long::class.java) ?: 0L,
                eventCreatedBy = snapshot.child("eventCreatedBy").getValue(String::class.java) ?: "",
                eventTimeZoneId = snapshot.child("eventTimeZoneId").getValue(String::class.java) ?: "",
                eventJoinedIds = snapshot.child("eventJoinedIds").children.mapNotNull { it.getValue(String::class.java) },
                eventJoinedNames = snapshot.child("eventJoinedNames").children.mapNotNull { it.getValue(String::class.java) },
                eventDeclinedIds = snapshot.child("eventDeclinedIds").children.mapNotNull { it.getValue(String::class.java) },
                eventDeclinedNames = snapshot.child("eventDeclinedNames").children.mapNotNull { it.getValue(String::class.java) },
                captureAllowedIds = snapshot.child("captureAllowedIds").children.mapNotNull { it.getValue(String::class.java) },
                photoUrl = snapshot.child("photoUrl").getValue(String::class.java) ?: "",
                eventDuration = snapshot.child("eventDuration").getValue(String::class.java) ?: "",
                eventDurationMs = snapshot.child("eventDurationMs").getValue(Long::class.java) ?: 0L,
                messageTemplateKey = snapshot.child("messageTemplateKey").getValue(String::class.java) ?: "",
                messageTemplateParams = snapshot.child("messageTemplateParams")
                    .children
                    .mapNotNull { it.getValue(String::class.java) }
            )
        }

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                onMessageAdded(parseMessage(snapshot))
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                onMessageChanged(parseMessage(snapshot))
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val removedId = snapshot.child("id").getValue(String::class.java)
                    ?: snapshot.key
                    ?: return
                onMessageRemoved(removedId)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {}
        }

        database.getReference("circles/$circleId/messages")
            .orderByChild("timestamp")
            .addChildEventListener(listener)

        return listener
    }

    // Devuelve todas las URLs de fotos enviadas en el chat de un círculo.
    // Se usa para pintar la galería en el dialog de info del grupo.
    suspend fun getCirclePhotoUrls(circleId: String): List<String> {
        return suspendCancellableCoroutine { cont ->
            database.getReference("circles/$circleId/messages")
                .orderByChild("type")
                .equalTo("PHOTO")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val urls = snapshot.children
                            .mapNotNull { it.child("photoUrl").getValue(String::class.java) }
                            .filter { it.isNotBlank() }
                            // Las más recientes primero
                            .reversed()
                        cont.resume(urls)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(emptyList())
                    }
                })
        }
    }

    // Detiene la escucha del chat para evitar fugas cuando se cierra la pantalla.
    fun removeMessagesListener(circleId: String, listener: ChildEventListener) {
        database.getReference("circles/$circleId/messages")
            .orderByChild("timestamp")
            .removeEventListener(listener)
    }

    // Permite borrar un mensaje únicamente si pertenece al usuario actual.
    suspend fun deleteOwnMessage(circleId: String, messageId: String): Boolean {
        val uid = getUserId() ?: return false
        val msgRef = database.getReference("circles/$circleId/messages/$messageId")

        return suspendCancellableCoroutine { cont ->
            msgRef.addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        cont.resume(false)
                        return
                    }

                    val senderId = snapshot.child("senderId").getValue(String::class.java).orEmpty()
                    if (senderId != uid) {
                        cont.resume(false)
                        return
                    }

                    msgRef.removeValue()
                        .addOnSuccessListener { cont.resume(true) }
                        .addOnFailureListener { cont.resume(false) }
                }

                override fun onCancelled(error: DatabaseError) {
                    cont.resume(false)
                }
            })
        }
    }
}