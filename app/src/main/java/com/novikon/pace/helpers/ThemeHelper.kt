package com.novikon.pace.helpers

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.novikon.pace.constants.PrefsConstants

// Objeto encargado de gestionar el tema de la app (claro/oscuro).
// Es un "object" porque no necesita estado propio — solo lee y escribe
// en SharedPreferences y aplica el tema globalmente.
//
// Se usa en TODAS las Activities, siempre antes de setContentView(),
// para que el tema se aplique antes de que se dibuje la pantalla.
object ThemeHelper {

    // Clave donde guardamos el modo de tema elegido por el usuario
    private const val THEME_KEY = "theme_mode"

    // Devuelve el modo de tema guardado en SharedPreferences.
    // Si el usuario nunca ha cambiado el tema, devuelve modo claro por defecto.
    fun getThemeMode(context: Context): Int {
        val prefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(
            THEME_KEY,
            AppCompatDelegate.MODE_NIGHT_NO // por defecto: modo claro
        )
    }

    // Guarda el modo de tema elegido por el usuario y lo aplica
    // inmediatamente a toda la app.
    // Se llama desde SettingsActivity cuando el usuario mueve el switch.
    fun setThemeMode(context: Context, mode: Int) {
        val prefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(THEME_KEY, mode).apply()

        // Aplica el tema a nivel global — afecta a todas las Activities
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    // Lee el tema guardado y lo aplica.
    // Se llama al inicio de cada Activity antes de setContentView()
    // para que la pantalla se dibuje ya con el tema correcto.
    fun applyTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(getThemeMode(context))
    }
}