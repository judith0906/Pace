package com.novikon.pace.ui.circles

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.novikon.pace.R
import com.novikon.pace.data.CirclesRealtimeManager
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.utils.PickyEvent
import com.novikon.pace.utils.PickyManager
import com.novikon.pace.utils.applySystemBarInsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Pantalla de circulos: lista grupos del usuario y permite crear/unirse a nuevos.
class CirclesActivity : AppCompatActivity() {

    private lateinit var rvCircles: RecyclerView
    private lateinit var backButton: ImageButton
    private lateinit var addButton: FloatingActionButton
    private lateinit var adapter: CirclesAdapter

    private val circlesManager by lazy { CirclesRealtimeManager(this) }

// onCreate: inicializa la pantalla y prepara vistas/eventos iniciales.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_circles)
        applySystemBarInsets()

        rvCircles = findViewById(R.id.rv_circles)
        backButton = findViewById(R.id.backButton)
        addButton = findViewById(R.id.btn_add_circle)

        adapter = CirclesAdapter { circle ->
            val intent = Intent(this, CircleChatActivity::class.java).apply {
                putExtra(CircleChatActivity.EXTRA_CIRCLE_ID, circle.id)
                putExtra(CircleChatActivity.EXTRA_CIRCLE_NAME, circle.name)
            }
            startActivity(intent)
        }

        rvCircles.layoutManager = LinearLayoutManager(this)
        rvCircles.adapter = adapter

        backButton.setOnClickListener { finish() }

        addButton.setOnClickListener {
            AddCircleDialog(
                onJoinCircleByCode = { code -> joinByCode(code) },
                onCreateCircle = { name, max -> createCircle(name, max) }
            ).show(supportFragmentManager, "AddCircleDialog")
        }

        loadCircles()
        processPendingJoinCode()
    }
    override fun onResume() {
        super.onResume()
        loadCircles()
    }

    private fun processPendingJoinCode() {
        val prefs = getSharedPreferences("deep_link", MODE_PRIVATE)
        val pendingCode = prefs.getString(DeepLinkActivity.PENDING_JOIN_CODE, null)
        if (pendingCode != null) {
            prefs.edit().remove(DeepLinkActivity.PENDING_JOIN_CODE).apply()
            joinByCode(pendingCode)
        }
    }
    private fun loadCircles() {
        lifecycleScope.launch {
            val circles = circlesManager.getUserCircles()
            adapter.submitList(circles)
        }
    }
    private fun createCircle(name: String, maxParticipants: Int) {
        lifecycleScope.launch {
            val createdId = circlesManager.createCircle(name, maxParticipants)
            Toast.makeText(
                this@CirclesActivity,
                if (createdId != null) getString(R.string.circle_created) else getString(R.string.circle_create_error),
                Toast.LENGTH_SHORT
            ).show()

            if (createdId != null) loadCircles()
        }
    }
    private fun joinByCode(code: String) {
        lifecycleScope.launch {
            val result = circlesManager.joinCircleByCode(code)

            if (result.success) {
                showPickyJoined()
                loadCircles()
                return@launch
            }

            val msg = when (result.reason) {
                com.novikon.pace.data.JoinFailReason.INVALID_CODE ->
                    getString(R.string.error_code_6_digits)
                com.novikon.pace.data.JoinFailReason.CODE_NOT_FOUND ->
                    getString(R.string.circle_code_not_found)
                com.novikon.pace.data.JoinFailReason.GROUP_NOT_FOUND ->
                    getString(R.string.circle_not_found)
                com.novikon.pace.data.JoinFailReason.BLOCKED ->
                    getString(R.string.cannot_join_blocked_user)
                com.novikon.pace.data.JoinFailReason.GROUP_FULL_OR_TRANSACTION_FAILED ->
                    getString(R.string.circle_join_error_group_full_or_permissions)
                null ->
                    getString(R.string.circle_join_error)
            }

            Toast.makeText(this@CirclesActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPickyJoined() {
        val state = PickyManager.getPickyState(PickyEvent.CIRCLE_JOINED)
        val overlay = layoutInflater.inflate(R.layout.overlay_picky, null) as ViewGroup

        overlay.findViewById<ImageView>(R.id.pickyImage).setImageResource(state.imageRes)
        overlay.findViewById<TextView>(R.id.pickyMessage).setText(state.messageRes)

        val container = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        container.addView(overlay)

        lifecycleScope.launch {
            delay(2500)
            container.removeView(overlay)
        }
    }
}