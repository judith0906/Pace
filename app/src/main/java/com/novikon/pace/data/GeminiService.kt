package com.novikon.pace.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.novikon.pace.BuildConfig
import com.novikon.pace.models.AdviceContent
import com.novikon.pace.models.Habit
import com.novikon.pace.ui.stats.StatsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class GeminiResult {
    data class Success(val advice: AdviceContent) : GeminiResult()
    data class Error(val message: String) : GeminiResult()
    data object NoKey : GeminiResult()
}

object GeminiService {

    private val models = listOf(
        "gemini-flash-latest",
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash-lite"
    )

    private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val RETRY_DELAY_MS = 2000L
    private const val BETWEEN_MODELS_DELAY_MS = 800L

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun getAdvice(
        statsData: StatsData,
        habits: List<Habit>,
        languageCode: String
    ): GeminiResult = withContext(Dispatchers.IO) {
        val rawKey = BuildConfig.GEMINI_API_KEY
        if (rawKey.isBlank() || rawKey.trim() == "null") {
            android.util.Log.w("GeminiService", "API key not configured in local.properties")
            return@withContext GeminiResult.NoKey
        }

        val apiKey = rawKey.trim()
        android.util.Log.i("GeminiService", "Using API key: ${apiKey.take(12)}... (len=${apiKey.length})")

        val prompt = buildPrompt(statsData, habits, languageCode)
        val requestBody = buildJsonRequestBody(prompt)

        for (model in models) {
            android.util.Log.i("GeminiService", "Trying model: $model")
            val url = "$API_BASE/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "null body"

                when (response.code) {
                    200 -> {
                        val parsed = parseAdviceResponse(body)
                        if (parsed != null) {
                            return@withContext GeminiResult.Success(parsed)
                        }
                        android.util.Log.w("GeminiService", "Model $model returned 200 but parse failed")
                    }
                    429 -> {
                        android.util.Log.w("GeminiService",
                            "Model $model: HTTP 429. Body: ${body.take(300)}")
                    }
                    404 -> {
                        android.util.Log.w("GeminiService", "Model $model: HTTP 404")
                    }
                    403 -> {
                        android.util.Log.w("GeminiService",
                            "Model $model: HTTP 403. Body: ${body.take(200)}")
                        return@withContext GeminiResult.Error(
                            "La API key no tiene acceso a Gemini. " +
                                    "Obtén una gratis en https://aistudio.google.com/apikey"
                        )
                    }
                    else -> {
                        android.util.Log.w("GeminiService",
                            "Model $model: HTTP ${response.code}. Body: ${body.take(200)}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GeminiService", "Model $model exception", e)
            }

            delay(BETWEEN_MODELS_DELAY_MS)
        }

        GeminiResult.Error(
            "Cuota de Gemini agotada (429). Las claves nuevas empiezan con AQ. " +
                    "Crea un nuevo proyecto en https://aistudio.google.com/apikey " +
                    "o habilita facturación (sin costo en free tier)."
        )
    }

    private fun buildJsonRequestBody(prompt: String): String {
        return gson.toJson(mapOf(
            "contents" to listOf(mapOf(
                "parts" to listOf(mapOf("text" to prompt))
            )),
            "generationConfig" to mapOf(
                "temperature" to 0.7,
                "maxOutputTokens" to 900,
                "topP" to 0.95,
                "topK" to 40
            )
        ))
    }

    private fun buildPrompt(
        data: StatsData,
        habits: List<Habit>,
        languageCode: String
    ): String {
        val languageNames = mapOf(
            "es" to "español",
            "en" to "inglés",
            "fr" to "francés"
        )
        val langName = languageNames[languageCode] ?: "español"

        val categories = data.categoryPercentages.entries
            .sortedByDescending { it.value }
            .joinToString(", ") { (cat, pct) ->
                "${cat.name}: ${String.format("%.0f", pct)}%"
            }

        val monthlyDays = if (data.monthlyDays.isNotEmpty()) {
            data.monthlyDays.entries.sortedBy { it.key }
                .joinToString(", ") { (day, done) ->
                    "Día $day: ${if (done) "✅" else "❌"}"
                }
        } else "Sin datos"

        val yearly = data.yearlyConsistency.entries.sortedBy { it.key }
            .joinToString(", ") { (month, pct) ->
                "Mes $month: ${String.format("%.0f", pct)}%"
            }

        val top5Str = data.top5Habits.withIndex()
            .joinToString("\n") { (i, pair) ->
                "  ${i + 1}. ${pair.first} — ${pair.second} veces"
            }

        val allHabits = if (habits.isNotEmpty()) {
            habits.joinToString("\n") { h ->
                "  ${h.emoji} ${h.name} (${h.category.name})"
            }
        } else "No hay hábitos registrados"

        val lowestCategories = if (data.categoryPercentages.isNotEmpty()) {
            data.categoryPercentages.minByOrNull { it.value }?.let {
                "${it.key.name} (${String.format("%.0f", it.value)}%)"
            } ?: "ninguna"
        } else "ninguna"

        return """Eres un consejero amigable especializado en hábitos y productividad. NO estás conversando con nadie ni respondiendo a un mensaje: estás escribiendo contenido editorial breve y personalizado, como una cápsula de consejos.

=== BASE DE TÉCNICAS VALIDADAS (usa estas, no inventes otras) ===

MENTAL: mindfulness 5 min al despertar, journaling de 3 líneas antes de dormir, respiración 4-7-8 ante estrés, limitar pantallas 1h antes de dormir.
PHYSICAL: progresión gradual (empezar con la mitad del objetivo), asociar ejercicio a música/podcast, entrenar con compañía o grupo, agendar como una cita fija.
BAD_HABITS (dejar hábito): regla de los 5 minutos (posponer el impulso 5 min), sustituir el gesto (chicle, agua, caminar), quitar el disparador del entorno, avisar a alguien de confianza del objetivo.
STUDY: Pomodoro 25/5, ambiente sin móvil a la vista, recompensa pequeña tras cada bloque, repasar en las primeras 24h (repetición espaciada).
ROUTINE: encadenar el hábito nuevo a uno ya automático (habit stacking, ej. "después de lavarme los dientes..."), dejar preparado lo necesario la noche antes, recompensa inmediata tras completarlo.

=== REGLAS DE ESTILO OBLIGATORIAS (aplican a TODOS los campos) ===
- PROHIBIDO usar frases conversacionales o de "respuesta": nada de "basándome en tus datos", "he visto que", "veo que tienes", "te recomiendo", "como puedes observar". Ve directo al grano.
- PROHIBIDO saludar, presentar el consejo o hacer meta-comentarios ("aquí tienes algunos consejos:").
- Cada campo sigue SIEMPRE esta estructura: una frase de contexto muy breve (máx. 25-30 palabras) + salto de línea + una lista de 2-3 bullets accionables, cada uno empezando por "• " y máximo 15 palabras por bullet.
- Los bullets son imperativos y concretos ("Reserva 10 min antes de dormir", no "podrías intentar reservar algo de tiempo").
- Cero relleno, cero frases motivacionales genéricas sin acción asociada.
- Usar palabras amigables, que se puedan entender del todo, no uses tecnicismos (Ej de lo que NO hay que hacer: "El enfoque físico carece de tracción por falta de ejecución constante" - Ej: de lo que SI hay que hacer:  "Te cuesta ser constante con el ejercicio")
- Elige 2-3 técnicas de la BASE DE TÉCNICAS VALIDADAS según la categoría del hábito, adaptándolas al caso concreto del usuario
- Usa $langName en todo el contenido.

Responde ÚNICAMENTE con un objeto JSON válido (SIN markdown, SIN bloques de código, SIN texto adicional fuera del JSON). Usa esta estructura exacta, donde cada valor sigue el formato "frase\n• bullet\n• bullet":

{
  "summaryAdvice": "...",
  "categoryAdvice": "...",
  "top5Advice": "...",
  "monthlyEvaluation": "...",
  "generalAdvice": "...",
  "specificTips": "..."
}

=== DATOS DEL USUARIO ===

📊 RESUMEN:
- Racha actual: ${data.currentStreak} días
- Racha máxima: ${data.maxStreak} días
- Constancia mensual: ${data.monthlyConsistency}%
${if (data.starHabitName.isNotBlank()) "• Hábito estrella: ${data.starHabitEmoji} ${data.starHabitName}" else ""}

📂 DISTRIBUCIÓN POR CATEGORÍA:
$categories

📅 DÍAS DEL MES:
$monthlyDays

📈 EVOLUCIÓN ANUAL:
$yearly

🏆 TOP 5 HÁBITOS:
$top5Str

📋 TODOS LOS HÁBITOS DEL USUARIO:
$allHabits

=== CONTENIDO POR CAMPO (máx. total indicado, frase + bullets incluidos) ===

1️⃣ summaryAdvice (máx 65 palabras):
   • Constancia < 50%: frase sobre empezar en pequeño + bullets con 2 micro-acciones concretas
   • Constancia 50-79%: frase sobre consolidar + bullets con 2 acciones de refuerzo
   • Constancia ≥ 80%: frase de reconocimiento breve + bullets con 2 formas de mantener el nivel

2️⃣ categoryAdvice (máx 90 palabras, escala con el nº de categorías presentes en $categories):
   • Si el usuario tiene UNA sola categoría: frase breve sobre esa categoría + 2 bullets (elogio si ≥60%, consejo si <60%).
   • Si tiene VARIAS categorías: 
     - Frase de apertura muy breve que las mencione todas de forma natural (ej. "Repaso por área:").
     - Un bullet por cada categoría presente en $categories (máx 4 bullets; si hay más de 4, prioriza las 2 con mejor % y las 2 con peor %).
     - Cada bullet: nombre de la categoría + elogio si ≥70% de constancia, o consejo concreto de 1 técnica si <70%.
     - No repitas la misma técnica en dos bullets distintos.

3️⃣ top5Advice (máx 65 palabras):
   • Frase sobre el patrón del top 5 + bullets con 2 técnicas (habit stacking, recompensa, registro visual)

4️⃣ monthlyEvaluation (máx 60 palabras):
   • Frase-titular del mes (ej. "Mes con altibajos entre semana") + bullets con el patrón detectado (días que fallan) y 1 sugerencia

5️⃣ generalAdvice (máx 55 palabras):
   • Frase inspiradora corta + bullets con 1-2 acciones inmediatas

6️⃣ specificTips (máx 80 palabras):
   • Frase señalando la categoría más floja: $lowestCategories
   • Bullets con 3 técnicas MUY concretas según el tipo de hábito predominante:
     - BAD_HABITS → técnica de sustitución (ej. regla 5 min, chicle, respirar)
     - PHYSICAL → hacerlo agradable (música, compañía, progresión)
     - STUDY → Pomodoro, ambiente, recompensa
     - ROUTINE → automatizar con trigger-acción-recompensa
     - MENTAL → meditación guiada, journaling, micro-pausas

IMPORTANTE: Responde SOLO con el JSON. Sin acentos en las claves JSON. Sin texto antes o después. Recuerda: NO es una conversación, es contenido editorial tipo revista."""
    }

    private fun parseAdviceResponse(responseBody: String): AdviceContent? {
        return try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            val candidates = json.getAsJsonArray("candidates")
            if (candidates == null || candidates.size() == 0) {
                android.util.Log.w("GeminiService", "No candidates in response")
                return null
            }

            val text = candidates[0].asJsonObject
                .getAsJsonObject("content")
                .getAsJsonArray("parts")[0].asJsonObject
                .get("text")?.asString ?: run {
                    android.util.Log.w("GeminiService", "No text in candidate")
                    return null
                }

            val cleaned = extractJson(text.trim())
            val adviceJson = try {
                JsonParser.parseString(cleaned).asJsonObject
            } catch (e: Exception) {
                android.util.Log.w("GeminiService", "JSON parse error on cleaned text", e)
                return null
            }

            AdviceContent(
                summaryAdvice = adviceJson.get("summaryAdvice")?.asString ?: "",
                categoryAdvice = adviceJson.get("categoryAdvice")?.asString ?: "",
                top5Advice = adviceJson.get("top5Advice")?.asString ?: "",
                monthlyEvaluation = adviceJson.get("monthlyEvaluation")?.asString ?: "",
                generalAdvice = adviceJson.get("generalAdvice")?.asString ?: "",
                specificTips = adviceJson.get("specificTips")?.asString ?: ""
            )
        } catch (e: Exception) {
            android.util.Log.w("GeminiService", "parseAdviceResponse failed", e)
            null
        }
    }

    private fun extractJson(text: String): String {
        var cleaned = text.trim()
        if (cleaned.startsWith("```")) {
            val firstNewline = cleaned.indexOf('\n')
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1).trim()
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length - 3).trim()
            }
        }
        return cleaned
    }
}
