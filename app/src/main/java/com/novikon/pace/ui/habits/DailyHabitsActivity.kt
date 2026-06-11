package com.novikon.pace.ui.habits

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import com.novikon.pace.adapters.DailyHabitAdapter
import com.novikon.pace.data.HabitsManager
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.models.Habit
import com.novikon.pace.utils.SettingsManager
import com.novikon.pace.utils.applySystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Pantalla de habitos diarios: permite marcar progreso y registrar completados.
class DailyHabitsActivity : AppCompatActivity() {

    private lateinit var habitsManager: HabitsManager
    private lateinit var settingsManager: SettingsManager

    private lateinit var backButton: ImageButton
    private lateinit var editHabitsButton: ImageButton
    private lateinit var calendarButton: ImageButton
    private lateinit var currentDateText: TextView
    private lateinit var restDayCard: CardView
    private lateinit var progressCard: CardView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
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
        settingsManager = SettingsManager(this)

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

        if (selectedHabits.isEmpty()) {
            // Si no hay hábitos configurados, ir a la pantalla de selección
            startActivity(Intent(this, HabitSelectionActivity::class.java))
            finish()
            return
        }

        // Inicializar los logs del día solo si NO es día de descanso —
        // los días de descanso no deben tener logs para que el historial los pinte correctamente
        val todayIndex = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0; Calendar.TUESDAY -> 1; Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3; Calendar.FRIDAY -> 4; Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6; else -> 0
        }
        if (settingsManager.activeDayIndices.contains(todayIndex)) {
            habitsManager.initializeDayLogsIfNeeded(currentDate)
        }

        // Cargar el estado de los hábitos para hoy desde el caché local
        val logs = habitsManager.getHabitLogsForDate(currentDate)
        logs.forEach { log ->
            habitStatus[log.habitId] = log.isDone
        }

        withContext(Dispatchers.Main) {
            // Crear el adapter con la lista completa de hábitos —
            // organizeHabits() los agrupará internamente por franja horaria
            habitAdapter = DailyHabitAdapter(this@DailyHabitsActivity, selectedHabits, habitStatus) { habitId, isDone ->
                // logHabit es suspend — necesita lifecycleScope
                lifecycleScope.launch {
                    habitsManager.logHabit(habitId, currentDate, isDone)
                }
                habitStatus[habitId] = isDone
                updateProgress()
            }

            habitsRecyclerView.adapter = habitAdapter
            updateProgress()
        }
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

    // onResume se ejecuta siempre después de onCreate y también al volver
    // de editar hábitos. Solo recargamos si la carga inicial ya terminó —
    // si no, el propio onCreate ya está gestionando la primera carga.
    override fun onResume() {
        super.onResume()
        if (initialLoadDone) {
            lifecycleScope.launch { loadHabits() }
        }
    }
}