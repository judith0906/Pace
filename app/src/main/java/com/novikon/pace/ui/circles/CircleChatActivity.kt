package com.novikon.pace.ui.circles

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.firebase.database.ChildEventListener
import com.novikon.pace.R
import com.novikon.pace.data.CircleChatEventsManager
import com.novikon.pace.data.CirclesRealtimeManager
import com.novikon.pace.data.HabitsManager
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.models.Message
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.google.android.material.slider.Slider
import com.novikon.pace.utils.applySystemBarInsets

// Pantalla de chat de circulo: gestiona mensajes, eventos y acciones del grupo.
class CircleChatActivity : AppCompatActivity() {

    // Agrupa mensajes predefinidos por temática para enviarlos rápidamente al chat.
    private data class PredefinedSection(
        val title: String,
        val emoji: String,
        val messages: List<PredefinedMessageOption>
    )

    companion object {
        const val EXTRA_CIRCLE_ID = "extra_circle_id"
        const val EXTRA_CIRCLE_NAME = "extra_circle_name"
    }

    private lateinit var toolbar: Toolbar
    private lateinit var rvMessages: RecyclerView
    private lateinit var btnCreateEvent: MaterialButton
    private lateinit var btnSendMessage: MaterialButton

    private val circlesManager by lazy { CirclesRealtimeManager(this) }
    private val eventsManager by lazy { CircleChatEventsManager(this) }
    private lateinit var messagesAdapter: MessagesAdapter

    private lateinit var circleId: String
    private lateinit var circleName: String

    private var messagesListener: ChildEventListener? = null
    private val eventStartHandler = Handler(Looper.getMainLooper())
    private val eventStartTicker = object : Runnable {
        // Revisa periódicamente eventos vencidos y refresca estados temporales en pantalla.
        override fun run() {
            lifecycleScope.launch { eventsManager.checkAndStartDueEvents(circleId) }
            messagesAdapter.refreshTemporalStates()
            eventStartHandler.postDelayed(this, 30_000L)
        }
    }

    private var pendingCaptureMessage: Message? = null

