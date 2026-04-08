package com.novikon.pace.helpers

import android.content.Context
import com.novikon.pace.constants.Language
import com.novikon.pace.constants.PrefsConstants
import java.util.Locale

// Objeto encargado de gestionar el idioma de la app.
// Es un "object" porque no necesita estado propio — solo lee y escribe
// en SharedPreferences.
//
// Se usa en TODAS las Activities antes de setContentView(),
// para que el idioma se aplique antes de que se dibuje la pantalla.
object LanguageHelper {

    // Clave donde guardamos el código del idioma elegido por el usuario
    private const val LANGUAGE_KEY = "language"

    // Devuelve el código del idioma guardado en SharedPreferences.
    // Si el usuario nunca ha cambiado el idioma, devuelve español por defecto.
    fun getLanguageCode(context: Context): String {
        val prefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(LANGUAGE_KEY, "es") ?: "es"
    }

    // Guarda el código del idioma elegido por el usuario.
    // Después de llamar a esto, la Activity debe llamar a recreate()
    // para que la pantalla se redibuje con el nuevo idioma.
    fun changeLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(LANGUAGE_KEY, languageCode).apply()
    }

    // Lee el idioma guardado y lo aplica al contexto.
    // Se llama al inicio de cada Activity antes de setContentView().
    fun applyLanguage(context: Context) {
        val languageCode = getLanguageCode(context)
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = context.resources.configuration
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    // Devuelve el nombre legible del idioma actual para mostrarlo
    // en la pantalla de ajustes (ej: "Español", "English", "Français").
    fun getLanguageDisplayName(context: Context): String {
        val code = getLanguageCode(context)
        return Language.values().find { it.code == code }?.displayName ?: "Español"
    }
}