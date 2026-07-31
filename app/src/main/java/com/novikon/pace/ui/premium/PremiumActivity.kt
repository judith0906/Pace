package com.novikon.pace.ui.premium

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.novikon.pace.R
import com.novikon.pace.data.SubscriptionManager
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.utils.applySystemBarInsets

// Pantalla de Pace Premium: presenta los beneficios de la suscripción,
// permite elegir plan (mensual/anual) y dispara la compra.
class PremiumActivity : AppCompatActivity() {

    private lateinit var subscriptionManager: SubscriptionManager

    private lateinit var monthlyPlanCard: MaterialCardView
    private lateinit var yearlyPlanCard: MaterialCardView
    private lateinit var subscribeButton: MaterialButton
    private lateinit var trialSubtitle: TextView

    private var isYearlySelected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_premium)
        applySystemBarInsets()

        subscriptionManager = SubscriptionManager(this)

        initializeViews()
        setupListeners()
        renderState()
    }

    private fun initializeViews() {
        monthlyPlanCard = findViewById(R.id.monthlyPlanCard)
        yearlyPlanCard = findViewById(R.id.yearlyPlanCard)
        subscribeButton = findViewById(R.id.subscribeButton)
        trialSubtitle = findViewById(R.id.trialSubtitle)
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }

        monthlyPlanCard.setOnClickListener {
            isYearlySelected = false
            updatePlanSelection()
        }

        yearlyPlanCard.setOnClickListener {
            isYearlySelected = true
            updatePlanSelection()
        }

        subscribeButton.setOnClickListener {
            // Billing de Google Play se integrará cuando se cree el producto
            // en Play Console. Por ahora mostramos un aviso.
            Toast.makeText(this, getString(R.string.premium_coming_soon), Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.restoreButton).setOnClickListener {
            Toast.makeText(this, getString(R.string.premium_coming_soon), Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.termsButton).setOnClickListener {
            Toast.makeText(this, getString(R.string.premium_coming_soon), Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderState() {
        if (subscriptionManager.isPremium) {
            monthlyPlanCard.isEnabled = false
            yearlyPlanCard.isEnabled = false
            subscribeButton.text = getString(R.string.premium_current_plan)
            subscribeButton.setOnClickListener(null)
            trialSubtitle.text = ""
        } else {
            updatePlanSelection()
        }
    }

    private fun updatePlanSelection() {
        val selectedColor = getColor(R.color.premium_gold)
        val defaultColor = getColor(R.color.border_color)

        monthlyPlanCard.strokeColor = if (isYearlySelected) defaultColor else selectedColor
        monthlyPlanCard.strokeWidth = if (isYearlySelected) 1 else 2
        yearlyPlanCard.strokeColor = if (isYearlySelected) selectedColor else defaultColor
        yearlyPlanCard.strokeWidth = if (isYearlySelected) 2 else 1

        if (isYearlySelected) {
            subscribeButton.text = getString(R.string.premium_cta_yearly)
        } else {
            subscribeButton.text = getString(R.string.premium_cta_monthly)
        }
    }
}
