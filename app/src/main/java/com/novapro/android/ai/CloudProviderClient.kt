package com.novapro.android.ai

import com.novapro.android.data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** API adapters for user-configured OpenAI, Anthropic Claude, and DeepSeek keys. */
object CloudProviderClient {
    /** Fetches model IDs that the provider API makes visible to this user's key. */
    suspend fun listModels(provider: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Bitte trage zuerst deinen API-Key für $provider ein." }
        val normalized = provider.lowercase()
        val (endpoint, headers) = when (normalized) {
            "openai" -> "https://api.openai.com/v1/models" to emptyMap()
            "deepseek" -> "https://api.deepseek.com/models" to emptyMap()
            "claude", "anthropic" -> "https://api.anthropic.com/v1/models?limit=1000" to mapOf("x-api-key" to apiKey.trim(), "anthropic-version" to "2023-06-01")
            else -> throw IllegalArgumentException("Modellliste für '$provider' wird nicht unterstützt.")
        }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            if (!headers.containsKey("x-api-key")) setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(raw) }.getOrNull()
                ?: throw IllegalStateException("$provider gab keine gültige JSON-Antwort zurück (HTTP $status).")
            if (status !in 200..299) {
                val details = response.optJSONObject("error")?.optString("message")
                    ?: response.optString("message", "")
                throw IllegalStateException("Modellliste konnte nicht geladen werden (HTTP $status): ${details.ifBlank { "API-Key oder Zugriff prüfen." }}")
            }
            val data = response.optJSONArray("data") ?: response.optJSONArray("models") ?: JSONArray()
            buildList {
                for (i in 0 until data.length()) {
                    val row = data.optJSONObject(i) ?: continue
                    val id = row.optString("id", row.optString("name", "")).removePrefix("models/").trim()
                    if (id.isNotBlank()) add(id)
                }
            }.distinct().sorted()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun generate(provider: String, model: String, apiKey: String, history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Bitte hinterlege zuerst deinen API-Key für $provider in den NOVA-Einstellungen." }
        val normalized = provider.lowercase()
        when (normalized) {
            "openai" -> chatCompletions("https://api.openai.com/v1/chat/completions", model, apiKey, history)
            "deepseek" -> chatCompletions("https://api.deepseek.com/chat/completions", model, apiKey, history)
            "claude", "anthropic" -> anthropicMessages(model, apiKey, history)
            else -> throw IllegalArgumentException("Cloud-Anbieter '$provider' wird nicht unterstützt.")
        }
    }

    private fun messages(history: List<ChatMessage>): JSONArray = JSONArray().apply {
        history.filter { it.role == "user" || it.role == "assistant" || it.role == "model" }.forEach { message ->
            put(JSONObject().put("role", if (message.role == "model") "assistant" else message.role).put("content", message.text))
        }
    }

    private fun chatCompletions(endpoint: String, model: String, apiKey: String, history: List<ChatMessage>): String {
        val body = JSONObject().put("model", model).put("messages", messages(history)).put("stream", false)
        return request(endpoint, apiKey, body, extraHeaders = emptyMap()) { response ->
            val choices = response.optJSONArray("choices") ?: JSONArray()
            choices.optJSONObject(0)?.optJSONObject("message")?.optString("content", "").orEmpty().trim()
        }
    }

    private fun anthropicMessages(model: String, apiKey: String, history: List<ChatMessage>): String {
        val allowed = JSONArray().apply {
            history.filter { it.role == "user" || it.role == "assistant" || it.role == "model" }.forEach { message ->
                put(JSONObject().put("role", if (message.role == "model") "assistant" else message.role).put("content", message.text))
            }
        }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 4096)
            .put("system", "You are NOVA, a helpful and direct AI assistant. Answer in the user's language and be honest when unsure.")
            .put("messages", allowed)
        return request("https://api.anthropic.com/v1/messages", apiKey, body, mapOf("anthropic-version" to "2023-06-01", "x-api-key" to apiKey)) { response ->
            val content = response.optJSONArray("content") ?: JSONArray()
            buildString {
                for (i in 0 until content.length()) {
                    val item = content.optJSONObject(i) ?: continue
                    if (item.optString("type") == "text") append(item.optString("text"))
                }
            }.trim()
        }
    }

    private fun request(
        endpoint: String,
        apiKey: String,
        body: JSONObject,
        extraHeaders: Map<String, String>,
        parse: (JSONObject) -> String
    ): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!extraHeaders.containsKey("x-api-key")) setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            extraHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(raw) }.getOrNull()
                ?: throw IllegalStateException("Cloud-Anbieter gab keine gültige JSON-Antwort zurück (HTTP $status).")
            if (status !in 200..299) {
                val details = response.optJSONObject("error")?.optString("message")
                    ?: response.optString("message", "")
                throw IllegalStateException("Cloud API Fehler ($status): ${details.ifBlank { "Anfrage fehlgeschlagen." }}")
            }
            return parse(response).ifBlank { throw IllegalStateException("Der Cloud-Anbieter hat keine Textantwort zurückgegeben.") }
        } finally {
            connection.disconnect()
        }
    }
}
