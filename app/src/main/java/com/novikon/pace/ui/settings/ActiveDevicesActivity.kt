package com.novikon.pace.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.novikon.pace.R
import com.novikon.pace.data.RealtimeDatabaseManager
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ActiveDevicesActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var devicesContainer: LinearLayout

    private lateinit var databaseManager: RealtimeDatabaseManager
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_active_devices)

        databaseManager = RealtimeDatabaseManager()
        sessionManager = SessionManager(this)

        initializeViews()
        setupListeners()
        loadDevices()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.backButton)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        emptyText = findViewById(R.id.emptyText)
        devicesContainer = findViewById(R.id.devicesContainer)
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }
    }

    // ========== CARGAR DISPOSITIVOS ==========

    private fun loadDevices() {
        showLoading(true)

        lifecycleScope.launch {
            val devices = databaseManager.getActiveDevices()
            showLoading(false)

            if (devices.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                return@launch
            }

            // Ordenar: este dispositivo primero, luego por última actividad descendente
            val currentDeviceId = sessionManager.getDeviceId()
            val sorted = devices.sortedWith(compareByDescending<Map<String, Any>> {
                it["deviceId"] == currentDeviceId
            }.thenByDescending {
                it["lastActiveTimestamp"] as? Long ?: 0L
            })

            renderDevices(sorted, currentDeviceId)
        }
    }

    // ========== RENDERIZAR LISTA ==========

    private fun renderDevices(devices: List<Map<String, Any>>, currentDeviceId: String) {
        devicesContainer.removeAllViews()

        devices.forEach { device ->
            val deviceId = device["deviceId"] as? String ?: return@forEach
            val deviceName = device["deviceName"] as? String ?: "Dispositivo desconocido"
            val osVersion = device["osVersion"] as? String ?: ""
            val lastActive = device["lastActiveTimestamp"] as? Long ?: 0L
            val isCurrentDevice = deviceId == currentDeviceId

            val itemView = layoutInflater.inflate(R.layout.item_active_device, devicesContainer, false)

            // Nombre del dispositivo
            itemView.findViewById<TextView>(R.id.deviceName).text = deviceName

            // OS
            itemView.findViewById<TextView>(R.id.deviceOs).text = osVersion

            // Último acceso
            itemView.findViewById<TextView>(R.id.deviceLastActive).text =
                getString(R.string.active_devices_last_active, formatLastActive(lastActive))

            // Badge y botón según si es el dispositivo actual
            val badge = itemView.findViewById<TextView>(R.id.thisDeviceBadge)
            val closeButton = itemView.findViewById<ImageButton>(R.id.closeSessionButton)

            if (isCurrentDevice) {
                badge.visibility = View.VISIBLE
                closeButton.visibility = View.GONE
            } else {
                badge.visibility = View.GONE
                closeButton.visibility = View.VISIBLE
                closeButton.setOnClickListener {
                    showCloseSessionDialog(deviceId, deviceName, itemView)
                }
            }

            devicesContainer.addView(itemView)
        }
    }

    // ========== CERRAR SESIÓN EN UN DISPOSITIVO ==========

    private fun showCloseSessionDialog(deviceId: String, deviceName: String, itemView: View) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.active_devices_close_confirm_title))
            .setMessage(getString(R.string.active_devices_close_confirm_message, deviceName))
            .setPositiveButton(getString(R.string.active_devices_close_session)) { _, _ ->
                closeSessionOnDevice(deviceId, deviceName, itemView)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun closeSessionOnDevice(deviceId: String, deviceName: String, itemView: View) {
        lifecycleScope.launch {
            val success = databaseManager.removeDevice(deviceId, databaseManager.getUserId() ?: return@launch)
            if (success) {
                // Animar la salida del item eliminado
                devicesContainer.removeView(itemView)
                Toast.makeText(
                    this@ActiveDevicesActivity,
                    getString(R.string.active_devices_session_closed, deviceName),
                    Toast.LENGTH_SHORT
                ).show()

                // Mostrar vacío si no quedan otros dispositivos
                if (devicesContainer.childCount == 0) {
                    emptyText.visibility = View.VISIBLE
                }
            } else {
                Toast.makeText(
                    this@ActiveDevicesActivity,
                    getString(R.string.error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ========== HELPERS ==========

    private fun showLoading(isLoading: Boolean) {
        loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        devicesContainer.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    // Formatea el timestamp de última actividad en texto legible.
    // Si fue hace menos de un minuto muestra "Ahora mismo",
    // si fue hoy muestra "Hace X horas", si fue antes muestra la fecha.
    private fun formatLastActive(timestamp: Long): String {
        if (timestamp == 0L) return "—"

        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Ahora mismo"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                "Hace $minutes min"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "Hace $hours h"
            }
            else -> {
                SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}