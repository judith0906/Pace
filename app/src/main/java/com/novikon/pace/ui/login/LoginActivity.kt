package com.novikon.pace.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.novikon.pace.R
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.ui.main.MainActivity
import com.novikon.pace.ui.signup.SignUpActivity
import com.novikon.pace.utils.SessionManager

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var googleSignInButton: MaterialButton

    private lateinit var authManager: LoginAuthManager
    private lateinit var sessionManager: SessionManager

    // Launcher para el flujo de Google Sign-In.
    // Cuando el usuario elige su cuenta, Google devuelve el resultado
    // aquí y lo pasamos al authManager para que lo procese.
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authManager.handleGoogleSignInResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Aplicar tema e idioma ANTES de setContentView()
        // para que la pantalla se dibuje ya con los ajustes correctos
        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        setupAuthManager()
        initializeViews()
        setupListeners()
    }

    // Crea el LoginAuthManager pasándole los callbacks que
    // se ejecutarán cuando el login tenga éxito o falle.
    private fun setupAuthManager() {
        authManager = LoginAuthManager(
            activity = this,
            context = this,
            webClientId = getString(R.string.default_web_client_id),
            onAuthSuccess = ::onAuthenticationSuccess,
            onAuthError = ::onAuthenticationError
        )
    }

    private fun initializeViews() {
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        googleSignInButton = findViewById(R.id.googleSignInButton)
    }

    private fun setupListeners() {
        loginButton.setOnClickListener {
            handleEmailPasswordLogin()
        }

        googleSignInButton.setOnClickListener {
            authManager.startGoogleSignIn(googleSignInLauncher)
        }

        findViewById<android.widget.TextView>(R.id.forgotPasswordText).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        findViewById<android.widget.TextView>(R.id.signUpText).setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    // Recoge los valores de los campos, los valida y si son correctos
    // llama al authManager para iniciar sesión.
    // Si hay errores de validación los muestra en los propios campos.
    private fun handleEmailPasswordLogin() {
        val email = emailInput.text.toString()
        val password = passwordInput.text.toString()

        // Pasamos el Context para que los mensajes de error
        // salgan en el idioma configurado por el usuario
        val validationResult = LoginValidator.validateCredentials(this, email, password)

        if (validationResult.isValid) {
            authManager.signInWithEmailPassword(email, password)
        } else {
            LoginValidator.applyErrors(emailInput, passwordInput, validationResult)
        }
    }

    // Se ejecuta cuando el login es exitoso.
    // Guarda el timestamp del login y navega a MainActivity.
    private fun onAuthenticationSuccess(userName: String?) {
        sessionManager.saveLastLoginTime()

        val message = if (userName != null) {
            getString(R.string.welcome_user, userName)
        } else {
            getString(R.string.welcome)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // Se ejecuta cuando el login falla.
    // Muestra el mensaje de error en un Toast.
    private fun onAuthenticationError(errorMessage: String) {
        Toast.makeText(
            this,
            "${getString(R.string.error)}$errorMessage",
            Toast.LENGTH_LONG
        ).show()
    }
}