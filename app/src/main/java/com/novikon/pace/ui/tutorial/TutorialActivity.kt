package com.novikon.pace.ui.tutorial

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.button.MaterialButton
import com.novikon.pace.R
import com.novikon.pace.ui.main.MainActivity
import com.novikon.pace.utils.SettingsManager

class TutorialActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager
    private lateinit var dotsLayout: LinearLayout
    private lateinit var nextButton: MaterialButton
    private lateinit var prevButton: MaterialButton
    private lateinit var settingsManager: SettingsManager

    private val pages = listOf(
        TutorialPage(
            imageRes = R.drawable.picky_saludo,
            titleRes = R.string.tutorial_welcome_title,
            descRes = R.string.tutorial_welcome_desc,
            screenshotRes = null
        ),
        TutorialPage(
            imageRes = R.drawable.picky_senyalando,
            titleRes = R.string.tutorial_habits_title,
            descRes = R.string.tutorial_habits_desc,
            screenshotRes = R.drawable.habitos_diarios
        ),
        TutorialPage(
            imageRes = R.drawable.picky_amor,
            titleRes = R.string.tutorial_circles_title,
            descRes = R.string.tutorial_circles_desc,
            screenshotRes = R.drawable.circulos_menu
        ),
        TutorialPage(
            imageRes = R.drawable.picky_euforico,
            titleRes = R.string.tutorial_motivation_title,
            descRes = R.string.tutorial_motivation_desc,
            screenshotRes = R.drawable.habitos_logros1
        ),
        TutorialPage(
            imageRes = R.drawable.picky_orgulloso,
            titleRes = R.string.tutorial_ready_title,
            descRes = R.string.tutorial_ready_desc,
            screenshotRes = null
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        settingsManager = SettingsManager(this)

        viewPager = findViewById(R.id.viewPager)
        dotsLayout = findViewById(R.id.dotsLayout)
        nextButton = findViewById(R.id.nextButton)
        prevButton = findViewById(R.id.prevButton)

        viewPager.adapter = TutorialPagerAdapter(pages)
        updateUI(0)

        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                updateUI(position)
            }
            override fun onPageScrollStateChanged(state: Int) {}
        })

        nextButton.setOnClickListener {
            val current = viewPager.currentItem
            if (current < pages.size - 1) {
                viewPager.currentItem = current + 1
            } else {
                settingsManager.markTutorialShown()
                startActivity(Intent(this, MainActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        }

        prevButton.setOnClickListener {
            val current = viewPager.currentItem
            if (current > 0) {
                viewPager.currentItem = current - 1
            }
        }
    }

    private fun updateUI(position: Int) {
        updateDots(position)
        updateButtons(position)
    }

    private fun updateDots(currentPage: Int) {
        dotsLayout.removeAllViews()
        pages.forEachIndexed { index, _ ->
            val dot = TextView(this).apply {
                text = if (index == currentPage) "\u25CF" else "\u25CB"
                textSize = 18f
                setTextColor(
                    if (index == currentPage) getColor(R.color.accent_primary)
                    else getColor(R.color.text_tertiary)
                )
                setPadding(6, 0, 6, 0)
            }
            dotsLayout.addView(dot)
        }
    }

    private fun updateButtons(position: Int) {
        val isLastPage = position == pages.size - 1
        val isFirstPage = position == 0

        prevButton.visibility = if (isFirstPage) View.GONE else View.VISIBLE
        nextButton.text = if (isLastPage) getString(R.string.tutorial_start) else getString(R.string.tutorial_next)
    }

    private data class TutorialPage(
        val imageRes: Int,
        val titleRes: Int,
        val descRes: Int,
        val screenshotRes: Int?
    )

    private inner class TutorialPagerAdapter(
        private val pages: List<TutorialPage>
    ) : PagerAdapter() {

        override fun getCount(): Int = pages.size

        override fun isViewFromObject(view: View, obj: Any): Boolean = view == obj

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val inflater = LayoutInflater.from(container.context)
            val view = inflater.inflate(R.layout.item_tutorial_page, container, false)

            val page = pages[position]

            val screenshotImage = view.findViewById<ImageView>(R.id.screenshotImage)
            val dimOverlay = view.findViewById<View>(R.id.dimOverlay)
            val pickyImage = view.findViewById<ImageView>(R.id.pickyImage)
            val titleText = view.findViewById<TextView>(R.id.titleText)
            val descText = view.findViewById<TextView>(R.id.descText)

            pickyImage.setImageResource(page.imageRes)
            titleText.setText(page.titleRes)
            descText.setText(page.descRes)

            if (page.screenshotRes != null) {
                screenshotImage.setImageResource(page.screenshotRes)
                screenshotImage.visibility = View.VISIBLE
                dimOverlay.visibility = View.VISIBLE
            } else {
                screenshotImage.visibility = View.GONE
                dimOverlay.visibility = View.GONE
            }

            container.addView(view)
            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            container.removeView(obj as View)
        }
    }
}
