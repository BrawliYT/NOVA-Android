package com.novapro.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatConversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Neuer Chat",
    val messages: List<ChatMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

object ChatStore {
    private const val PREFS = "nova_chat_store"
    private const val KEY_CHATS = "chats_json"

    fun load(context: android.content.Context): List<ChatConversation> {
        val raw = context.getSharedPreferences(PREFS, 0).getString(KEY_CHATS, "[]") ?: "[]"
        return runCatching {
            val rows = JSONArray(raw)
            buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val messagesJson = row.optJSONArray("messages") ?: JSONArray()
                    val messages = buildList {
                        for (j in 0 until messagesJson.length()) {
                            val msg = messagesJson.getJSONObject(j)
                            add(
                                ChatMessage(
                                    id = msg.optString("id", UUID.randomUUID().toString()),
                                    role = msg.optString("role", "user"),
                                    text = msg.optString("text", ""),
                                    timestamp = msg.optLong("timestamp", 0L)
                                )
                            )
                        }
                    }
                    add(
                        ChatConversation(
                            id = row.optString("id", UUID.randomUUID().toString()),
                            title = row.optString("title", "Chat"),
                            messages = messages,
                            updatedAt = row.optLong("updatedAt", 0L)
                        )
                    )
                }
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun save(context: android.content.Context, chats: List<ChatConversation>) {
        val rows = JSONArray()
        chats.forEach { chat ->
            val messages = JSONArray()
            chat.messages.forEach { message ->
                messages.put(
                    JSONObject()
                        .put("id", message.id)
                        .put("role", message.role)
                        .put("text", message.text)
                        .put("timestamp", message.timestamp)
                )
            }
            rows.put(
                JSONObject()
                    .put("id", chat.id)
                    .put("title", chat.title)
                    .put("updatedAt", chat.updatedAt)
                    .put("messages", messages)
            )
        }
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY_CHATS, rows.toString()).apply()
    }
}
