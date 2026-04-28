package com.novikon.pace.data

import android.content.Context
import android.graphics.Bitmap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.novikon.pace.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

// Gestiona la subida de foto de perfil a Supabase y el guardado de su URL en Firebase.
class ProfilePhotoManager(
    private val context: Context
) {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    // Sube el bitmap recortado del avatar y devuelve la URL pública.
    // La imagen se guarda en una carpeta por usuario para mantener ordenado el bucket.
    suspend fun uploadProfilePhoto(bitmap: Bitmap): String? {
        val uid = auth.currentUser?.uid ?: return null

        val imageBytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
            stream.toByteArray()
        }

        val objectPath = "profiles/$uid/avatar_${System.currentTimeMillis()}.jpg"
        return uploadToSupabase(imageBytes, objectPath)
    }

    // Guarda en Firebase Realtime Database la URL final del avatar del usuario.
    suspend fun saveProfilePhotoUrl(url: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return suspendCancellableCoroutine { cont ->
            database.getReference("users/$uid/profile/photoUrl")
                .setValue(url)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    // Sube bytes al bucket de Supabase y construye la URL pública.
    private suspend fun uploadToSupabase(imageBytes: ByteArray, objectPath: String): String? {
        return withContext(Dispatchers.IO) {
            val baseUrl = BuildConfig.SUPABASE_URL.removeSuffix("/")
            val bucket = BuildConfig.SUPABASE_PROFILE_BUCKET
            val uploadUrl = "$baseUrl/storage/v1/object/$bucket/$objectPath"

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
                if (code in 200..299) {
                    "$baseUrl/storage/v1/object/public/$bucket/$objectPath"
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }
    }
}