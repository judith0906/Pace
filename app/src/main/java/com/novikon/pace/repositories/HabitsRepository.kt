package com.novikon.pace.repositories

import android.content.Context
import android.provider.Settings.Global.getString
import com.novikon.pace.R
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
    fun getAllPredefinedHabits(context: Context): List<Habit> {
        return getPhysicalHabits(context) +
                getMentalHabits(context) +
                getStudyHabits(context) +
                getRoutineHabits(context) +
                getBadHabitsToEliminate(context) +
                getWellbeingHabits(context)
    }

    // ── HÁBITOS POR CATEGORÍA ─────────────────────────────────────────────────

    private fun getPhysicalHabits(context: Context): List<Habit> {
        return listOf(
            Habit("physical_1", context.getString(R.string.physical1), "💪", "30-60 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_2", context.getString(R.string.physical2), "🏃", "30 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_3", context.getString(R.string.physical3), "🏋️", "60 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_4", context.getString(R.string.physical4), "🧘", "30 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_5", context.getString(R.string.physical5), "🤸", "15 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_6", context.getString(R.string.physical6), "🚶", "Todo el día", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_7", context.getString(R.string.physical7), "💧", "Todo el día", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_8", context.getString(R.string.physical8), "🏊", "45 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_9", context.getString(R.string.physical9), "🚴", "30 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY),
            Habit("physical_10", context.getString(R.string.physical10), "💃", "30 min", HabitCategory.PHYSICAL, TimeOfDay.ALL_DAY)
        )
    }
    private fun getMentalHabits(context: Context): List<Habit> {
        return listOf(
            Habit("mental_1", context.getString(R.string.mental1), "🧘‍♂️", "10-20 min", HabitCategory.MENTAL, TimeOfDay.ALL_DAY),
            Habit("mental_2", context.getString(R.string.mental2), "📚", "30 min", HabitCategory.MENTAL, TimeOfDay.ALL_DAY),
            Habit("mental_3", context.getString(R.string.mental3), "🎓", "30 min", HabitCategory.MENTAL, TimeOfDay.ALL_DAY),
            Habit("mental_4", context.getString(R.string.mental4), "🗣️", "20 min", HabitCategory.MENTAL, TimeOfDay.ALL_DAY),
            Habit("mental_5", context.getString(R.string.mental5), "🧩", "15 min", HabitCategory.MENTAL, TimeOfDay.ALL_DAY),
            Habit("mental_6", context.getString(R.string.mental6), "📝", "15 min", HabitCategory.MENTAL, TimeOfDay.EVENING),
            Habit("mental_7", context.getString(R.string.mental7), "🎧", "30 min", HabitCategory.MENTAL, TimeOfDay.ALL_DAY),
            Habit("mental_8", context.getString(R.string.mental8), "🌸", "10 min", HabitCategory.MENTAL, TimeOfDay.MORNING),
            Habit("mental_9", context.getString(R.string.mental9), "🎬", "45 min", HabitCategory.MENTAL, TimeOfDay.EVENING),
            Habit("mental_10", context.getString(R.string.mental10), "🎸", "30 min", HabitCategory.MENTAL, TimeOfDay.ALL_DAY)
        )
    }
    private fun getStudyHabits(context: Context): List<Habit> {
        return listOf(
            Habit("study_1", context.getString(R.string.study1), "📖", "60 min", HabitCategory.STUDY, TimeOfDay.ALL_DAY),
            Habit("study_2", context.getString(R.string.study2), "✍️", "45 min", HabitCategory.STUDY, TimeOfDay.ALL_DAY),
            Habit("study_5", context.getString(R.string.study5), "🗂️", "20 min", HabitCategory.STUDY, TimeOfDay.ALL_DAY),
            Habit("study_6", context.getString(R.string.study6), "📄", "45 min", HabitCategory.STUDY, TimeOfDay.ALL_DAY),
            Habit("study_8", context.getString(R.string.study8), "👨‍🏫", "Según horario", HabitCategory.STUDY, TimeOfDay.ALL_DAY),
            Habit("study_9", context.getString(R.string.study9), "🔍", "45 min", HabitCategory.STUDY, TimeOfDay.ALL_DAY),
            Habit("study_10", context.getString(R.string.study10), "📝", "90 min", HabitCategory.STUDY, TimeOfDay.ALL_DAY)
        )
    }
    private fun getRoutineHabits(context: Context): List<Habit> {
        return listOf(
            Habit("routine_1", context.getString(R.string.routine1), "🛏️", "5 min", HabitCategory.ROUTINE, TimeOfDay.MORNING),
            Habit("routine_2", context.getString(R.string.routine2), "🚿", "15 min", HabitCategory.ROUTINE, TimeOfDay.MORNING),
            Habit("routine_3", context.getString(R.string.routine3), "🍳", "20 min", HabitCategory.ROUTINE, TimeOfDay.MORNING),
            Habit("routine_4", context.getString(R.string.routine4), "🥗", "30 min", HabitCategory.ROUTINE, TimeOfDay.MORNING),
            Habit("routine_5", context.getString(R.string.routine5), "🧹", "20 min", HabitCategory.ROUTINE, TimeOfDay.ALL_DAY),
            Habit("routine_6", context.getString(R.string.routine6), "📅", "10 min", HabitCategory.ROUTINE, TimeOfDay.MORNING),
            Habit("routine_7", context.getString(R.string.routine7), "📧", "15 min", HabitCategory.ROUTINE, TimeOfDay.ALL_DAY),
            Habit("routine_8", context.getString(R.string.routine8), "👨‍🍳", "40 min", HabitCategory.ROUTINE, TimeOfDay.EVENING),
            Habit("routine_9", context.getString(R.string.routine9), "🌙", "30 min", HabitCategory.ROUTINE, TimeOfDay.EVENING),
            Habit("routine_10", context.getString(R.string.routine10), "😴", "8 horas", HabitCategory.ROUTINE, TimeOfDay.EVENING),
            Habit("routine_11", context.getString(R.string.routine11), "💆", "20 min", HabitCategory.ROUTINE, TimeOfDay.ALL_DAY),
            Habit("routine_12", context.getString(R.string.routine12), "🪴", "10 min", HabitCategory.ROUTINE, TimeOfDay.ALL_DAY)
        )
    }
    private fun getBadHabitsToEliminate(context: Context): List<Habit> {
        return listOf(
            Habit("bad_1", context.getString(R.string.bad1), "🚭", "Todo el día", HabitCategory.BAD_HABITS, TimeOfDay.ALL_DAY),
            Habit("bad_2", context.getString(R.string.bad2), "🍺", "Todo el día", HabitCategory.BAD_HABITS, TimeOfDay.ALL_DAY),
            Habit("bad_3", context.getString(R.string.bad3), "🍟", "Todo el día", HabitCategory.BAD_HABITS, TimeOfDay.ALL_DAY),
            Habit("bad_4", context.getString(R.string.bad4), "📱", "Máx 30 min", HabitCategory.BAD_HABITS, TimeOfDay.ALL_DAY),
            Habit("bad_6", context.getString(R.string.bad6), "🌃", "Antes 23:00", HabitCategory.BAD_HABITS, TimeOfDay.EVENING),
            Habit("bad_7", context.getString(R.string.bad7), "☕", "Después 16:00", HabitCategory.BAD_HABITS, TimeOfDay.AFTERNOON),
            Habit("bad_8", context.getString(R.string.bad8), "🍪", "Todo el día", HabitCategory.BAD_HABITS, TimeOfDay.ALL_DAY),
            Habit("bad_9", context.getString(R.string.bad9), "🍭", "Todo el día", HabitCategory.BAD_HABITS, TimeOfDay.ALL_DAY),
            Habit("bad_11", context.getString(R.string.bad11), "📵", "1h antes", HabitCategory.BAD_HABITS, TimeOfDay.EVENING),
            Habit("bad_12", context.getString(R.string.bad12), "🍽️", "Todo el día", HabitCategory.BAD_HABITS, TimeOfDay.ALL_DAY)
        )
    }
    private fun getWellbeingHabits(context: Context): List<Habit> {
        return listOf(
            Habit("wellbeing_2", context.getString(R.string.wellbeing2), "📞", "20 min", HabitCategory.WELLBEING, TimeOfDay.ALL_DAY),
            Habit("wellbeing_3", context.getString(R.string.wellbeing3), "🫂", "30 min", HabitCategory.WELLBEING, TimeOfDay.ALL_DAY),
            Habit("wellbeing_6", context.getString(R.string.wellbeing6), "🔌", "Después horario", HabitCategory.WELLBEING, TimeOfDay.ALL_DAY),
            Habit("wellbeing_7", context.getString(R.string.wellbeing7), "🎨", "30 min", HabitCategory.WELLBEING, TimeOfDay.ALL_DAY),
            Habit("wellbeing_9", context.getString(R.string.wellbeing9), "🌳", "30 min", HabitCategory.WELLBEING, TimeOfDay.ALL_DAY),
            Habit("wellbeing_10", context.getString(R.string.wellbeing10), "🛁", "45 min", HabitCategory.WELLBEING, TimeOfDay.ALL_DAY)
        )
    }
}