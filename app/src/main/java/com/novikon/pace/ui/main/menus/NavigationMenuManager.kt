package com.novikon.pace.ui.main.menus

import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.novikon.pace.R
import com.novikon.pace.ui.achievements.MonthlyAchievementsActivity
import com.novikon.pace.ui.habits.HabitHistoryActivity
import com.novikon.pace.ui.settings.SettingsActivity

/**
 * Gestiona la barra lateral izquierda (menú de navegación general).
 * Responsable de configurar los ítems del menú y manejar sus clics.
 */
class NavigationMenuManager(
    private val activity: AppCompatActivity,
    private val navigationView: NavigationView,
    private val drawerLayout: DrawerLayout,
    private val settingsLauncher: ActivityResultLauncher<Intent>,
    private val onNavigateToDailyHabits: () -> Unit,
    private val onNavigateToCircles: () -> Unit
) {
    fun setup() {
        styleMenuItems()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            handleMenuClick(menuItem)
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    // Aplica negrita al ítem de ajustes para destacarlo visualmente
    private fun styleMenuItems() {
        navigationView.menu.findItem(R.id.menu_settings)?.let { item ->
            val spannable = SpannableString(item.title)
            spannable.setSpan(StyleSpan(Typeface.BOLD), 0, spannable.length, 0)
            item.title = spannable
        }
    }
    private fun handleMenuClick(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.menu_habits_history ->
                activity.startActivity(Intent(activity, HabitHistoryActivity::class.java))
            R.id.menu_daily_habits ->
                onNavigateToDailyHabits()
            R.id.menu_circles ->
                onNavigateToCircles()
            R.id.menu_achievements ->
                activity.startActivity(Intent(activity, MonthlyAchievementsActivity::class.java))
            R.id.menu_settings ->
                settingsLauncher.launch(Intent(activity, SettingsActivity::class.java))
        }
    }
}