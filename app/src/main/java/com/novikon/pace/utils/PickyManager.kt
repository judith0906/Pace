package com.novikon.pace.utils

import com.novikon.pace.R

object PickyManager {

    fun getPickyState(event: PickyEvent): PickyState {
        return when (event) {
    PickyEvent.ACHIEVEMENT_UNLOCKED -> PickyState(
        R.drawable.picky_orgulloso,
        R.string.picky_achievement_unlocked
    )
    PickyEvent.APP_OPEN -> PickyState(
        R.drawable.picky_normal,
        R.string.picky_app_open
    )
            PickyEvent.HABIT_COMPLETED -> PickyState(
                R.drawable.picky_logro,
                R.string.picky_habit_done
            )
            PickyEvent.ALL_COMPLETED -> PickyState(
                R.drawable.picky_euforico,
                R.string.picky_all_done
            )
            PickyEvent.STREAK_BROKEN -> PickyState(
                R.drawable.picky_triste,
                R.string.picky_streak_broken
            )
            PickyEvent.MILESTONE_REACHED -> PickyState(
                R.drawable.picky_orgulloso,
                R.string.picky_milestone
            )
            PickyEvent.CIRCLE_JOINED -> PickyState(
                R.drawable.picky_saludo,
                R.string.picky_circle_joined
            )
            PickyEvent.MOMENT_SHARED -> PickyState(
                R.drawable.picky_amor,
                R.string.picky_moment_shared
            )
            PickyEvent.NIGHT_TIME -> PickyState(
                R.drawable.picky_dormido,
                R.string.picky_night
            )
        }
    }
}

enum class PickyEvent {
    ACHIEVEMENT_UNLOCKED,
    APP_OPEN, HABIT_COMPLETED, ALL_COMPLETED,
    STREAK_BROKEN, MILESTONE_REACHED,
    CIRCLE_JOINED, MOMENT_SHARED, NIGHT_TIME
}

data class PickyState(
    val imageRes: Int,
    val messageRes: Int
)
