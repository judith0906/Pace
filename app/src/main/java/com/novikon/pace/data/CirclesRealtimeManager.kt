package com.novikon.pace.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.novikon.pace.R
import com.novikon.pace.models.BlockedUser
import com.novikon.pace.models.Circle
import com.novikon.pace.models.CircleMember
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Informacion resumida del grupo: nombre, miembros, limites y metadatos.
data class GroupInfo(
    val circleId: String,
    val name: String,
    val createdBy: String,
    val joinCode: String,
    val maxParticipants: Int,
    val memberIds: List<String>
)

// Resultado de bloqueo: indica efectos del bloqueo sobre usuarios y pertenencia.
data class BlockActionResult(
    val success: Boolean,
    val blockerLeftGroup: Boolean,
    val blockedUserRemoved: Boolean
)

// Motivos de fallo al unirse: clasifica por que un codigo de acceso no prospera.
enum class JoinFailReason {
    INVALID_CODE,
    CODE_NOT_FOUND,
    GROUP_NOT_FOUND,
    BLOCKED,
    GROUP_FULL_OR_TRANSACTION_FAILED
}

// Resultado de union: informa si el ingreso al circulo se completo o fallo.
data class JoinResult(
    val success: Boolean,
    val reason: JoinFailReason? = null
)

