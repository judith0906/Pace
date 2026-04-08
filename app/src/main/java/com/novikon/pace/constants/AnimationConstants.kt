package com.novikon.pace.constants

// AnimationConstants → valores de la animación del splash.
//
// Filosofía: simple y con presencia, como Netflix o Gmail.
// El logo aparece como una unidad con confianza, la barra
// crece despacio transmitiendo constancia, y todo respira
// con calma antes de desvanecerse.
object AnimationConstants {

    // ── DURACIONES ────────────────────────────────────────────────────────────

    // El logo aparece despacio — presencia, no urgencia
    const val LOGO_FADE_IN_DURATION = 1000L

    // La barra crece durante 1.8 segundos — lenta y constante
    const val PROGRESS_LINE_DURATION = 1800L

    // Ancho final de la barra en dp — contenido, no exagerado
    const val PROGRESS_LINE_WIDTH_DP = 160

    // "Novikon" aparece con suavidad al final
    const val NOVIKON_FADE_DURATION = 800L

    // El fade out final es lento — la pantalla se apaga sin prisa
    const val FADE_OUT_DURATION = 600L

    // ── DELAYS ───────────────────────────────────────────────────────────────

    // Pausa inicial — deja al usuario orientarse
    const val INITIAL_DELAY = 300L

    // La barra empieza cuando el logo ya está visible
    const val PROGRESS_LINE_START_DELAY = 900L

    // "Novikon" espera a que la barra termine
    const val NOVIKON_START_DELAY = 400L

    // Tiempo en pantalla antes del fade out — momento de calma
    const val FINAL_PAUSE = 1400L

    // ── EFECTOS VISUALES ─────────────────────────────────────────────────────

    // El logo empieza al 92% y crece a su tamaño real —
    // apenas perceptible pero da presencia y vida
    const val LOGO_SCALE_START = 0.92f
    const val LOGO_SCALE_END = 1.0f
}