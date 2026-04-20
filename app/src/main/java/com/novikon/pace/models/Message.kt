package com.novikon.pace.models

// Data class que representa un mensaje dentro de un círculo.
// Se guarda en Firebase bajo circles/{circleId}/messages/{messageId}/
//
// Estructura en Firebase:
// circles/{circleId}/messages/{messageId}/
//   text        → contenido del mensaje
//   senderId    → userId de quien lo envió
//   senderName  → nombre de quien lo envió (guardado aquí para no hacer
//                 otra consulta a Firebase cada vez que se pinta un mensaje)
//   timestamp   → momento exacto del envío en milisegundos
//
// El messageId lo genera Firebase automáticamente con push() —
// es un key cronológico que ya ordena los mensajes de más antiguo a más nuevo.
data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val timestamp: Long = 0L,
    val type: String = "TEXT",

    val eventId: String = "",
    val eventHabitName: String = "",
    val eventScheduledAt: Long = 0L,
    val eventCreatedBy: String = "",
    val eventTimeZoneId: String = "",
    val eventJoinedIds: List<String> = emptyList(),
    val eventJoinedNames: List<String> = emptyList(),
    val eventDeclinedIds: List<String> = emptyList(),
    val eventDeclinedNames: List<String> = emptyList(),

    val captureAllowedIds: List<String> = emptyList(),
    val photoUrl: String = "",

    val messageTemplateKey: String = "",
    val messageTemplateParams: List<String> = emptyList()
)