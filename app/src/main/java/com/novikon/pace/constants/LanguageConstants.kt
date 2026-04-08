package com.novikon.pace.constants

// Enum con los idiomas disponibles en la app.
// Está separado de LanguageHelper para que si en el futuro
// quieres añadir más idiomas, solo toques este archivo.
//
// Cada valor tiene:
//   - code: el código ISO que usa Android internamente
//   - displayName: el nombre que ve el usuario en ajustes
enum class Language(val code: String, val displayName: String) {
    SPANISH("es", "Español"),
    ENGLISH("en", "English"),
    FRENCH("fr", "Français")
}