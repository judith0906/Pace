package com.novikon.pace.models

// Data class que representa un día en el calendario del historial.
// No es un dato del usuario — es un modelo de UI, creado
// específicamente para que CalendarAdapter sepa cómo pintar
// cada celda del calendario.
//
// Se genera en HabitHistoryActivity al construir el calendario
// y se pasa a CalendarAdapter para que lo muestre.
data class CalendarDay(
    val dayOfMonth: Int,            // número del día (1-31)
    val date: String,               // fecha en formato "yyyy-MM-dd" (ej: "2024-03-16")
    val isCurrentMonth: Boolean,    // false si el día es de un mes anterior o posterior
    // (el calendario muestra siempre 42 días para llenar 6 semanas)
    val isToday: Boolean,           // true si es el día de hoy — muestra un borde especial
    val isRestDay: Boolean,         // true si ese día no había hábitos activos
    val completionStatus: DayStatus // estado de completitud del día — determina el color del círculo
)

// Enum que define los posibles estados de un día en el calendario.
// CalendarAdapter usa este valor para decidir qué color y estilo
// aplicar a cada celda.
enum class DayStatus {
    COMPLETED,  // todos los hábitos completados → círculo negro
    INCOMPLETE, // algún hábito sin completar → círculo gris
    REST_DAY,   // día de descanso configurado → sin círculo
    NO_DATA     // día futuro, antes de instalar la app, o sin hábitos → sin círculo
}