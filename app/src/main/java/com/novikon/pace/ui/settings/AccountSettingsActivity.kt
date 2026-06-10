package com.novikon.pace.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.ui.login.LoginActivity
import com.novikon.pace.utils.SessionManager
import com.novikon.pace.utils.applySystemBarInsets
import kotlinx.coroutines.launch
import java.util.TimeZone

// Pantalla de cuenta: administra datos de perfil, privacidad y acciones de sesion.
class AccountSettingsActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var currentNameText: TextView
    private lateinit var currentTimezoneText: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var habitsManager: HabitsManager
    private lateinit var sessionManager: SessionManager

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
        loadSystemTimezone()
    }
    private fun initializeViews() {
        backButton = findViewById(R.id.backButton)
        currentNameText = findViewById(R.id.currentNameText)
        currentTimezoneText = findViewById(R.id.currentTimezoneText)
    }
    private fun setupListeners() {
        backButton.setOnClickListener { finish() }

        findViewById<View>(R.id.changeNameOption).setOnClickListener {
            showChangeNameDialog()
        }

        // Zona horaria informativa
        findViewById<View>(R.id.timezoneOption).setOnClickListener { }

        findViewById<View>(R.id.signOutAllDevicesOption).setOnClickListener {
            showSignOutAllDevicesDialog()
        }

        findViewById<View>(R.id.viewActiveDevicesOption).setOnClickListener {
            startActivity(Intent(this, ActiveDevicesActivity::class.java))
        }

        findViewById<View>(R.id.whoCanInviteOption).setOnClickListener {
            showPrivacyDialog(getString(R.string.who_can_invite_circles))
        }

        findViewById<View>(R.id.whoCanSeeHabitsOption).setOnClickListener {
            showPrivacyDialog(getString(R.string.who_can_see_habits))
        }

        findViewById<View>(R.id.blockUsersOption).setOnClickListener {
            showBlockedUsersDialog()
        }

        findViewById<View>(R.id.downloadDataOption).setOnClickListener {
            Toast.makeText(this, getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
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
    private fun loadSystemTimezone() {
        val tz = TimeZone.getDefault()
        val offsetMs = tz.rawOffset
        val offsetHours = offsetMs / 3_600_000
        val offsetMinutes = Math.abs((offsetMs % 3_600_000) / 60_000)

        val gmtOffset = when {
            offsetMinutes > 0 -> "GMT%+d:%02d".format(offsetHours, offsetMinutes)
            else -> "GMT%+d".format(offsetHours)
        }

        val readableName = tz.id.replace("_", " ")
        currentTimezoneText.text = "$readableName ($gmtOffset)"
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
                Toast.makeText(
                    this,
                    getString(R.string.privacy_selected_format, selected),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
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
        habitsManager.clearLocalData()

        sessionManager.markAllDevicesLoggedOut {
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

        database.getReference("users/$userId")
            .removeValue()
            .addOnSuccessListener {
                user.delete()
                    .addOnSuccessListener {
                        habitsManager.clearLocalData()
                        sessionManager.markUserLoggedOut()
                        Toast.makeText(this, getString(R.string.account_deleted), Toast.LENGTH_SHORT).show()

                        val intent = Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
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
    override fun onDestroy() {
        blockedUsersDialog?.dismiss()
        super.onDestroy()
    }
}