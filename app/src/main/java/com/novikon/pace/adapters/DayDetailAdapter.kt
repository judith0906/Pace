package com.novikon.pace.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import com.novikon.pace.models.Habit

// Adapter para el diálogo de detalle de un día en el historial.
// Muestra la lista de hábitos de ese día con su estado —
// un tick si se completó o una cruz si no.
class DayDetailAdapter(
    private var habits: List<Habit>,
    private val habitStatus: Map<String, Boolean>  // id del hábito → true si está hecho
) : RecyclerView.Adapter<DayDetailAdapter.HabitViewHolder>() {

    inner class HabitViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val habitEmoji: TextView = view.findViewById(R.id.habitEmoji)
        val habitName: TextView = view.findViewById(R.id.habitName)
        val habitDuration: TextView = view.findViewById(R.id.habitDuration)
        val habitStatus: TextView = view.findViewById(R.id.habitStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.dialog_habit_item, parent, false)
        return HabitViewHolder(view)
    }

    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        val habit = habits[position]
        val isDone = habitStatus[habit.id] ?: false

        holder.habitEmoji.text = habit.emoji
        holder.habitName.text = habit.name
        holder.habitDuration.text = habit.duration

        // Mostrar tick o cruz según si el hábito se completó ese día
        if (isDone) {
            holder.habitStatus.text = "✓"
            holder.habitStatus.setTextColor(
                holder.itemView.context.getColor(R.color.accent_primary)
            )
        } else {
            holder.habitStatus.text = "✗"
            holder.habitStatus.setTextColor(
                holder.itemView.context.getColor(R.color.text_tertiary)
            )
        }
    }

    override fun getItemCount(): Int = habits.size
}