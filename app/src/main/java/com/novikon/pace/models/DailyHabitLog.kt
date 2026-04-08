package com.novikon.pace.models

// Data class que representa el registro de un hábito en un día concreto.
// Es el "tick" diario — cada vez que el usuario marca un hábito
// como hecho o no hecho, se crea un DailyHabitLog.
//
// La combinación de habitId + date identifica unívocamente un registro:
// no puede haber dos logs del mismo hábito en el mismo día.
//
// Se usa en HabitsManager (para guardar y recuperar registros),
// DailyHabitsActivity (para saber qué hábitos están hechos hoy)
// y HabitHistoryActivity (para calcular el estado de cada día).
data class DailyHabitLog(
    val habitId: String,                            // id del hábito registrado
    val date: String,                               // fecha en formato "yyyy-MM-dd" (ej: "2024-03-16")
    val isDone: Boolean,                            // true si se completó, false si no
    val timestamp: Long = System.currentTimeMillis() // momento exacto del registro en milisegundos
)