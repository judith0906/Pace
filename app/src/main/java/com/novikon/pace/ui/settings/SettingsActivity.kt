package com.novikon.pace.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.NumberPicker
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
    private lateinit var activeDaysText: TextView
    private lateinit var remindersContainer: LinearLayout
    private lateinit var addReminderButton: LinearLayout

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
        activeDaysText = findViewById(R.id.activeDaysText)
        remindersContainer = findViewById(R.id.remindersContainer)
        addReminderButton = findViewById(R.id.addReminderButton)
    }

    // Rellena la pantalla con los valores actuales de tema, idioma y recordatorios.
    private fun loadSettings() {
        darkModeSwitch.isChecked =
            ThemeHelper.getThemeMode(this) == AppCompatDelegate.MODE_NIGHT_YES
        currentLanguageText.text = LanguageHelper.getLanguageDisplayName(this)
        remindersSwitch.isChecked = settingsManager.areRemindersEnabled
        activeDaysText.text = getActiveDaysDisplayText()
        rebuildRemindersUI()
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

        findViewById<android.view.View>(R.id.activeDaysOption).setOnClickListener {
            showActiveDaysDialog()
        }
        addReminderButton.setOnClickListener {
            showAddReminderDialog()
        }
    }

    // Muestra selector de idioma y aplica el cambio inmediatamente.
    private fun showLanguageDialog() {
        val languages = Language.values()
        val currentCode = LanguageHelper.getLanguageCode(this)

        val dialogView = layoutInflater.inflate(R.layout.dialog_language_picker, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.languageChipContainer)

        var selectedLanguage = languages.firstOrNull { it.code == currentCode } ?: languages[0]
        val chipViews = mutableListOf<TextView>()

        languages.forEach { language ->
            val chip = layoutInflater.inflate(R.layout.item_day_chip, container, false) as TextView
            chip.text = language.displayName
            chip.isSelected = language.code == currentCode
            chip.setTextColor(
                ContextCompat.getColor(this,
                    if (chip.isSelected) R.color.day_chip_text_selected
                    else R.color.day_chip_text_unselected
                )
            )
            chip.setOnClickListener {
                selectedLanguage = language
                chipViews.forEach { c ->
                    c.isSelected = false
                    c.setTextColor(ContextCompat.getColor(this, R.color.day_chip_text_unselected))
                }
                chip.isSelected = true
                chip.setTextColor(ContextCompat.getColor(this, R.color.day_chip_text_selected))
            }
            container.addView(chip)
            chipViews.add(chip)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_language))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                if (selectedLanguage.code != currentCode) {
                    LanguageHelper.changeLanguage(this, selectedLanguage.code)
                    syncSettings()
                    Toast.makeText(
                        this,
                        "${getString(R.string.idm_chng)}${selectedLanguage.displayName}",
                        Toast.LENGTH_SHORT
                    ).show()
                    recreate()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
    }

    // Permite elegir la hora exacta para enviar recordatorios diarios.
    private fun showTimePicker(currentTime: String, minHour: Int, maxHour: Int, onTimeSet: (String) -> Unit) {
        val timeParts = currentTime.split(":")
        val currentHour = timeParts[0].toIntOrNull()?.coerceIn(minHour, maxHour) ?: minHour
        val currentMinute = timeParts[1].toIntOrNull() ?: 0

        val dialogView = layoutInflater.inflate(R.layout.dialog_time_picker, null)
        val hourPicker = dialogView.findViewById<NumberPicker>(R.id.hourPicker)
        val minutePicker = dialogView.findViewById<NumberPicker>(R.id.minutePicker)

        // Configurar horas según rango de la franja
        val hours = (minHour..maxHour).map { String.format("%02d", it) }.toTypedArray()
        hourPicker.minValue = 0
        hourPicker.maxValue = hours.size - 1
        hourPicker.displayedValues = hours
        hourPicker.value = (currentHour - minHour).coerceIn(0, hours.size - 1)
        hourPicker.wrapSelectorWheel = false

        // Configurar minutos 00-59
        val minutes = (0..59).map { String.format("%02d", it) }.toTypedArray()
        minutePicker.minValue = 0
        minutePicker.maxValue = 59
        minutePicker.displayedValues = minutes
        minutePicker.value = currentMinute
        minutePicker.wrapSelectorWheel = true

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val selectedHour = minHour + hourPicker.value
                val selectedMinute = minutePicker.value
                val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                onTimeSet(formattedTime)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
    }

    // Permite seleccionar qué días de la semana recibir recordatorios.
    private fun showActiveDaysDialog() {
        val allDays = resources.getStringArray(R.array.week_days_short)
        val selectedIndices = settingsManager.activeDayIndices.toMutableSet()

        val dialogView = layoutInflater.inflate(R.layout.dialog_active_days, null)
        val chipContainer = dialogView.findViewById<LinearLayout>(R.id.chipContainer)

        allDays.mapIndexed { index, day ->
            val chip = layoutInflater.inflate(R.layout.item_day_chip, chipContainer, false) as TextView
            chip.text = day
            chip.isSelected = selectedIndices.contains(index)
            chip.setTextColor(
                ContextCompat.getColor(this,
                    if (selectedIndices.contains(index)) R.color.day_chip_text_selected
                    else R.color.day_chip_text_unselected
                )
            )
            chip.setOnClickListener {
                chip.isSelected = !chip.isSelected
                chip.setTextColor(
                    ContextCompat.getColor(this,
                        if (chip.isSelected) R.color.day_chip_text_selected
                        else R.color.day_chip_text_unselected
                    )
                )
                if (chip.isSelected) selectedIndices.add(index) else selectedIndices.remove(index)
            }
            chipContainer.addView(chip)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.active_days_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val oldIndices = settingsManager.activeDayIndices
                val todayCalendar = java.util.Calendar.getInstance()
                val iterCalendar = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.MONTH, -12)
                    val dow = get(java.util.Calendar.DAY_OF_WEEK)
                    val back = if (dow == java.util.Calendar.SUNDAY) 6 else dow - 2
                    add(java.util.Calendar.DAY_OF_MONTH, -back)
                }
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

                while (!iterCalendar.after(todayCalendar)) {
                    val weekKey = sdf.format(iterCalendar.time)
                    if (settingsManager.getWeeklyActiveDays(weekKey) == null) {
                        settingsManager.saveWeeklyActiveDays(weekKey, oldIndices)
                    }
                    iterCalendar.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                }

                settingsManager.activeDayIndices = selectedIndices
                activeDaysText.text = getActiveDaysDisplayText()

                val calendar = java.util.Calendar.getInstance()
                val todayDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                val todayIndex = when (todayDayOfWeek) {
                    java.util.Calendar.MONDAY -> 0
                    java.util.Calendar.TUESDAY -> 1
                    java.util.Calendar.WEDNESDAY -> 2
                    java.util.Calendar.THURSDAY -> 3
                    java.util.Calendar.FRIDAY -> 4
                    java.util.Calendar.SATURDAY -> 5
                    java.util.Calendar.SUNDAY -> 6
                    else -> 0
                }

                val removedDays = oldIndices - selectedIndices
                val removedDayAlreadyPassed = removedDays.any { it < todayIndex }

                if (!removedDayAlreadyPassed) {
                    val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                    val daysToMonday = if (dayOfWeek == java.util.Calendar.SUNDAY) 6 else dayOfWeek - 2
                    calendar.add(java.util.Calendar.DAY_OF_MONTH, -daysToMonday)
                    val weekStartDate = sdf.format(calendar.time)
                    settingsManager.saveWeeklyActiveDays(weekStartDate, selectedIndices)
                }

                if (settingsManager.areRemindersEnabled) scheduleReminders()
                syncSettings()
                Toast.makeText(this, getString(R.string.day_chng), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()

        // Botones en gris
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
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
            morningEnabled = settingsManager.morningReminderEnabled,
            morningTime = settingsManager.morningReminderTime,
            afternoonEnabled = settingsManager.afternoonReminderEnabled,
            afternoonTime = settingsManager.afternoonReminderTime,
            eveningEnabled = settingsManager.eveningReminderEnabled,
            eveningTime = settingsManager.eveningReminderTime,
            allDayEnabled = settingsManager.allDayReminderEnabled,
            allDayTime = settingsManager.allDayReminderTime
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

    // Datos de cada franja
    data class ReminderSlot(
        val slotIndex: Int,       // 0=mañana, 1=tarde, 2=noche, 3=todo el día
        val titleRes: Int,
        val rangeRes: Int,
        val minHour: Int,
        val maxHour: Int,
        val defaultTime: String,
        val enabledGetter: () -> Boolean,
        val enabledSetter: (Boolean) -> Unit,
        val timeGetter: () -> String,
        val timeSetter: (String) -> Unit
    )

    private fun allSlots() = listOf(
        ReminderSlot(
            slotIndex = 0,
            titleRes = R.string.reminder_slot_title_morning,
            rangeRes = R.string.reminder_slot_range_morning,
            minHour = 5, maxHour = 11,
            defaultTime = "08:00",
            enabledGetter = { settingsManager.morningReminderEnabled },
            enabledSetter = { settingsManager.morningReminderEnabled = it },
            timeGetter = { settingsManager.morningReminderTime },
            timeSetter = { settingsManager.morningReminderTime = it }
        ),
        ReminderSlot(
            slotIndex = 1,
            titleRes = R.string.reminder_slot_title_afternoon,
            rangeRes = R.string.reminder_slot_range_afternoon,
            minHour = 12, maxHour = 18,
            defaultTime = "15:00",
            enabledGetter = { settingsManager.afternoonReminderEnabled },
            enabledSetter = { settingsManager.afternoonReminderEnabled = it },
            timeGetter = { settingsManager.afternoonReminderTime },
            timeSetter = { settingsManager.afternoonReminderTime = it }
        ),
        ReminderSlot(
            slotIndex = 2,
            titleRes = R.string.reminder_slot_title_evening,
            rangeRes = R.string.reminder_slot_range_evening,
            minHour = 19, maxHour = 23,
            defaultTime = "21:00",
            enabledGetter = { settingsManager.eveningReminderEnabled },
            enabledSetter = { settingsManager.eveningReminderEnabled = it },
            timeGetter = { settingsManager.eveningReminderTime },
            timeSetter = { settingsManager.eveningReminderTime = it }
        ),
        ReminderSlot(
            slotIndex = 3,
            titleRes = R.string.reminder_slot_title_allday,
            rangeRes = R.string.reminder_slot_range_allday,
            minHour = 0, maxHour = 23,
            defaultTime = "09:00",
            enabledGetter = { settingsManager.allDayReminderEnabled },
            enabledSetter = { settingsManager.allDayReminderEnabled = it },
            timeGetter = { settingsManager.allDayReminderTime },
            timeSetter = { settingsManager.allDayReminderTime = it }
        )
    )

    private fun rebuildRemindersUI() {
        remindersContainer.removeAllViews()
        val activeSlots = allSlots().filter { it.enabledGetter() }
        activeSlots.forEach { slot -> addReminderRow(slot) }
    }

    private fun addReminderRow(slot: ReminderSlot) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 16)
        }

        // Parte izquierda: título + hora (clickable para cambiar hora)
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.attr.selectableItemBackground.let {
                val typedValue = android.util.TypedValue()
                theme.resolveAttribute(it, typedValue, true)
                typedValue.resourceId
            })
        }

        val titleView = TextView(this).apply {
            text = getString(slot.titleRes)
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val timeView = TextView(this).apply {
            text = slot.timeGetter()
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_secondary))
            setPadding(0, 4, 0, 0)
        }

        textContainer.addView(titleView)
        textContainer.addView(timeView)

        // Click en la parte de texto abre el selector de hora
        textContainer.setOnClickListener {
            showTimePicker(slot.timeGetter(), slot.minHour, slot.maxHour) { newTime ->
                slot.timeSetter(newTime)
                timeView.text = newTime
                if (settingsManager.areRemindersEnabled) scheduleReminders()
                syncSettings()
            }
        }

        // Botón eliminar (×)
        val deleteButton = TextView(this).apply {
            text = "×"
            textSize = 22f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_secondary))
            setPadding(24, 0, 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                slot.enabledSetter(false)
                if (settingsManager.areRemindersEnabled) scheduleReminders()
                syncSettings()
                rebuildRemindersUI()
            }
        }

        row.addView(textContainer)
        row.addView(deleteButton)

        // Divisor encima si ya hay filas
        if (remindersContainer.childCount > 0) {
            val divider = android.view.View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.setMargins(0, 4, 0, 4) }
                setBackgroundColor(ContextCompat.getColor(this@SettingsActivity, R.color.border_color))
            }
            remindersContainer.addView(divider)
        }

        remindersContainer.addView(row)
    }

    private fun showAddReminderDialog() {
        val available = allSlots().filter { !it.enabledGetter() }
        if (available.isEmpty()) {
            Toast.makeText(this, getString(R.string.all_reminders_added), Toast.LENGTH_SHORT).show()
            return
        }

        val titles = available.map {
            "${getString(it.titleRes)}  ·  ${getString(it.rangeRes)}"
        }.toTypedArray()

        val dialog = AlertDialog.Builder(this, R.style.PaceAlertDialog)
            .setTitle(getString(R.string.choose_reminder_slot))
            .setItems(titles) { _, which ->
                val slot = available[which]
                slot.enabledSetter(true)
                showTimePicker(slot.timeGetter(), slot.minHour, slot.maxHour) { newTime ->
                    slot.timeSetter(newTime)
                    if (settingsManager.areRemindersEnabled) scheduleReminders()
                    syncSettings()
                    rebuildRemindersUI()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
    }
}