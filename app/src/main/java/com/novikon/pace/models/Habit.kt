package com.novikon.pace.models

// Data class que representa un hábito.
// Es el "molde" de un hábito — define qué información
// tiene cada hábito de la app.
//
// Se usa en HabitsRepository (para crearlos), HabitsManager
// (para guardarlos y recuperarlos), y en todos los adapters
// (para mostrarlos en pantalla).
data class Habit(
    val id: String,                          // identificador único (ej: "physical_1", "custom_123456")
    val name: String,                        // nombre del hábito (ej: "Hacer ejercicio")
    val emoji: String,                       // emoji representativo (ej: "💪")
    val duration: String,                    // tiempo estimado (ej: "30 min", "Todo el día")
    val category: HabitCategory,             // categoría a la que pertenece
    val timeOfDay: TimeOfDay = TimeOfDay.ALL_DAY, // franja horaria en la que se realiza
    val isCustom: Boolean = false            // true si lo creó el usuario, false si es predefinido
)