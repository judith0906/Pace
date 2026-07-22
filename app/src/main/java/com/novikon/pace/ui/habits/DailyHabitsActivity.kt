package com.novikon.pace.ui.habits

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import com.novikon.pace.adapters.DailyHabitAdapter
import com.novikon.pace.billing.AdManager
import com.novikon.pace.data.AchievementDefinitions
import com.novikon.pace.data.AchievementsManager
import com.novikon.pace.data.CirclesRealtimeManager
import com.novikon.pace.data.HabitsManager
import com.novikon.pace.data.MonthlyAchievementsAnalyzer
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.models.DailyHabitLog
import com.novikon.pace.models.Habit
import com.novikon.pace.repositories.HabitsRepository
import com.novikon.pace.ui.achievements.AchievementUnlockActivity
import com.novikon.pace.utils.PickyEvent
import com.novikon.pace.utils.PickyManager
import com.novikon.pace.utils.SettingsManager
import com.novikon.pace.utils.applySystemBarInsets
import com.novikon.pace.data.SubscriptionManager
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Pantalla de habitos diarios: permite marcar progreso y registrar completados.
class DailyHabitsActivity : AppCompatActivity() {

    private lateinit var habitsManager: HabitsManager
    private lateinit var achievementsManager: AchievementsManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var adManager: AdManager
    private lateinit var prefs: SharedPreferences

    private lateinit var backButton: ImageButton
    private lateinit var editHabitsButton: ImageButton
    private lateinit var calendarButton: ImageButton
    private lateinit var currentDateText: TextView
    private lateinit var restDayCard: CardView
    private lateinit var progressCard: CardView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var pickyCard: CardView
    private lateinit var pickyImage: ImageView
    private lateinit var pickyMessage: TextView
    private lateinit var habitsRecyclerView: RecyclerView

    private lateinit var habitAdapter: DailyHabitAdapter
    private var selectedHabits = listOf<Habit>()

    // Map con el estado de cada hábito hoy — id → true si está hecho
    private val habitStatus = mutableMapOf<String, Boolean>()
    private var currentDate = ""

    // Controla si onCreate ya ejecutó la carga inicial de hábitos.
    // Evita que onResume vuelva a cargar nada justo después de onCreate,
    // ya que onResume siempre se ejecuta tras él y causaría que el adapter
    // se recreara con datos incompletos, haciendo que falten hábitos.
    private var initialLoadDone = false

// onCreate: inicializa la pantalla y prepara vistas/eventos iniciales.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_daily_habits)
        applySystemBarInsets()

        habitsManager = HabitsManager(this)
        achievementsManager = AchievementsManager(this)
        settingsManager = SettingsManager(this)
        adManager = AdManager(this)
        prefs = getSharedPreferences("pace_ads", MODE_PRIVATE)

        initializeViews()
        setupCurrentDate()
        setupListeners()
        checkIfRestDay()

        lifecycleScope.launch {
            loadHabits()
            // Marcamos que la carga inicial terminó para que onResume
            // no vuelva a ejecutarla en este mismo ciclo de vida
            withContext(Dispatchers.Main) {
                initialLoadDone = true
            }
        }

