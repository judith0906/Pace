package com.novikon.pace.ui.signup

import android.content.Context
import android.util.Patterns
import com.google.android.material.textfield.TextInputEditText
import com.novikon.pace.R

// Objeto encargado de validar los campos del formulario de registro.
// Está separado de SignUpActivity para que la Activity no tenga
// lógica de validación mezclada con lógica de navegación.
//
// Recibe el Context para poder acceder a strings.xml y devolver
// los mensajes de error en el idioma configurado por el usuario.
object SignUpValidator {

    // Data class que agrupa el resultado de la validación.
    // isValid es true solo si no hay ningún error en ningún campo.
    data class ValidationResult(
        val isValid: Boolean,
        val nameError: String? = null,
        val emailError: String? = null,
        val passwordError: String? = null,
        val confirmPasswordError: String? = null
    )

    // Valida todos los campos del formulario de registro.
    // Recibe el Context para acceder a strings.xml con el idioma correcto.
    fun validateSignUpCredentials(
        context: Context,
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): ValidationResult {
        var nameError: String? = null
        var emailError: String? = null
        var passwordError: String? = null
        var confirmPasswordError: String? = null

        // Validar nombre: no puede estar vacío y debe tener al menos 2 caracteres
        when {
            name.isEmpty() -> {
                nameError = context.getString(R.string.name_required)
            }
            name.length < 2 -> {
                nameError = context.getString(R.string.name_too_short)
            }
        }

        // Validar email: no puede estar vacío y debe tener formato válido
        when {
            email.isEmpty() -> {
                emailError = context.getString(R.string.email_required)
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                emailError = context.getString(R.string.invalid_email)
            }
        }

        // Validar contraseña: no puede estar vacía, debe tener al menos
        // 6 caracteres (mínimo de Firebase) y contener al menos un número
        when {
            password.isEmpty() -> {
                passwordError = context.getString(R.string.password_required)
            }
            password.length < 6 -> {
                passwordError = context.getString(R.string.password_too_short)
            }
            !password.any { it.isDigit() } -> {
                passwordError = context.getString(R.string.password_needs_number)
            }
        }

        // Validar confirmación: no puede estar vacía y debe coincidir
        // con la contraseña introducida
        when {
            confirmPassword.isEmpty() -> {
                confirmPasswordError = context.getString(R.string.confirm_password_required)
            }
            confirmPassword != password -> {
                confirmPasswordError = context.getString(R.string.passwords_do_not_match)
            }
        }

        return ValidationResult(
            isValid = nameError == null && emailError == null &&
                    passwordError == null && confirmPasswordError == null,
            nameError = nameError,
            emailError = emailError,
            passwordError = passwordError,
            confirmPasswordError = confirmPasswordError
        )
    }

    // Aplica los errores del ValidationResult a los campos del formulario.
    // Si un campo no tiene error, su valor será null y Android
    // quitará el mensaje de error que hubiera antes.
    fun applyErrors(
        nameInput: TextInputEditText,
        emailInput: TextInputEditText,
        passwordInput: TextInputEditText,
        confirmPasswordInput: TextInputEditText,
        result: ValidationResult
    ) {
        nameInput.error = result.nameError
        emailInput.error = result.emailError
        passwordInput.error = result.passwordError
        confirmPasswordInput.error = result.confirmPasswordError
    }
}