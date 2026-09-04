package com.novikon.pace.ui.premium

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.Purchase
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.novikon.pace.R
import com.novikon.pace.billing.BillingManager
import com.novikon.pace.data.SubscriptionManager
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.models.Subscription
import com.novikon.pace.utils.applySystemBarInsets
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// Pantalla de Pace Premium: presenta los beneficios de la suscripción,
// permite elegir plan (mensual/anual) y lanza la compra vía Google Play Billing.
class PremiumActivity : AppCompatActivity() {

    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var billingManager: BillingManager

    private lateinit var monthlyPlanCard: MaterialCardView
    private lateinit var yearlyPlanCard: MaterialCardView
    private lateinit var subscribeButton: MaterialButton
    private lateinit var trialSubtitle: TextView
    private lateinit var monthlyPriceView: TextView
    private lateinit var yearlyPriceView: TextView

    private var isYearlySelected = true
    private var isDestroyed = false

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

        billingManager = BillingManager(
            activity = this,
            onPurchaseResult = { purchase -> handlePurchaseResult(purchase) },
            onProductsLoaded = { updatePrices() }
        )
    }

    override fun onResume() {
        super.onResume()
        if (!isDestroyed) {
            billingManager.restorePurchases { purchase ->
                if (!isDestroyed && !isFinishing) {
                    if (purchase != null && !subscriptionManager.isPremium) {
                        handlePurchaseResult(purchase)
                    } else {
                        updatePrices()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        isDestroyed = true
        billingManager.endConnection()
        super.onDestroy()
    }

    private fun initializeViews() {
        monthlyPlanCard = findViewById(R.id.monthlyPlanCard)
        yearlyPlanCard = findViewById(R.id.yearlyPlanCard)
        subscribeButton = findViewById(R.id.subscribeButton)
        trialSubtitle = findViewById(R.id.trialSubtitle)
        monthlyPriceView = findViewById(R.id.monthlyPriceView)
        yearlyPriceView = findViewById(R.id.yearlyPriceView)
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
            if (!isYearlySelected) {
                billingManager.launchMonthly()
            } else {
                billingManager.launchYearly()
            }
        }

        findViewById<TextView>(R.id.restoreButton).setOnClickListener {
            if (!isDestroyed && !isFinishing) {
                billingManager.restorePurchases { purchase ->
                    if (!isDestroyed && !isFinishing) {
                        runOnUiThread {
                            if (!isDestroyed && !isFinishing) {
                                if (purchase != null) {
                                    handlePurchaseResult(purchase)
                                    Toast.makeText(
                                        this@PremiumActivity,
                                        getString(R.string.premium_restore_success),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        this@PremiumActivity,
                                        getString(R.string.premium_restore_error),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                }
            }
        }

        findViewById<TextView>(R.id.termsButton).setOnClickListener {
            if (!isDestroyed && !isFinishing) {
                Toast.makeText(this, getString(R.string.premium_coming_soon), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderState() {
        if (isDestroyed || isFinishing) return
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
        if (isDestroyed || isFinishing) return
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

    private fun updatePrices() {
        if (isDestroyed || isFinishing) return
        billingManager.monthlyPrice()?.let { monthlyPriceView.text = it }
        billingManager.yearlyPrice()?.let { yearlyPriceView.text = it }
    }

    private fun handlePurchaseResult(purchase: Purchase?) {
        if (purchase == null) return
        if (isDestroyed || isFinishing) return

        val productId = purchase.products.firstOrNull() ?: BillingManager.MONTHLY_PRODUCT_ID
        val isYearly = productId == BillingManager.YEARLY_PRODUCT_ID
        val durationDays = if (isYearly) 365L else 30L

        val subscription = Subscription(
            isActive = true,
            productId = productId,
            purchaseToken = purchase.purchaseToken,
            activatedAt = purchase.purchaseTime,
            expiresAt = purchase.purchaseTime + TimeUnit.DAYS.toMillis(durationDays),
            lastValidatedAt = System.currentTimeMillis(),
            platform = "android"
        )

        subscriptionManager.updateLocalCache(subscription)
        if (!isDestroyed && !isFinishing) {
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    Toast.makeText(this, getString(R.string.premium_subscribe_success), Toast.LENGTH_SHORT)
                        .show()
                    renderState()
                }
            }
        }

        lifecycleScope.launch {
            subscriptionManager.saveSubscriptionToFirebase(subscription)
        }
    }
}