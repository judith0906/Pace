package com.novikon.pace.ui.circles

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.Toast
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

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.let { circlesManager.removeMessagesListener(circleId, it) }
    }
}