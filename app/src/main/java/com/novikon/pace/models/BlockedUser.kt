package com.novikon.pace.models

data class BlockedUser(
    val userId: String,
    val displayName: String,
    val photoUrl: String? = null
)