package com.novikon.pace.constants

// Constantes relacionadas con SharedPreferences.
// Todos los managers de la app usan PREFS_NAME para abrir
// el mismo archivo de preferencias, evitando tener múltiples
// archivos dispersos para la misma app.
object PrefsConstants {
    const val PREFS_NAME = "pace_prefs"
}