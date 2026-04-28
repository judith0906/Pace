package com.novikon.pace.ui.signup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import com.novikon.pace.R

// Clase encargada de toda la lógica de autenticación del registro.
// Está separada de SignUpActivity para que la Activity solo gestione
// la UI — esta clase gestiona todo lo relacionado con Firebase Auth.
//
// Recibe el Context para acceder a strings.xml con el idioma correcto.
// Comunica el resultado a la Activity mediante dos callbacks:
//   - onAuthSuccess: cuando el registro es correcto, devuelve el nombre del usuario
//   - onAuthError: cuando algo falla, devuelve el mensaje de error
class SignUpAuthManager(
    private val activity: Activity,
    private val context: Context,
    private val webClientId: String,
    private val onAuthSuccess: () -> Unit,
    private val onAuthError: (errorMessage: String) -> Unit
) {

    private val auth: FirebaseAuth = Firebase.auth
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val TAG = "SignUpAuthManager"
    }

    init {
        setupGoogleSignIn()
    }
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(activity, gso)
    }

    // Crea una cuenta nueva con email, contraseña y nombre.
    // Después de crear la cuenta actualiza el perfil de Firebase Auth
    // con el nombre introducido por el usuario.
    fun createAccountWithEmailPassword(name: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "createUserWithEmail:success")

                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()

                    auth.currentUser?.updateProfile(profileUpdates)
                        ?.addOnCompleteListener { updateTask ->
                            if (updateTask.isSuccessful) {
                                Log.d(TAG, "User profile updated")
                            } else {
                                Log.w(TAG, "User profile update failed", updateTask.exception)
                            }

                            // Guardar el nombre también en Realtime Database —
                            // es desde donde MainActivity y AccountSettings lo leen
                            val userId = auth.currentUser?.uid
                            if (userId != null && name.isNotEmpty()) {
                                FirebaseDatabase.getInstance()
                                    .getReference("users/$userId/profile/displayName")
                                    .setValue(name)
                            }

                            onAuthSuccess()
                        }
                } else {
                    Log.w(TAG, "createUserWithEmail:failure", task.exception)
                    onAuthError(
                        task.exception?.message ?: context.getString(R.string.error_unknown)
                    )
                }
            }
    }

    // Lanza el selector de cuentas de Google.
    fun startGoogleSignIn(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(googleSignInClient.signInIntent)
    }

    // Procesa el resultado que devuelve Google después de que
    // el usuario elige su cuenta.
    fun handleGoogleSignInResult(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { token ->
                firebaseAuthWithGoogle(token)
            } ?: run {
                onAuthError(context.getString(R.string.error_google_token))
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign in failed", e)
            onAuthError(context.getString(R.string.error_google_signin))
        }
    }

    // Autentica el token de Google con Firebase.
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    onAuthSuccess()
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    onAuthError(context.getString(R.string.error_firebase_auth))
                }
            }
    }
}