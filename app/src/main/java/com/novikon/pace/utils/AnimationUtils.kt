package com.novikon.pace.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.animation.OvershootInterpolator
import android.widget.TextView

// AnimationUtils es una caja de herramientas de animación — funciones estáticas reutilizables.
// Clases que lo usan: SplashAnimator
object AnimationUtils {

    // Crea la animación completa de una letra del splash.
    // Recibe la vista (la letra), su posición original, los valores de escala,
    // cuánto se desplaza, cuánto dura, y la tensión del rebote.
    // Devuelve un AnimatorSet listo para llamar a .start() cuando quieras.
    fun createElasticAnimation(
        view: TextView,
        originalTranslationX: Float,   // posición horizontal final (donde debe quedarse)
        scaleStart: Float,              // tamaño inicial (ej: 0.5 = mitad)
        scaleOvershoot: Float,          // tamaño máximo del rebote (ej: 1.1 = 10% más grande)
        scaleEnd: Float,                // tamaño final (ej: 1.0 = normal)
        slideDistance: Float,           // píxeles que se desplaza desde la derecha
        duration: Long,                 // duración total en milisegundos
        overshootTension: Float         // intensidad del rebote (mayor = más brusco)
    ): AnimatorSet {

        val animatorSet = AnimatorSet()

        // Animación 1: la letra aparece (alpha de 0 a 1)
        val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            this.duration = duration
        }

        // Animación 2: la letra crece en X con rebote
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", scaleStart, scaleOvershoot, scaleEnd).apply {
            this.duration = duration
        }

        // Animación 3: la letra crece en Y con rebote (igual que X para que sea uniforme)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", scaleStart, scaleOvershoot, scaleEnd).apply {
            this.duration = duration
        }

        // Animación 4: la letra se desliza desde la derecha hasta su posición final
        val slide = ObjectAnimator.ofFloat(
            view,
            "translationX",
            originalTranslationX - slideDistance,  // empieza desplazada a la derecha
            originalTranslationX                   // termina en su posición real
        ).apply {
            this.duration = duration
        }

        // Las cuatro animaciones corren a la vez (playTogether)
        animatorSet.playTogether(fadeIn, scaleX, scaleY, slide)

        // OvershootInterpolator hace que el movimiento "se pase" y luego vuelva,
        // dando el efecto elástico. La tensión controla cuánto se pasa.
        animatorSet.interpolator = OvershootInterpolator(overshootTension)

        return animatorSet
    }

    // Prepara la vista en el estado inicial ANTES de lanzar la animación.
    // Si no hicieras esto, la letra ya estaría visible antes de animarse.
    fun prepareElasticState(
        view: TextView,
        scaleStart: Float,
        originalTranslationX: Float,
        slideDistance: Float
    ) {
        view.alpha = 0f                                         // invisible
        view.scaleX = scaleStart                               // pequeña
        view.scaleY = scaleStart                               // pequeña
        view.translationX = originalTranslationX - slideDistance // desplazada a la derecha
    }
}