package com.novikon.pace.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.novikon.pace.R
import com.novikon.pace.models.Habit
import com.novikon.pace.models.TimeOfDay

// Adapter para la pantalla de hábitos del día.
// Muestra los hábitos agrupados por franja horaria con cabeceras
// de sección (Mañana, Tarde, Noche, T*do el día).
//
// Recibe el Context para acceder a strings.xml con el idioma correcto.
//
// Usa dos tipos de ViewHolder:
//   - HeaderViewHolder: para las cabeceras de sección
//   - HabitViewHolder: para cada hábito
class DailyHabitAdapter(
    private val context: Context,
    private var habits: List<Habit>,
    private val habitStatus: Map<String, Boolean>,  // id del hábito → true si está hecho
    private val onHabitStatusChanged: (String, Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_HABIT = 1
    }

    // Clase interna que representa cada elemento de la lista —
    // puede ser una cabecera o un hábito.
    private data class ListItem(
        val type: Int,
        val header: String? = null,
        val habit: Habit? = null
    )

    // Lista procesada con cabeceras y hábitos intercalados
    private var items: List<ListItem> = emptyList()

    init {
        organizeHabits()
    }

    // Organiza los hábitos en secciones según su franja horaria.
    // Usa el campo timeOfDay de Habit y los strings traducidos
    // para los títulos de cada sección.
    private fun organizeHabits() {
        val itemsList = mutableListOf<ListItem>()

        val morningHabits = habits.filter { it.timeOfDay == TimeOfDay.MORNING }
        val afternoonHabits = habits.filter { it.timeOfDay == TimeOfDay.AFTERNOON }
        val eveningHabits = habits.filter { it.timeOfDay == TimeOfDay.EVENING }
        val allDayHabits = habits.filter { it.timeOfDay == TimeOfDay.ALL_DAY }

        // Añadir cada sección solo si tiene hábitos
        if (morningHabits.isNotEmpty()) {
            itemsList.add(ListItem(VIEW_TYPE_HEADER, context.getString(R.string.time_morning)))
            morningHabits.forEach { itemsList.add(ListItem(VIEW_TYPE_HABIT, habit = it)) }
        }

        if (afternoonHabits.isNotEmpty()) {
            itemsList.add(ListItem(VIEW_TYPE_HEADER, context.getString(R.string.time_afternoon)))
            afternoonHabits.forEach { itemsList.add(ListItem(VIEW_TYPE_HABIT, habit = it)) }
        }

        if (eveningHabits.isNotEmpty()) {
            itemsList.add(ListItem(VIEW_TYPE_HEADER, context.getString(R.string.time_evening)))
            eveningHabits.forEach { itemsList.add(ListItem(VIEW_TYPE_HABIT, habit = it)) }
        }

        if (allDayHabits.isNotEmpty()) {
            itemsList.add(ListItem(VIEW_TYPE_HEADER, context.getString(R.string.time_all_day)))
            allDayHabits.forEach { itemsList.add(ListItem(VIEW_TYPE_HABIT, habit = it)) }
        }

        items = itemsList
    }
    override fun getItemViewType(position: Int): Int {
        return items[position].type
    }

// onCreateViewHolder: infla el layout de cada item y crea su ViewHolder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.time_section_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.habit_item, parent, false)
                HabitViewHolder(view)
            }
        }
    }

// onBindViewHolder: vincula los datos del elemento actual con su vista.
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]

        when (holder) {
            is HeaderViewHolder -> {
                holder.headerText.text = item.header
            }
            is HabitViewHolder -> {
                item.habit?.let { habit ->
                    holder.bind(habit, habitStatus[habit.id] ?: false)
                }
            }
        }
    }

    // getItemCount: devuelve la cantidad total de elementos a renderizar.
    override fun getItemCount(): Int = items.size

    // ViewHolder para las cabeceras de sección (Mañana, Tarde...)
    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerText: TextView = view.findViewById(R.id.timeHeaderText)
    }

    // ViewHolder para cada hábito con sus botones de hecho/no hecho
    inner class HabitViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val habitEmoji: TextView = view.findViewById(R.id.habitEmoji)
        private val habitName: TextView = view.findViewById(R.id.habitName)
        private val habitTime: TextView = view.findViewById(R.id.habitTime)
        private val notDoneButton: MaterialButton = view.findViewById(R.id.notDoneButton)
        private val doneButton: MaterialButton = view.findViewById(R.id.doneButton)
        fun bind(habit: Habit, isDone: Boolean) {
            habitEmoji.text = habit.emoji
            habitName.text = habit.name
            habitTime.text = habit.duration

            updateButtonStates(isDone)

            notDoneButton.setOnClickListener {
                updateButtonStates(false)
                onHabitStatusChanged(habit.id, false)
            }

            doneButton.setOnClickListener {
                updateButtonStates(true)
                onHabitStatusChanged(habit.id, true)
            }
        }

        // Resalta el botón activo y oscurece el inactivo
        private fun updateButtonStates(isDone: Boolean) {
            doneButton.alpha = if (isDone) 1.0f else 0.5f
            notDoneButton.alpha = if (isDone) 0.5f else 1.0f
        }
    }

}