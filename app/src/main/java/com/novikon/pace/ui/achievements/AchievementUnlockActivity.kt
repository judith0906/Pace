package com.novikon.pace.ui.achievements

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.novikon.pace.R
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.utils.applySystemBarInsets

class AchievementUnlockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACHIEVEMENT_NAME = "achievement_name"
        const val EXTRA_ACHIEVEMENT_EMOJI = "achievement_emoji"
    }

    private lateinit var rootOverlay: FrameLayout
    private lateinit var celebrationCard: MaterialCardView
    private lateinit var achievementEmoji: TextView
    private lateinit var achievementName: TextView
    private lateinit var confettiBackground: ImageView
    private lateinit var goToAchievements: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var confettiRunning = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_achievement_unlock)
        applySystemBarInsets()

        val name = intent.getStringExtra(EXTRA_ACHIEVEMENT_NAME) ?: ""
        val emoji = intent.getStringExtra(EXTRA_ACHIEVEMENT_EMOJI) ?: "\uD83C\uDFC6"

        rootOverlay = findViewById(R.id.rootOverlay)
        celebrationCard = findViewById(R.id.celebrationCard)
        achievementEmoji = findViewById(R.id.achievementEmoji)
        achievementName = findViewById(R.id.achievementName)
        confettiBackground = findViewById(R.id.confettiBackground)
        goToAchievements = findViewById(R.id.goToAchievements)

        achievementEmoji.text = emoji
        achievementName.text = name

        goToAchievements.setOnClickListener {
            val intent = Intent(this, MonthlyAchievementsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }

        rootOverlay.setOnClickListener {
            dismissWithAnimation()
        }

        startEntranceAnimation()
        startConfettiAnimation()
    }

    private fun startEntranceAnimation() {
        val fadeIn = ObjectAnimator.ofFloat(rootOverlay, "alpha", 0f, 1f)
        fadeIn.duration = 300
        fadeIn.interpolator = DecelerateInterpolator()

        val scaleX = ObjectAnimator.ofFloat(celebrationCard, "scaleX", 0.3f, 1.05f, 1f)
        scaleX.duration = 600
        scaleX.interpolator = BounceInterpolator()

        val scaleY = ObjectAnimator.ofFloat(celebrationCard, "scaleY", 0.3f, 1.05f, 1f)
        scaleY.duration = 600
        scaleY.interpolator = BounceInterpolator()

        val cardFadeIn = ObjectAnimator.ofFloat(celebrationCard, "alpha", 0f, 1f)
        cardFadeIn.duration = 400
        cardFadeIn.interpolator = DecelerateInterpolator()

        val set = AnimatorSet()
        set.playTogether(fadeIn, cardFadeIn)
        set.play(scaleX).with(scaleY).after(100)
        set.start()
    }

    private fun startConfettiAnimation() {
        if (!confettiRunning) return

        val rotation = ObjectAnimator.ofFloat(confettiBackground, "rotation", 0f, 360f)
        rotation.duration = 4000
        rotation.interpolator = LinearInterpolator()
        rotation.repeatCount = ObjectAnimator.INFINITE

        val pulse = ObjectAnimator.ofFloat(confettiBackground, "alpha", 0.2f, 0.5f)
        pulse.duration = 1500
        pulse.interpolator = LinearInterpolator()
        pulse.repeatCount = ObjectAnimator.INFINITE
        pulse.repeatMode = ObjectAnimator.REVERSE

        val set = AnimatorSet()
        set.playTogether(rotation, pulse)
        set.start()
    }

    private fun dismissWithAnimation() {
        confettiRunning = false
        handler.removeCallbacksAndMessages(null)

        val fadeOut = ObjectAnimator.ofFloat(rootOverlay, "alpha", 1f, 0f)
        fadeOut.duration = 250
        fadeOut.interpolator = DecelerateInterpolator()

        val scaleX = ObjectAnimator.ofFloat(celebrationCard, "scaleX", 1f, 0.3f)
        scaleX.duration = 200

        val scaleY = ObjectAnimator.ofFloat(celebrationCard, "scaleY", 1f, 0.3f)
        scaleY.duration = 200

        fadeOut.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(p0: Animator) {}
            override fun onAnimationRepeat(p0: Animator) {}
            override fun onAnimationEnd(p0: Animator) { finish() }
            override fun onAnimationCancel(p0: Animator) { finish() }
        })

        val set = AnimatorSet()
        set.playTogether(fadeOut, scaleX, scaleY)
        set.start()
    }

    override fun onBackPressed() {
        dismissWithAnimation()
    }
}
