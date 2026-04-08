package com.novikon.pace.ui.circles

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
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
import kotlinx.coroutines.launch

class CirclesActivity : AppCompatActivity() {

    private lateinit var rvCircles: RecyclerView
    private lateinit var backButton: ImageButton
    private lateinit var addButton: FloatingActionButton
    private lateinit var adapter: CirclesAdapter

    private val circlesManager = CirclesRealtimeManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_circles)

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
                onJoinCircle = { circleId -> joinCircle(circleId) },
                onCreateCircle = { name -> createCircle(name) }
            ).show(supportFragmentManager, "AddCircleDialog")
        }

        loadCircles()
    }

    override fun onResume() {
        super.onResume()
        loadCircles()
    }

    private fun loadCircles() {
        lifecycleScope.launch {
            val circles = circlesManager.getUserCircles()
            adapter.submitList(circles)
        }
    }

    private fun createCircle(name: String) {
        lifecycleScope.launch {
            val createdId = circlesManager.createCircle(name)
            if (createdId == null) {
                Toast.makeText(
                    this@CirclesActivity,
                    getString(R.string.circle_create_error),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            Toast.makeText(
                this@CirclesActivity,
                getString(R.string.circle_created),
                Toast.LENGTH_SHORT
            ).show()

            loadCircles()
        }
    }

    private fun joinCircle(circleId: String) {
        val userId = circlesManager.getUserId()
        if (userId.isNullOrBlank()) {
            Toast.makeText(
                this,
                getString(R.string.circle_join_error),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        lifecycleScope.launch {
            val success = circlesManager.addMemberToCircle(circleId, userId)
            Toast.makeText(
                this@CirclesActivity,
                if (success) getString(R.string.circle_joined) else getString(R.string.circle_join_error),
                Toast.LENGTH_SHORT
            ).show()

            if (success) loadCircles()
        }
    }
}