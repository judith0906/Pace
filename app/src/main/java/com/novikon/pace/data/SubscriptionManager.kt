package com.novikon.pace.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.novikon.pace.constants.PrefsConstants
import com.novikon.pace.models.Subscription

class SubscriptionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PrefsConstants.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val databaseManager = RealtimeDatabaseManager()

    companion object {
        private const val KEY_IS_PREMIUM = "subscription_is_active"
        private const val KEY_PRODUCT_ID = "subscription_product_id"
        private const val KEY_IS_TRIAL = "subscription_is_trial"
        private const val KEY_EXPIRES_AT = "subscription_expires_at"
        private const val KEY_CACHE_TIMESTAMP = "subscription_cache_timestamp"
        private const val CACHE_MAX_AGE_MS = 60 * 60 * 1000L
    }

    val isPremium: Boolean
        get() {
            val cacheTimestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0)
            val cacheAge = System.currentTimeMillis() - cacheTimestamp
            if (cacheAge > 24 * 60 * 60 * 1000L && !prefs.getBoolean(KEY_IS_PREMIUM, false)) {
                return false
            }
            return prefs.getBoolean(KEY_IS_PREMIUM, false)
        }

    val currentProductId: String
        get() = prefs.getString(KEY_PRODUCT_ID, "") ?: ""

    val isTrial: Boolean
        get() = prefs.getBoolean(KEY_IS_TRIAL, false)

    fun updateLocalCache(subscription: Subscription) {
        prefs.edit {
            putBoolean(KEY_IS_PREMIUM, subscription.isActive)
            putString(KEY_PRODUCT_ID, subscription.productId)
            putBoolean(KEY_IS_TRIAL, subscription.isTrial)
            putLong(KEY_EXPIRES_AT, subscription.expiresAt)
            putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
        }
    }

    fun clearCache() {
        prefs.edit {
            remove(KEY_IS_PREMIUM)
            remove(KEY_PRODUCT_ID)
            remove(KEY_IS_TRIAL)
            remove(KEY_EXPIRES_AT)
            remove(KEY_CACHE_TIMESTAMP)
        }
    }

    suspend fun saveSubscriptionToFirebase(subscription: Subscription): Boolean {
        return databaseManager.saveSubscription(subscription)
    }

    suspend fun syncSubscriptionFromFirebase(): Subscription? {
        return try {
            val subscription = databaseManager.getSubscription()
            if (subscription != null) {
                updateLocalCache(subscription)
            }
            subscription
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
