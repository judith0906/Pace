package com.novikon.pace.ui.circles

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.novikon.pace.R

class CircleMembersAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bitmaps = mutableListOf<Bitmap?>()
    private val activeTargets = mutableListOf<CustomTarget<Bitmap>>()  // ← NUEVO
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 4f
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0F0F0")
    }
    private var placeholderBitmap: Bitmap? = null

    init {
        val drawable = androidx.core.content.ContextCompat.getDrawable(
            context,
            R.drawable.ic_person
        )

        drawable?.let {
            val bitmap = Bitmap.createBitmap(
                it.intrinsicWidth,
                it.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(bitmap)
            it.setBounds(0, 0, canvas.width, canvas.height)
            it.draw(canvas)

            placeholderBitmap = bitmap
        }

        android.util.Log.d("AVATAR", "placeholderBitmap = $placeholderBitmap")
    }

    // Cancela todas las peticiones Glide activas
    private fun cancelPendingLoads() {
        try {
            activeTargets.forEach { Glide.with(context.applicationContext).clear(it) }
        } catch (_: Exception) { }
        activeTargets.clear()
    }

    fun loadMembers(photoUrls: List<String?>, totalCount: Int) {
        cancelPendingLoads()  // ← NUEVO: cancelar antes de empezar
        bitmaps.clear()
        val toLoad = photoUrls.take(5)
        var loaded = 0

        if (toLoad.isEmpty()) {
            repeat(minOf(totalCount, 5)) { bitmaps.add(null) }
            invalidate()
            return
        }

        repeat(toLoad.size) { bitmaps.add(null) }

        // Snapshot de la lista actual para que los callbacks no afecten a cargas futuras
        val currentBitmaps = bitmaps  // ← misma lista, pero loaded/toLoad.size están capturados

        toLoad.forEachIndexed { index, url ->
            val target = object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    currentBitmaps[index] = resource
                    loaded++
                    if (loaded == toLoad.size) invalidate()
                }
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    currentBitmaps[index] = null
                }
                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    loaded++
                    if (loaded == toLoad.size) invalidate()
                }
            }
            activeTargets.add(target)  // ← NUEVO: guardar referencia
            Log.d("AVATAR", "url[$index] = $url")
            Glide.with(context)
                .asBitmap()
                .load(url)
                .circleCrop()
                .into(target)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelPendingLoads()  // ← NUEVO: limpiar al destruirse la vista
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f * 0.32f  // radio de cada avatar
        val dist = minOf(w, h) / 2f * 0.42f  // distancia al centro

        val count = bitmaps.size.coerceAtLeast(1)

        // Posiciones según número de miembros
        val positions: List<Pair<Float, Float>> = when (count) {
            1 -> listOf(Pair(cx, cy))
            2 -> listOf(
                Pair(cx - dist * 0.6f, cy),
                Pair(cx + dist * 0.6f, cy)
            )
            3 -> listOf(
                Pair(cx, cy - dist * 0.7f),
                Pair(cx - dist * 0.6f, cy + dist * 0.4f),
                Pair(cx + dist * 0.6f, cy + dist * 0.4f)
            )
            4 -> listOf(
                Pair(cx, cy - dist * 0.7f),
                Pair(cx - dist * 0.7f, cy),
                Pair(cx + dist * 0.7f, cy),
                Pair(cx, cy + dist * 0.7f)
            )
            else -> listOf(
                Pair(cx, cy - dist),
                Pair(cx - dist * 0.95f, cy - dist * 0.31f),
                Pair(cx - dist * 0.59f, cy + dist * 0.81f),
                Pair(cx + dist * 0.59f, cy + dist * 0.81f),
                Pair(cx + dist * 0.95f, cy - dist * 0.31f)
            )
        }

        positions.forEachIndexed { i, (x, y) ->
            val bmp = bitmaps.getOrNull(i)
            if (bmp != null) {
                // Sombra/borde blanco
                canvas.drawCircle(x, y, r + 3f, strokePaint)
                // Dibujar bitmap circular
                val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val matrix = Matrix()
                val scale = (r * 2f) / minOf(bmp.width, bmp.height).toFloat()
                matrix.setScale(scale, scale)
                matrix.postTranslate(x - r, y - r)
                shader.setLocalMatrix(matrix)
                paint.shader = shader
                canvas.drawCircle(x, y, r, paint)
                paint.shader = null
            } else {
                // Placeholder gris con ic_person
                canvas.drawCircle(x, y, r + 3f, strokePaint)
                paint.color = Color.parseColor("#E0E0E0")
                canvas.drawCircle(x, y, r, paint)
                placeholderBitmap?.let { ph ->
                    val pSize = (r * 1.0f).toInt()
                    val scaled = Bitmap.createScaledBitmap(ph, pSize, pSize, true)
                    canvas.drawBitmap(scaled, x - pSize / 2f, y - pSize / 2f, null)
                }
            }
        }
    }
}