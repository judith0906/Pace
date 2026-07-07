package com.novikon.pace.utils

object DurationParser {

    fun parseToMillis(duration: String): Long {
        val trimmed = duration.trim().lowercase()

        if (trimmed.contains("todo el día") || trimmed.contains("all day")) {
            return 16L * 60 * 60 * 1000
        }

        val number = trimmed.replace(Regex("[^0-9.,]"), "")
            .replace(",", ".")
            .toDoubleOrNull() ?: return 60L * 60 * 1000

        val minutes = when {
            trimmed.contains("hora") || trimmed.contains("hour") -> (number * 60).toLong()
            trimmed.contains("min") || trimmed.contains("minuto") -> number.toLong()
            trimmed.contains("seg") || trimmed.contains("segundo") -> (number / 60).toLong()
            else -> number.toLong()
        }

        return (minutes.coerceAtLeast(1)) * 60 * 1000
    }
}
