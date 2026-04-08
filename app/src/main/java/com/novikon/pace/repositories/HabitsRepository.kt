package com.novikon.pace.repositories

import com.novikon.pace.models.Habit
import com.novikon.pace.models.HabitCategory
import com.novikon.pace.models.TimeOfDay

// Fuente de datos estática con todos los hábitos predefinidos de la app.
// No conecta a internet ni a base de datos — los datos están hardcodeados
// aquí porque son los mismos para todos los usuarios y nunca cambian.
//
// Es un "object" porque no necesita estado propio ni instancias múltiples.
// Se usa desde HabitSelectionActivity para mostrar los hábitos disponibles.
object HabitsRepository {

    // Devuelve todos los hábitos predefinidos de todas las categorías
    // en una sola lista. Se usa para buscar hábitos por id.
    fun getAllPredefinedHabits(): List<Habit> {
        return getPhysicalHabits() +
                getMentalHabits() +
                getStudyHabits() +
                getRoutineHabits() +
                getBadHabitsToEliminate() +
                getWellbeingHabits()
    }

    // ── HÁBITOS POR CATEGORÍA ─────────────────────────────────────────────────

    private fun getPhysicalHabits(): List<Habit> {
        return listOf(
            Habit("physical_1", "Hacer ejercicio", "💪", "30-60 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_2", "Salir a correr", "🏃", "30 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_3", "Ir al gimnasio", "🏋️", "60 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_4", "Hacer yoga", "🧘", "30 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_5", "Estiramientos", "🤸", "15 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_6", "Caminar 10,000 pasos", "🚶", "Todo el día", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_7", "Beber 2L de agua", "💧", "Todo el día", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_8", "Nadar", "🏊", "45 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_9", "Andar en bicicleta", "🚴", "30 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_10", "Bailar", "💃", "30 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY)
        )
    }

    private fun getMentalHabits(): List<Habit> {
        return listOf(
            Habit("mental_1", "Meditar", "🧘‍♂️", "10-20 min", HabitCategory.MENTAL, TimeOfDay.ALL_DAY),
            Habit("mental_2", "Leer un libro", "📚", "30 min", HabitCategory.MENTAL, TimeOfDay.ALL_DAY),
            Habit("mental_3", "Aprender algo nuevo", "🎓", "30 min", HabitCategory.MENTAL, TimeOfDay.ALL_DAY),
            Habit("mental_4", "Practicar un idioma", "🗣️", "20 min", HabitCategory.MENTAL, TimeOfDay.ALL_DAY),
            Habit("mental_5", "Resolver puzzles/sudoku", "🧩", "15 min", HabitCategory.MENTAL, TimeOfDay.ALL_DAY),
            Habit("mental_6", "Escribir en diario", "📝", "15 min", HabitCategory.MENTAL, TimeOfDay.EVENING),
            Habit("mental_7", "Escuchar podcast educativo", "🎧", "30 min", HabitCategory.MENTAL, TimeOfDay.ALL_DAY),
            Habit("mental_8", "Practicar mindfulness", "🌸", "10 min", HabitCategory.MENTAL, TimeOfDay.MORNING),
            Habit("mental_9", "Ver documental", "🎬", "45 min", HabitCategory.MENTAL, TimeOfDay.EVENING),
            Habit("mental_10", "Tocar un instrumento", "🎸", "30 min", HabitCategory.MENTAL, TimeOfDay.ALL_DAY)
        )
    }

    private fun getStudyHabits(): List<Habit> {
        return listOf(
            Habit("study_1", "Estudiar/repasar", "📖", "60 min", HabitCategory.STUDY, TimeOfDay.ALL_DAY),
            Habit("study_2", "Hacer ejercicios prácticos", "✍️", "45 min", HabitCategory.STUDY, TimeOfDay.ALL_DAY),
            Habit("study_5", "Repasar flashcards", "🗂️", "20 min", HabitCategory.STUDY, TimeOfDay.ALL_DAY),
            Habit("study_6", "Hacer resúmenes", "📄", "45 min", HabitCategory.STUDY, TimeOfDay.ALL_DAY),
            Habit("study_8", "Asistir a clase/tutoría", "👨‍🏫", "Según horario", HabitCategory.STUDY, TimeOfDay.ALL_DAY),
            Habit("study_9", "Investigar sobre algún tema del que no sabes", "🔍", "45 min", HabitCategory.STUDY, TimeOfDay.ALL_DAY),
            Habit("study_10", "Preparar examen", "📝", "90 min", HabitCategory.STUDY, TimeOfDay.ALL_DAY)
        )
    }

    private fun getRoutineHabits(): List<Habit> {
        return listOf(
            Habit("routine_1", "Hacer la cama", "🛏️", "5 min", HabitCategory.ROUTINE, TimeOfDay.MORNING),
            Habit("routine_2", "Ducha matutina", "🚿", "15 min", HabitCategory.ROUTINE, TimeOfDay.MORNING),
            Habit("routine_3", "Desayunar saludable", "🍳", "20 min", HabitCategory.ROUTINE, TimeOfDay.MORNING),
            Habit("routine_4", "Preparar comida", "🥗", "30 min", HabitCategory.ROUTINE, TimeOfDay.MORNING),
            Habit("routine_5", "Limpiar/ordenar", "🧹", "20 min", HabitCategory.ROUTINE, TimeOfDay.ALL_DAY),
            Habit("routine_6", "Planificar el día", "📅", "10 min", HabitCategory.ROUTINE, TimeOfDay.MORNING),
            Habit("routine_7", "Revisar emails", "📧", "15 min", HabitCategory.ROUTINE, TimeOfDay.ALL_DAY),
            Habit("routine_8", "Cocinar cena", "👨‍🍳", "40 min", HabitCategory.ROUTINE, TimeOfDay.EVENING),
            Habit("routine_9", "Rutina nocturna", "🌙", "30 min", HabitCategory.ROUTINE, TimeOfDay.EVENING),
            Habit("routine_10", "Dormir 8 horas", "😴", "8 horas", HabitCategory.ROUTINE, TimeOfDay.EVENING),
            Habit("routine_11", "Cuidado personal", "💆", "20 min", HabitCategory.ROUTINE, TimeOfDay.ALL_DAY),
            Habit("routine_12", "Regar plantas", "🪴", "10 min", HabitCategory.ROUTINE, TimeOfDay.ALL_DAY)
        )
    }

    private fun getBadHabitsToEliminate(): List<Habit> {
        return listOf(
            Habit("bad_1", "No fumar", "🚭", "Todo el día", HabitCategory.BAD_HABITS, TimeOfDay.ALL_DAY),
            Habit("bad_2", "No consumir alcohol", "🍺", "Todo el día", HabitCategory.BAD_HABITS, TimeOfDay.ALL_DAY),
            Habit("bad_3", "No comer comida basura", "🍟", "Todo el día", HabitCategory.BAD_HABITS, TimeOfDay.ALL_DAY),
            Habit("bad_4", "Limitar redes sociales", "📱", "Máx 30 min", HabitCategory.BAD_HABITS, TimeOfDay.ALL_DAY),
            Habit("bad_6", "No trasnochar", "🌃", "Antes 23:00", HabitCategory.BAD_HABITS, TimeOfDay.EVENING),
            Habit("bad_7", "Evitar cafeína tarde", "☕", "Después 16:00", HabitCategory.BAD_HABITS, TimeOfDay.AFTERNOON),
            Habit("bad_8", "No picar entre comidas", "🍪", "Todo el día", HabitCategory.BAD_HABITS, TimeOfDay.ALL_DAY),
            Habit("bad_9", "Reducir azúcar", "🍭", "Todo el día", HabitCategory.BAD_HABITS, TimeOfDay.ALL_DAY),
            Habit("bad_11", "Evitar pantallas antes dormir", "📵", "1h antes", HabitCategory.BAD_HABITS, TimeOfDay.EVENING),
            Habit("bad_12", "No saltarse comidas", "🍽️", "Todo el día", HabitCategory.BAD_HABITS, TimeOfDay.ALL_DAY)
        )
    }

    private fun getWellbeingHabits(): List<Habit> {
        return listOf(
            Habit("wellbeing_2", "Llamar a seres queridos", "📞", "20 min", HabitCategory.WELLBEING, TimeOfDay.ALL_DAY),
            Habit("wellbeing_3", "Tiempo de calidad contigo", "🫂", "30 min", HabitCategory.WELLBEING, TimeOfDay.ALL_DAY),
            Habit("wellbeing_6", "Desconectar del trabajo", "🔌", "Después horario", HabitCategory.WELLBEING, TimeOfDay.ALL_DAY),
            Habit("wellbeing_7", "Hacer algo que disfrutes", "🎨", "30 min", HabitCategory.WELLBEING, TimeOfDay.ALL_DAY),
            Habit("wellbeing_9", "Contacto con naturaleza", "🌳", "30 min", HabitCategory.WELLBEING, TimeOfDay.ALL_DAY),
            Habit("wellbeing_10", "Sesión de autocuidado", "🛁", "45 min", HabitCategory.WELLBEING, TimeOfDay.ALL_DAY)
        )
    }
}