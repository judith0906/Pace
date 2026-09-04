package com.novikon.pace.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import com.novikon.pace.R
import com.novikon.pace.data.SubscriptionManager
import com.novikon.pace.ui.premium.PremiumActivity

/**
 * Bloquea funciones Premium para usuarios free mostrando un diálogo que
 * redirige al paywall cuando el usuario aún no es Premium.
 */
object PremiumGate {

    /** Devuelve true si el usuario tiene Premium activo. */
    fun isPremium(context: Context): Boolean =
        SubscriptionManager(context).isPremium

    /**
     * Muestra el diálogo de "función Premium" y abre el paywall si el usuario
     * pulsa "Ir a Premium".
     */
    fun showGate(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.premium_gate_title)
            .setMessage(R.string.premium_gate_message)
            .setPositiveButton(R.string.premium_gate_go) { _, _ ->
                activity.startActivity(Intent(activity, PremiumActivity::class.java))
            }
            .setNegativeButton(R.string.premium_gate_later, null)
            .show()
    }
}