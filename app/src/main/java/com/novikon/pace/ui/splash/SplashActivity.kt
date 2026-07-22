package com.novikon.pace.ui.splash

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.android.gms.ads.MobileAds
import com.novikon.pace.R
import com.novikon.pace.data.RealtimeDatabaseManager
import com.novikon.pace.ui.login.LoginActivity
import com.novikon.pace.ui.main.MainActivity
import com.novikon.pace.utils.SessionManager
import com.novikon.pace.utils.SettingsManager
import kotlinx.coroutines.launch

// Pantalla splash: prepara app al inicio y coordina animacion de bienvenida.
class SplashActivity : AppCompatActivity() {

    private lateinit var animator: SplashAnimator
    private lateinit var sessionManager: SessionManager
    private val auth: FirebaseAuth = Firebase.auth

// onCreate: inicializa la pantalla y prepara vistas/eventos iniciales.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Pace)
        setContentView(R.layout.activity_splash)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)

        // Detectar e inicializar el idioma del dispositivo si es la primera vez —
        // si el idioma del dispositivo es soportado (es/en/fr) lo usa,
        // si no, usa inglés por defecto. Este valor persiste al cerrar sesión.
        val settingsManager = SettingsManager(this)

        // Pre-inicializar el SDK de anuncios durante el splash para que esté
        // listo cuando MainActivity cargue el banner — así no compite por CPU
        // con el resto del startup. MobileAds.initialize() corre en segundo plano
        // y no bloquea el hilo principal.
        MobileAds.initialize(this) {}

        setupWindowInsets()
        setupAnimations()
    }
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.splash)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    private fun setupAnimations() {
        val logoText = findViewById<TextView>(R.id.logoText)
        val paceLogo = findViewById<View>(R.id.paceLogo)
        val novikonText = findViewById<TextView>(R.id.novikonText)
        val progressLine = findViewById<View>(R.id.progressLine)

        animator = SplashAnimator(
            scope = lifecycleScope,
            logoText = logoText,
            paceLogo = paceLogo,
            novikonText = novikonText,
            progressLine = progressLine,
            onAnimationComplete = ::navigateToNextScreen
        )

        animator.startAnimations()
    }

    // Decide a qué pantalla ir cuando termina la animación.
    // Primero hace las comprobaciones locales (rápidas) y luego
    // verifica con Firebase si este dispositivo sigue autorizado.
    //
    // Flujo:
    //   1. Si no hay usuario Firebase o la sesión local expiró → login (sin consultar Firebase)
    //   2. Si la sesión local es válida → consultar Firebase para ver si el dispositivo
    //      sigue registrado (puede haber sido eliminado remotamente desde otro dispositivo)
    //   3. Si Firebase confirma que el dispositivo existe → MainActivity
    //   4. Si Firebase dice que el dispositivo ya no existe → marcar logout y → login
    //   5. Si Firebase falla (sin red) → dejar pasar, mejor falso positivo que bloquear
    private fun navigateToNextScreen() {
        val currentUser = auth.currentUser

        // Paso 1: comprobaciones locales — no necesitan red
        if (currentUser == null || sessionManager.shouldShowLogin()) {
            goToLogin()
            return
        }

        // Paso 2: verificar con Firebase si este dispositivo sigue activo
        val deviceId = sessionManager.getDeviceId()
        val databaseManager = RealtimeDatabaseManager()

        lifecycleScope.launch {
            val isDeviceStillRegistered = databaseManager.isDeviceRegistered(deviceId)

            if (isDeviceStillRegistered == false) {
                // El dispositivo fue eliminado remotamente — forzar login
                sessionManager.forceLogout()
                goToLogin()
            } else {
                // true (existe) o null (sin red) → dejar pasar
                goToMain()
            }
        }
    }
    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}