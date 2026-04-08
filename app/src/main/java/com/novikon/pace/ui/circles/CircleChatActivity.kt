package com.novikon.pace.ui.circles

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.ChildEventListener
import com.novikon.pace.R
import com.novikon.pace.data.CirclesRealtimeManager
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import kotlinx.coroutines.launch

class CircleChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CIRCLE_ID = "extra_circle_id"
        const val EXTRA_CIRCLE_NAME = "extra_circle_name"
    }

    private lateinit var toolbar: Toolbar
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: TextInputEditText
    private lateinit var btnSend: ImageButton

    private val circlesManager = CirclesRealtimeManager()
    private lateinit var messagesAdapter: MessagesAdapter

    private lateinit var circleId: String
    private lateinit var circleName: String

    private var messagesListener: ChildEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_circle_chat)

        circleId = intent.getStringExtra(EXTRA_CIRCLE_ID) ?: run {
            finish()
            return
        }
        circleName = intent.getStringExtra(EXTRA_CIRCLE_NAME) ?: getString(R.string.my_circles)

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupMessageInput()
        observeMessages()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_circle_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_group_info -> { showGroupInfoDialog(); true }
            R.id.action_block_user -> { showBlockUserDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar_chat)
        rvMessages = findViewById(R.id.rv_messages)
        etMessage = findViewById(R.id.et_message)
        btnSend = findViewById(R.id.btn_send)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = circleName
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        val currentUserId = circlesManager.getUserId() ?: ""
        messagesAdapter = MessagesAdapter(currentUserId)

        rvMessages.apply {
            layoutManager = LinearLayoutManager(this@CircleChatActivity).apply {
                stackFromEnd = true
            }
            adapter = messagesAdapter
        }
    }

    private fun setupMessageInput() {
        btnSend.setOnClickListener { sendMessage() }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun observeMessages() {
        messagesListener = circlesManager.observeMessages(circleId) { message ->
            messagesAdapter.addMessage(message)
            rvMessages.scrollToPosition(messagesAdapter.itemCount - 1)
        }
    }

    private fun sendMessage() {
        val text = etMessage.text?.toString()?.trim()
        if (text.isNullOrBlank()) return

        etMessage.setText("")

        lifecycleScope.launch {
            val success = circlesManager.sendMessage(circleId, text)
            if (!success) {
                etMessage.setText(text)
                etMessage.setSelection(text.length)
                Toast.makeText(
                    this@CircleChatActivity,
                    getString(R.string.chat_send_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showGroupInfoDialog() {
        lifecycleScope.launch {
            val info = circlesManager.getGroupInfo(circleId) ?: return@launch
            val members = circlesManager.getMemberDisplayNames(info.memberIds)
            val isAdmin = circlesManager.getUserId() == info.createdBy

            val view = layoutInflater.inflate(R.layout.dialog_group_info, null)
            val tvName = view.findViewById<TextView>(R.id.tv_group_name)
            val tvCode = view.findViewById<TextView>(R.id.tv_join_code)
            val tvSummary = view.findViewById<TextView>(R.id.tv_members_summary)
            val layoutAdmin = view.findViewById<View>(R.id.layout_admin_max)
            val etNewMax = view.findViewById<EditText>(R.id.et_new_max)
            val btnSaveMax = view.findViewById<Button>(R.id.btn_save_max)
            val lvMembers = view.findViewById<ListView>(R.id.lv_members)

            tvName.text = info.name
            tvCode.text = info.joinCode
            tvSummary.text = getString(R.string.group_members_summary, members.size, info.maxParticipants)

            val names = members.map { it.displayName }
            lvMembers.adapter =
                ArrayAdapter(this@CircleChatActivity, android.R.layout.simple_list_item_1, names)

            if (isAdmin) {
                layoutAdmin.visibility = View.VISIBLE
                etNewMax.setText(info.maxParticipants.toString())
                btnSaveMax.setOnClickListener {
                    val newMax = etNewMax.text.toString().toIntOrNull() ?: return@setOnClickListener
                    lifecycleScope.launch {
                        val ok = circlesManager.updateMaxParticipants(circleId, newMax)
                        Toast.makeText(
                            this@CircleChatActivity,
                            if (ok) getString(R.string.group_max_updated) else getString(R.string.group_max_update_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                lvMembers.setOnItemLongClickListener { _, _, position, _ ->
                    val target = members[position]
                    if (target.userId == info.createdBy) return@setOnItemLongClickListener true

                    AlertDialog.Builder(this@CircleChatActivity)
                        .setTitle(getString(R.string.remove_member))
                        .setMessage(getString(R.string.remove_member_confirm, target.displayName))
                        .setPositiveButton(getString(R.string.yes)) { _, _ ->
                            lifecycleScope.launch {
                                val ok = circlesManager.removeMember(circleId, target.userId)
                                Toast.makeText(
                                    this@CircleChatActivity,
                                    if (ok) getString(R.string.member_removed) else getString(R.string.member_remove_error),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()

                    true
                }
            }

            AlertDialog.Builder(this@CircleChatActivity)
                .setTitle(getString(R.string.group_info))
                .setView(view)
                .setPositiveButton(getString(R.string.close), null)
                .show()
        }
    }

    private fun showBlockUserDialog() {
        lifecycleScope.launch {
            val info = circlesManager.getGroupInfo(circleId) ?: return@launch
            val members = circlesManager.getMemberDisplayNames(info.memberIds)
                .filter { it.userId != circlesManager.getUserId() }

            if (members.isEmpty()) {
                Toast.makeText(this@CircleChatActivity, getString(R.string.no_users_to_block), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val names = members.map { it.displayName }.toTypedArray()

            AlertDialog.Builder(this@CircleChatActivity)
                .setTitle(getString(R.string.block_user))
                .setItems(names) { _, which ->
                    val target = members[which]
                    lifecycleScope.launch {
                        val ok = circlesManager.blockUser(target.userId)
                        Toast.makeText(
                            this@CircleChatActivity,
                            if (ok) getString(R.string.user_blocked) else getString(R.string.block_user_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.let { circlesManager.removeMessagesListener(circleId, it) }
    }
}