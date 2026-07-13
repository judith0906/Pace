package com.novikon.pace.ui.achievements

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import com.novikon.pace.models.MonthlyAchievement

class AchievementsAdapter(
    private var achievements: List<MonthlyAchievement> = emptyList(),
    private val onItemClick: (MonthlyAchievement) -> Unit = {}
) : RecyclerView.Adapter<AchievementsAdapter.AchievementViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchievementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_achievement, parent, false)
        return AchievementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AchievementViewHolder, position: Int) {
        holder.bind(achievements[position])
    }

    override fun getItemCount(): Int = achievements.size

    fun submitList(list: List<MonthlyAchievement>) {
        achievements = list
        notifyDataSetChanged()
    }

    inner class AchievementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val emojiText: TextView = itemView.findViewById(R.id.achievementEmoji)
        private val nameText: TextView = itemView.findViewById(R.id.achievementName)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.achievementProgress)
        private val progressText: TextView = itemView.findViewById(R.id.achievementProgressText)
        private val completedCheck: ImageView = itemView.findViewById(R.id.completedCheck)

        fun bind(achievement: MonthlyAchievement) {
            val context = itemView.context

            if (achievement.isHidden && !achievement.completed) {
                emojiText.text = "\u2753"
                nameText.text = context.getString(R.string.achievement_hidden_title)
                progressText.text = context.getString(R.string.achievement_hidden_desc)
                progressBar.progress = 0
                completedCheck.visibility = View.GONE
                itemView.alpha = 0.6f
            } else {
                emojiText.text = achievement.emoji
                nameText.text = context.getString(achievement.nameRes)

                val pct = if (achievement.target > 0)
                    (achievement.progress * 100) / achievement.target else 0
                progressBar.progress = minOf(pct, 100)

                progressText.text = "${achievement.progress}/${achievement.target}"

                completedCheck.visibility = if (achievement.completed) View.VISIBLE else View.GONE
                itemView.alpha = 1.0f
            }

            itemView.setOnClickListener { onItemClick(achievement) }
        }
    }
}
