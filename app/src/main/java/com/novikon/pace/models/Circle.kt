package com.novikon.pace.models

// Modelo de un circulo: guarda datos base del grupo y su configuracion.
data class Circle(
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val memberCount: Int = 0,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val memberPhotoUrls: List<String?> = emptyList()
)