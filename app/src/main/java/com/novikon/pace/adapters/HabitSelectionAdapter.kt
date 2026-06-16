package com.novikon.pace.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import com.novikon.pace.models.Habit

// Adapter para la pantalla de selección de hábitos.
// Muestra cada hábito con su emoji, nombre y duración,
// y permite seleccionarlo/deseleccionarlo con un tap.
//
// onDelete es opcional — solo se pasa para hábitos personalizados,
// mostrando el botón de borrar únicamente en ese caso.
class HabitSelectionAdapter(
    private var habits: List<Habit>,
    private val selectedHabitIds: MutableSet<String>,
    private val onHabitToggled: (habit: Habit, isSelected: Boolean) -> Unit,
    private val onDelete: ((habit: Habit) -> Unit)? = null
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

    fun updateHabits(newHabits: List<Habit>) {
        habits = newHabits
        notifyDataSetChanged()
    }

    inner class HabitViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val habitEmoji: TextView = view.findViewById(R.id.habitEmoji)
        private val habitName: TextView = view.findViewById(R.id.habitName)
        private val habitDuration: TextView = view.findViewById(R.id.habitDuration)
        private val habitCheckbox: android.widget.CheckBox = view.findViewById(R.id.habitCheckbox)
        private val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)

        fun bind(habit: Habit) {
            habitEmoji.text = habit.emoji
            habitName.text = habit.name
            habitDuration.text = habit.duration

            updateSelectionState(selectedHabitIds.contains(habit.id))

            // Mostrar botón de borrar solo si el adapter tiene onDelete (hábitos custom)
            if (onDelete != null) {
                deleteButton.visibility = View.VISIBLE
                deleteButton.setOnClickListener {
                    onDelete.invoke(habit)
                }
            } else {
                deleteButton.visibility = View.GONE
            }

            itemView.setOnClickListener {
                val isNowSelected = !selectedHabitIds.contains(habit.id)
                if (isNowSelected) {
                    selectedHabitIds.add(habit.id)
                } else {
                    selectedHabitIds.remove(habit.id)
                }
                updateSelectionState(isNowSelected)
                onHabitToggled(habit, isNowSelected)
            }
        }

        private fun updateSelectionState(isSelected: Boolean) {
            habitCheckbox.isChecked = isSelected
            itemView.alpha = if (isSelected) 1.0f else 0.7f
        }
    }
}