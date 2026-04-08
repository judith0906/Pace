package com.novikon.pace.ui.login

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
import com.novikon.pace.R

// Clase encargada de toda la lógica de autenticación del login.
// Está separada de LoginActivity para que la Activity solo gestione
// la UI — esta clase gestiona todo lo relacionado con Firebase Auth.
//
// Recibe el Context para acceder a strings.xml con el idioma correcto.
// Comunica el resultado a la Activity mediante dos callbacks:
//   - onAuthSuccess: cuando el login es correcto, devuelve el nombre del usuario
//   - onAuthError: cuando algo falla, devuelve el mensaje de error
class LoginAuthManager(
    private val activity: Activity,
    private val context: Context,          // para acceder a strings.xml
    private val webClientId: String,
    private val onAuthSuccess: (userName: String?) -> Unit,
    private val onAuthError: (errorMessage: String) -> Unit
) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val TAG = "LoginAuthManager"
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

    // Inicia sesión con email y contraseña usando Firebase Auth.
    fun signInWithEmailPassword(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    onAuthSuccess(auth.currentUser?.displayName)
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    onAuthError(task.exception?.message ?: context.getString(R.string.error_unknown))
                }
            }
    }

    // Lanza el selector de cuentas de Google.
    // Primero cierra la sesión de Google anterior para forzar
    // que aparezca el selector aunque ya hubiera una cuenta elegida.
    fun startGoogleSignIn(launcher: ActivityResultLauncher<Intent>) {
        googleSignInClient.signOut().addOnCompleteListener(activity) {
            launcher.launch(googleSignInClient.signInIntent)
        }
    }

    // Procesa el resultado que devuelve Google después de que
    // el usuario elige su cuenta. Si todo va bien, autentica
    // el token con Firebase.
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
                    onAuthSuccess(auth.currentUser?.displayName)
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    onAuthError(context.getString(R.string.error_firebase_auth))
                }
            }
    }
}