package com.novikon.pace.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.novikon.pace.data.SubscriptionManager

class AdManager(private val context: Context) {

    private var bannerAdView: AdView? = null
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private val subscriptionManager by lazy { SubscriptionManager(context) }

    companion object {
        // IDs de prueba — reemplazar con IDs reales de AdMob Console
        private const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }

    fun initialize() {
        MobileAds.initialize(context) {}
    }

    fun loadAndShowBanner(container: ViewGroup) {
        if (subscriptionManager.isPremium) return

        bannerAdView?.let { removeBanner(it) }

        val adView = AdView(context).apply {
            adUnitId = BANNER_AD_UNIT_ID
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, 360))
        }
        bannerAdView = adView

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        container.addView(adView, params)

        adView.loadAd(AdRequest.Builder().build())
    }

    fun hideBanner() {
        bannerAdView?.visibility = View.GONE
    }

    fun showBanner() {
        if (subscriptionManager.isPremium) return
        bannerAdView?.visibility = View.VISIBLE
    }

    fun removeBannerIfPresent() {
        bannerAdView?.let { removeBanner(it) }
        bannerAdView = null
    }

    private fun removeBanner(adView: AdView) {
        adView.removeAllViews()
        (adView.parent as? ViewGroup)?.removeView(adView)
    }

    fun loadInterstitial() {
        if (subscriptionManager.isPremium) return
        if (isInterstitialLoading) return
        isInterstitialLoading = true

        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                }
            })
    }

    fun showInterstitial(activity: Activity, retries: Int = 5, onDismissed: () -> Unit = {}) {
        if (subscriptionManager.isPremium) {
            onDismissed()
            return
        }

        interstitialAd?.let { ad ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitial()
                    onDismissed()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    loadInterstitial()
                    onDismissed()
                }
            }
            ad.show(activity)
        } ?: run {
            if (retries > 0 && isInterstitialLoading) {
                Handler(Looper.getMainLooper()).postDelayed({
                    showInterstitial(activity, retries - 1, onDismissed)
                }, 1000)
            } else {
                loadInterstitial()
                onDismissed()
            }
        }
    }

    // Muestra interstitial con cooldown por slot.
    // Cada punto de la app usa un slot distinto para evitar
    // que se solapen los tiempos de espera.
    fun showInterstitialWithCooldown(
        activity: Activity,
        slot: String,
        cooldownMs: Long = 30 * 60 * 1000L,
        onDismissed: () -> Unit = {}
    ) {
        val prefs = context.getSharedPreferences("pace_ads", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastShow = prefs.getLong("last_interstitial_$slot", 0)
        if (now - lastShow < cooldownMs) return
        prefs.edit().putLong("last_interstitial_$slot", now).apply()
        showInterstitial(activity, onDismissed = onDismissed)
    }

    fun cleanup() {
        removeBannerIfPresent()
        interstitialAd = null
    }
}
