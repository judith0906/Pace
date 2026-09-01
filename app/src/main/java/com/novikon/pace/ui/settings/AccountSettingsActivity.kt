package com.novikon.pace.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.novikon.pace.data.CirclesRealtimeManager
import com.novikon.pace.data.HabitsManager
import com.novikon.pace.billing.PremiumGate
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.ui.backup.CloudBackupActivity

import com.novikon.pace.ui.login.LoginActivity
import com.novikon.pace.ui.premium.PremiumActivity
import com.novikon.pace.ui.stats.StatsActivity
import com.novikon.pace.utils.SessionManager
import com.novikon.pace.utils.applySystemBarInsets
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.TimeZone
import kotlin.coroutines.resume

// Pantalla de cuenta: administra datos de perfil, privacidad y acciones de sesion.
class AccountSettingsActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var currentNameText: TextView
    private lateinit var currentEmailText: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var habitsManager: HabitsManager
    private lateinit var sessionManager: SessionManager
    private val googleReauthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn
            .getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val credential = com.google.firebase.auth.GoogleAuthProvider
                .getCredential(account.idToken, null)
            proceedDeleteWithCredential(credential)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_google_signin), Toast.LENGTH_LONG).show()
        }
    }

    private val circlesManager by lazy { CirclesRealtimeManager(this) }
    private var blockedUsersDialog: AlertDialog? = null

