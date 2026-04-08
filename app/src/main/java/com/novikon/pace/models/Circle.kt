package com.novikon.pace.models

// Data class que representa un círculo (grupo de chat).
// Se guarda en Firebase bajo circles/{circleId}/
//
// La estructura en Firebase es:
// circles/{circleId}/
//   name         → nombre del círculo
//   createdBy    → userId del creador
//   createdAt    → timestamp de creación
//   members/     → mapa userId → true (para saber quién pertenece)
//   messages/    → subcolección de mensajes
//
// Además, en users/{userId}/circles/{circleId} → true
// para que cada usuario pueda consultar sus propios círculos
// de forma eficiente sin escanear todos los círculos globales.
data class Circle(
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val members: Map<String, Boolean> = emptyMap(),
    // lastMessage y lastMessageTime se usan solo para la preview en la lista —
    // no se guardan en Firebase directamente, se infieren del último mensaje
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val memberCount: Int = 0
)