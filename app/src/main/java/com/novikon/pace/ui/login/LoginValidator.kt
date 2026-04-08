package com.novikon.pace.ui.login

import android.content.Context
import android.util.Patterns
import com.google.android.material.textfield.TextInputEditText
import com.novikon.pace.R

// Objeto encargado de validar los campos del formulario de login.
// Está separado de LoginActivity para que la Activity no tenga
// lógica de validación mezclada con lógica de navegación.
//
// Recibe el Context para poder acceder a strings.xml y devolver
// los mensajes de error en el idioma configurado por el usuario.
object LoginValidator {

    // Data class que agrupa el resultado de la validación.
    // isValid es true solo si no hay ningún error en ningún campo.
    data class ValidationResult(
        val isValid: Boolean,
        val emailError: String? = null,
        val passwordError: String? = null
    )

    // Valida el email y la contraseña introducidos por el usuario.
    // Recibe el Context para acceder a strings.xml con el idioma correcto.
    // Devuelve un ValidationResult con los errores encontrados,
    // o con isValid = true si todo es correcto.
    fun validateCredentials(context: Context, email: String, password: String): ValidationResult {
        var emailError: String? = null
        var passwordError: String? = null

        // Validar email: no puede estar vacío y debe tener formato válido
        when {
            email.isEmpty() -> {
                emailError = context.getString(R.string.email_required)
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                emailError = context.getString(R.string.invalid_email)
            }
        }

        // Validar contraseña: no puede estar vacía y debe tener al menos 6 caracteres
        // (6 es el mínimo que exige Firebase Auth)
        when {
            password.isEmpty() -> {
                passwordError = context.getString(R.string.password_required)
            }
            password.length < 6 -> {
                passwordError = context.getString(R.string.password_too_short)
            }
        }

        return ValidationResult(
            isValid = emailError == null && passwordError == null,
            emailError = emailError,
            passwordError = passwordError
        )
    }

    // Aplica los errores del ValidationResult a los campos del formulario.
    // Si un campo no tiene error, su valor será null y Android
    // quitará el mensaje de error que hubiera antes.
    fun applyErrors(
        emailInput: TextInputEditText,
        passwordInput: TextInputEditText,
        result: ValidationResult
    ) {
        emailInput.error = result.emailError
        passwordInput.error = result.passwordError
    }
}