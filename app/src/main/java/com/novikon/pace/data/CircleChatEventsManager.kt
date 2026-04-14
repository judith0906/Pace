package com.novikon.pace.data

import android.graphics.Bitmap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.novikon.pace.models.Message
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import com.novikon.pace.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
//Temporal
import android.util.Log

class CircleChatEventsManager {

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun getUserId(): String? = auth.currentUser?.uid

    private fun getUserName(): String {
        return auth.currentUser?.displayName
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "Usuario"
    }

    suspend fun createEvent(
        circleId: String,
        habitName: String,
        scheduledAtMillis: Long
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

            val updates = mapOf<String, Any>(
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

                "circles/$circleId/messages/$messageId/id" to messageId,
                "circles/$circleId/messages/$messageId/type" to "EVENT",
                "circles/$circleId/messages/$messageId/text" to "Evento: $habitName",
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
                "circles/$circleId/messages/$messageId/eventDeclinedNames" to emptyList<String>()
            )

            baseRef.updateChildren(updates)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

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

                val habitName = eventSnapshot.child("habitName").getValue(String::class.java) ?: "Evento"
                val joinedIds = eventSnapshot.child("joinedIds").children.mapNotNull { it.getValue(String::class.java) }
                val now = System.currentTimeMillis()

                val msgRef = database.getReference("circles/$circleId/messages").push()
                val msgId = msgRef.key ?: run {
                    onDone()
                    return
                }

                val messageMap = mapOf(
                    "id" to msgId,
                    "type" to "EVENT_START",
                    "text" to "Ha iniciado el evento: $habitName",
                    "senderId" to "system",
                    "senderName" to "system",
                    "timestamp" to now,
                    "eventId" to eventId,
                    "eventHabitName" to habitName,
                    "captureAllowedIds" to joinedIds
                )

                msgRef.setValue(messageMap)
                    .addOnCompleteListener { onDone() }
            }
        })
    }

    suspend fun sendPhotoMoment(
        circleId: String,
        eventId: String,
        bitmap: Bitmap
    ): Boolean {
        val uid = getUserId() ?: return false
        val userName = getUserName()
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
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
                "text" to "Foto",
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
                } else null
            } catch (e: Exception) {
                Log.e("SupabaseUpload", "upload exception", e)
                null
            } finally {
                conn.disconnect()
            }
        }
    }

    fun observeMessages(
        circleId: String,
        onMessageAdded: (Message) -> Unit,
        onMessageChanged: (Message) -> Unit
    ): ChildEventListener {
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
                eventJoinedIds = snapshot.child("eventJoinedIds").children.mapNotNull { it.getValue(String::class.java) },
                eventJoinedNames = snapshot.child("eventJoinedNames").children.mapNotNull { it.getValue(String::class.java) },
                eventDeclinedIds = snapshot.child("eventDeclinedIds").children.mapNotNull { it.getValue(String::class.java) },
                eventDeclinedNames = snapshot.child("eventDeclinedNames").children.mapNotNull { it.getValue(String::class.java) },
                captureAllowedIds = snapshot.child("captureAllowedIds").children.mapNotNull { it.getValue(String::class.java) },
                photoUrl = snapshot.child("photoUrl").getValue(String::class.java) ?: ""
            )
        }

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                onMessageAdded(parseMessage(snapshot))
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                onMessageChanged(parseMessage(snapshot))
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        database.getReference("circles/$circleId/messages")
            .orderByChild("timestamp")
            .addChildEventListener(listener)

        return listener
    }

    fun removeMessagesListener(circleId: String, listener: ChildEventListener) {
        database.getReference("circles/$circleId/messages")
            .orderByChild("timestamp")
            .removeEventListener(listener)
    }
}