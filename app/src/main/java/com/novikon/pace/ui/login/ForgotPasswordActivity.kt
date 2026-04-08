package com.novikon.pace.ui.login

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.novikon.pace.R
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var sendButton: MaterialButton
    private val auth: FirebaseAuth = Firebase.auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Aplicar tema e idioma ANTES de setContentView()
        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_forgot_password)

        initializeViews()
        setupListeners()
    }

    private fun initializeViews() {
        emailInput = findViewById(R.id.emailInput)
        sendButton = findViewById(R.id.sendButton)
    }

    private fun setupListeners() {
        findViewById<android.widget.ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        sendButton.setOnClickListener {
            handlePasswordReset()
        }

        findViewById<android.widget.TextView>(R.id.backToLoginText).setOnClickListener {
            finish()
        }
    }

    // Valida el email, deshabilita el botón mientras envía
    // y muestra el resultado en un Toast.
    private fun handlePasswordReset() {
        val email = emailInput.text.toString().trim()

        // Validar que el email no esté vacío y tenga formato correcto
        if (email.isEmpty()) {
            emailInput.error = getString(R.string.email_required)
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = getString(R.string.invalid_email)
            return
        }

        // Deshabilitar el botón mientras se envía para evitar
        // que el usuario pulse varias veces seguidas
        sendButton.isEnabled = false
        sendButton.text = getString(R.string.sending)

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                // Rehabilitar el botón cuando termina, haya ido bien o mal
                sendButton.isEnabled = true
                sendButton.text = getString(R.string.send_link)

                if (task.isSuccessful) {
                    // Mensaje que antes venía de AuthConstants — ahora desde strings.xml
                    Toast.makeText(
                        this,
                        getString(R.string.password_reset_email_sent),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    Toast.makeText(
                        this,
                        "${getString(R.string.error)}${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}