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
import com.novikon.pace.models.Message
import kotlinx.coroutines.launch

// Activity que muestra el chat de un círculo concreto.
//
// Recibe por Intent:
//   EXTRA_CIRCLE_ID   → ID del círculo en Firebase
//   EXTRA_CIRCLE_NAME → nombre del círculo (para el título de la toolbar)
//
// Al abrirse registra un ChildEventListener en Firebase que escucha
// cada mensaje nuevo en tiempo real y lo añade al final de la lista.
// Al cerrarse elimina el listener para evitar memory leaks.
//
// El adapter diferencia mensajes propios (alineados a la derecha)
// de mensajes de otros (alineados a la izquierda) usando el userId actual.
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

    // Referencia al listener activo — necesaria para eliminarlo en onDestroy
    private var messagesListener: ChildEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_circle_chat)

        // Obtener los datos del círculo del Intent
        circleId = intent.getStringExtra(EXTRA_CIRCLE_ID) ?: run {
            finish()
            return
        }
        circleName = intent.getStringExtra(EXTRA_CIRCLE_NAME) ?: "Círculo"

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
                // stackFromEnd hace que los mensajes nuevos aparezcan al final
                // y el scroll empiece desde abajo, como en cualquier chat
                stackFromEnd = true
            }
            adapter = messagesAdapter
        }
    }

    private fun setupMessageInput() {
        btnSend.setOnClickListener { sendMessage() }

        // Enviar también al pulsar "Enviar" en el teclado
        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    // Registra el listener de mensajes en tiempo real.
    // Cada vez que llega un mensaje nuevo (onChildAdded) lo añade al adapter
    // y hace scroll automático al final para que sea visible.
    private fun observeMessages() {
        messagesListener = circlesManager.observeMessages(circleId) { message ->
            messagesAdapter.addMessage(message)
            // Scroll automático al último mensaje
            rvMessages.scrollToPosition(messagesAdapter.itemCount - 1)
        }
    }

    // Recoge el texto del campo de entrada, lo valida y lo envía a Firebase.
    // Vacía el campo inmediatamente para dar feedback rápido al usuario —
    // el mensaje aparecerá en la lista vía el listener en tiempo real.
    private fun sendMessage() {
        val text = etMessage.text?.toString()?.trim()
        if (text.isNullOrBlank()) return

        // Vaciar el campo antes de enviar para dar feedback inmediato
        etMessage.setText("")

        lifecycleScope.launch {
            val success = circlesManager.sendMessage(circleId, text)
            if (!success) {
                // Restaurar el texto si falló el envío
                etMessage.setText(text)
                etMessage.setSelection(text.length)
                Toast.makeText(
                    this@CircleChatActivity,
                    "Error al enviar el mensaje. Inténtalo de nuevo.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Limpieza obligatoria: eliminar el listener de mensajes al cerrar la Activity
    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.let { circlesManager.removeMessagesListener(circleId, it) }
    }
}