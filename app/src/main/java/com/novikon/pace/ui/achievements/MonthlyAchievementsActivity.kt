package com.novikon.pace.ui.achievements

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import com.novikon.pace.data.AchievementsManager
import com.novikon.pace.data.CirclesRealtimeManager
import com.novikon.pace.data.HabitsManager
import com.novikon.pace.data.MonthlyAchievementsAnalyzer
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.models.MonthlyAchievement
import com.novikon.pace.utils.applySystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MonthlyAchievementsActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var titleText: TextView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView

    private lateinit var achievementsManager: AchievementsManager
    private lateinit var habitsManager: HabitsManager
    private lateinit var adapter: AchievementsAdapter

    private var currentYearMonth = ""
    private var achievements = listOf<MonthlyAchievement>()

    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_monthly_achievements)
        applySystemBarInsets()

        achievementsManager = AchievementsManager(this)
        habitsManager = HabitsManager(this)

        currentYearMonth = achievementsManager.getCurrentYearMonth()

        initializeViews()
        setupRecyclerView()
        setupListeners()

        lifecycleScope.launch {
            loadData()
        }
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.backButton)
        titleText = findViewById(R.id.titleText)
        progressText = findViewById(R.id.progressText)
        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.achievementsRecyclerView)
    }

    private fun setupRecyclerView() {
        adapter = AchievementsAdapter(
            onItemClick = { achievement -> showAchievementDetailDialog(achievement) }
        )
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }
    }

    private suspend fun loadData() {
        val habits = habitsManager.getSelectedHabitsAsync()
        val logs = habitsManager.getHabitLogs()

        val cachedAchievements = achievementsManager.loadAchievements(currentYearMonth)
        val analyzer = MonthlyAchievementsAnalyzer()

        val input = MonthlyAchievementsAnalyzer.AnalysisInput(
            logs = logs,
            habits = habits,
            eventsCreated = 0,
            supportMessagesSent = 0
        )

        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH) + 1
        val result = analyzer.analyze(input, currentMonth)

        val merged = result.achievements.map { calc ->
            val cached = cachedAchievements.find { it.id == calc.id }
            if (cached != null && cached.completedAt > 0L && calc.completed) {
                calc.copy(completedAt = cached.completedAt)
            } else {
                calc
            }
        }

        achievementsManager.saveAchievements(currentYearMonth, merged)
        achievements = merged

        val newlyCompleted = merged.filter { it.completed }
            .filter { calc ->
                val cached = cachedAchievements.find { it.id == calc.id }
                cached == null || !cached.completed
            }

        if (newlyCompleted.isNotEmpty()) {
            val circlesManager = CirclesRealtimeManager(this@MonthlyAchievementsActivity)
            val firstCompleted = newlyCompleted.first()
            val achievementName = getString(firstCompleted.nameRes)

            withContext(Dispatchers.IO) {
                circlesManager.sendAchievementNotificationToAllCircles(
                    achievementName = achievementName,
                    context = this@MonthlyAchievementsActivity
                )
            }
        }

        withContext(Dispatchers.Main) {
            updateUI()
            if (newlyCompleted.isNotEmpty()) {
                showCelebration(newlyCompleted.first())
            }
        }
    }

    private fun updateUI() {
        val date = monthFormat.parse(currentYearMonth) ?: Date()
        val display = displayFormat.format(date)
        titleText.text = getString(R.string.achievements_title_month, display.replaceFirstChar { it.uppercase() })

        val total = achievements.size
        val completedCount = achievements.count { it.completed }
        progressText.text = getString(R.string.achievements_progress, completedCount, total)

        val pct = if (total > 0) (completedCount * 100) / total else 0
        progressBar.progress = pct

        adapter.submitList(achievements)
    }

    private fun showCelebration(achievement: MonthlyAchievement) {
        val name = getString(achievement.nameRes)
        val emoji = if (achievement.isHidden && !achievement.completed) "\u2753" else achievement.emoji

        val intent = Intent(this, AchievementUnlockActivity::class.java).apply {
            putExtra(AchievementUnlockActivity.EXTRA_ACHIEVEMENT_NAME, name)
            putExtra(AchievementUnlockActivity.EXTRA_ACHIEVEMENT_EMOJI, emoji)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private fun showAchievementDetailDialog(achievement: MonthlyAchievement) {
        val name: String
        val description: String
        val isHiddenDisplay: Boolean

        if (achievement.isHidden && !achievement.completed) {
            name = getString(R.string.achievement_hidden_title)
            description = getString(R.string.achievement_hidden_desc)
            isHiddenDisplay = true
        } else {
            name = getString(achievement.nameRes)
            description = getString(achievement.descriptionRes)
            isHiddenDisplay = false
        }

        val emoji = if (isHiddenDisplay) "\u2753" else achievement.emoji
        val statusEmoji = if (achievement.completed) "\u2705" else "\u23F3"
        val statusText = if (achievement.completed)
            "${getString(R.string.achievement_completed_title)}"
        else
            "${achievement.progress}/${achievement.target}"

        val message = buildString {
            append("${emoji} $name\n\n")
            append("$description\n\n")
            append("$statusEmoji $statusText")
        }

        AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(message)
            .setPositiveButton(R.string.close) { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
