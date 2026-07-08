package com.novikon.pace.ui.signup

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
import com.novikon.pace.ui.tutorial.TutorialActivity
import com.novikon.pace.utils.SessionManager
import com.novikon.pace.utils.applySystemBarInsets

// Pantalla de registro: crea cuentas nuevas con validacion y alta de usuario.
class SignUpActivity : AppCompatActivity() {

    private lateinit var nameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var signUpButton: MaterialButton
    private lateinit var googleSignUpButton: MaterialButton

    private lateinit var authManager: SignUpAuthManager
    private lateinit var sessionManager: SessionManager

    // Launcher para el flujo de Google Sign-In.
    // Cuando el usuario elige su cuenta, Google devuelve el resultado
    // aquí y lo pasamos al authManager para que lo procese.
    private val googleSignUpLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authManager.handleGoogleSignInResult(result.data)
    }

// onCreate: inicializa la pantalla y prepara vistas/eventos iniciales.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Aplicar tema e idioma ANTES de setContentView()
        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_signup)
        applySystemBarInsets()

        sessionManager = SessionManager(this)

        setupAuthManager()
        initializeViews()
        setupListeners()
    }
    private fun setupAuthManager() {
        authManager = SignUpAuthManager(
            activity = this,
            context = this,
            webClientId = getString(R.string.default_web_client_id),
            onAuthSuccess = ::onRegistrationSuccess,
            onAuthError = ::onRegistrationError
        )
    }
    private fun initializeViews() {
        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        signUpButton = findViewById(R.id.signUpButton)
        googleSignUpButton = findViewById(R.id.googleSignUpButton)
    }
    private fun setupListeners() {
        signUpButton.setOnClickListener {
            handleSignUp()
        }

        googleSignUpButton.setOnClickListener {
            authManager.startGoogleSignIn(googleSignUpLauncher)
        }

        // Volver a LoginActivity
        findViewById<android.widget.TextView>(R.id.loginText).setOnClickListener {
            finish()
        }
    }

    // Recoge los valores de los campos, los valida y si son correctos
    // deshabilita el botón y llama al authManager para crear la cuenta.
    private fun handleSignUp() {
        val name = nameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        // Pasamos el Context para que los mensajes de error
        // salgan en el idioma configurado por el usuario
        val validationResult = SignUpValidator.validateSignUpCredentials(
            this, name, email, password, confirmPassword
        )

        if (validationResult.isValid) {
            // Deshabilitar el botón mientras se crea la cuenta
            // para evitar que el usuario pulse varias veces seguidas
            signUpButton.isEnabled = false
            signUpButton.text = getString(R.string.creating_account)

            authManager.createAccountWithEmailPassword(name, email, password)
        } else {
            SignUpValidator.applyErrors(
                nameInput,
                emailInput,
                passwordInput,
                confirmPasswordInput,
                validationResult
            )
        }
    }

    // Se ejecuta cuando el registro es exitoso.
    // Guarda el timestamp del login, rehabilita el botón y navega al tutorial.
    private fun onRegistrationSuccess() {
        sessionManager.saveLastLoginTime()

        signUpButton.isEnabled = true
        signUpButton.text = getString(R.string.create_account)

        // Mensaje que antes venía de AuthConstants — ahora desde strings.xml
        Toast.makeText(
            this,
            getString(R.string.account_created_success),
            Toast.LENGTH_SHORT
        ).show()

        startActivity(Intent(this, TutorialActivity::class.java))
        finish()
    }

    // Se ejecuta cuando el registro falla.
    // Rehabilita el botón y muestra el mensaje de error en un Toast.
    private fun onRegistrationError(errorMessage: String) {
        signUpButton.isEnabled = true
        signUpButton.text = getString(R.string.create_account)

        Toast.makeText(
            this,
            "${getString(R.string.error)}$errorMessage",
            Toast.LENGTH_LONG
        ).show()
    }
}