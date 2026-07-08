package com.novikon.pace.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FCMTokenManager {

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun saveToken(token: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return suspendCancellableCoroutine { cont ->
            database.getReference("users/$userId/fcmTokens/$token")
                .setValue(true)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    suspend fun removeToken(token: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return suspendCancellableCoroutine { cont ->
            database.getReference("users/$userId/fcmTokens/$token")
                .removeValue()
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    suspend fun removeAllTokens(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return suspendCancellableCoroutine { cont ->
            database.getReference("users/$userId/fcmTokens")
                .removeValue()
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    fun getCurrentToken(onToken: (String?) -> Unit) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                onToken(if (task.isSuccessful) task.result else null)
            }
    }
}
