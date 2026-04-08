package com.novikon.pace.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.novikon.pace.R
import com.novikon.pace.data.HabitsManager
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.ui.login.LoginActivity
import com.novikon.pace.utils.SessionManager
import java.util.TimeZone

class AccountSettingsActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var currentNameText: TextView
    private lateinit var currentTimezoneText: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var habitsManager: HabitsManager
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_account_settings)

        auth = Firebase.auth
        database = FirebaseDatabase.getInstance()
        habitsManager = HabitsManager(this)
        sessionManager = SessionManager(this)

        initializeViews()
        setupListeners()
        loadUserName()
        loadSystemTimezone()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.backButton)
        currentNameText = findViewById(R.id.currentNameText)
        currentTimezoneText = findViewById(R.id.currentTimezoneText)
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }

        findViewById<android.view.View>(R.id.changeNameOption).setOnClickListener {
            showChangeNameDialog()
        }

        // La zona horaria es solo informativa — siempre se coge del sistema,
        // no hay nada que configurar al pulsarla
        findViewById<android.view.View>(R.id.timezoneOption).setOnClickListener { }

        findViewById<android.view.View>(R.id.signOutAllDevicesOption).setOnClickListener {
            showSignOutAllDevicesDialog()
        }

        findViewById<android.view.View>(R.id.viewActiveDevicesOption).setOnClickListener {
            startActivity(Intent(this, ActiveDevicesActivity::class.java))
        }

        findViewById<android.view.View>(R.id.whoCanInviteOption).setOnClickListener {
            showPrivacyDialog(getString(R.string.who_can_invite_circles))
        }

        findViewById<android.view.View>(R.id.whoCanSeeHabitsOption).setOnClickListener {
            showPrivacyDialog(getString(R.string.who_can_see_habits))
        }

        findViewById<android.view.View>(R.id.blockUsersOption).setOnClickListener {
            Toast.makeText(this, getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.downloadDataOption).setOnClickListener {
            Toast.makeText(this, getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.deleteAccountOption).setOnClickListener {
            showDeleteAccountDialog()
        }
    }

    // ========== CARGAR NOMBRE ==========

    private fun loadUserName() {
        val user = auth.currentUser ?: return
        val userId = user.uid

        // Escuchar cambios en tiempo real desde Firebase
        database.getReference("users/$userId/profile/displayName")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.getValue(String::class.java) ?: user.displayName ?: "Usuario"
                    currentNameText.text = name
                }

                override fun onCancelled(error: DatabaseError) {
                    // Fallback a Firebase Auth
                    currentNameText.text = user.displayName ?: "Usuario"
                }
            })
    }

    // ========== ZONA HORARIA ==========

    // Lee la zona horaria directamente del sistema en cada apertura de la pantalla.
    // TimeZone.getDefault() siempre devuelve el valor actual del dispositivo,
    // por lo que si el usuario la cambió en los ajustes del móvil, aquí
    // ya aparecerá actualizada sin necesidad de ningún botón extra.
    private fun loadSystemTimezone() {
        val tz = TimeZone.getDefault()
        val offsetMs = tz.rawOffset
        val offsetHours = offsetMs / 3_600_000
        val offsetMinutes = Math.abs((offsetMs % 3_600_000) / 60_000)

        val gmtOffset = when {
            offsetMinutes > 0 -> "GMT%+d:%02d".format(offsetHours, offsetMinutes)
            else -> "GMT%+d".format(offsetHours)
        }

        // Convertir el ID estilo "Europe/Madrid" a "Europa/Madrid"
        // reemplazando guiones bajos por espacios para mejor legibilidad
        val readableName = tz.id.replace("_", " ")
        currentTimezoneText.text = "$readableName ($gmtOffset)"
    }

    // ========== CAMBIAR NOMBRE ==========

    private fun showChangeNameDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.dialogInput)

        nameInput.hint = getString(R.string.new_name)
        nameInput.setText(currentNameText.text)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.change_name))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newName = nameInput.text.toString().trim()
                if (newName.isNotEmpty()) {
                    changeName(newName)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun changeName(newName: String) {
        val user = auth.currentUser ?: return
        val userId = user.uid

        // 1. Guardar en Realtime Database
        database.getReference("users/$userId/profile/displayName")
            .setValue(newName)
            .addOnSuccessListener {
                // 2. Actualizar en Firebase Auth
                val profileUpdates = userProfileChangeRequest {
                    displayName = newName
                }

                user.updateProfile(profileUpdates)
                    .addOnSuccessListener {
                        Toast.makeText(this, getString(R.string.name_changed_success), Toast.LENGTH_SHORT).show()
                        // El listener en loadUserName() y en MainActivity actualizarán automáticamente
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "${getString(R.string.error)}${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "${getString(R.string.error)}${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ========== PRIVACIDAD ==========

    private fun showPrivacyDialog(title: String) {
        val options = arrayOf(
            getString(R.string.everyone),
            getString(R.string.friends_only),
            getString(R.string.nobody)
        )

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options) { _, which ->
                val selected = options[which]
                Toast.makeText(this, "Seleccionado: $selected", Toast.LENGTH_SHORT).show()
                // Aquí guardarías la preferencia en Firebase
            }
            .show()
    }

    // ========== CERRAR SESIÓN EN TODOS LOS DISPOSITIVOS ==========

    private fun showSignOutAllDevicesDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sign_out_all_title))
            .setMessage(getString(R.string.sign_out_all_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                signOutAllDevices()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun signOutAllDevices() {
        habitsManager.clearLocalData()

        // Primero borrar Firebase (auth todavía activo), luego cerrar sesión
        sessionManager.markAllDevicesLoggedOut {
            // Esto se ejecuta en el hilo IO, hay que volver al main para UI
            runOnUiThread {
                auth.signOut()
                Toast.makeText(this, getString(R.string.signed_out_all), Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }
    }

    // ========== ELIMINAR CUENTA ==========

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_account_title))
            .setMessage(getString(R.string.delete_account_message))
            .setPositiveButton(getString(R.string.delete_account_confirm)) { _, _ ->
                deleteAccount()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteAccount() {
        val user = auth.currentUser ?: return
        val userId = user.uid

        // 1. Eliminar datos de Firebase Realtime Database
        database.getReference("users/$userId")
            .removeValue()
            .addOnSuccessListener {
                // 2. Eliminar cuenta de Firebase Auth
                user.delete()
                    .addOnSuccessListener {
                        habitsManager.clearLocalData()
                        sessionManager.markUserLoggedOut()

                        Toast.makeText(this, getString(R.string.account_deleted), Toast.LENGTH_SHORT).show()

                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "${getString(R.string.error)}${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "${getString(R.string.error)}${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}