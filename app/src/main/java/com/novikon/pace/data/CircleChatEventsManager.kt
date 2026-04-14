package com.novikon.pace.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.novikon.pace.models.Message
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
            val messageRef = database.getReference("circles/$circleId/messages/$messageId")

            eventRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
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
                eventDeclinedNames = snapshot.child("eventDeclinedNames").children.mapNotNull { it.getValue(String::class.java) }
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