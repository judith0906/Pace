package com.novikon.pace.ui.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import com.novikon.pace.constants.AnimationConstants
import com.novikon.pace.utils.fadeOut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// SplashAnimator lleva toda la lógica de animación del splash.
//
// Secuencia:
//   1. El logo completo aparece como unidad — fade + escala suave
//   2. Una barra fina crece de izquierda a derecha bajo el logo —
//      representa constancia y acompañamiento
//   3. "Novikon Productions" aparece con fade al final
//   4. Todo se desvanece suavemente antes de navegar
//
// Clases que lo usan: SplashActivity
class SplashAnimator(
    private val scope: CoroutineScope,
    private val logoText: TextView,
    private val paceLogo: View,
    private val novikonText: TextView,
    private val progressLine: View,
    private val onAnimationComplete: () -> Unit
) {

    fun startAnimations() {
        initializeViews()

        scope.launch {

            // Fase 1: el icono aparece solo con presencia
            delay(AnimationConstants.INITIAL_DELAY)
            animateLogo()

            // Fase 2: "PACE" aparece bajo el icono con fade suave
            // — el nombre llega después del símbolo, como confirmación
            delay(AnimationConstants.LOGO_FADE_IN_DURATION - 200L)
            ObjectAnimator.ofFloat(logoText, "alpha", 0f, 1f).apply {
                duration = 800L
                interpolator = DecelerateInterpolator()
                start()
            }

            // Fase 3: la barra crece bajo el texto
            delay(AnimationConstants.PROGRESS_LINE_START_DELAY)
            animateProgressLine()

            // Fase 4: "Novikon" aparece al terminar la barra
            delay(AnimationConstants.PROGRESS_LINE_DURATION + AnimationConstants.NOVIKON_START_DELAY)
            animateNovikonText()

            // Fase 5: calma y fade out final
            delay(AnimationConstants.NOVIKON_FADE_DURATION + AnimationConstants.FINAL_PAUSE)
            paceLogo.fadeOut(AnimationConstants.FADE_OUT_DURATION)
            logoText.fadeOut(AnimationConstants.FADE_OUT_DURATION)
            progressLine.fadeOut(AnimationConstants.FADE_OUT_DURATION)
            novikonText.fadeOut(AnimationConstants.FADE_OUT_DURATION)

            delay(AnimationConstants.FADE_OUT_DURATION)
            onAnimationComplete()
        }
    }

    // El logo aparece con fade + escala suave como una sola unidad.
    // AccelerateDecelerateInterpolator da una curva orgánica —
    // como si el logo tomara aire y lo soltara despacio.
    private fun animateLogo() {
        val fadeIn = ObjectAnimator.ofFloat(paceLogo, "alpha", 0f, 1f).apply {
            duration = AnimationConstants.LOGO_FADE_IN_DURATION
        }

        val scaleX = ObjectAnimator.ofFloat(
            paceLogo, "scaleX",
            AnimationConstants.LOGO_SCALE_START,
            AnimationConstants.LOGO_SCALE_END
        ).apply {
            duration = AnimationConstants.LOGO_FADE_IN_DURATION
        }

        val scaleY = ObjectAnimator.ofFloat(
            paceLogo, "scaleY",
            AnimationConstants.LOGO_SCALE_START,
            AnimationConstants.LOGO_SCALE_END
        ).apply {
            duration = AnimationConstants.LOGO_FADE_IN_DURATION
        }

        AnimatorSet().apply {
            playTogether(fadeIn, scaleX, scaleY)
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    // La barra crece de 0dp a PROGRESS_LINE_WIDTH_DP usando un ValueAnimator
    // que actualiza el layoutParams en cada frame — suave y constante.
    // DecelerateInterpolator hace que frene al llegar al final,
    // como un trazo que termina con calma.
    private fun animateProgressLine() {
        val density = progressLine.resources.displayMetrics.density
        val targetWidth = (AnimationConstants.PROGRESS_LINE_WIDTH_DP * density).toInt()

        // Primero hacemos visible la barra — empieza en alpha 0
        // y aparece durante el primer tramo del crecimiento
        progressLine.alpha = 0f
        progressLine.fadeIn(300)

        ValueAnimator.ofInt(0, targetWidth).apply {
            duration = AnimationConstants.PROGRESS_LINE_DURATION
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { animator ->
                // Actualizamos el ancho en cada frame de la animación
                val params = progressLine.layoutParams
                params.width = animator.animatedValue as Int
                progressLine.layoutParams = params
            }
            start()
        }
    }

    // "Novikon Productions" aparece con fade suave.
    // Sin escala ni movimiento — solo presencia discreta al final.
    private fun animateNovikonText() {
        ObjectAnimator.ofFloat(novikonText, "alpha", 0f, 1f).apply {
            duration = AnimationConstants.NOVIKON_FADE_DURATION
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun initializeViews() {
        // El logo y el texto arrancan invisibles —
        // cada uno animará por separado con su propio timing
        paceLogo.alpha = 0f
        paceLogo.scaleX = AnimationConstants.LOGO_SCALE_START
        paceLogo.scaleY = AnimationConstants.LOGO_SCALE_START
        logoText.alpha = 0f
        progressLine.alpha = 0f
        progressLine.layoutParams.width = 0
        novikonText.alpha = 0f
    }

    // Extensión local para fadeIn sin importar ViewExtensions —
    // evita dependencias innecesarias para una operación simple
    private fun View.fadeIn(duration: Long) {
        ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
            this.duration = duration
            start()
        }
    }
}