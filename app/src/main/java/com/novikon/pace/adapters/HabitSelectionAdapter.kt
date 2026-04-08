package com.novikon.pace.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import com.novikon.pace.models.Habit
import com.novikon.pace.models.TimeOfDay

// Adapter para la pantalla de selección de hábitos.
// Muestra cada hábito con su emoji, nombre y duración,
// y permite seleccionarlo/deseleccionarlo con un tap.
//
// El callback onHabitToggled devuelve el Habit completo y el nuevo estado —
// HabitSelectionActivity decide si mostrar el dialog de franja horaria
// basándose en el timeOfDay del hábito recibido.
class HabitSelectionAdapter(
    private var habits: List<Habit>,
    private val selectedHabitIds: MutableSet<String>,
    private val onHabitToggled: (habit: Habit, isSelected: Boolean) -> Unit
) : RecyclerView.Adapter<HabitSelectionAdapter.HabitViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_habit_selectable, parent, false)
        return HabitViewHolder(view)
    }

    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        holder.bind(habits[position])
    }

    override fun getItemCount(): Int = habits.size

    // Reemplaza la lista de hábitos y refresca el RecyclerView.
    // Se usa al añadir hábitos personalizados desde el dialog.
    fun updateHabits(newHabits: List<Habit>) {
        habits = newHabits
        notifyDataSetChanged()
    }

    inner class HabitViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val habitEmoji: TextView = view.findViewById(R.id.habitEmoji)
        private val habitName: TextView = view.findViewById(R.id.habitName)
        private val habitDuration: TextView = view.findViewById(R.id.habitDuration)
        private val habitCheckbox: android.widget.CheckBox = view.findViewById(R.id.habitCheckbox)

        fun bind(habit: Habit) {
            habitEmoji.text = habit.emoji
            habitName.text = habit.name
            habitDuration.text = habit.duration

            updateSelectionState(selectedHabitIds.contains(habit.id))

            itemView.setOnClickListener {
                val isNowSelected = !selectedHabitIds.contains(habit.id)

                if (isNowSelected) {
                    selectedHabitIds.add(habit.id)
                } else {
                    selectedHabitIds.remove(habit.id)
                }

                updateSelectionState(isNowSelected)
                // Devolvemos el Habit completo para que la Activity
                // pueda comprobar si necesita mostrar el dialog de franja horaria
                onHabitToggled(habit, isNowSelected)
            }
        }

        private fun updateSelectionState(isSelected: Boolean) {
            // Actualizamos el checkbox visualmente — clickable está en false
            // en el XML para que el click lo gestione el itemView completo
            habitCheckbox.isChecked = isSelected
            itemView.alpha = if (isSelected) 1.0f else 0.7f
        }
    }
}