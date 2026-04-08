package com.novikon.pace.models

// Enum que define en qué franja horaria se realiza un hábito.
// Se usa en DailyHabitAdapter para agrupar los hábitos
// en secciones dentro de la pantalla de hábitos del día.
enum class TimeOfDay {
    MORNING,    // 🌅 Mañana
    AFTERNOON,  // ☀️ Tarde
    EVENING,    // 🌙 Noche
    ALL_DAY     // 📅 Todo el día
}