package com.novikon.pace.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import com.novikon.pace.models.CalendarDay
import com.novikon.pace.models.DayStatus

// Adapter para el calendario del historial de hábitos.
// Muestra un grid de 42 celdas (6 semanas × 7 días) donde cada celda
// representa un día con un color según su estado de completitud.
//
// Estados posibles de cada día:
//   - COMPLETED: todos los hábitos completados → círculo negro
//   - INCOMPLETE: algún hábito sin completar → círculo gris
//   - REST_DAY: día de descanso configurado → sin círculo
//   - NO_DATA: día futuro o antes de instalar la app → sin círculo
class CalendarAdapter(
    private var days: List<CalendarDay>,
    private val onDayClick: (CalendarDay) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.DayViewHolder>() {

    inner class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayContainer: View = view.findViewById(R.id.dayContainer)
        val statusCircle: View = view.findViewById(R.id.statusCircle)
        val dayNumber: TextView = view.findViewById(R.id.dayNumber)
        val todayCircle: View = view.findViewById(R.id.todayCircle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.calendar_day_item, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = days[position]

        holder.dayNumber.text = if (day.dayOfMonth > 0) day.dayOfMonth.toString() else ""

        // Resetear el estado visual antes de aplicar el nuevo —
        // importante porque RecyclerView reutiliza las vistas
        holder.statusCircle.visibility = View.GONE
        holder.todayCircle.visibility = View.GONE
        holder.statusCircle.setBackgroundResource(0)

        when {
            // Días de otros meses — se muestran atenuados y no son clicables
            !day.isCurrentMonth -> {
                holder.dayNumber.alpha = 0.3f
                holder.dayNumber.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.text_tertiary)
                )
                holder.dayContainer.isClickable = false
            }

            // Días del mes actual — aplicar estilo según estado
            else -> {
                holder.dayNumber.alpha = 1.0f

                when (day.completionStatus) {
                    // Todos los hábitos completados → círculo negro con texto blanco
                    DayStatus.COMPLETED -> {
                        holder.statusCircle.visibility = View.VISIBLE
                        holder.statusCircle.setBackgroundResource(R.drawable.calendar_day_completed)
                        holder.dayNumber.setTextColor(Color.WHITE)
                    }
                    // Algún hábito sin completar → círculo gris con texto blanco
                    DayStatus.INCOMPLETE -> {
                        holder.statusCircle.visibility = View.VISIBLE
                        holder.statusCircle.setBackgroundResource(R.drawable.calendar_day_incomplete)
                        holder.dayNumber.setTextColor(Color.WHITE)
                    }
                    // Día de descanso o sin datos → sin círculo, texto normal
                    else -> {
                        holder.dayNumber.setTextColor(
                            ContextCompat.getColor(holder.itemView.context, R.color.text_primary)
                        )
                    }
                }

                // Si es hoy, mostrar el borde encima del círculo de estado
                if (day.isToday) {
                    holder.todayCircle.visibility = View.VISIBLE
                }

                // Solo son clicables los días que tienen datos
                holder.dayContainer.isClickable = day.completionStatus != DayStatus.NO_DATA
            }
        }

        // Solo se puede pulsar un día del mes actual con datos
        holder.dayContainer.setOnClickListener {
            if (day.isCurrentMonth && day.completionStatus != DayStatus.NO_DATA) {
                onDayClick(day)
            }
        }
    }

    override fun getItemCount(): Int = days.size

    // Actualiza los días del calendario — se llama cuando el usuario
    // navega al mes anterior o siguiente.
    fun updateDays(newDays: List<CalendarDay>) {
        days = newDays
        notifyDataSetChanged()
    }
}