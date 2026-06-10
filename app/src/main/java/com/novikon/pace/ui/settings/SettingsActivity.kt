package com.novikon.pace.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.novikon.pace.R
import com.novikon.pace.constants.Language
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.utils.ReminderScheduler
import com.novikon.pace.utils.SettingsManager
import com.novikon.pace.utils.applySystemBarInsets
import kotlinx.coroutines.launch

// Pantalla de ajustes: centraliza preferencias generales de la aplicacion.
class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    private lateinit var darkModeSwitch: SwitchMaterial
    private lateinit var currentLanguageText: TextView
    private lateinit var remindersSwitch: SwitchMaterial
    private lateinit var reminderTimeText: TextView
    private lateinit var activeDaysText: TextView

    // Launcher para pedir permiso de notificaciones en Android 13+.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            settingsManager.areRemindersEnabled = true
            remindersSwitch.isChecked = true
            scheduleReminders()
            syncSettings()
            Toast.makeText(this, getString(R.string.rmb_on), Toast.LENGTH_SHORT).show()
        } else {
            remindersSwitch.isChecked = false
            settingsManager.areRemindersEnabled = false
            Toast.makeText(this, getString(R.string.notification_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    // Carga preferencias guardadas y prepara la UI de ajustes generales.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_settings)
        applySystemBarInsets()

        settingsManager = SettingsManager(this)

        initializeViews()
        loadSettings()
        setupListeners()
    }

    // Vincula controles visuales para reflejar y editar las preferencias.
    private fun initializeViews() {
        darkModeSwitch = findViewById(R.id.darkModeSwitch)
        currentLanguageText = findViewById(R.id.currentLanguageText)
        remindersSwitch = findViewById(R.id.remindersSwitch)
        reminderTimeText = findViewById(R.id.reminderTimeText)
        activeDaysText = findViewById(R.id.activeDaysText)
    }

    // Rellena la pantalla con los valores actuales de tema, idioma y recordatorios.
    private fun loadSettings() {
        darkModeSwitch.isChecked =
            ThemeHelper.getThemeMode(this) == AppCompatDelegate.MODE_NIGHT_YES
        currentLanguageText.text = LanguageHelper.getLanguageDisplayName(this)
        remindersSwitch.isChecked = settingsManager.areRemindersEnabled
        reminderTimeText.text = settingsManager.reminderTime
        activeDaysText.text = getActiveDaysDisplayText()
    }

    // Escucha cambios del usuario y guarda cada preferencia cuando se modifica.
    private fun setupListeners() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // ── MODO OSCURO ───────────────────────────────────────────────────────
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val themeMode = if (isChecked) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }

            ThemeHelper.setThemeMode(this, themeMode)
            // Sincronizar con Firebase — el tema cambió
            syncSettings()

            Toast.makeText(
                this,
                if (isChecked) getString(R.string.dark_on) else getString(R.string.dark_off),
                Toast.LENGTH_SHORT
            ).show()

            recreate()
        }

        // ── IDIOMA ────────────────────────────────────────────────────────────
        findViewById<android.view.View>(R.id.languageOption).setOnClickListener {
            showLanguageDialog()
        }

        // ── RECORDATORIOS ─────────────────────────────────────────────────────
        remindersSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    when {
                        ContextCompat.checkSelfPermission(
                            this, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            settingsManager.areRemindersEnabled = true
                            scheduleReminders()
                            syncSettings()
                            Toast.makeText(this, getString(R.string.rmb_on), Toast.LENGTH_SHORT).show()
                        }
                        shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                            showPermissionRationale()
                        }
                        else -> {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                } else {
                    settingsManager.areRemindersEnabled = true
                    scheduleReminders()
                    syncSettings()
                    Toast.makeText(this, getString(R.string.rmb_on), Toast.LENGTH_SHORT).show()
                }
            } else {
                settingsManager.areRemindersEnabled = false
                ReminderScheduler.cancelReminders(this)
                syncSettings()
                Toast.makeText(this, getString(R.string.rmb_off), Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<android.view.View>(R.id.reminderTimeOption).setOnClickListener {
            showTimePicker()
        }

        findViewById<android.view.View>(R.id.activeDaysOption).setOnClickListener {
            showActiveDaysDialog()
        }
    }

    // Muestra selector de idioma y aplica el cambio inmediatamente.
    private fun showLanguageDialog() {
        val languages = Language.values()
        val languageNames = languages.map { it.displayName }.toTypedArray()
        val currentIndex = languages.indexOfFirst {
            it.code == LanguageHelper.getLanguageCode(this)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_language))
            .setSingleChoiceItems(languageNames, currentIndex) { dialog, which ->
                val selectedLanguage = languages[which]

                LanguageHelper.changeLanguage(this, selectedLanguage.code)
                // Sincronizar con Firebase — el idioma cambió
                syncSettings()

                Toast.makeText(
                    this,
                    "${getString(R.string.idm_chng)}${selectedLanguage.displayName}",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // Permite elegir la hora exacta para enviar recordatorios diarios.
    private fun showTimePicker() {
        val timeParts = settingsManager.reminderTime.split(":")
        val hour = timeParts[0].toIntOrNull() ?: 20
        val minute = timeParts[1].toIntOrNull() ?: 0

        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                settingsManager.reminderTime = formattedTime
                reminderTimeText.text = formattedTime

                if (settingsManager.areRemindersEnabled) {
                    scheduleReminders()
                }

                // Sincronizar con Firebase — la hora cambió
                syncSettings()

                Toast.makeText(
                    this,
                    "${getString(R.string.hr_chng)}$formattedTime",
                    Toast.LENGTH_SHORT
                ).show()
            },
            hour,
            minute,
            true
        ).show()
    }

    // Permite seleccionar qué días de la semana recibir recordatorios.
    private fun showActiveDaysDialog() {
        val allDays = resources.getStringArray(R.array.week_days_short)
        val selectedIndices = settingsManager.activeDayIndices.toMutableSet()
        val checkedItems = BooleanArray(allDays.size) { selectedIndices.contains(it) }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.active_days_title))
            .setMultiChoiceItems(allDays, checkedItems) { _, which, isChecked ->
                if (isChecked) selectedIndices.add(which) else selectedIndices.remove(which)
            }
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                settingsManager.activeDayIndices = selectedIndices
                activeDaysText.text = getActiveDaysDisplayText()

                val calendar = java.util.Calendar.getInstance()
                val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                val daysToMonday = if (dayOfWeek == java.util.Calendar.SUNDAY) 6 else dayOfWeek - 2
                calendar.add(java.util.Calendar.DAY_OF_MONTH, -daysToMonday)
                val weekStartDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(calendar.time)
                settingsManager.saveWeeklyActiveDays(weekStartDate, selectedIndices)

                if (settingsManager.areRemindersEnabled) {
                    scheduleReminders()
                }

                // Sincronizar con Firebase — los días activos cambiaron
                syncSettings()

                Toast.makeText(this, getString(R.string.day_chng), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // Explica por qué se necesita permiso de notificaciones antes de volver a pedirlo.
    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.notification_permission_title))
            .setMessage(getString(R.string.notification_permission_message))
            .setPositiveButton(getString(R.string.grant_permission)) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                remindersSwitch.isChecked = false
                settingsManager.areRemindersEnabled = false
            }
            .show()
    }

    // Sincroniza todos los ajustes actuales con Firebase en background.
    // Se llama después de cada cambio de ajuste para mantener Firebase
    // siempre actualizado sin bloquear la UI.
    private fun syncSettings() {
        lifecycleScope.launch {
            settingsManager.saveSettingsToFirebase()
        }
    }

    // Programa o reprograma alarmas locales según la configuración actual.
    private fun scheduleReminders() {
        ReminderScheduler.scheduleReminders(
            context = this,
            areRemindersEnabled = settingsManager.areRemindersEnabled,
            activeDayIndices = settingsManager.activeDayIndices,
            reminderTime = settingsManager.reminderTime
        )
    }

    // Convierte los índices de días activos en un texto legible para la UI.
    private fun getActiveDaysDisplayText(): String {
        val allDays = resources.getStringArray(R.array.week_days_short)
        return settingsManager.activeDayIndices
            .sorted()
            .mapNotNull { allDays.getOrNull(it) }
            .joinToString(", ")
            .ifEmpty { getString(R.string.no_days_selected) }
    }
}