        adManager.initialize()
        adManager.loadInterstitial()
    }
    private fun initializeViews() {
        backButton = findViewById(R.id.backButton)
        editHabitsButton = findViewById(R.id.editHabitsButton)
        calendarButton = findViewById(R.id.calendarButton)
        currentDateText = findViewById(R.id.currentDateText)
        restDayCard = findViewById(R.id.restDayCard)
        progressCard = findViewById(R.id.progressCard)
        progressText = findViewById(R.id.progressText)
        progressBar = findViewById(R.id.progressBar)
        pickyCard = findViewById(R.id.pickyCard)
        pickyImage = findViewById(R.id.pickyImage)
        pickyMessage = findViewById(R.id.pickyMessage)
        habitsRecyclerView = findViewById(R.id.habitsRecyclerView)

        habitsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    // Formatea la fecha actual para mostrarla en pantalla
    // y guarda el formato de base de datos para los logs.
    private fun setupCurrentDate() {
        val calendar = Calendar.getInstance()
        val displayPattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE d MMMM")
        val displayFormat = SimpleDateFormat(displayPattern, Locale.getDefault())
        currentDateText.text = displayFormat.format(calendar.time)

        val dbFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        currentDate = dbFormat.format(calendar.time)
    }

    // Carga los hábitos desde Firebase (o caché si no hay internet),
    // carga el estado de cada uno para hoy y configura el adapter.
    private suspend fun loadHabits() {
        selectedHabits = habitsManager.getSelectedHabitsAsync()

        val predefinedHabits = HabitsRepository.getAllPredefinedHabits(this)
        val predefinedMap = predefinedHabits.associateBy { it.id }

        selectedHabits = selectedHabits.map { habit ->
            if (!habit.isCustom) {
                predefinedMap[habit.id]?.copy(
                    timeOfDay = habit.timeOfDay,
                    duration = habit.duration,
                    color = habit.color
                ) ?: habit
            } else {
                habit
            }
        }

        if (selectedHabits.isEmpty()) {
            // Si no hay hábitos configurados, ir a la pantalla de selección
            startActivity(Intent(this, HabitSelectionActivity::class.java))
            finish()
            return
        }

        // Inicializar los logs del día solo si NO es día de descanso —
        val todayIndex = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0; Calendar.TUESDAY -> 1; Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3; Calendar.FRIDAY -> 4; Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6; else -> 0
        }
        if (settingsManager.activeDayIndices.contains(todayIndex)) {
            habitsManager.syncFromFirebase()
            // Solo añadir logs para hábitos nuevos, sin borrar el progreso existente
            val existingLogs = habitsManager.getHabitLogsForDate(currentDate)
            val existingIds = existingLogs.map { it.habitId }.toSet()
            selectedHabits.filter { it.id !in existingIds }.forEach { habit ->
                val log = DailyHabitLog(
                    habitId = habit.id,
                    date = currentDate,
                    isDone = false,
                    timestamp = System.currentTimeMillis(),
                    source = "MANUAL",
                    eventId = "",
                    habitName = habit.name,
                    habitEmoji = habit.emoji,
                    habitDuration = habit.duration,
                    isEventHabit = false
                )
                habitsManager.logHabit(log)
            }
        }

        // Cargar el estado de los hábitos para hoy desde el caché local
        val logs = habitsManager.getHabitLogsForDate(currentDate)
        logs.forEach { log ->
            habitStatus[log.habitId] = log.isDone
        }

        withContext(Dispatchers.Main) {
            // Crear el adapter con la lista completa de hábitos —
            // organizeHabits() los agrupará internamente por franja horaria
            habitAdapter = DailyHabitAdapter(
                context = this@DailyHabitsActivity,
                habits = selectedHabits,
                habitStatus = habitStatus,
                onHabitStatusChanged = { habitId, isDone ->
                    lifecycleScope.launch {
                        habitsManager.logHabit(habitId, currentDate, isDone)
                        checkAndCelebrateAchievements()
                    }
                    habitStatus[habitId] = isDone
                    updateProgress()
                    if (isDone) {
                        val completed = habitStatus.values.count { it }
                        if (completed >= selectedHabits.size) {
                            showPicky(PickyEvent.ALL_COMPLETED)
                            showInterstitialIfAllowed()
                        } else {
                            showPicky(PickyEvent.HABIT_COMPLETED)
                        }
                    }
                },
                onEditHabit = { habit -> showHabitCustomizationSheet(habit) }
            )

            habitsRecyclerView.adapter = habitAdapter
            updateProgress()
            if (restDayCard.visibility != View.VISIBLE) {
                showPicky(PickyEvent.APP_OPEN, selectedHabits.size)
            }
        }
    }
    private fun showPicky(event: PickyEvent, habitCount: Int = 0) {
        val state = PickyManager.getPickyState(event)
        pickyImage.setImageResource(state.imageRes)

        val message = if (event == PickyEvent.APP_OPEN && habitCount > 0) {
            getString(state.messageRes, habitCount)
        } else {
            getString(state.messageRes)
        }
        pickyMessage.text = message

        if (pickyCard.visibility != View.VISIBLE) {
            pickyCard.visibility = View.VISIBLE
            pickyCard.scaleX = 0f
            pickyCard.scaleY = 0f
            ObjectAnimator.ofFloat(pickyCard, "scaleX", 0f, 1.05f, 1f).apply {
                duration = 400L
                interpolator = DecelerateInterpolator()
                start()
            }
            ObjectAnimator.ofFloat(pickyCard, "scaleY", 0f, 1.05f, 1f).apply {
                duration = 400L
                interpolator = DecelerateInterpolator()
                start()
            }
        }
    }

    private suspend fun checkAndCelebrateAchievements() {
        val yearMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1

        val habits = habitsManager.getSelectedHabitsFromCache()
        val logs = habitsManager.getHabitLogs()

        val cachedAchs = achievementsManager.loadAchievements(yearMonth)
        val analyzer = MonthlyAchievementsAnalyzer()
        val input = MonthlyAchievementsAnalyzer.AnalysisInput(
            logs = logs,
            habits = habits,
            eventsCreated = 0,
            supportMessagesSent = 0
        )
        val result = analyzer.analyze(input, currentMonth)

        val merged = result.achievements.map { calc ->
            val cached = cachedAchs.find { it.id == calc.id }
            if (cached != null && cached.completedAt > 0L && calc.completed) {
                calc.copy(completedAt = cached.completedAt)
            } else {
                calc
            }
        }

        val newlyCompleted = merged.filter { it.completed }
            .filter { calc ->
                val cached = cachedAchs.find { it.id == calc.id }
                cached == null || !cached.completed
            }

        if (newlyCompleted.isNotEmpty()) {
            achievementsManager.saveAchievements(yearMonth, merged)

            val lastNotified = achievementsManager.getLastNotifiedAchievements()
            val newIds = newlyCompleted.map { it.id }.toSet()
            achievementsManager.markNotified(lastNotified + newIds)

            val circlesManager = CirclesRealtimeManager(this@DailyHabitsActivity)
            val first = newlyCompleted.first()
            val def = AchievementDefinitions.getDefinitionById(first.id)
            val name = if (def != null) getString(def.nameRes) else first.id
            val emoji = def?.emoji ?: "\uD83C\uDFC6"

            withContext(Dispatchers.IO) {
                circlesManager.sendAchievementNotificationToAllCircles(
                    achievementName = name,
                    context = this@DailyHabitsActivity
                )
            }

            withContext(Dispatchers.Main) {
                val intent = Intent(this@DailyHabitsActivity, AchievementUnlockActivity::class.java).apply {
                    putExtra(AchievementUnlockActivity.EXTRA_ACHIEVEMENT_NAME, name)
                    putExtra(AchievementUnlockActivity.EXTRA_ACHIEVEMENT_EMOJI, emoji)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun showHabitCustomizationSheet(habit: Habit) {
        val sheet = HabitCustomizationSheet(habit) { updatedHabit ->
            val index = selectedHabits.indexOfFirst { it.id == habit.id }
            if (index != -1) {
                selectedHabits = selectedHabits.toMutableList().also {
                    it[index] = updatedHabit
                }
                lifecycleScope.launch {
                    habitsManager.saveSelectedHabits(selectedHabits)
                    withContext(Dispatchers.Main) {
                        habitAdapter.updateHabits(selectedHabits)
                    }
                }
            }
        }
        sheet.show(supportFragmentManager, "HabitCustomizationSheet")
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }

        editHabitsButton.setOnClickListener {
            startActivity(Intent(this, HabitSelectionActivity::class.java))
        }

        calendarButton.setOnClickListener {
            startActivity(Intent(this, HabitHistoryActivity::class.java))
        }
    }

    // Comprueba si hoy es día de descanso según la configuración del usuario.
    // Si lo es, muestra la tarjeta de descanso y oculta la lista de hábitos.
    private fun checkIfRestDay() {
        val calendar = Calendar.getInstance()

        val dayIndex = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }

        if (!settingsManager.activeDayIndices.contains(dayIndex)) {
            restDayCard.visibility = View.VISIBLE
            progressCard.visibility = View.GONE
            habitsRecyclerView.visibility = View.GONE
        } else {
            restDayCard.visibility = View.GONE
            progressCard.visibility = View.VISIBLE
            habitsRecyclerView.visibility = View.VISIBLE
        }
    }

    // Actualiza la barra de progreso y el texto con los hábitos completados.
    private fun updateProgress() {
        val totalHabits = selectedHabits.size
        val completedHabits = habitStatus.values.count { it }

        progressText.text = "$completedHabits/$totalHabits"
        progressBar.progress = if (totalHabits > 0) {
            (completedHabits * 100) / totalHabits
        } else {
            0
        }
    }

    // ── ANUNCIOS INTERSTITIAL ────────────────────────────────────────────────

    // Máximo 3 interstitial por hora.
    private fun canShowInterstitial(): Boolean {
        val now = System.currentTimeMillis()
        val oneHour = 60 * 60 * 1000L
        val timestamps = prefs.getStringSet("interstitial_timestamps", mutableSetOf())?.toMutableSet()
            ?: mutableSetOf()

        // Limpiar timestamps anteriores a 1 hora
        val recent = timestamps.filter { now - it.toLong() < oneHour }.toMutableSet()
        if (recent.size >= 3) return false

        prefs.edit().putStringSet("interstitial_timestamps", recent).apply()
        return true
    }

    private fun recordInterstitialShown() {
        val now = System.currentTimeMillis().toString()
        val timestamps = prefs.getStringSet("interstitial_timestamps", mutableSetOf())?.toMutableSet()
            ?: mutableSetOf()
        timestamps.add(now)
        prefs.edit().putStringSet("interstitial_timestamps", timestamps).apply()
    }

    private fun showInterstitialIfAllowed() {
        if (!canShowInterstitial()) return
        recordInterstitialShown()
        adManager.showInterstitial(this)
        adManager.loadInterstitial()
    }

    // onResume se ejecuta siempre después de onCreate y también al volver
    // de editar hábitos. Solo recargamos si la carga inicial ya terminó —
    // si no, el propio onCreate ya está gestionando la primera carga.
    override fun onResume() {
        super.onResume()
        if (initialLoadDone) {
            lifecycleScope.launch { loadHabits() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adManager.cleanup()
    }
}