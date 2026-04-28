package com.novikon.pace.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import com.novikon.pace.models.Habit

// Adapter de detalle diario: pinta la lista de habitos para un dia concreto.
class DayDetailAdapter(
    private var habits: List<Habit>,
    private val habitStatus: Map<String, Boolean>  // id del hábito -> true si está hecho
) : RecyclerView.Adapter<DayDetailAdapter.HabitViewHolder>() {

    inner class HabitViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.rootHabitItem) // NUEVO (id en XML)
        val habitEmoji: TextView = view.findViewById(R.id.habitEmoji)
        val habitName: TextView = view.findViewById(R.id.habitName)
        val habitDuration: TextView = view.findViewById(R.id.habitDuration)
        val habitStatus: TextView = view.findViewById(R.id.habitStatus)
    }

    // onCreateViewHolder: infla el layout de cada item y crea su ViewHolder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.dialog_habit_item, parent, false)
        return HabitViewHolder(view)
    }

    // onBindViewHolder: vincula los datos del elemento actual con su vista.
    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        val habit = habits[position]
        val isDone = habitStatus[habit.id] ?: false
        val context = holder.itemView.context

        holder.habitEmoji.text = habit.emoji
        holder.habitName.text = habit.name
        holder.habitDuration.text = habit.duration

        // Evento unido -> fondo beige
        val isEventHabit = habit.id.startsWith("event_join_")
        if (isEventHabit) {
            holder.root.setBackgroundColor(ContextCompat.getColor(context, R.color.event_habit_beige))
        } else {
            holder.root.setBackgroundColor(Color.TRANSPARENT)
        }

        if (isDone) {
            holder.habitStatus.text = "✓"
            holder.habitStatus.setTextColor(
                ContextCompat.getColor(context, R.color.accent_primary)
            )
        } else {
            holder.habitStatus.text = "✗"
            holder.habitStatus.setTextColor(
                ContextCompat.getColor(context, R.color.text_tertiary)
            )
        }
    }

    // getItemCount: devuelve la cantidad total de elementos a renderizar.
    override fun getItemCount(): Int = habits.size
}