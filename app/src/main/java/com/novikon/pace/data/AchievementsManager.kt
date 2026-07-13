package com.novikon.pace.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.novikon.pace.constants.PrefsConstants
import com.novikon.pace.models.MonthlyAchievement
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

class AchievementsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PrefsConstants.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    companion object {
        private const val KEY_ACHIEVEMENTS_PREFIX = "achievements_"
        private const val KEY_LAST_NOTIFIED = "achievements_last_notified"
        private const val KEY_LAST_CHECKED_MONTH = "achievements_last_checked_month"
    }

    private fun getUserId(): String? = auth.currentUser?.uid

    fun getCurrentYearMonth(): String = dateFormat.format(Date())

    fun getCurrentMonth(): Int = Calendar.getInstance().get(Calendar.MONTH) + 1

    fun getActiveDefinitions(): List<MonthlyAchievement> =
        AchievementDefinitions.getAchievementsForMonth(getCurrentMonth())

    suspend fun loadAchievements(yearMonth: String): List<MonthlyAchievement> {
        val userId = getUserId() ?: return freshDefinitions()

        return try {
            val fresh = fetchFromFirebase(userId, yearMonth)
            if (fresh != null) {
                saveToLocalCache(yearMonth, fresh)
                fresh
            } else {
                freshDefinitions()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            freshDefinitions()
        }
    }

    suspend fun saveAchievements(yearMonth: String, achievements: List<MonthlyAchievement>) {
        saveToLocalCache(yearMonth, achievements)
        val userId = getUserId() ?: return
        saveToFirebase(userId, yearMonth, achievements)
    }

    fun isNewMonth(): Boolean {
        val currentMonth = getCurrentYearMonth()
        val lastChecked = prefs.getString(KEY_LAST_CHECKED_MONTH, "")
        return currentMonth != lastChecked
    }

    fun markMonthChecked() {
        prefs.edit { putString(KEY_LAST_CHECKED_MONTH, getCurrentYearMonth()) }
    }

    fun getLastNotifiedAchievements(): Set<String> {
        return prefs.getStringSet(KEY_LAST_NOTIFIED, emptySet()) ?: emptySet()
    }

    fun markNotified(achievementIds: Set<String>) {
        prefs.edit { putStringSet(KEY_LAST_NOTIFIED, achievementIds) }
    }

    private suspend fun fetchFromFirebase(userId: String, yearMonth: String): List<MonthlyAchievement>? {
        return suspendCancellableCoroutine { continuation ->
            database.getReference("users/$userId/achievements/$yearMonth")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) {
                            continuation.resume(null)
                            return
                        }

                        val results = mutableListOf<MonthlyAchievement>()
                        snapshot.children.forEach { achSnapshot ->
                            val id = achSnapshot.key ?: return@forEach
                            val def = AchievementDefinitions.getDefinitionById(id)

                            if (def != null) {
                                val progress = achSnapshot.child("progress")
                                    .getValue(Int::class.java) ?: 0
                                val completed = achSnapshot.child("completed")
                                    .getValue(Boolean::class.java) ?: false
                                val completedAt = achSnapshot.child("completedAt")
                                    .getValue(Long::class.java) ?: 0L

                                results.add(
                                    def.copy(
                                        progress = progress,
                                        completed = completed,
                                        completedAt = completedAt
                                    )
                                )
                            }
                        }

                        continuation.resume(
                            if (results.isEmpty()) null else results.sortedBy { it.sortOrder }
                        )
                    }

                    override fun onCancelled(error: DatabaseError) {
                        continuation.resume(null)
                    }
                })
        }
    }

    private fun saveToFirebase(userId: String, yearMonth: String, achievements: List<MonthlyAchievement>) {
        val ref = database.getReference("users/$userId/achievements/$yearMonth")
        val updates = mutableMapOf<String, Any>()

        achievements.forEach { ach ->
            val path = "${ach.id}"
            val data = mapOf<String, Any>(
                "progress" to ach.progress,
                "completed" to ach.completed,
                "completedAt" to ach.completedAt
            )
            updates[path] = data
        }

        ref.updateChildren(updates)
    }

    private fun saveToLocalCache(yearMonth: String, achievements: List<MonthlyAchievement>) {
        val key = "$KEY_ACHIEVEMENTS_PREFIX$yearMonth"

        val data = achievements.joinToString("|") { ach ->
            "${ach.id},${ach.progress},${ach.completed},${ach.completedAt}"
        }

        prefs.edit { putString(key, data) }
    }

    private fun freshDefinitions(): List<MonthlyAchievement> {
        return getActiveDefinitions()
    }
}
