package com.novikon.pace.models

// Enum que define las categorías posibles de un hábito.
// Al ser un enum, los valores están fijos y controlados —
// no se puede crear una categoría que no esté aquí definida,
// lo que evita errores por strings mal escritos.
//
// Se usa en Habit (para asignar categoría), HabitsRepository
// (para agrupar hábitos por categoría) y HabitSelectionActivity
// (para filtrar hábitos por categoría con los chips).
enum class HabitCategory {
    PHYSICAL,       // hábitos físicos (ejercicio, deporte...)
    MENTAL,         // hábitos mentales (meditación, lectura...)
    STUDY,          // hábitos de estudio (repasar, tomar apuntes...)
    ROUTINE,        // hábitos de rutina diaria (hacer la cama, desayunar...)
    BAD_HABITS,     // hábitos negativos a eliminar (no fumar, no procrastinar...)
    WELLBEING,      // hábitos de bienestar emocional (gratitud, autocuidado...)
    CUSTOM          // hábitos creados por el usuario
}