    // Marca si el usuario está actualmente dentro de esta pantalla de chat.
    // Se usa para suprimir notificaciones mientras la actividad está en primer plano.
    private var isInForeground = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_needed), Toast.LENGTH_SHORT).show()
        }
    }

    // URI temporal donde la cámara escribe la foto de alta resolución
    private var cameraPhotoUri: android.net.Uri? = null

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        if (!saved) return@registerForActivityResult
        val uri = cameraPhotoUri ?: return@registerForActivityResult
        // Decodificar el archivo completo para obtener máxima calidad
        val bitmap = android.graphics.BitmapFactory.decodeStream(
            contentResolver.openInputStream(uri)
        ) ?: return@registerForActivityResult
        showCapturePreviewDialog(bitmap)
    }

    // Lanzador para pedir permiso de notificaciones en Android 13+
    private val notifPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* resultado ignorado: si concede, ya funcionará la próxima notificación */ }

    // Prepara la pantalla del chat del grupo y activa la escucha en tiempo real.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)
        setContentView(R.layout.activity_circle_chat)
        applySystemBarInsets()

        circleId = intent.getStringExtra(EXTRA_CIRCLE_ID) ?: run {
            finish()
            return
        }
        circleName = intent.getStringExtra(EXTRA_CIRCLE_NAME) ?: getString(R.string.my_circles)

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupBottomActions()
        observeMessages()

        // Solicitar permiso de notificaciones en Android 13+ si aún no está concedido
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        eventStartTicker.run()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_circle_chat, menu)
        val item = menu.findItem(R.id.action_group_info)
        val spannable = android.text.SpannableString(item.title)
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(
                ContextCompat.getColor(this, R.color.text_secondary)
            ),
            0, spannable.length, 0
        )
        item.title = spannable
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_group_info -> {
                showGroupInfoDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Enlaza toolbar, lista de mensajes y botones inferiores del chat.
    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar_chat)
        rvMessages = findViewById(R.id.rv_messages)
        btnCreateEvent = findViewById(R.id.btn_create_event)
        btnSendMessage = findViewById(R.id.btn_send_message)
    }

    // Configura el encabezado con nombre del grupo y botón para volver.
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = circleName
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener { finish() }
    }

    // Inicializa la lista de mensajes y conecta acciones de cada tipo de mensaje.
    private fun setupRecyclerView() {
        val currentUserId = circlesManager.getUserId() ?: ""
        messagesAdapter = MessagesAdapter(
            currentUserId = currentUserId,
            onJoinEvent = { message -> respondToEvent(message, true) },
            onDeclineEvent = { message -> respondToEvent(message, false) },
            onCaptureMoment = { message -> onCaptureMomentClicked(message) },
            onDeleteMessage = { message -> confirmDeleteMessage(message) },
            // Abre la foto en pantalla completa al pulsar en el chat
            onPhotoClick = { url -> showFullscreenImage(url) }
        )

        rvMessages.apply {
            layoutManager = LinearLayoutManager(this@CircleChatActivity).apply { stackFromEnd = true }
            adapter = messagesAdapter
        }
    }

    // Conecta los botones inferiores para crear eventos y enviar mensajes rápidos.
    private fun setupBottomActions() {
        btnCreateEvent.setOnClickListener { showCreateEventDialog() }
        btnSendMessage.setOnClickListener { showSendMessageDialog() }
    }

    // Escucha mensajes en vivo y mantiene la lista sincronizada con Firebase.
    private fun observeMessages() {
        val currentUserId = circlesManager.getUserId() ?: ""
        messagesListener = eventsManager.observeMessages(
            circleId = circleId,
            onMessageAdded = { message ->
                // Muestra notificación solo si el usuario está fuera del chat
                if (!isInForeground) {
                    CircleNotificationHelper.showIfNeeded(
                        context = this,
                        message = message,
                        currentUserId = currentUserId,
                        circleName = circleName,
                        circleId = circleId,
                        targetActivityClass = CircleChatActivity::class.java
                    )
                }
                messagesAdapter.addMessage(message)
                rvMessages.scrollToPosition(messagesAdapter.itemCount - 1)
            },
            onMessageChanged = { message ->
                messagesAdapter.updateMessage(message)
            },
            onMessageRemoved = { messageId ->
                messagesAdapter.removeMessage(messageId)
            }
        )
    }

    // Devuelve el catálogo de mensajes sugeridos organizados por categoría (20 por sección).
    private fun getPredefinedSections(): List<PredefinedSection> {
        return listOf(
            PredefinedSection(
                title = getString(R.string.msg_section_congratulations),
                emoji = "🎉",
                messages = (1..20).map {
                    PredefinedMessageOption("msg_congrats_$it", getString(resources.getIdentifier("msg_congrats_$it", "string", packageName)))
                }
            ),
            PredefinedSection(
                title = getString(R.string.msg_section_amazement),
                emoji = "😮",
                messages = (1..20).map {
                    PredefinedMessageOption("msg_amazement_$it", getString(resources.getIdentifier("msg_amazement_$it", "string", packageName)))
                }
            ),
            PredefinedSection(
                title = getString(R.string.msg_section_admiration),
                emoji = "👏",
                messages = (1..20).map {
                    PredefinedMessageOption("msg_admiration_$it", getString(resources.getIdentifier("msg_admiration_$it", "string", packageName)))
                }
            ),
            PredefinedSection(
                title = getString(R.string.msg_section_motivation),
                emoji = "💪",
                messages = (1..20).map {
                    PredefinedMessageOption("msg_motivation_$it", getString(resources.getIdentifier("msg_motivation_$it", "string", packageName)))
                }
            ),
            PredefinedSection(
                title = getString(R.string.msg_section_happiness),
                emoji = "😊",
                messages = (1..20).map {
                    PredefinedMessageOption("msg_happiness_$it", getString(resources.getIdentifier("msg_happiness_$it", "string", packageName)))
                }
            ),
            PredefinedSection(
                title = getString(R.string.msg_section_achievement),
                emoji = "🏆",
                messages = (1..20).map {
                    PredefinedMessageOption("msg_achievement_$it", getString(resources.getIdentifier("msg_achievement_$it", "string", packageName)))
                }
            )
        )
    }

    // Muestra un diálogo de mensajes sugeridos para enviar apoyo al grupo en un toque.
    private fun showSendMessageDialog() {
        val sections = getPredefinedSections()
        if (sections.isEmpty()) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_predefined_messages, null)
        val chipGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_group_sections)
        val rvOptions = dialogView.findViewById<RecyclerView>(R.id.rv_predefined_messages)
        val btnClose = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_close_dialog)

        val adapter = PredefinedMessagesAdapter { selected ->
            lifecycleScope.launch {
                val success = circlesManager.sendTemplateMessage(
                    circleId = circleId,
                    messageTemplateKey = selected.templateKey,
                    messageTemplateParams = emptyList(),
                    fallbackText = selected.fallbackText
                )
                if (!success) {
                    Toast.makeText(
                        this@CircleChatActivity,
                        getString(R.string.chat_send_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        rvOptions.layoutManager = LinearLayoutManager(this)
        rvOptions.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Cerrar desde botón inferior
        btnClose.setOnClickListener { dialog.dismiss() }

        // Crear chips dinámicamente
        sections.forEachIndexed { index, section ->
            val chip = Chip(this).apply {
                text = "${section.emoji} ${section.title}"
                isCheckable = true
                isCheckedIconVisible = false
                setChipBackgroundColorResource(R.color.accent_secondary)
                setTextColor(ContextCompat.getColor(this@CircleChatActivity, R.color.text_primary))
                chipStrokeWidth = 1f
                chipStrokeColor = ContextCompat.getColorStateList(this@CircleChatActivity, R.color.border_color)
            }

            chip.setOnClickListener {
                adapter.submitData(section.emoji, section.messages)
            }

            chipGroup.addView(chip)

            if (index == 0) {
                chip.isChecked = true
                adapter.submitData(section.emoji, section.messages)
            }
        }

        dialog.setOnShowListener {
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()
    }

    // Permite elegir hábito, fecha y hora para crear un nuevo evento del grupo.
    private fun showCreateEventDialog() {
        lifecycleScope.launch {
            val habits = HabitsManager(this@CircleChatActivity).getSelectedHabitsAsync()
            if (habits.isEmpty()) {
                Toast.makeText(this@CircleChatActivity, getString(R.string.event_no_habits), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val dialogView = layoutInflater.inflate(R.layout.dialog_create_event, null)
            val spinner = dialogView.findViewById<Spinner>(R.id.spinner_habits)
            val btnPickDate = dialogView.findViewById<MaterialButton>(R.id.btn_pick_date)
            val btnPickTime = dialogView.findViewById<MaterialButton>(R.id.btn_pick_time)
            val tvSelectedDate = dialogView.findViewById<TextView>(R.id.tv_selected_date)
            val tvSelectedTime = dialogView.findViewById<TextView>(R.id.tv_selected_time)

            val habitLabels = habits.map { "${it.emoji} ${it.name}" }
            val adapter = android.widget.ArrayAdapter(
                this@CircleChatActivity,
                android.R.layout.simple_spinner_item,
                habitLabels
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            spinner.adapter = adapter

            val calendar = Calendar.getInstance()
            var hasDate = false
            var hasTime = false

            btnPickDate.setOnClickListener {
                val now = Calendar.getInstance()
                DatePickerDialog(
                    this@CircleChatActivity,
                    { _, year, month, dayOfMonth ->
                        calendar.set(Calendar.YEAR, year)
                        calendar.set(Calendar.MONTH, month)
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        hasDate = true
                        tvSelectedDate.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)
                    },
                    now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)
                ).show()
            }

            btnPickTime.setOnClickListener {
                val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
                val currentMinute = calendar.get(Calendar.MINUTE)

                val dialogView = layoutInflater.inflate(R.layout.dialog_time_picker, null)
                val hourPicker = dialogView.findViewById<NumberPicker>(R.id.hourPicker)
                val minutePicker = dialogView.findViewById<NumberPicker>(R.id.minutePicker)

                val hours = (0..23).map { String.format("%02d", it) }.toTypedArray()
                hourPicker.minValue = 0
                hourPicker.maxValue = 23
                hourPicker.displayedValues = hours
                hourPicker.value = currentHour
                hourPicker.wrapSelectorWheel = true

                val minutes = (0..59).map { String.format("%02d", it) }.toTypedArray()
                minutePicker.minValue = 0
                minutePicker.maxValue = 59
                minutePicker.displayedValues = minutes
                minutePicker.value = currentMinute
                minutePicker.wrapSelectorWheel = true

                val timeDialog = AlertDialog.Builder(this@CircleChatActivity)
                    .setView(dialogView)
                    .setPositiveButton(getString(R.string.save)) { _, _ ->
                        val hour = hourPicker.value
                        val minute = minutePicker.value
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        hasTime = true
                        tvSelectedTime.text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()

                timeDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                    ContextCompat.getColor(this@CircleChatActivity, R.color.text_secondary)
                )
                timeDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
                    ContextCompat.getColor(this@CircleChatActivity, R.color.text_secondary)
                )
            }

            AlertDialog.Builder(this@CircleChatActivity)
                .setTitle(getString(R.string.event_dialog_title))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.event_send)) { _, _ ->
                    val index = spinner.selectedItemPosition
                    if (index !in habits.indices) {
                        Toast.makeText(this@CircleChatActivity, getString(R.string.event_select_habit_error), Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    if (!hasDate || !hasTime) {
                        Toast.makeText(this@CircleChatActivity, getString(R.string.event_select_datetime_error), Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val scheduledAt = calendar.timeInMillis
                    if (scheduledAt <= System.currentTimeMillis()) {
                        Toast.makeText(this@CircleChatActivity, getString(R.string.event_future_date_error), Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val habitName = "${habits[index].emoji} ${habits[index].name}"

                    lifecycleScope.launch {
                        val tzId = java.util.TimeZone.getDefault().id
                        val ok = eventsManager.createEvent(circleId, habitName, scheduledAt, tzId)
                if (ok) {
                    // Programar el Worker para que el evento arranque a su hora
                    // aunque ningún usuario esté en el chat en ese momento
                    com.novikon.pace.workers.EventStartWorker.scheduleForEvent(
                        context = this@CircleChatActivity,
                        circleId = circleId,
                        scheduledAtMillis = scheduledAt
                    )
                }
                        Toast.makeText(
                            this@CircleChatActivity,
                            if (ok) getString(R.string.event_created_success) else getString(R.string.event_created_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show().also { d ->
                    d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                        ContextCompat.getColor(this@CircleChatActivity, R.color.text_secondary)
                    )
                    d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
                        ContextCompat.getColor(this@CircleChatActivity, R.color.text_secondary)
                    )
                }
        }
    }

    // Guarda la respuesta del usuario al evento y actualiza su historial si corresponde.
    private fun respondToEvent(message: Message, join: Boolean) {
        if (message.eventId.isBlank() || message.id.isBlank()) return

        lifecycleScope.launch {
            val ok = eventsManager.respondToEvent(
                circleId = circleId,
                messageId = message.id,
                eventId = message.eventId,
                join = join
            )

            if (!ok) {
                Toast.makeText(this@CircleChatActivity, getString(R.string.event_response_error), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val myUid = circlesManager.getUserId()
            val isForeignEvent = message.eventCreatedBy != myUid
            if (!isForeignEvent) return@launch

            val habitsManager = HabitsManager(this@CircleChatActivity)

            if (join) {
                habitsManager.logJoinedEventToHistory(
                    eventId = message.eventId,
                    habitLabel = message.eventHabitName,
                    scheduledAtMillis = message.eventScheduledAt,
                    eventTimeZoneId = message.eventTimeZoneId
                )
            } else {
                // NUEVO: si cambia a declinar, quitar del historial
                habitsManager.removeJoinedEventFromHistory(message.eventId)
            }
        }
    }

    // Valida si el usuario puede subir foto del evento y solicita cámara si hace falta.
    private fun onCaptureMomentClicked(message: Message) {
        val now = System.currentTimeMillis()
        val isAllowedUser = message.captureAllowedIds.contains(circlesManager.getUserId())
        val stillInWindow = message.timestamp > 0L && now <= (message.timestamp + 60 * 60 * 1000L)

        if (!isAllowedUser || !stillInWindow) {
            Toast.makeText(this, getString(R.string.event_capture_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        pendingCaptureMessage = message
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Crea un archivo temporal y lanza la cámara apuntando a él para obtener foto completa.
    private fun launchCamera() {
        val photoFile = java.io.File(cacheDir, "camera/photo_${System.currentTimeMillis()}.jpg")
            .also { it.parentFile?.mkdirs() }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        cameraPhotoUri = uri
        cameraLauncher.launch(uri)
    }

    // Enseña vista previa de la foto antes de publicarla en el chat.
    private fun showCapturePreviewDialog(bitmap: Bitmap) {
        val preview = layoutInflater.inflate(R.layout.dialog_capture_moment_preview, null)
        val iv = preview.findViewById<ImageView>(R.id.iv_capture_preview)
        iv.setImageBitmap(bitmap)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.event_capture_moment))
            .setView(preview)
            .setPositiveButton(getString(R.string.event_send)) { _, _ ->
                val msg = pendingCaptureMessage ?: return@setPositiveButton
                lifecycleScope.launch {
                    val ok = eventsManager.sendPhotoMoment(
                        circleId = circleId,
                        eventId = msg.eventId,
                        bitmap = bitmap
                    )
                    if (!ok) {
                        Toast.makeText(this@CircleChatActivity, getString(R.string.event_photo_send_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // Abre la información del grupo y muestra acciones según si el usuario es admin o miembro.
    private fun showGroupInfoDialog() {
        lifecycleScope.launch {
            val info = circlesManager.getGroupInfo(circleId) ?: return@launch
            val members = circlesManager.getMemberDisplayNames(info.memberIds)
            val myUid = circlesManager.getUserId()
            val isAdmin = myUid == info.createdBy

            val view = layoutInflater.inflate(R.layout.dialog_group_info, null)
            val etGroupName = view.findViewById<EditText>(R.id.tv_group_name)
            val btnEditName = view.findViewById<android.widget.ImageButton>(R.id.btn_edit_name)
            val btnDeleteGroup = view.findViewById<MaterialButton>(R.id.btn_delete_group)
            val tvJoinCode = view.findViewById<TextView>(R.id.tv_join_code)
            val layoutJoinCode = view.findViewById<View>(R.id.layout_join_code)
            val tvMembersSummary = view.findViewById<TextView>(R.id.tv_members_summary)
            val layoutAdminMax = view.findViewById<View>(R.id.layout_admin_max)
            val tvMaxValue = view.findViewById<TextView>(R.id.tv_max_value)
            val sliderMaxMembers = view.findViewById<com.google.android.material.slider.Slider>(R.id.slider_max_members)
            val btnSaveMax = view.findViewById<MaterialButton>(R.id.btn_save_max)
            val rvMembers = view.findViewById<RecyclerView>(R.id.rv_members)
            val btnLeaveGroup = view.findViewById<MaterialButton>(R.id.btn_leave_group)

            etGroupName.setText(info.name)

            tvMembersSummary.text = getString(
                R.string.group_members_summary,
                members.size,
                info.maxParticipants
            )

            rvMembers.layoutManager = LinearLayoutManager(this@CircleChatActivity)
            rvMembers.adapter = GroupMembersAdapter(
                currentUserId = myUid,
                members = members,
                onBlockClicked = { member ->
                    AlertDialog.Builder(this@CircleChatActivity)
                        .setTitle(getString(R.string.block_user))
                        .setMessage(getString(R.string.block_user_confirm, member.displayName))
                        .setPositiveButton(getString(R.string.yes)) { _, _ ->
                            lifecycleScope.launch {
                                val result = circlesManager.blockUserWithPolicy(circleId, member.userId)
                                if (!result.success) {
                                    Toast.makeText(this@CircleChatActivity, getString(R.string.block_user_error), Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                if (result.blockerLeftGroup) {
                                    Toast.makeText(this@CircleChatActivity, getString(R.string.block_and_left_group_success), Toast.LENGTH_SHORT).show()
                                    finish()
                                } else if (result.blockedUserRemoved) {
                                    Toast.makeText(this@CircleChatActivity, getString(R.string.block_and_remove_success), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@CircleChatActivity, getString(R.string.user_blocked), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
            )

            if (isAdmin) {
                btnEditName.visibility = View.VISIBLE
                btnDeleteGroup.visibility = View.VISIBLE
                layoutAdminMax.visibility = View.VISIBLE
                layoutJoinCode.visibility = View.VISIBLE
                tvJoinCode.text = if (info.joinCode.isBlank()) "-" else info.joinCode

                sliderMaxMembers.value = info.maxParticipants.toFloat().coerceIn(3f, 6f)
                tvMaxValue.text = info.maxParticipants.toString()
                sliderMaxMembers.addOnChangeListener { _, value, _ ->
                    tvMaxValue.text = value.toInt().toString()
                }

                btnSaveMax.setOnClickListener {
                    val newMax = sliderMaxMembers.value.toInt()
                    lifecycleScope.launch {
                        val ok = circlesManager.updateMaxParticipants(circleId, newMax)
                        Toast.makeText(
                            this@CircleChatActivity,
                            if (ok) getString(R.string.group_max_updated) else getString(R.string.group_max_update_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                var editing = false
                btnEditName.setOnClickListener {
                    editing = !editing
                    etGroupName.isEnabled = editing
                    if (editing) {
                        etGroupName.requestFocus()
                        etGroupName.setSelection(etGroupName.text?.length ?: 0)
                        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                                as android.view.inputmethod.InputMethodManager
                        imm.showSoftInput(etGroupName, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                        btnEditName.setImageResource(R.drawable.ic_send)
                    } else {
                        val newName = etGroupName.text.toString().trim()
                        if (newName.isNotBlank()) {
                            lifecycleScope.launch {
                                val ok = circlesManager.updateCircleName(circleId, newName)
                                if (ok) {
                                    circleName = newName
                                    supportActionBar?.title = newName
                                }
                                Toast.makeText(
                                    this@CircleChatActivity,
                                    if (ok) getString(R.string.group_rename_success) else getString(R.string.group_rename_error),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                                as android.view.inputmethod.InputMethodManager
                        imm.hideSoftInputFromWindow(etGroupName.windowToken, 0)
                        btnEditName.setImageResource(R.drawable.ic_edit)
                    }
                }

                btnDeleteGroup.setOnClickListener {
                    AlertDialog.Builder(this@CircleChatActivity)
                        .setTitle(getString(R.string.delete_group))
                        .setMessage(getString(R.string.delete_group_confirm))
                        .setPositiveButton(getString(R.string.delete_group_confirm_button)) { _, _ ->
                            lifecycleScope.launch {
                                val ok = circlesManager.deleteCircle(circleId)
                                Toast.makeText(
                                    this@CircleChatActivity,
                                    if (ok) getString(R.string.group_deleted) else getString(R.string.group_delete_error),
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (ok) finish()
                            }
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
            } else {
                btnLeaveGroup.visibility = View.VISIBLE
                btnLeaveGroup.setOnClickListener {
                    AlertDialog.Builder(this@CircleChatActivity)
                        .setTitle(getString(R.string.leave_group))
                        .setMessage(getString(R.string.leave_group_confirm))
                        .setPositiveButton(getString(R.string.leave_group)) { _, _ ->
                            lifecycleScope.launch {
                                val ok = circlesManager.leaveCircle(circleId)
                                Toast.makeText(
                                    this@CircleChatActivity,
                                    if (ok) getString(R.string.group_left) else getString(R.string.group_left_error),
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (ok) finish()
                            }
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
            }

            // Galería
            val galleryRv = view.findViewById<RecyclerView>(R.id.rv_photo_gallery)
            val galleryEmpty = view.findViewById<TextView>(R.id.tv_gallery_empty)
            val galleryPhotos = mutableListOf<String>()
            galleryRv.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@CircleChatActivity, 3)
            val galleryAdapter = CirclePhotoGalleryAdapter(galleryPhotos) { url -> showFullscreenImage(url) }
            galleryRv.adapter = galleryAdapter

            lifecycleScope.launch {
                val photos = eventsManager.getCirclePhotoUrls(circleId)
                if (photos.isEmpty()) {
                    galleryEmpty.visibility = View.VISIBLE
                    galleryRv.visibility = View.GONE
                } else {
                    galleryEmpty.visibility = View.GONE
                    galleryRv.visibility = View.VISIBLE
                    galleryPhotos.addAll(photos)
                    galleryAdapter.notifyDataSetChanged()
                }
            }

            if (isFinishing || isDestroyed) return@launch

            val dialog = AlertDialog.Builder(this@CircleChatActivity)
                .setTitle(getString(R.string.group_info))
                .setView(view)
                .setPositiveButton(getString(R.string.close), null)
                .show()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                ContextCompat.getColor(this@CircleChatActivity, R.color.text_secondary)
            )
        }
    }

    // Pide confirmación y borra un mensaje propio del chat.
    private fun confirmDeleteMessage(message: Message) {
        val myUid = circlesManager.getUserId()
        if (message.senderId != myUid) return

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_message_confirm_title))
            .setMessage(getString(R.string.delete_message_confirm_body))
            .setPositiveButton(getString(R.string.delete_message)) { _, _ ->
                lifecycleScope.launch {
                    val ok = eventsManager.deleteOwnMessage(circleId, message.id)
                    Toast.makeText(
                        this@CircleChatActivity,
                        if (ok) getString(R.string.delete_message_success) else getString(R.string.delete_message_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // Muestra una imagen en pantalla completa real, ocupando t*do el display.
    // Carga la imagen en alta calidad sin reescalar.
    private fun showFullscreenImage(url: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_fullscreen_image, null)
        val iv = dialogView.findViewById<ImageView>(R.id.iv_fullscreen)
        val btnClose = dialogView.findViewById<android.widget.ImageButton>(R.id.btn_close_fullscreen)

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(dialogView)

        // Forzar que la ventana ocupe exactamente el 100% de la pantalla
        dialog.window?.apply {
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Sin padding ni márgenes del sistema
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
            // Ocultar barra de navegación y status bar para inmersión total
            decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }

        // Cerrar al pulsar fondo o botón X
        dialogView.setOnClickListener { dialog.dismiss() }
        btnClose.setOnClickListener { dialog.dismiss() }
        // La imagen en sí no cierra al tocarla (puede que el usuario quiera hacer zoom)

        dialog.show()

        // Carga la imagen original sin reducir calidad
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        executor.execute {
            runCatching {
                java.net.URL(url).openStream().use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
            }.onSuccess { bitmap ->
                if (bitmap != null) handler.post { iv.setImageBitmap(bitmap) }
            }
        }
    }
    // Marca la pantalla como activa: no mostrar notificaciones mientras se ve el chat.
    // También cancela cualquier notificación pendiente de este círculo.
    override fun onResume() {
        super.onResume()
        isInForeground = true
        CircleNotificationHelper.cancelNotification(this, circleId)
    }

    // Al salir de la pantalla se vuelven a permitir las notificaciones.
    override fun onPause() {
        super.onPause()
        isInForeground = false
    }

    // Libera listeners y tareas periódicas al salir de la pantalla.
    override fun onDestroy() {
        super.onDestroy()
        eventStartHandler.removeCallbacks(eventStartTicker)
        messagesListener?.let { eventsManager.removeMessagesListener(circleId, it) }
    }
}