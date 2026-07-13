package com.novikon.pace.models

enum class AchievementCategory {
    CONSISTENCY, DIVERSITY, SOCIAL, PROGRESS, SPECIAL
}

data class MonthlyAchievement(
    val id: String,
    val nameRes: Int,
    val descriptionRes: Int,
    val emoji: String,
    val category: AchievementCategory,
    val isHidden: Boolean = false,
    val progress: Int = 0,
    val target: Int = 1,
    val completed: Boolean = false,
    val completedAt: Long = 0L,
    val sortOrder: Int = 0,
    val rotationGroup: Int = 0
)