// onCreate: inicializa la pantalla y prepara vistas/eventos iniciales.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_account_settings)
        applySystemBarInsets()

        auth = Firebase.auth
        database = FirebaseDatabase.getInstance()
        habitsManager = HabitsManager(this)
        sessionManager = SessionManager(this)

        initializeViews()
        setupListeners()
        loadUserName()
    }
    private fun initializeViews() {
        backButton = findViewById(R.id.backButton)
        currentNameText = findViewById(R.id.currentNameText)
        currentEmailText = findViewById(R.id.currentEmailText)
        currentEmailText.text = auth.currentUser?.email ?: ""
    }
    private fun setupListeners() {
        backButton.setOnClickListener { finish() }

        findViewById<View>(R.id.changeNameOption).setOnClickListener {
            showChangeNameDialog()
        }

        findViewById<View>(R.id.changeEmailOption).setOnClickListener {
            showChangeEmailDialog()
        }

        findViewById<View>(R.id.signOutAllDevicesOption).setOnClickListener {
            showSignOutAllDevicesDialog()
        }

        findViewById<View>(R.id.viewActiveDevicesOption).setOnClickListener {
            startActivity(Intent(this, ActiveDevicesActivity::class.java))
        }

        findViewById<View>(R.id.blockUsersOption).setOnClickListener {
            showBlockedUsersDialog()
        }

        findViewById<View>(R.id.downloadDataOption).setOnClickListener {
            if (PremiumGate.isPremium(this)) {
                startActivity(Intent(this, StatsActivity::class.java))
            } else {
                startActivity(Intent(this, PremiumActivity::class.java))
            }
        }

        findViewById<View>(R.id.cloudBackupOption).setOnClickListener {
            if (PremiumGate.isPremium(this)) {
                startActivity(Intent(this, CloudBackupActivity::class.java))
            } else {
                startActivity(Intent(this, PremiumActivity::class.java))
            }
        }

        findViewById<View>(R.id.deleteAccountOption).setOnClickListener {
            showDeleteAccountDialog()
        }
    }
    private fun loadUserName() {
        val user = auth.currentUser ?: return
        val userId = user.uid

        database.getReference("users/$userId/profile/displayName")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.getValue(String::class.java)
                        ?: user.displayName
                        ?: getString(R.string.default_user)
                    currentNameText.text = name
                }
                override fun onCancelled(error: DatabaseError) {
                    currentNameText.text = user.displayName ?: getString(R.string.default_user)
                }
            })
    }
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
                if (newName.isNotEmpty()) changeName(newName)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    private fun changeName(newName: String) {
        val user = auth.currentUser ?: return
        val userId = user.uid

        database.getReference("users/$userId/profile/displayName")
            .setValue(newName)
            .addOnSuccessListener {
                val profileUpdates = userProfileChangeRequest {
                    displayName = newName
                }

                user.updateProfile(profileUpdates)
                    .addOnSuccessListener {
                        Toast.makeText(this, getString(R.string.name_changed_success), Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "${getString(R.string.error)}${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "${getString(R.string.error)}${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    private fun showBlockedUsersDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_blocked_users, null)
        val rvBlockedUsers = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_blocked_users)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tv_blocked_empty)

        lateinit var adapter: BlockedUsersAdapter
        adapter = BlockedUsersAdapter { blockedUser ->
            showUnblockConfirmation(blockedUser.userId, blockedUser.displayName) {
                loadBlockedUsersInto(adapter, tvEmpty)
            }
        }

        rvBlockedUsers.layoutManager = LinearLayoutManager(this)
        rvBlockedUsers.adapter = adapter

        blockedUsersDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.block_users))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.close), null)
            .create()

        blockedUsersDialog?.show()
        loadBlockedUsersInto(adapter, tvEmpty)
    }
    private fun loadBlockedUsersInto(adapter: BlockedUsersAdapter, tvEmpty: TextView) {
        lifecycleScope.launch {
            val blockedUsers = circlesManager.getBlockedUsers()
            adapter.submitList(blockedUsers)
            tvEmpty.visibility = if (blockedUsers.isEmpty()) View.VISIBLE else View.GONE
        }
    }
    private fun showUnblockConfirmation(targetUid: String, displayName: String, onDone: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.unblock_user))
            .setMessage(getString(R.string.unblock_user_confirm, displayName))
            .setPositiveButton(getString(R.string.unblock_user)) { _, _ ->
                lifecycleScope.launch {
                    val ok = circlesManager.unblockUser(targetUid)
                    Toast.makeText(
                        this@AccountSettingsActivity,
                        if (ok) getString(R.string.user_unblocked) else getString(R.string.unblock_user_error),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (ok) onDone()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    private fun showSignOutAllDevicesDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sign_out_all_title))
            .setMessage(getString(R.string.sign_out_all_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ -> signOutAllDevices() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    private fun signOutAllDevices() {
        sessionManager.markAllDevicesLoggedOut {
            runOnUiThread {
                Toast.makeText(
                    this,
                    getString(R.string.signed_out_all),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    private fun showDeleteAccountDialog() {
        val user = auth.currentUser ?: return
        val isGoogleUser = user.providerData.any { it.providerId == "google.com" }

        if (isGoogleUser) {
            // Confirmar primero, luego reautenticar con Google
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_account_title))
                .setMessage(getString(R.string.delete_account_message))
                .setPositiveButton(getString(R.string.delete_account_confirm)) { _, _ ->
                    startGoogleReauth()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        } else {
            // Confirmar con contraseña
            val input = android.widget.EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                hint = getString(R.string.in_psswd)
            }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_account_title))
                .setMessage(getString(R.string.delete_account_message))
                .setView(input)
                .setPositiveButton(getString(R.string.delete_account_confirm)) { _, _ ->
                    val password = input.text.toString()
                    if (password.isNotBlank()) {
                        val email = user.email ?: return@setPositiveButton
                        val credential = com.google.firebase.auth.EmailAuthProvider
                            .getCredential(email, password)
                        proceedDeleteWithCredential(credential)
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun startGoogleReauth() {
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions
            .Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn
            .getClient(this, gso)
        googleReauthLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun proceedDeleteWithCredential(credential: com.google.firebase.auth.AuthCredential) {
        val user = auth.currentUser ?: return
        val userId = user.uid

        user.reauthenticate(credential)
            .addOnSuccessListener {
                getSharedPreferences("pace_prefs", MODE_PRIVATE)
                    .edit().putBoolean("deleting_account", true).apply()
                lifecycleScope.launch {
                    val userId = user.uid

                    // 1) Limpiar círculos (ANTES de borrar datos del usuario para poder leer sus círculos)
                    circlesManager.removeUserFromAllCircles(userId)

                    // 2) Borrar subnodos de users/$UserId explícitamente
                    // (evita conflictos con reglas en cascada)
                    val userRef = database.getReference("users/$userId")
                    val subnodes = listOf("habits", "habit_logs", "settings", "profile", "devices", "circles", "blocked", "firstInstallDate", "subscription")
                    subnodes.forEach { node ->
                        suspendCancellableCoroutine<Unit> { cont ->
                            userRef.child(node).removeValue()
                                .addOnSuccessListener { cont.resume(Unit) }
                                .addOnFailureListener { cont.resume(Unit) }
                        }
                    }

                    // Borrar displayName explícitamente antes que profile
                    suspendCancellableCoroutine<Unit> { cont ->
                        userRef.child("profile/displayName").removeValue()
                            .addOnSuccessListener { cont.resume(Unit) }
                            .addOnFailureListener { cont.resume(Unit) }
                    }

                    // Borrar el nodo raíz una vez vacío
                    suspendCancellableCoroutine<Unit> { cont ->
                        userRef.removeValue()
                            .addOnSuccessListener { cont.resume(Unit) }
                            .addOnFailureListener { cont.resume(Unit) }
                    }

                    // 3) Borrar cuenta de Auth (al final, cuando ya no necesitamos permisos)
                    val deleteOk = suspendCancellableCoroutine<Boolean> { cont ->
                        user.delete()
                            .addOnSuccessListener { cont.resume(true) }
                            .addOnFailureListener { cont.resume(false) }
                    }

                    if (deleteOk) {
                        habitsManager.clearLocalData()
                        sessionManager.markUserLoggedOut()
                        Toast.makeText(
                            this@AccountSettingsActivity,
                            getString(R.string.account_deleted),
                            Toast.LENGTH_SHORT
                        ).show()
                        val intent = Intent(this@AccountSettingsActivity, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(
                            this@AccountSettingsActivity,
                            getString(R.string.error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "${getString(R.string.error)}${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun showChangeEmailDialog() {
        val user = auth.currentUser ?: return
        val isGoogleUser = user.providerData.any { it.providerId == "google.com" }

        if (isGoogleUser) {
            Toast.makeText(this, getString(R.string.change_email_google_not_allowed), Toast.LENGTH_LONG).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val emailInput = dialogView.findViewById<TextInputEditText>(R.id.dialogInput)
        emailInput.hint = getString(R.string.new_email)
        emailInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.change_email))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newEmail = emailInput.text.toString().trim()
                if (newEmail.isNotEmpty()) changeEmail(user, newEmail)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
    }

    private fun changeEmail(user: com.google.firebase.auth.FirebaseUser, newEmail: String) {
        user.verifyBeforeUpdateEmail(newEmail)
            .addOnSuccessListener {
                Toast.makeText(this, getString(R.string.change_email_verification_sent), Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "${getString(R.string.error)}${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroy() {
        blockedUsersDialog?.dismiss()
        super.onDestroy()
    }
}