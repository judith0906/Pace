package com.novikon.pace.ui.circles

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
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

class CircleChatActivity : AppCompatActivity() {

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
        override fun run() {
            lifecycleScope.launch { eventsManager.checkAndStartDueEvents(circleId) }
            messagesAdapter.refreshTemporalStates()
            eventStartHandler.postDelayed(this, 30_000L)
        }
    }

    private var pendingCaptureMessage: Message? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_needed), Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) return@registerForActivityResult
        showCapturePreviewDialog(bitmap)
    }

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
        setupBottomActions()
        observeMessages()

        eventStartTicker.run()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_circle_chat, menu)
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

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar_chat)
        rvMessages = findViewById(R.id.rv_messages)
        btnCreateEvent = findViewById(R.id.btn_create_event)
        btnSendMessage = findViewById(R.id.btn_send_message)
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
        messagesAdapter = MessagesAdapter(
            currentUserId = currentUserId,
            onJoinEvent = { message -> respondToEvent(message, true) },
            onDeclineEvent = { message -> respondToEvent(message, false) },
            onCaptureMoment = { message -> onCaptureMomentClicked(message) }
        )

        rvMessages.apply {
            layoutManager = LinearLayoutManager(this@CircleChatActivity).apply { stackFromEnd = true }
            adapter = messagesAdapter
        }
    }

    private fun setupBottomActions() {
        btnCreateEvent.setOnClickListener { showCreateEventDialog() }
        btnSendMessage.setOnClickListener { showSendMessageDialog() }
    }

    private fun observeMessages() {
        messagesListener = eventsManager.observeMessages(
            circleId = circleId,
            onMessageAdded = { message ->
                messagesAdapter.addMessage(message)
                rvMessages.scrollToPosition(messagesAdapter.itemCount - 1)
            },
            onMessageChanged = { message ->
                messagesAdapter.updateMessage(message)
            }
        )
    }

    private fun showSendMessageDialog() {
        val input = androidx.appcompat.widget.AppCompatEditText(this).apply {
            hint = getString(R.string.chat_hint_message)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.chat_send_message))
            .setView(input)
            .setPositiveButton(getString(R.string.event_send)) { _, _ ->
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isBlank()) return@setPositiveButton

                lifecycleScope.launch {
                    val success = circlesManager.sendMessage(circleId, text)
                    if (!success) {
                        Toast.makeText(this@CircleChatActivity, getString(R.string.chat_send_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

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
                val now = Calendar.getInstance()
                TimePickerDialog(
                    this@CircleChatActivity,
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        hasTime = true
                        tvSelectedTime.text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                    },
                    now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true
                ).show()
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
                        val ok = eventsManager.createEvent(circleId, habitName, scheduledAt)
                        Toast.makeText(
                            this@CircleChatActivity,
                            if (ok) getString(R.string.event_created_success) else getString(R.string.event_created_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

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
            }
        }
    }

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
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

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

    private fun showGroupInfoDialog() {
        lifecycleScope.launch {
            val info = circlesManager.getGroupInfo(circleId) ?: return@launch
            val members = circlesManager.getMemberDisplayNames(info.memberIds)
            val myUid = circlesManager.getUserId()
            val isAdmin = myUid == info.createdBy

            val view = layoutInflater.inflate(R.layout.dialog_group_info, null)
            val tvGroupName = view.findViewById<TextView>(R.id.tv_group_name)
            val btnDeleteGroup = view.findViewById<MaterialButton>(R.id.btn_delete_group)
            val tvJoinCode = view.findViewById<TextView>(R.id.tv_join_code)
            val tvMembersSummary = view.findViewById<TextView>(R.id.tv_members_summary)
            val layoutAdminMax = view.findViewById<View>(R.id.layout_admin_max)
            val etNewMax = view.findViewById<EditText>(R.id.et_new_max)
            val btnSaveMax = view.findViewById<Button>(R.id.btn_save_max)
            val rvMembers = view.findViewById<RecyclerView>(R.id.rv_members)
            val btnLeaveGroup = view.findViewById<MaterialButton>(R.id.btn_leave_group)

            tvGroupName.text = info.name
            tvJoinCode.text = if (isAdmin) {
                if (info.joinCode.isBlank()) "-" else info.joinCode
            } else {
                getString(R.string.hidden_for_members)
            }

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
                                    Toast.makeText(
                                        this@CircleChatActivity,
                                        getString(R.string.block_user_error),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }

                                if (result.blockerLeftGroup) {
                                    Toast.makeText(
                                        this@CircleChatActivity,
                                        getString(R.string.block_and_left_group_success),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    finish()
                                } else if (result.blockedUserRemoved) {
                                    Toast.makeText(
                                        this@CircleChatActivity,
                                        getString(R.string.block_and_remove_success),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        this@CircleChatActivity,
                                        getString(R.string.user_blocked),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
            )

            if (isAdmin) {
                layoutAdminMax.visibility = View.VISIBLE
                btnDeleteGroup.visibility = View.VISIBLE
                etNewMax.setText(info.maxParticipants.toString())

                btnSaveMax.setOnClickListener {
                    val newMax = etNewMax.text.toString().toIntOrNull()
                    if (newMax == null || newMax <= 0) return@setOnClickListener

                    lifecycleScope.launch {
                        val ok = circlesManager.updateMaxParticipants(circleId, newMax)
                        Toast.makeText(
                            this@CircleChatActivity,
                            if (ok) getString(R.string.group_max_updated) else getString(R.string.group_max_update_error),
                            Toast.LENGTH_SHORT
                        ).show()
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

            if (isFinishing || isDestroyed) return@launch

            AlertDialog.Builder(this@CircleChatActivity)
                .setTitle(getString(R.string.group_info))
                .setView(view)
                .setPositiveButton(getString(R.string.close), null)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        eventStartHandler.removeCallbacks(eventStartTicker)
        messagesListener?.let { eventsManager.removeMessagesListener(circleId, it) }
    }
}