package com.novikon.pace.ui.stats

import com.novikon.pace.models.DailyHabitLog
import com.novikon.pace.models.Habit
import com.novikon.pace.models.HabitCategory
import java.text.SimpleDateFormat
import java.util.*

data class StatsData(
    val currentStreak: Int,
    val maxStreak: Int,
    val monthlyConsistency: Int, // porcentaje 0-100
    val starHabitName: String,
    val starHabitEmoji: String,
    val categoryPercentages: Map<HabitCategory, Float>,
    val monthlyDays: Map<Int, Boolean>, // día del mes → completó algo ese día
    val yearlyConsistency: Map<Int, Float>, // mes (1-12) → % completitud
    val top5Habits: List<Pair<String, Int>> // nombre → veces completado
)

object StatsDataAnalyzer {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun analyze(logs: List<DailyHabitLog>, habits: List<Habit>): StatsData {
        val doneLogs = logs.filter { it.isDone }
        val habitMap = habits.associateBy { it.id }

        // ── RACHA ACTUAL ──────────────────────────────────────────────────────
        val currentStreak = calculateCurrentStreak(doneLogs)

        // ── RACHA MÁXIMA ──────────────────────────────────────────────────────
        val maxStreak = calculateMaxStreak(doneLogs)

        // ── CONSTANCIA MENSUAL ────────────────────────────────────────────────
        val monthlyConsistency = calculateMonthlyConsistency(doneLogs)

        // ── HÁBITO ESTRELLA ───────────────────────────────────────────────────
        val habitCounts = doneLogs.groupBy { it.habitId }
            .mapValues { it.value.size }
        val starHabitId = habitCounts.maxByOrNull { it.value }?.key ?: ""
        val starHabit = habitMap[starHabitId]
        val starHabitName = starHabit?.name
            ?: doneLogs.firstOrNull { it.habitId == starHabitId }?.habitName
            ?: ""
        val starHabitEmoji = starHabit?.emoji
            ?: doneLogs.firstOrNull { it.habitId == starHabitId }?.habitEmoji
            ?: ""

        // ── DISTRIBUCIÓN POR CATEGORÍA ────────────────────────────────────────
        val categoryPercentages = calculateCategoryPercentages(doneLogs, habitMap)

        // ── CONSTANCIA MENSUAL POR DÍA ────────────────────────────────────────
        val monthlyDays = calculateMonthlyDays(doneLogs)

        // ── EVOLUCIÓN ANUAL ───────────────────────────────────────────────────
        val yearlyConsistency = calculateYearlyConsistency(doneLogs)

        // ── TOP 5 HÁBITOS ─────────────────────────────────────────────────────
        val top5 = habitCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { entry ->
                val name = habitMap[entry.key]?.let { "${it.emoji} ${it.name}" }
                    ?: doneLogs.firstOrNull { it.habitId == entry.key }
                        ?.let { "${it.habitEmoji} ${it.habitName}" }
                    ?: entry.key
                Pair(name, entry.value)
            }

        return StatsData(
            currentStreak = currentStreak,
            maxStreak = maxStreak,
            monthlyConsistency = monthlyConsistency,
            starHabitName = starHabitName,
            starHabitEmoji = starHabitEmoji,
            categoryPercentages = categoryPercentages,
            monthlyDays = monthlyDays,
            yearlyConsistency = yearlyConsistency,
            top5Habits = top5
        )
    }

    private fun calculateCurrentStreak(doneLogs: List<DailyHabitLog>): Int {
        val daysWithActivity = doneLogs.map { it.date }.toSet()
        val cal = Calendar.getInstance()
        var streak = 0

        // Si hoy no tiene actividad, empezamos desde ayer
        val today = sdf.format(cal.time)
        if (!daysWithActivity.contains(today)) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        while (true) {
            val dateStr = sdf.format(cal.time)
            if (daysWithActivity.contains(dateStr)) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else break
        }
        return streak
    }

