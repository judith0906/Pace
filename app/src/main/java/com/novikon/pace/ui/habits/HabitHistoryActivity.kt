package com.novikon.pace.ui.habits

import android.os.Bundle
import android.text.format.DateFormat
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import com.novikon.pace.adapters.CalendarAdapter
import com.novikon.pace.adapters.DayDetailAdapter
import com.novikon.pace.data.HabitsManager
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.models.CalendarDay
import com.novikon.pace.models.DayStatus
import com.novikon.pace.models.Habit
import com.novikon.pace.utils.SettingsManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HabitHistoryActivity : AppCompatActivity() {

    private lateinit var habitsManager: HabitsManager
    private lateinit var settingsManager: SettingsManager

    private lateinit var backButton: ImageButton
    private lateinit var previousMonthButton: ImageButton
    private lateinit var nextMonthButton: ImageButton
    private lateinit var currentMonthText: TextView
    private lateinit var calendarRecyclerView: RecyclerView

    private lateinit var calendarAdapter: CalendarAdapter

    //TODO: Hay que hacer que coja los habitos nuevamente desde Firebase siempre que se reinstale la app porque sino no se ven los anteriores

    // Calendario que representa el mes que se está mostrando actualmente.
    // Se modifica cuando el usuario navega entre meses.
    private var currentCalendar = Calendar.getInstance()

    // Hábitos cacheados para no tener que pedirlos a Firebase
    // cada vez que se genera el calendario o se abre el detalle de un día.
    private var cachedHabits: List<Habit> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_habit_history)

        habitsManager = HabitsManager(this)
        settingsManager = SettingsManager(this)

        // Guardar la fecha de primera instalación si no existe todavía —
        // se usa para no marcar como incompletos los días anteriores a la app
        if (settingsManager.firstInstallDate.isEmpty()) {
            settingsManager.firstInstallDate =
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }

        initializeViews()
        setupCalendar()
        setupListeners()

        // Cargar hábitos y luego generar el calendario del mes actual
        lifecycleScope.launch {
            cachedHabits = habitsManager.getSelectedHabitsAsync()
            loadMonth()
        }
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.backButton)
        previousMonthButton = findViewById(R.id.previousMonthButton)
        nextMonthButton = findViewById(R.id.nextMonthButton)
        currentMonthText = findViewById(R.id.currentMonthText)
        calendarRecyclerView = findViewById(R.id.calendarRecyclerView)
    }

    // Configura el RecyclerView con un GridLayoutManager de 7 columnas
    // (una por cada día de la semana).
    private fun setupCalendar() {
        calendarRecyclerView.layoutManager = GridLayoutManager(this, 7)
        calendarAdapter = CalendarAdapter(emptyList()) { day ->
            showDayDetailDialog(day)
        }
        calendarRecyclerView.adapter = calendarAdapter
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }

        // Navegar al mes anterior y regenerar el calendario
        previousMonthButton.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            loadMonth()
        }

        // Navegar al mes siguiente y regenerar el calendario
        nextMonthButton.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            loadMonth()
        }
    }

    // Actualiza el título del mes y regenera los días del calendario.
    private fun loadMonth() {
        // Capitalizar la primera letra del nombre del mes
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        currentMonthText.text = monthFormat.format(currentCalendar.time)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        calendarAdapter.updateDays(generateCalendarDays())
    }

    // Genera los 42 días (6 semanas × 7 días) del calendario.
    // Incluye días del mes anterior y siguiente para completar las semanas.
    private fun generateCalendarDays(): List<CalendarDay> {
        val days = mutableListOf<CalendarDay>()
        val calendar = currentCalendar.clone() as Calendar

        // Ir al primer día del mes
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        // Retroceder hasta el lunes de la primera semana
        val daysToGoBack = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2
        calendar.add(Calendar.DAY_OF_MONTH, -daysToGoBack)

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val firstInstallDate = settingsManager.firstInstallDate
        val currentMonth = currentCalendar.get(Calendar.MONTH)
        val currentYear = currentCalendar.get(Calendar.YEAR)

        for (i in 0 until 42) {
            val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            val isCurrentMonth = calendar.get(Calendar.MONTH) == currentMonth &&
                    calendar.get(Calendar.YEAR) == currentYear

            days.add(
                CalendarDay(
                    dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
                    date = dateString,
                    isCurrentMonth = isCurrentMonth,
                    isToday = dateString == today,
                    isRestDay = getRestDayForDate(calendar),
                    completionStatus = calculateDayStatus(
                        date = dateString,
                        isRestDay = getRestDayForDate(calendar),
                        isBeforeInstall = dateString < firstInstallDate
                    )
                )
            )

            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        return days
    }

    // Determina si un día concreto era día de descanso,
    // consultando la configuración de esa semana específica.
    // Si no hay configuración guardada para esa semana,
    // usa la configuración actual como referencia.
    private fun getRestDayForDate(calendar: Calendar): Boolean {
        // Calcular el lunes de esa semana para buscar su configuración
        val weekCalendar = calendar.clone() as Calendar
        val dayOfWeek = weekCalendar.get(Calendar.DAY_OF_WEEK)
        val daysToMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - 2
        weekCalendar.add(Calendar.DAY_OF_MONTH, -daysToMonday)
        val weekStartDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(weekCalendar.time)

        // Usar la configuración de esa semana, o la actual si no existe
        val activeDays = settingsManager.getWeeklyActiveDays(weekStartDate)
            ?: settingsManager.activeDayIndices

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

        return !activeDays.contains(dayIndex)
    }

    // Calcula el estado de un día para determinar el color del círculo.
    private fun calculateDayStatus(
        date: String,
        isRestDay: Boolean,
        isBeforeInstall: Boolean
    ): DayStatus {
        // Días anteriores a la instalación → sin datos
        if (isBeforeInstall) return DayStatus.NO_DATA

        // Días de descanso → estado especial
        if (isRestDay) return DayStatus.REST_DAY

        // Días futuros → sin datos
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (date > today) return DayStatus.NO_DATA

        // Sin hábitos configurados → sin datos
        if (cachedHabits.isEmpty()) return DayStatus.NO_DATA

        val logs = habitsManager.getHabitLogsForDate(date)

        // Sin registros pero había hábitos → incompleto
        if (logs.isEmpty()) return DayStatus.INCOMPLETE

        // Comparar hábitos completados con el total
        return if (logs.count { it.isDone } == cachedHabits.size) {
            DayStatus.COMPLETED
        } else {
            DayStatus.INCOMPLETE
        }
    }

    // Muestra el diálogo con el detalle de los hábitos de un día concreto.
    private fun showDayDetailDialog(day: CalendarDay) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_day_detail, null)

        val dialog = AlertDialog.Builder(this, R.style.BlurredDialogTheme)
            .setView(dialogView)
            .create()

        val closeButton = dialogView.findViewById<ImageButton>(R.id.closeButton)
        val dateText = dialogView.findViewById<TextView>(R.id.dialogDateText)
        val progressText = dialogView.findViewById<TextView>(R.id.dialogProgressText)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.dialogProgressBar)
        val habitsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.dialogHabitsRecyclerView)

        // Formatear la fecha para mostrarla de forma legible
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayPattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE d MMMM")
        val displayFormat = SimpleDateFormat(displayPattern, Locale.getDefault())
        val date = dateFormat.parse(day.date)
        dateText.text = date?.let { displayFormat.format(it) } ?: day.date

        // Calcular el progreso del día
        val logs = habitsManager.getHabitLogsForDate(day.date)
        val habitStatusMap = logs.associate { it.habitId to it.isDone }
        val completedHabits = logs.count { it.isDone }
        val totalHabits = cachedHabits.size

        progressText.text = "$completedHabits/$totalHabits"
        progressBar.progress = if (totalHabits > 0) (completedHabits * 100) / totalHabits else 0

        habitsRecyclerView.layoutManager = LinearLayoutManager(this)
        habitsRecyclerView.adapter = DayDetailAdapter(cachedHabits, habitStatusMap)

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}