// Gestor realtime de circulos: encapsula operaciones CRUD y membresia en Firebase.
class CirclesRealtimeManager(
    private val context: Context
) {

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    fun getUserId(): String? = auth.currentUser?.uid
    fun getUserName(): String = auth.currentUser?.displayName
        ?: auth.currentUser?.email?.substringBefore("@")
        ?: context.getString(R.string.default_user)

    // -------------------- JOIN CODES --------------------

    private fun randomJoinCode(length: Int = 6): String {
        return (1..length).map { ('0'..'9').random() }.joinToString("")
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
    private suspend fun existsBlockBetween(userA: String, userB: String): Boolean {
        val aBlocksB = suspendCancellableCoroutine<Boolean> { cont ->
            database.getReference("users/$userA/blocked/$userB")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) = cont.resume(snapshot.exists())
                    override fun onCancelled(error: DatabaseError) = cont.resume(false)
                })
        }
        if (aBlocksB) return true

        val bBlocksA = suspendCancellableCoroutine<Boolean> { cont ->
            database.getReference("users/$userB/blocked/$userA")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) = cont.resume(snapshot.exists())
                    override fun onCancelled(error: DatabaseError) = cont.resume(false)
                })
        }

        return bBlocksA
    }

    // -------------------- CIRCLES CRUD --------------------

    suspend fun createCircle(name: String, maxParticipants: Int): String? {
        val userId = getUserId() ?: return null
        val joinCode = generateUniqueJoinCode()
        val safeMax = maxParticipants.coerceAtLeast(2)

        return suspendCancellableCoroutine { cont ->
            val newCircleRef = database.getReference("circles").push()
            val circleId = newCircleRef.key ?: run {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val timestamp = System.currentTimeMillis()

            val updates = mapOf(
                "circles/$circleId/name" to name,
                "circles/$circleId/createdBy" to userId,
                "circles/$circleId/createdAt" to timestamp,
                "circles/$circleId/joinCode" to joinCode,
                "circles/$circleId/maxParticipants" to safeMax,
                "circles/$circleId/members/$userId" to true,
                "users/$userId/circles/$circleId" to true,
                "circleJoinCodes/$joinCode" to circleId
            )

            database.reference.updateChildren(updates)
                .addOnSuccessListener { cont.resume(circleId) }
                .addOnFailureListener { cont.resume(null) }
        }
    }
    suspend fun joinCircleByCode(codeRaw: String): JoinResult {
        val userId = getUserId() ?: return JoinResult(false, JoinFailReason.GROUP_NOT_FOUND)
        val code = codeRaw.trim()
        if (!code.matches(Regex("^\\d{6}$"))) return JoinResult(false, JoinFailReason.INVALID_CODE)

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
        } ?: return JoinResult(false, JoinFailReason.CODE_NOT_FOUND)

        // Leer SOLO members (permitido por reglas), no /circles/$circleId completo
        val memberIds = suspendCancellableCoroutine<List<String>> { cont ->
            database.getReference("circles/$circleId/members")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        cont.resume(snapshot.children.mapNotNull { it.key })
                    }
                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(emptyList())
                    }
                })
        }

        // Bloqueo bidireccional con miembros actuales
        for (memberId in memberIds) {
            if (existsBlockBetween(memberId, userId)) {
                return JoinResult(false, JoinFailReason.BLOCKED)
            }
        }

        val joined = addMemberToCircleTransactional(circleId, userId)
        if (!joined) return JoinResult(false, JoinFailReason.GROUP_FULL_OR_TRANSACTION_FAILED)

        sendSystemTemplateMessage(
            circleId = circleId,
            templateKey = "circles_system_joined",
            templateParams = listOf(getUserName()),
            fallbackText = context.getString(R.string.circles_system_joined, getUserName())
        )

        return JoinResult(true, null)
    }
    private suspend fun addMemberToCircleTransactional(circleId: String, targetUserId: String): Boolean {
        return suspendCancellableCoroutine { cont ->
            val membersRef = database.getReference("circles/$circleId/members")
            val maxRef = database.getReference("circles/$circleId/maxParticipants")

            maxRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(maxSnapshot: DataSnapshot) {
                    val max = maxSnapshot.getValue(Int::class.java) ?: 6

                    membersRef.runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            val map = currentData.value as? Map<String, Any?> ?: emptyMap()

                            if (map.containsKey(targetUserId)) {
                                return Transaction.success(currentData)
                            }

                            if (map.size >= max) {
                                return Transaction.abort()
                            }

                            val mutable = map.toMutableMap()
                            mutable[targetUserId] = true
                            currentData.value = mutable
                            return Transaction.success(currentData)
                        }
                        override fun onComplete(
                            error: DatabaseError?,
                            committed: Boolean,
                            snapshot: DataSnapshot?
                        ) {
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
    suspend fun leaveCircle(circleId: String): Boolean {
        val uid = getUserId() ?: return false
        val info = getGroupInfo(circleId) ?: return false
        if (info.createdBy == uid) return false

        // Mensaje ANTES de salir, para no perder permisos de escritura en messages
        val leaverName = getDisplayName(uid)
        val systemOk = sendSystemTemplateMessage(
            circleId = circleId,
            templateKey = "circles_system_left",
            templateParams = listOf(leaverName),
            fallbackText = context.getString(R.string.circles_system_left, leaverName)
        )
        if (!systemOk) return false

        return suspendCancellableCoroutine { cont ->
            val updates = mapOf(
                "circles/$circleId/members/$uid" to null,
                "users/$uid/circles/$circleId" to null
            )
            database.reference.updateChildren(updates)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }
    suspend fun deleteCircle(circleId: String): Boolean {
        val currentUid = getUserId() ?: return false
        val info = getGroupInfo(circleId) ?: return false
        if (info.createdBy != currentUid) return false

        val updates = mutableMapOf<String, Any?>(
            "circles/$circleId" to null
        )

        if (info.joinCode.isNotBlank()) {
            updates["circleJoinCodes/${info.joinCode}"] = null
        }

        info.memberIds.forEach { uid ->
            updates["users/$uid/circles/$circleId"] = null
        }

        return suspendCancellableCoroutine { cont ->
            database.reference.updateChildren(updates)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    // -------------------- GROUP INFO --------------------

    suspend fun getGroupInfo(circleId: String): GroupInfo? {
        return suspendCancellableCoroutine { cont ->
            database.getReference("circles/$circleId")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) {
                            cont.resume(null)
                            return
                        }

                        cont.resume(
                            GroupInfo(
                                circleId = circleId,
                                name = snapshot.child("name").getValue(String::class.java) ?: "",
                                createdBy = snapshot.child("createdBy").getValue(String::class.java) ?: "",
                                joinCode = snapshot.child("joinCode").getValue(String::class.java) ?: "",
                                maxParticipants = snapshot.child("maxParticipants").getValue(Int::class.java) ?: 6,
                                memberIds = snapshot.child("members").children.mapNotNull { it.key }
                            )
                        )
                    }
                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(null)
                    }
                })
        }
    }
    suspend fun getUserCircles(): List<Circle> {
        val userId = getUserId() ?: return emptyList()

        val circleIds = suspendCancellableCoroutine<List<String>> { cont ->
            database.getReference("users/$userId/circles")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        cont.resume(snapshot.children.mapNotNull { it.key })
                    }
                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(emptyList())
                    }
                })
        }

        if (circleIds.isEmpty()) return emptyList()

        val circles = mutableListOf<Circle>()
        for (circleId in circleIds) {
            val circle = getCircleById(circleId) ?: continue
            circles.add(circle)
        }

        return circles.sortedByDescending {
            it.lastMessageTime.takeIf { t -> t > 0L } ?: it.createdAt
        }
    }
    private suspend fun getCircleById(circleId: String): Circle? {
        val snapshot = suspendCancellableCoroutine<DataSnapshot?> { cont ->
            database.getReference("circles/$circleId")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        cont.resume(if (snapshot.exists()) snapshot else null)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(null)
                    }
                })
        } ?: return null

        val name = snapshot.child("name").getValue(String::class.java) ?: ""
        val createdBy = snapshot.child("createdBy").getValue(String::class.java) ?: ""
        val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
        val memberCount = snapshot.child("members").childrenCount.toInt()

        var lastMessage = ""
        var lastMessageTime = 0L
        val lastMsg = snapshot.child("messages").children.lastOrNull()
        if (lastMsg != null) {
            val templateKey = lastMsg.child("messageTemplateKey").getValue(String::class.java)
            val templateParams = lastMsg.child("messageTemplateParams").children
                .mapNotNull { it.getValue(String::class.java) }
                .toTypedArray()

            lastMessage = if (!templateKey.isNullOrBlank()) {
                val resId = context.resources.getIdentifier(templateKey, "string", context.packageName)
                if (resId != 0) {
                    if (templateParams.isNotEmpty()) context.getString(resId, *templateParams)
                    else context.getString(resId)
                } else {
                    lastMsg.child("text").getValue(String::class.java) ?: ""
                }
            } else {
                lastMsg.child("text").getValue(String::class.java) ?: ""
            }
            lastMessageTime = lastMsg.child("timestamp").getValue(Long::class.java) ?: 0L
        }

        val memberIds = snapshot.child("members").children.mapNotNull { it.key }
        val photoUrls = mutableListOf<String?>()
        for (uid in memberIds.take(5)) {
            val url = suspendCancellableCoroutine<String?> { c ->
                database.getReference("users/$uid/profile/photoUrl")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(s: DataSnapshot) {
                            c.resume(s.getValue(String::class.java))
                        }
                        override fun onCancelled(e: DatabaseError) { c.resume(null) }
                    })
            }
            photoUrls.add(if (!url.isNullOrBlank()) url else null)
        }

        return Circle(
            id = circleId,
            name = name,
            createdBy = createdBy,
            createdAt = createdAt,
            memberCount = memberCount,
            lastMessage = lastMessage,
            lastMessageTime = lastMessageTime,
            memberPhotoUrls = photoUrls
        )
    }
    suspend fun getMemberDisplayNames(userIds: List<String>): List<CircleMember> {
        val me = getUserId()
        val myName = getUserName()
        val result = mutableListOf<CircleMember>()

        for (uid in userIds) {
            val displayName = suspendCancellableCoroutine<String> { cont ->
                database.getReference("users/$uid/profile/displayName")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val name = snapshot.getValue(String::class.java)?.trim()
                            cont.resume(
                                when {
                                    !name.isNullOrEmpty() -> name
                                    uid == me -> myName
                                    else -> context.getString(R.string.default_user)
                                }
                            )
                        }
                        override fun onCancelled(error: DatabaseError) {
                            cont.resume(
                                if (uid == me) myName else context.getString(R.string.default_user)
                            )
                        }
                    })
            }

            result.add(CircleMember(userId = uid, displayName = displayName))
        }

        return result
    }
    private suspend fun getDisplayName(uid: String): String {
        return suspendCancellableCoroutine { cont ->
            database.getReference("users/$uid/profile/displayName")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val name = snapshot.getValue(String::class.java)
                        cont.resume(name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.default_user))
                    }
                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(context.getString(R.string.default_user))
                    }
                })
        }
    }

    // Renombra el círculo. Solo lo puede hacer el admin (creador del grupo).
    suspend fun updateCircleName(circleId: String, newName: String): Boolean {
        val currentUid = getUserId() ?: return false
        val info = getGroupInfo(circleId) ?: return false
        if (info.createdBy != currentUid) return false
        if (newName.isBlank()) return false

        return suspendCancellableCoroutine { cont ->
            database.getReference("circles/$circleId/name")
                .setValue(newName.trim())
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }
    suspend fun updateMaxParticipants(circleId: String, newMax: Int): Boolean {
        val currentUid = getUserId() ?: return false
        val info = getGroupInfo(circleId) ?: return false

        if (info.createdBy != currentUid) return false
        if (newMax < info.memberIds.size) return false

        return suspendCancellableCoroutine { cont ->
            database.getReference("circles/$circleId/maxParticipants")
                .setValue(newMax)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }
    suspend fun removeMember(circleId: String, memberUid: String): Boolean {
        val currentUid = getUserId() ?: return false
        val info = getGroupInfo(circleId) ?: return false

        if (info.createdBy != currentUid) return false
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

    suspend fun removeUserFromAllCircles(userId: String): Boolean {
        val circleIds = suspendCancellableCoroutine<List<String>> { cont ->
            database.getReference("users/$userId/circles")
                .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        cont.resume(snapshot.children.mapNotNull { it.key })
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        cont.resume(emptyList())
                    }
                })
        }

        if (circleIds.isEmpty()) return true

        val updates = mutableMapOf<String, Any?>()

        for (circleId in circleIds) {
            val info = getGroupInfo(circleId) ?: continue

            if (info.createdBy == userId) {
                // Es creador: borrar el círculo entero
                updates["circles/$circleId"] = null
                if (info.joinCode.isNotBlank()) {
                    updates["circleJoinCodes/${info.joinCode}"] = null
                }
                // Limpiar la referencia en todos los miembros
                info.memberIds.forEach { memberId ->
                    updates["users/$memberId/circles/$circleId"] = null
                }
            } else {
                // Es miembro: solo salir
                updates["circles/$circleId/members/$userId"] = null
                updates["users/$userId/circles/$circleId"] = null
            }
        }

        return suspendCancellableCoroutine { cont ->
            database.reference.updateChildren(updates)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    // -------------------- BLOCKING --------------------

    suspend fun blockUser(targetUid: String): Boolean {
        val currentUid = getUserId() ?: return false
        return suspendCancellableCoroutine { cont ->
            database.getReference("users/$currentUid/blocked/$targetUid")
                .setValue(true)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }
    suspend fun unblockUser(targetUid: String): Boolean {
        val currentUid = getUserId() ?: return false
        return suspendCancellableCoroutine { cont ->
            database.getReference("users/$currentUid/blocked/$targetUid")
                .removeValue()
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }
    suspend fun getBlockedUsers(): List<BlockedUser> {
        val currentUid = getUserId() ?: return emptyList()

        val blockedIds = suspendCancellableCoroutine<List<String>> { cont ->
            database.getReference("users/$currentUid/blocked")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        cont.resume(snapshot.children.mapNotNull { it.key })
                    }
                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(emptyList())
                    }
                })
        }

        if (blockedIds.isEmpty()) return emptyList()

        val result = mutableListOf<BlockedUser>()
        for (uid in blockedIds) {
            val displayName = getDisplayName(uid)

            val photoUrl = suspendCancellableCoroutine<String?> { cont ->
                database.getReference("users/$uid/profile/photoUrl")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            cont.resume(snapshot.getValue(String::class.java))
                        }
                        override fun onCancelled(error: DatabaseError) {
                            cont.resume(null)
                        }
                    })
            }

            result.add(
                BlockedUser(
                    userId = uid,
                    displayName = displayName,
                    photoUrl = photoUrl
                )
            )
        }

        return result
    }
    suspend fun blockUserWithPolicy(circleId: String, targetUid: String): BlockActionResult {
        val blockerUid = getUserId() ?: return BlockActionResult(false, false, false)
        val info = getGroupInfo(circleId) ?: return BlockActionResult(false, false, false)

        val blockedOk = blockUser(targetUid)
        if (!blockedOk) return BlockActionResult(false, false, false)

        val blockerIsAdmin = info.createdBy == blockerUid

        return if (blockerIsAdmin) {
            if (targetUid == blockerUid) {
                BlockActionResult(false, false, false)
            } else {
                val removed = removeMember(circleId, targetUid)
                if (removed) {
                    val targetName = getDisplayName(targetUid)
                    sendSystemTemplateMessage(
                        circleId = circleId,
                        templateKey = "circles_system_left",
                        templateParams = listOf(targetName),
                        fallbackText = context.getString(R.string.circles_system_left, targetName)
                    )
                }
                BlockActionResult(
                    success = removed,
                    blockerLeftGroup = false,
                    blockedUserRemoved = removed
                )
            }
        } else {
            val left = leaveCircle(circleId)
            BlockActionResult(
                success = left,
                blockerLeftGroup = left,
                blockedUserRemoved = false
            )
        }
    }

    // -------------------- MUTE --------------------

    suspend fun muteCircle(circleId: String): Boolean {
        val uid = getUserId() ?: return false
        return suspendCancellableCoroutine { cont ->
            database.getReference("users/$uid/mutedCircles/$circleId")
                .setValue(true)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    suspend fun unmuteCircle(circleId: String): Boolean {
        val uid = getUserId() ?: return false
        return suspendCancellableCoroutine { cont ->
            database.getReference("users/$uid/mutedCircles/$circleId")
                .removeValue()
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    suspend fun isCircleMuted(circleId: String): Boolean {
        val uid = getUserId() ?: return false
        return suspendCancellableCoroutine { cont ->
            database.getReference("users/$uid/mutedCircles/$circleId")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        cont.resume(snapshot.exists() && snapshot.getValue(Boolean::class.java) == true)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(false)
                    }
                })
        }
    }

    // -------------------- MESSAGES --------------------

    private suspend fun sendSystemTemplateMessage(
        circleId: String,
        templateKey: String,
        templateParams: List<String> = emptyList(),
        fallbackText: String
    ): Boolean {
        return suspendCancellableCoroutine { cont ->
            val messagesRef = database.getReference("circles/$circleId/messages")
            val newMsgRef = messagesRef.push()
            val messageId = newMsgRef.key ?: run {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }

            val messageMap = mapOf(
                "id" to messageId,
                "text" to fallbackText,
                "messageTemplateKey" to templateKey,
                "messageTemplateParams" to templateParams,
                "senderId" to "system",
                "senderName" to "system",
                "type" to "SYSTEM",
                "timestamp" to System.currentTimeMillis()
            )

            newMsgRef.setValue(messageMap)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }
    suspend fun sendMessage(circleId: String, text: String): Boolean {
        val userId = getUserId() ?: return false
        val userName = getUserName()

        return suspendCancellableCoroutine { cont ->
            val messagesRef = database.getReference("circles/$circleId/messages")
            val newMsgRef = messagesRef.push()
            val messageId = newMsgRef.key ?: run {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }

            val messageMap = mapOf(
                "id" to messageId,
                "text" to text,
                "senderId" to userId,
                "senderName" to userName,
                "type" to "TEXT",
                "timestamp" to System.currentTimeMillis()
            )

            newMsgRef.setValue(messageMap)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }
    suspend fun sendTemplateMessage(
        circleId: String,
        messageTemplateKey: String,
        messageTemplateParams: List<String> = emptyList(),
        fallbackText: String = ""
    ): Boolean {
        val userId = getUserId() ?: return false
        val userName = getUserName()

        return suspendCancellableCoroutine { cont ->
            val messagesRef = database.getReference("circles/$circleId/messages")
            val newMsgRef = messagesRef.push()
            val messageId = newMsgRef.key ?: run {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }

            val messageMap = mapOf(
                "id" to messageId,
                "text" to fallbackText, // compatibilidad / fallback
                "messageTemplateKey" to messageTemplateKey,
                "messageTemplateParams" to messageTemplateParams,
                "senderId" to userId,
                "senderName" to userName,
                "type" to "TEXT",
                "timestamp" to System.currentTimeMillis()
            )

            newMsgRef.setValue(messageMap)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }
}