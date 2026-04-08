package com.novikon.pace.utils

import android.animation.ObjectAnimator
import android.view.View
import android.widget.TextView
import android.graphics.LinearGradient
import android.graphics.Shader

// Este archivo contiene "funciones de extensión"  para añadir funciones nuevas a
// clases existentes sin nodificar ni heredar de ellas, esto nos sirve para evitar repeticiones de codigo
// en la animación del Splash

// Clases que lo usan: SplashAnimator


// Hace aparecer cualquier View de forma gradual (fade in).
// 'this' dentro de la función se refiere a la View sobre la que se llama.
fun View.fadeIn(duration: Long, startDelay: Long = 0) {
    ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
        this.duration = duration       // cuánto dura la animación
        this.startDelay = startDelay   // cuánto espera antes de empezar (por defecto 0)
        start()                        // la lanza inmediatamente al llamar fadeIn()
    }
}

// Hace desaparecer cualquier View de forma gradual (fade out).
// Igual que fadeIn pero al revés: de opaco a transparente.
fun View.fadeOut(duration: Long, startDelay: Long = 0) {
    ObjectAnimator.ofFloat(this, "alpha", 1f, 0f).apply {
        this.duration = duration
        this.startDelay = startDelay
        start()
    }
}

// Aplica un degradado vertical al texto de un TextView.
// Recibe un array de colores (definido en AnimationConstants.GRADIENT_COLORS)
// y los aplica de arriba a abajo sobre el texto.
//
// Se usa en SplashAnimator para que el texto "PACE" tenga el degradado gris.
fun TextView.applyVerticalGradient(colors: IntArray) {
    // post{} espera a que la View esté dibujada antes de ejecutar el código,
    // porque necesitamos conocer el tamaño real del texto (textSize)
    // para calcular el degradado correctamente.
    post {
        val shader = LinearGradient(
            0f, 0f,        // punto de inicio del degradado (arriba)
            0f, textSize,  // punto final del degradado (abajo, altura del texto)
            colors,
            null,          // null = colores distribuidos uniformemente
            Shader.TileMode.CLAMP  // fuera del rango, mantiene el color del extremo
        )
        paint.shader = shader  // aplicar el degradado al "pincel" del texto
    }
}