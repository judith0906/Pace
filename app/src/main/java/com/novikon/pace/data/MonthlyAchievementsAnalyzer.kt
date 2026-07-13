package com.novikon.pace.data

import com.novikon.pace.models.DailyHabitLog
import com.novikon.pace.models.Habit
import com.novikon.pace.models.HabitCategory
import com.novikon.pace.models.MonthlyAchievement
import java.text.SimpleDateFormat
import java.util.*

class MonthlyAchievementsAnalyzer {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    data class AnalysisInput(
        val logs: List<DailyHabitLog>,
        val habits: List<Habit>,
        val eventsCreated: Int = 0,
        val supportMessagesSent: Int = 0
    )

    data class AnalysisResult(
        val achievements: List<MonthlyAchievement>,
        val newlyCompleted: List<String>
    )

    fun analyze(input: AnalysisInput, month: Int): AnalysisResult {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = if (month > 0) month - 1 else now.get(Calendar.MONTH)
        val today = now.get(Calendar.DAY_OF_MONTH)

        val doneLogs = input.logs.filter { it.isDone }
        val doneThisMonth = doneLogs.filter { log ->
            try {
                val cal = Calendar.getInstance().apply { time = sdf.parse(log.date) ?: Date() }
                cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth
            } catch (e: Exception) { false }
        }

        val habitMap = input.habits.associateBy { it.id }

        val uniqueDaysThisMonth = getUniqueDaysInMonth(doneThisMonth)
        val totalDaysInMonth = getTotalDaysInMonth(currentYear, currentMonth, today)

        val streak = calculateCurrentStreak(doneLogs)

        val perfectDaysCount = countPerfectDays(doneThisMonth, input.logs, currentYear, currentMonth)
        val consistencyPct = if (totalDaysInMonth > 0)
            (uniqueDaysThisMonth.size * 100) / totalDaysInMonth else 0
        val marathonDays = uniqueDaysThisMonth.size

        val eventJoinIds = doneThisMonth
            .filter { it.source == "EVENT_JOIN" }
            .map { it.eventId }
            .distinct()
            .count()

        val categoryCompletions = countByCategory(doneThisMonth, habitMap)
        val maxCategoryCount = categoryCompletions.maxOfOrNull { it.value } ?: 0
        val categoriesTouched = categoryCompletions.count { it.value > 0 }

        val earlyBirdDays = countEarlyBirdDays(doneThisMonth)
        val badHabitCount = countCategoryCompletions(doneThisMonth, habitMap, HabitCategory.BAD_HABITS)
        val comebackDetected = detectComeback(doneLogs, currentYear, currentMonth, today)

        val perfectWeekCount = countPerfectWeekDays(doneThisMonth, input.logs, currentYear, currentMonth)
        val doubleDayCount = countDoubleDays(doneThisMonth, currentYear, currentMonth)
        val morningRushDays = countMorningRushDays(doneThisMonth, input.logs, input.habits, currentYear, currentMonth)
        val favoriteHabitCount = calculateFavoriteHabitCount(doneThisMonth)

        val definitions = AchievementDefinitions.getAchievementsForMonth(currentMonth + 1)
        val defMap = definitions.associateBy { it.id }

        val result = definitions.map { def ->
            val progress = when (def.id) {
                "streak_7" -> minOf(streak, def.target)
                "perfect_day" -> minOf(perfectDaysCount, def.target)
                "streak_14" -> minOf(streak, def.target)
                "consistency_90" -> minOf(consistencyPct, 100)
                "marathon_21" -> minOf(marathonDays, def.target)
                "social_hero" -> minOf(eventJoinIds, def.target)
                "host_3" -> minOf(input.eventsCreated, def.target)
                "supporter_10" -> minOf(input.supportMessagesSent, def.target)
                "category_dominance" -> minOf(maxCategoryCount, def.target)
                "five_categories" -> minOf(categoriesTouched, def.target)
                "all_categories" -> minOf(categoriesTouched, def.target)
                "early_bird" -> minOf(earlyBirdDays, def.target)
                "bad_habit_slayer" -> minOf(badHabitCount, def.target)
                "comeback_king" -> if (comebackDetected) 1 else 0
                "perfect_week" -> minOf(perfectWeekCount, def.target)
                "double_day" -> minOf(doubleDayCount, def.target)
                "morning_rush" -> minOf(morningRushDays, def.target)
                "favorite_habit" -> minOf(favoriteHabitCount, def.target)
                else -> 0
            }

            val completed = progress >= def.target

            def.copy(
                progress = progress,
                completed = completed,
                completedAt = if (completed && def.completedAt == 0L)
                    System.currentTimeMillis() else def.completedAt
            )
        }

        val newlyCompleted = result
            .filter { it.completed && it.completedAt > 0L }
            .map { it.id }

        return AnalysisResult(
            achievements = result.sortedBy { it.sortOrder },
            newlyCompleted = newlyCompleted
        )
    }

