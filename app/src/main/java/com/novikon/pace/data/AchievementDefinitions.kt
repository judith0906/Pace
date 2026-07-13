package com.novikon.pace.data

import com.novikon.pace.R
import com.novikon.pace.models.AchievementCategory
import com.novikon.pace.models.MonthlyAchievement

/**
 * Grupos de rotación:
 *   0 = CORE (siempre presentes)
 *   1 = ROTACIÓN A (meses impares)
 *   2 = ROTACIÓN B (meses pares)
 *   3 = ROTACIÓN C (meses 3, 6, 9, 12)
 *   4 = ROTACIÓN D (meses 4, 8, 12)
 */
object AchievementDefinitions {

    private val ACHIEVEMENT_POOL: List<MonthlyAchievement> = listOf(
        // ── CORE (rotación 0) — siempre presentes ──
        MonthlyAchievement(
            id = "streak_7",
            nameRes = R.string.ach_streak_7_name,
            descriptionRes = R.string.ach_streak_7_desc,
            emoji = "\uD83D\uDD25",
            category = AchievementCategory.CONSISTENCY,
            target = 7,
            sortOrder = 1,
            rotationGroup = 0
        ),
        MonthlyAchievement(
            id = "perfect_day",
            nameRes = R.string.ach_perfect_day_name,
            descriptionRes = R.string.ach_perfect_day_desc,
            emoji = "\u2B50",
            category = AchievementCategory.CONSISTENCY,
            target = 1,
            sortOrder = 2,
            rotationGroup = 0
        ),
        MonthlyAchievement(
            id = "consistency_90",
            nameRes = R.string.ach_consistency_90_name,
            descriptionRes = R.string.ach_consistency_90_desc,
            emoji = "\uD83D\uDEE1\uFE0F",
            category = AchievementCategory.CONSISTENCY,
            target = 90,
            sortOrder = 3,
            rotationGroup = 0
        ),
        MonthlyAchievement(
            id = "marathon_21",
            nameRes = R.string.ach_marathon_21_name,
            descriptionRes = R.string.ach_marathon_21_desc,
            emoji = "\uD83C\uDFC3",
            category = AchievementCategory.CONSISTENCY,
            target = 21,
            sortOrder = 4,
            rotationGroup = 0
        ),
        MonthlyAchievement(
            id = "social_hero",
            nameRes = R.string.ach_social_hero_name,
            descriptionRes = R.string.ach_social_hero_desc,
            emoji = "\uD83E\uDD1D",
            category = AchievementCategory.SOCIAL,
            target = 5,
            sortOrder = 5,
            rotationGroup = 0
        ),

        // ── ROTACIÓN A (meses impares) ──
        MonthlyAchievement(
            id = "streak_14",
            nameRes = R.string.ach_streak_14_name,
            descriptionRes = R.string.ach_streak_14_desc,
            emoji = "\uD83D\uDCAA",
            category = AchievementCategory.CONSISTENCY,
            target = 14,
            sortOrder = 6,
            rotationGroup = 1
        ),
        MonthlyAchievement(
            id = "host_3",
            nameRes = R.string.ach_host_3_name,
            descriptionRes = R.string.ach_host_3_desc,
            emoji = "\uD83C\uDFAA",
            category = AchievementCategory.SOCIAL,
            target = 3,
            sortOrder = 7,
            rotationGroup = 1
        ),
        MonthlyAchievement(
            id = "category_dominance",
            nameRes = R.string.ach_category_dominance_name,
            descriptionRes = R.string.ach_category_dominance_desc,
            emoji = "\uD83C\uDFC6",
            category = AchievementCategory.DIVERSITY,
            target = 20,
            sortOrder = 8,
            rotationGroup = 1
        ),
        MonthlyAchievement(
            id = "early_bird",
            nameRes = R.string.ach_early_bird_name,
            descriptionRes = R.string.ach_early_bird_desc,
            emoji = "\uD83C\uDF05",
            category = AchievementCategory.SPECIAL,
            isHidden = true,
            target = 3,
            sortOrder = 9,
            rotationGroup = 1
        ),

        // ── ROTACIÓN B (meses pares) ──
        MonthlyAchievement(
            id = "supporter_10",
            nameRes = R.string.ach_supporter_10_name,
            descriptionRes = R.string.ach_supporter_10_desc,
            emoji = "\uD83D\uDCAC",
            category = AchievementCategory.SOCIAL,
            target = 10,
            sortOrder = 10,
            rotationGroup = 2
        ),
        MonthlyAchievement(
            id = "five_categories",
            nameRes = R.string.ach_five_categories_name,
            descriptionRes = R.string.ach_five_categories_desc,
            emoji = "\uD83C\uDF08",
            category = AchievementCategory.DIVERSITY,
            target = 5,
            sortOrder = 11,
            rotationGroup = 2
        ),
        MonthlyAchievement(
            id = "all_categories",
            nameRes = R.string.ach_all_categories_name,
            descriptionRes = R.string.ach_all_categories_desc,
            emoji = "\uD83C\uDF1F",
            category = AchievementCategory.DIVERSITY,
            target = 7,
            sortOrder = 12,
            rotationGroup = 2
        ),
        MonthlyAchievement(
            id = "bad_habit_slayer",
            nameRes = R.string.ach_bad_habit_slayer_name,
            descriptionRes = R.string.ach_bad_habit_slayer_desc,
            emoji = "\uD83D\uDEAB",
            category = AchievementCategory.SPECIAL,
            isHidden = true,
            target = 15,
            sortOrder = 13,
            rotationGroup = 2
        ),

        // ── ROTACIÓN C (meses 3, 6, 9, 12) ──
        MonthlyAchievement(
            id = "perfect_week",
            nameRes = R.string.ach_perfect_week_name,
            descriptionRes = R.string.ach_perfect_week_desc,
            emoji = "\uD83D\uDCC5",
            category = AchievementCategory.CONSISTENCY,
            target = 7,
            sortOrder = 14,
            rotationGroup = 3
        ),
        MonthlyAchievement(
            id = "double_day",
            nameRes = R.string.ach_double_day_name,
            descriptionRes = R.string.ach_double_day_desc,
            emoji = "\u26A1",
            category = AchievementCategory.PROGRESS,
            target = 10,
            sortOrder = 15,
            rotationGroup = 3
        ),
        MonthlyAchievement(
            id = "comeback_king",
            nameRes = R.string.ach_comeback_king_name,
            descriptionRes = R.string.ach_comeback_king_desc,
            emoji = "\uD83D\uDC51",
            category = AchievementCategory.SPECIAL,
            isHidden = true,
            target = 1,
            sortOrder = 16,
            rotationGroup = 3
        ),

        // ── ROTACIÓN D (meses 4, 8, 12) ──
        MonthlyAchievement(
            id = "morning_rush",
            nameRes = R.string.ach_morning_rush_name,
            descriptionRes = R.string.ach_morning_rush_desc,
            emoji = "\uD83C\uDF04",
            category = AchievementCategory.CONSISTENCY,
            target = 5,
            sortOrder = 17,
            rotationGroup = 4
        ),
        MonthlyAchievement(
            id = "favorite_habit",
            nameRes = R.string.ach_favorite_habit_name,
            descriptionRes = R.string.ach_favorite_habit_desc,
            emoji = "\uD83D\uDC9B",
            category = AchievementCategory.PROGRESS,
            isHidden = true,
            target = 25,
            sortOrder = 18,
            rotationGroup = 4
        ),
    )

    private val CORE = ACHIEVEMENT_POOL.filter { it.rotationGroup == 0 }
    private val ROTATION_A = ACHIEVEMENT_POOL.filter { it.rotationGroup == 1 }
    private val ROTATION_B = ACHIEVEMENT_POOL.filter { it.rotationGroup == 2 }
    private val ROTATION_C = ACHIEVEMENT_POOL.filter { it.rotationGroup == 3 }
    private val ROTATION_D = ACHIEVEMENT_POOL.filter { it.rotationGroup == 4 }

    fun getAchievementsForMonth(month: Int): List<MonthlyAchievement> {
        val selected = mutableListOf<MonthlyAchievement>()
        selected.addAll(CORE)

        val isOdd = month % 2 == 1
        val isDivBy3 = month % 3 == 0
        val isDivBy4 = month % 4 == 0

        if (isOdd) selected.addAll(ROTATION_A)
        else selected.addAll(ROTATION_B)

        if (isDivBy3) selected.addAll(ROTATION_C)
        if (isDivBy4) selected.addAll(ROTATION_D)

        return selected.sortedBy { it.sortOrder }
    }

    fun getDefinitionById(id: String): MonthlyAchievement? =
        ACHIEVEMENT_POOL.find { it.id == id }
}