    private fun calculateMaxStreak(doneLogs: List<DailyHabitLog>): Int {
        val daysWithActivity = doneLogs.map { it.date }.toSortedSet()
        if (daysWithActivity.isEmpty()) return 0

        var maxStreak = 0
        var currentStreak = 1
        var prevDate: Calendar? = null

        for (dateStr in daysWithActivity) {
            val cal = Calendar.getInstance().apply { time = sdf.parse(dateStr) ?: Date() }
            if (prevDate != null) {
                val diff = ((cal.timeInMillis - prevDate.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
                if (diff == 1) {
                    currentStreak++
                } else {
                    maxStreak = maxOf(maxStreak, currentStreak)
                    currentStreak = 1
                }
            }
            prevDate = cal
        }
        return maxOf(maxStreak, currentStreak)
    }

    private fun calculateMonthlyConsistency(doneLogs: List<DailyHabitLog>): Int {
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        val today = cal.get(Calendar.DAY_OF_MONTH)

        val daysWithActivityThisMonth = doneLogs.filter { log ->
            try {
                val logCal = Calendar.getInstance().apply { time = sdf.parse(log.date) ?: Date() }
                logCal.get(Calendar.MONTH) == currentMonth &&
                        logCal.get(Calendar.YEAR) == currentYear
            } catch (e: Exception) { false }
        }.map { it.date }.toSet()

        return if (today == 0) 0
        else ((daysWithActivityThisMonth.size.toFloat() / today) * 100).toInt()
    }

    private fun calculateCategoryPercentages(
        doneLogs: List<DailyHabitLog>,
        habitMap: Map<String, Habit>
    ): Map<HabitCategory, Float> {
        if (doneLogs.isEmpty()) return emptyMap()

        val categoryCounts = mutableMapOf<HabitCategory, Int>()
        doneLogs.forEach { log ->
            val category = habitMap[log.habitId]?.category ?: HabitCategory.CUSTOM
            categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
        }

        val total = categoryCounts.values.sum().toFloat()
        return categoryCounts.mapValues { (it.value / total) * 100f }
    }

    private fun calculateMonthlyDays(doneLogs: List<DailyHabitLog>): Map<Int, Boolean> {
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)

        val daysWithActivity = doneLogs.filter { log ->
            try {
                val logCal = Calendar.getInstance().apply { time = sdf.parse(log.date) ?: Date() }
                logCal.get(Calendar.MONTH) == currentMonth &&
                        logCal.get(Calendar.YEAR) == currentYear
            } catch (e: Exception) { false }
        }.map { log ->
            Calendar.getInstance().apply {
                time = sdf.parse(log.date) ?: Date()
            }.get(Calendar.DAY_OF_MONTH)
        }.toSet()

        val today = cal.get(Calendar.DAY_OF_MONTH)
        return (1..today).associateWith { day -> daysWithActivity.contains(day) }
    }

    private fun calculateYearlyConsistency(doneLogs: List<DailyHabitLog>): Map<Int, Float> {
        val result = mutableMapOf<Int, Float>()
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH) + 1
        val currentYear = cal.get(Calendar.YEAR)

        for (month in 1..currentMonth) {
            val logsInMonth = doneLogs.filter { log ->
                try {
                    val logCal = Calendar.getInstance().apply { time = sdf.parse(log.date) ?: Date() }
                    logCal.get(Calendar.MONTH) + 1 == month &&
                            logCal.get(Calendar.YEAR) == currentYear
                } catch (e: Exception) { false }
            }
            val daysInMonth = if (month == currentMonth) {
                cal.get(Calendar.DAY_OF_MONTH)
            } else {
                Calendar.getInstance().apply {
                    set(currentYear, month - 1, 1)
                }.getActualMaximum(Calendar.DAY_OF_MONTH)
            }
            val daysWithActivity = logsInMonth.map { it.date }.toSet().size
            result[month] = if (daysInMonth == 0) 0f
            else (daysWithActivity.toFloat() / daysInMonth) * 100f
        }
        return result
    }
}