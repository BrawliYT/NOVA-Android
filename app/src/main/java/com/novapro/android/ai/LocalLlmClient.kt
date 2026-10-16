package com.novapro.android.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.novapro.android.data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * Runs an imported .litertlm model fully on-device.
 * The engine is reused while the user stays in the same chat/model to avoid reloading it for every message.
 */
object LocalLlmClient : Closeable {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedChatId: String? = null
    private var loadedModelPath: String? = null

    suspend fun generate(
        modelPath: String,
        chatId: String,
        previousMessages: List<ChatMessage>,
        prompt: String,
        cachePath: String
    ): String = withContext(Dispatchers.IO) {
        require(modelPath.isNotBlank()) { "Bitte wähle in den Einstellungen zuerst ein .litertlm-Modell aus." }
        if (engine == null || conversation == null || loadedChatId != chatId || loadedModelPath != modelPath) {
            releaseResources()
            val nextEngine = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = cachePath
                )
            )
            nextEngine.initialize()
            val initial = previousMessages.mapNotNull { item ->
                when (item.role) {
                    "user" -> Message.user(item.text)
                    "assistant", "model" -> Message.model(item.text)
                    else -> null
                }
            }
            val nextConversation = nextEngine.createConversation(
                ConversationConfig(
                    systemInstruction = com.google.ai.edge.litertlm.Contents.of(
                        "You are NOVA, a helpful local AI assistant. Answer in the user's language, be concise, and be honest when unsure."
                    ),
                    initialMessages = initial
                )
            )
            engine = nextEngine
            conversation = nextConversation
            loadedChatId = chatId
            loadedModelPath = modelPath
        }

        val output = StringBuilder()
        conversation!!.sendMessageAsync(prompt).collect { message ->
            output.append(message.toString())
        }
        output.toString().trim().ifBlank { "Das lokale Modell hat keine Antwort ausgegeben. Prüfe, ob die Modell-Datei kompatibel ist." }
    }

    override fun close() {
        releaseResources()
    }

    private fun releaseResources() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
        loadedChatId = null
        loadedModelPath = null
    }
}
