package com.novikon.pace.ui.main.menus

import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.novikon.pace.R
import com.novikon.pace.data.HabitsManager
import com.novikon.pace.ui.login.LoginActivity
import com.novikon.pace.ui.settings.AccountSettingsActivity
import com.novikon.pace.utils.SessionManager

/**
 * Gestiona la barra lateral derecha (menú de usuario).
 * Responsable de configurar el header con datos del usuario,
 * estilar los ítems y manejar todas las acciones: cambio de foto,
 * cambio de contraseña, ajustes de cuenta y cierre de sesión.
 */
class UserMenuManager(
    private val activity: AppCompatActivity,
    private val navigationView: NavigationView,
    private val drawerLayout: DrawerLayout,
    private val habitsManager: HabitsManager,
    private val sessionManager: SessionManager
) {
    fun setup() {
        styleMenuItems()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            handleMenuClick(menuItem)
            drawerLayout.closeDrawer(GravityCompat.END)
            true
        }
    }

    // Muestra el email del usuario en el header del drawer.
    // El email no cambia, así que una lectura única es suficiente.
    fun setupUserEmail() {
        val headerView = navigationView.getHeaderView(0)
        headerView.findViewById<TextView>(R.id.userEmailText).text =
            Firebase.auth.currentUser?.email ?: ""
    }

    // Actualiza el nombre en el header. Llamado desde el listener de Firebase.
    fun updateUserName(name: String) {
        val headerView = navigationView.getHeaderView(0)
        headerView.findViewById<TextView>(R.id.userNameText).text = name
    }

    // Aplica negrita y color rojo al ítem de cerrar sesión
    private fun styleMenuItems() {
        navigationView.menu.findItem(R.id.menu_logout)?.let { item ->
            val spannable = SpannableString(item.title)
            spannable.setSpan(StyleSpan(Typeface.BOLD), 0, spannable.length, 0)
            spannable.setSpan(
                ForegroundColorSpan(activity.getColor(R.color.error_red)),
                0, spannable.length, 0
            )
            item.title = spannable
        }
    }
    private fun handleMenuClick(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.menu_change_photo -> changeProfilePhoto()
            R.id.menu_change_password -> changePassword()
            R.id.menu_account_settings ->
                activity.startActivity(Intent(activity, AccountSettingsActivity::class.java))
            R.id.menu_logout -> confirmLogout()
        }
    }

    // Muestra un diálogo para elegir entre cámara y galería.
    // La subida de foto está pendiente de implementar.
    private fun changeProfilePhoto() {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.change_profile_photo_title))
            .setItems(arrayOf(
                activity.getString(R.string.take_photo),
                activity.getString(R.string.choose_from_gallery)
            )) { _, _ ->
                Toast.makeText(activity, activity.getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // Muestra un diálogo para cambiar la contraseña.
    // Pide la contraseña actual para reautenticar antes de cambiarla.
    private fun changePassword() {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_change_password, null)
        val currentPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.currentPasswordInput)
        val newPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.newPasswordInput)
        val confirmPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.confirmPasswordInput)

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.change_password))
            .setView(dialogView)
            .setPositiveButton(activity.getString(R.string.save)) { _, _ ->
                val currentPassword = currentPasswordInput.text.toString()
                val newPassword = newPasswordInput.text.toString()
                val confirmPassword = confirmPasswordInput.text.toString()

                if (newPassword != confirmPassword) {
                    Toast.makeText(activity, activity.getString(R.string.password_mismatch), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val user = Firebase.auth.currentUser
                val email = user?.email

                if (email != null) {
                    // Reautenticar antes de cambiar la contraseña — Firebase lo requiere
                    val credential = EmailAuthProvider.getCredential(email, currentPassword)
                    user.reauthenticate(credential)
                        .addOnSuccessListener {
                            user.updatePassword(newPassword)
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        activity,
                                        activity.getString(R.string.password_changed_success),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(
                                        activity,
                                        "${activity.getString(R.string.error)}${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.current_password_incorrect),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    // Las cuentas de Google no tienen contraseña en Firebase
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.google_account_no_password),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }
    private fun confirmLogout() {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.logout_title))
            .setMessage(activity.getString(R.string.logout_message))
            .setPositiveButton(activity.getString(R.string.yes)) { _, _ -> signOut() }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }
    private fun signOut() {
        habitsManager.clearLocalData()
        sessionManager.markUserLoggedOut()
        Firebase.auth.signOut()

        // Cerrar también la sesión de Google para que aparezca
        // el selector de cuentas en el próximo login
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(activity.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(activity, gso).signOut()

        val intent = Intent(activity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
    }
}