    private fun getUniqueDaysInMonth(logs: List<DailyHabitLog>): Set<Int> {
        val cal = Calendar.getInstance()
        return logs.map { log ->
            cal.time = sdf.parse(log.date) ?: Date()
            cal.get(Calendar.DAY_OF_MONTH)
        }.toSet()
    }

    private fun getTotalDaysInMonth(year: Int, month: Int, today: Int): Int {
        val maxDay = Calendar.getInstance().apply {
            set(year, month, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
        return minOf(today, maxDay)
    }

    private fun countPerfectDays(
        doneThisMonth: List<DailyHabitLog>,
        allLogs: List<DailyHabitLog>,
        year: Int, month: Int
    ): Int {
        val logsByDay = allLogs
            .filter { log ->
                try {
                    val cal = Calendar.getInstance().apply { time = sdf.parse(log.date) ?: Date() }
                    cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
                } catch (e: Exception) { false }
            }
            .groupBy { it.date }

        return logsByDay.count { (_, logs) ->
            val nonEventLogs = logs.filter { !it.isEventHabit }
            nonEventLogs.size >= 4 && nonEventLogs.all { it.isDone }
        }
    }

    private fun calculateCurrentStreak(doneLogs: List<DailyHabitLog>): Int {
        val daysWithActivity = doneLogs.map { it.date }.toSet()
        val cal = Calendar.getInstance()
        var streak = 0

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

    private fun countByCategory(
        logs: List<DailyHabitLog>,
        habitMap: Map<String, Habit>
    ): Map<HabitCategory, Int> {
        val counts = mutableMapOf<HabitCategory, Int>()
        logs.forEach { log ->
            val category = habitMap[log.habitId]?.category ?: HabitCategory.CUSTOM
            counts[category] = (counts[category] ?: 0) + 1
        }
        return counts
    }

    private fun countCategoryCompletions(
        logs: List<DailyHabitLog>,
        habitMap: Map<String, Habit>,
        category: HabitCategory
    ): Int {
        return logs.count { log -> habitMap[log.habitId]?.category == category }
    }

    private fun countEarlyBirdDays(logs: List<DailyHabitLog>): Int {
        val h = Calendar.getInstance()
        val earlyDays = mutableSetOf<String>()
        logs.forEach { log ->
            if (log.timestamp > 0) {
                h.timeInMillis = log.timestamp
                if (h.get(Calendar.HOUR_OF_DAY) < 8) {
                    earlyDays.add(log.date)
                }
            }
        }
        return earlyDays.size
    }

    private fun detectComeback(
        doneLogs: List<DailyHabitLog>,
        currentYear: Int, currentMonth: Int, today: Int
    ): Boolean {
        val cal = Calendar.getInstance()
        cal.set(currentYear, currentMonth, 1)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val lastDayToCheck = minOf(today, maxDay)

        val uniqueDates = doneLogs
            .filter { log ->
                try {
                    val logCal = Calendar.getInstance().apply { time = sdf.parse(log.date) ?: Date() }
                    logCal.get(Calendar.YEAR) == currentYear && logCal.get(Calendar.MONTH) == currentMonth
                } catch (e: Exception) { false }
            }
            .map { it.date }
            .toSet()

        val dailyFlags = (1..lastDayToCheck).map { day ->
            val dateStr = String.format(Locale.US, "%04d-%02d-%02d", currentYear, currentMonth + 1, day)
            dateStr to uniqueDates.contains(dateStr)
        }.toMap()

        var hasBreak = false
        var currentRun = 0
        for (day in 1..lastDayToCheck) {
            val dateStr = String.format(Locale.US, "%04d-%02d-%02d", currentYear, currentMonth + 1, day)
            if (dailyFlags[dateStr] == true) {
                currentRun++
                if (currentRun >= 7 && hasBreak) return true
            } else {
                if (currentRun > 0) hasBreak = true
                currentRun = 0
            }
        }
        return false
    }

    private fun countPerfectWeekDays(
        doneThisMonth: List<DailyHabitLog>,
        allLogs: List<DailyHabitLog>,
        year: Int, month: Int
    ): Int {
        val logsByDay = allLogs
            .filter { log ->
                try {
                    val cal = Calendar.getInstance().apply { time = sdf.parse(log.date) ?: Date() }
                    cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
                } catch (e: Exception) { false }
            }
            .groupBy { it.date }
            .mapValues { (_, logs) ->
                val nonEvent = logs.filter { !it.isEventHabit }
                nonEvent.size >= 4 && nonEvent.all { it.isDone }
            }

        val sortedDays = logsByDay.entries
            .filter { it.value }
            .mapNotNull { entry ->
                try { sdf.parse(entry.key)?.let { Calendar.getInstance().apply { time = it } }
                } catch (e: Exception) { null }
            }
            .sortedBy { it.timeInMillis }

        var maxConsecutive = 0
        var currentRun = 0
        var prev: Calendar? = null

        for (cal in sortedDays) {
            if (prev != null) {
                val diff = ((cal.timeInMillis - prev.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
                if (diff == 1) currentRun++
                else {
                    maxConsecutive = maxOf(maxConsecutive, currentRun)
                    currentRun = 1
                }
            } else currentRun = 1
            prev = cal
        }

        return maxOf(maxConsecutive, currentRun)
    }

    private fun countDoubleDays(
        doneThisMonth: List<DailyHabitLog>,
        year: Int, month: Int
    ): Int {
        val countsByDay = doneThisMonth
            .filter { !it.isEventHabit }
            .groupBy { it.date }
            .mapValues { it.value.size }

        return countsByDay.count { (_, count) -> count >= 10 }
    }

    private fun countMorningRushDays(
        doneThisMonth: List<DailyHabitLog>,
        allLogs: List<DailyHabitLog>,
        habits: List<Habit>,
        year: Int, month: Int
    ): Int {
        val logsByDay = allLogs
            .filter { log ->
                try {
                    val cal = Calendar.getInstance().apply { time = sdf.parse(log.date) ?: Date() }
                    cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
                } catch (e: Exception) { false }
            }
            .groupBy { it.date }

        val morningHabitIds = habits
            .filter { it.timeOfDay == com.novikon.pace.models.TimeOfDay.MORNING }
            .map { it.id }
            .toSet()

        if (morningHabitIds.isEmpty()) return 0

        return logsByDay.count { (_, logs) ->
            val morningLogs = logs.filter { it.habitId in morningHabitIds }
            morningLogs.isNotEmpty() && morningLogs.all { it.isDone }
        }
    }

    private fun calculateFavoriteHabitCount(doneThisMonth: List<DailyHabitLog>): Int {
        return doneThisMonth
            .filter { !it.isEventHabit }
            .groupBy { it.habitId }
            .maxOfOrNull { it.value.size } ?: 0
    }
}
