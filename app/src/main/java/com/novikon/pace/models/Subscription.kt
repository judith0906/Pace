package com.novikon.pace.models

data class Subscription(
    val isActive: Boolean = false,
    val productId: String = "",
    val purchaseToken: String = "",
    val isTrial: Boolean = false,
    val activatedAt: Long = 0L,
    val expiresAt: Long = 0L,
    val lastValidatedAt: Long = 0L,
    val platform: String = "android"
)
