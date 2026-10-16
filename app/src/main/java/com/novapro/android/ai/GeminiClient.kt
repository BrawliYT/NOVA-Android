package com.novapro.android.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.novapro.android.data.ChatMessage
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {
    private const val DEFAULT_MODEL = "gemini-3.8-flash"
    private const val API_ROOT = "https://generativelanguage.googleapis.com/v1beta/models/"
    private const val SYSTEM_PROMPT = "You are NOVA, a helpful, direct AI assistant. Answer in the user's language. Be clear and honest when unsure."

    suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Bitte trage zuerst deinen Gemini-API-Key ein." }
        val found = linkedSetOf<String>()
        var pageToken: String? = null
        var pages = 0
        do {
            val query = buildString {
                append("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000")
                pageToken?.let { append("&pageToken=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
            }
            val connection = (URL(query).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("x-goog-api-key", apiKey.trim())
            }
            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val response = runCatching { JSONObject(raw) }.getOrNull()
                    ?: throw IllegalStateException("Gemini gab keine gültige JSON-Antwort zurück (HTTP $status).")
                if (status !in 200..299) {
                    val details = response.optJSONObject("error")?.optString("message").orEmpty()
                    throw IllegalStateException("Gemini-Modellliste fehlgeschlagen (HTTP $status): ${details.ifBlank { "API-Key oder Zugriff prüfen." }}")
                }
                val models = response.optJSONArray("models") ?: JSONArray()
                for (i in 0 until models.length()) {
                    val item = models.optJSONObject(i) ?: continue
                    val methods = item.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                    val supportsGeneration = (0 until methods.length()).any { methods.optString(it) == "generateContent" }
                    val name = item.optString("name", "").removePrefix("models/").trim()
                    if (supportsGeneration && name.isNotBlank()) found += name
                }
                pageToken = response.optString("nextPageToken").takeIf { it.isNotBlank() }
            } finally {
                connection.disconnect()
            }
            pages++
        } while (pageToken != null && pages < 10)
        found.sorted()
    }

    suspend fun generate(apiKey: String, history: List<ChatMessage>, model: String = DEFAULT_MODEL): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Bitte trage zuerst deinen Gemini-API-Key in den Einstellungen ein." }
        val contents = JSONArray()
        history.filter { it.role == "user" || it.role == "assistant" || it.role == "model" }.forEach { msg ->
            val role = if (msg.role == "user") "user" else "model"
            contents.put(
                JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 2048))

        val modelId = model.ifBlank { DEFAULT_MODEL }
        val endpoint = "$API_ROOT${java.net.URLEncoder.encode(modelId, "UTF-8")}:generateContent"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val responseStream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val details = runCatching { JSONObject(responseBody).getJSONObject("error").optString("message") }.getOrDefault("")
                throw IllegalStateException(if (details.isNotBlank()) "Gemini-Fehler ($status): $details" else "Gemini-Anfrage fehlgeschlagen (HTTP $status).")
            }
            val response = JSONObject(responseBody)
            val candidates = response.optJSONArray("candidates") ?: JSONArray()
            val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            val text = buildString {
                for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text", "").orEmpty())
            }.trim()
            if (text.isBlank()) throw IllegalStateException("Gemini hat keine Textantwort zurückgegeben.")
            text
        } finally {
            connection.disconnect()
        }
    }
}
