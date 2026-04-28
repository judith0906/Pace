package com.novikon.pace.models

// Modelo de usuario bloqueado: almacena identificador y metadatos del bloqueo.
data class BlockedUser(
    val userId: String,
    val displayName: String,
    val photoUrl: String? = null
)