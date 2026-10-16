package com.novapro.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novapro.android.ai.GeminiClient
import com.novapro.android.ai.CloudProviderClient
import com.novapro.android.ai.LocalLlmClient
import com.novapro.android.data.ChatConversation
import com.novapro.android.data.ChatMessage
import com.novapro.android.data.ChatStore
import com.novapro.android.security.ApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.Locale

private val Background = Color(0xFF090812)
private val SurfaceDark = Color(0xFF151322)
private val SurfaceRaised = Color(0xFF1E1A30)
private var Accent by mutableStateOf(Color(0xFF9A72FF))
private var AccentBright by mutableStateOf(Color(0xFFBEA2FF))
private val TextPrimary = Color(0xFFF5F1FF)
private val TextMuted = Color(0xFFACA5C1)

class MainActivity : ComponentActivity() {
    private var textToSpeech: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.GERMANY
            }
        }
        setContent {
            NovaTheme {
                NovaApp(onSpeak = { text ->
                    textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova-response")
                })
            }
        }
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }
}

@Composable
private fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentBright,
            onPrimary = Color(0xFF241446),
            secondary = Accent,
            background = Background,
            surface = SurfaceDark,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            surfaceVariant = SurfaceRaised,
            onSurfaceVariant = TextMuted,
            error = Color(0xFFFF8D9A)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaApp(onSpeak: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("nova_settings", 0) }
    var chats by remember { mutableStateOf(ChatStore.load(context)) }
    var activeChatId by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(prefs.getString("ai_mode", "Gemini") ?: "Gemini") }
    var modelPath by remember { mutableStateOf(prefs.getString("local_model_path", "") ?: "") }
    var chatSearch by remember { mutableStateOf("") }
    var settingsProvider by remember { mutableStateOf((prefs.getString("last_cloud_provider", if (mode == "Lokal") "Gemini" else mode) ?: "Gemini").let { if (it == "Lokal") "Gemini" else it }) }
    var apiKey by remember(settingsProvider) { mutableStateOf(ApiKeyStore.load(context, settingsProvider)) }
    var providerModelId by remember(settingsProvider) {
        mutableStateOf(prefs.getString("model_${settingsProvider.lowercase(Locale.ROOT)}", defaultProviderModel(settingsProvider)) ?: defaultProviderModel(settingsProvider))
    }
    var availableModels by remember(settingsProvider) { mutableStateOf(emptyList<String>()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var modelListError by remember { mutableStateOf<String?>(null) }
    var accentKey by remember { mutableStateOf(prefs.getString("accent_color", "purple") ?: "purple") }
    LaunchedEffect(accentKey) {
        val colors = when (accentKey) {
            "blue" -> Color(0xFF6E9EFF) to Color(0xFFB5D0FF)
            "mint" -> Color(0xFF55D6B0) to Color(0xFFACF2DA)
            else -> Color(0xFF9A72FF) to Color(0xFFBEA2FF)
        }
        Accent = colors.first
        AccentBright = colors.second
        prefs.edit().putString("accent_color", accentKey).apply()
    }
    var showSettings by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var renameChatId by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteChatId by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val keyboardController = LocalSoftwareKeyboardController.current

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingUri = uri
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val target = File(context.filesDir, "nova-local-model.litertlm")
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            target.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
                        } ?: error("Die ausgewählte Datei konnte nicht geöffnet werden.")
                        target.absolutePath
                    }
                }
                result.onSuccess { path ->
                    modelPath = path
                    prefs.edit().putString("local_model_path", path).apply()
                    errorMessage = null
                }.onFailure { errorMessage = "Modell konnte nicht importiert werden: ${it.message ?: "Unbekannter Fehler"}" }
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val transcript = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
            if (transcript.isNotBlank()) input = if (input.isBlank()) transcript else input.trimEnd() + " " + transcript
        }
    }

    fun startDictation() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich deine Nachricht")
        }
        runCatching { speechLauncher.launch(intent) }
            .onFailure { errorMessage = "Spracherkennung ist auf diesem Gerät nicht verfügbar: ${it.message ?: "Unbekannter Fehler"}" }
    }

    val activeChat = chats.firstOrNull { it.id == activeChatId }

    fun persist(next: List<ChatConversation>) {
        chats = next.sortedByDescending { it.updatedAt }
        ChatStore.save(context, chats)
    }

    fun newChat() {
        val chat = ChatConversation()
        persist(listOf(chat) + chats)
        activeChatId = chat.id
        input = ""
        errorMessage = null
        scope.launch { drawerState.close() }
    }

    fun sendPrompt() {
        val prompt = input.trim()
        if (prompt.isEmpty() || isGenerating) return
        keyboardController?.hide()
        val existing = chats.firstOrNull { it.id == activeChatId }
        val chat = existing ?: ChatConversation(title = prompt.take(34))
        val before = chat.messages
        val userMessage = ChatMessage(role = "user", text = prompt)
        val title = if (before.isEmpty() && (chat.title == "Neuer Chat" || chat.title == "Chat")) prompt.take(34) else chat.title
        val updatedChat = chat.copy(title = title, messages = before + userMessage, updatedAt = System.currentTimeMillis())
        val updatedList = if (existing == null) listOf(updatedChat) + chats else chats.map { if (it.id == chat.id) updatedChat else it }
        persist(updatedList)
        activeChatId = updatedChat.id
        input = ""
        errorMessage = null
        isGenerating = true
        scope.launch {
            try {
                val response = when (mode) {
                    "Gemini" -> GeminiClient.generate(
                        ApiKeyStore.load(context, "Gemini"),
                        before + userMessage,
                        prefs.getString("model_gemini", defaultProviderModel("Gemini")) ?: defaultProviderModel("Gemini")
                    )
                    "OpenAI", "Claude", "DeepSeek" -> CloudProviderClient.generate(
                        provider = mode,
                        model = prefs.getString("model_${mode.lowercase(Locale.ROOT)}", defaultProviderModel(mode)) ?: defaultProviderModel(mode),
                        apiKey = ApiKeyStore.load(context, mode),
                        history = before + userMessage
                    )
                    else -> LocalLlmClient.generate(
                        modelPath = modelPath,
                        chatId = updatedChat.id,
                        previousMessages = before,
                        prompt = prompt,
                        cachePath = context.cacheDir.absolutePath
                    )
                }
                val latest = ChatStore.load(context)
                val current = latest.firstOrNull { it.id == updatedChat.id } ?: updatedChat
                val assistantMessage = ChatMessage(role = "assistant", text = response)
                val finished = current.copy(messages = current.messages + assistantMessage, updatedAt = System.currentTimeMillis())
                persist(latest.map { if (it.id == finished.id) finished else it }.let { rows -> if (rows.none { it.id == finished.id }) listOf(finished) + rows else rows })
            } catch (t: Throwable) {
                errorMessage = t.message ?: "Unbekannter Fehler bei der KI-Anfrage."
            } finally {
                isGenerating = false
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp),
                drawerContainerColor = SurfaceDark,
                drawerContentColor = TextPrimary
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NovaMark(Modifier.size(42.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("NOVA", fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("PRO · ANDROID", fontSize = 10.sp, color = TextMuted, letterSpacing = 1.6.sp)
                    }
                }
                Button(onClick = { newChat() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Neuer Chat")
                }
                Spacer(Modifier.height(14.dp))
                Text("LETZTE CHATS", modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                OutlinedTextField(
                    value = chatSearch,
                    onValueChange = { chatSearch = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    placeholder = { Text("Chats durchsuchen …", color = TextMuted) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.SmartToy, contentDescription = null, tint = AccentBright, modifier = Modifier.size(18.dp)) },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceRaised,
                        unfocusedContainerColor = SurfaceRaised,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color(0xFF383149),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                val visibleChats = chats.filter { chat ->
                    chatSearch.isBlank() || chat.title.contains(chatSearch.trim(), ignoreCase = true) ||
                        chat.messages.any { it.text.contains(chatSearch.trim(), ignoreCase = true) }
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(visibleChats, key = { it.id }) { item ->
                        ChatDrawerItem(
                            chat = item,
                            selected = item.id == activeChatId,
                            onClick = {
                                activeChatId = item.id
                                errorMessage = null
                                scope.launch { drawerState.close() }
                            },
                            onRename = { renameChatId = item.id; renameValue = item.title },
                            onDelete = { deleteChatId = item.id }
                        )
                    }
                }
                Divider(color = Color(0xFF302A45))
                Row(
                    Modifier.fillMaxWidth().clickable { showSettings = true; scope.launch { drawerState.close() } }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = AccentBright)
                    Spacer(Modifier.width(12.dp))
                    Text("Einstellungen")
                }
            }
        },
        scrimColor = Color.Black.copy(alpha = 0.65f)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().background(Background).imePadding(),
            containerColor = Background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        if (showSettings) Text("Einstellungen", fontWeight = FontWeight.SemiBold)
                        else Row(verticalAlignment = Alignment.CenterVertically) {
                            NovaMark(Modifier.size(32.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("NOVA", fontWeight = FontWeight.Black, letterSpacing = 2.5.sp, fontSize = 19.sp)
                                Text(activeChat?.title ?: "Dein KI-Assistent", fontSize = 11.sp, color = TextMuted, maxLines = 1)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (showSettings) showSettings = false else scope.launch { drawerState.open() }
                        }) {
                            Icon(if (showSettings) Icons.Default.ArrowBack else Icons.Default.Menu, contentDescription = if (showSettings) "Zurück" else "Chats öffnen")
                        }
                    },
                    actions = {
                        if (!showSettings) {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Einstellungen", tint = AccentBright)
                            }
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = Background,
                        titleContentColor = TextPrimary,
                        navigationIconContentColor = TextPrimary,
                        actionIconContentColor = TextPrimary
                    )
                )
            },
            bottomBar = {
                if (!showSettings) {
                    Column(Modifier.fillMaxWidth().background(Background).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp)
                        ) {
                            listOf("Gemini", "OpenAI", "Claude", "DeepSeek", "Lokal").forEach { provider ->
                                FilterChip(
                                    selected = mode == provider,
                                    onClick = {
                                        mode = provider
                                        prefs.edit().putString("ai_mode", mode).apply()
                                        if (provider != "Lokal") {
                                            prefs.edit().putString("last_cloud_provider", provider).apply()
                                            settingsProvider = provider
                                            apiKey = ApiKeyStore.load(context, provider)
                                            providerModelId = prefs.getString("model_${provider.lowercase(Locale.ROOT)}", defaultProviderModel(provider)) ?: defaultProviderModel(provider)
                                        }
                                    },
                                    label = { Text(provider) },
                                    leadingIcon = {
                                        Icon(if (provider == "Lokal") Icons.Default.Memory else Icons.Default.Cloud, null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                        if (mode == "Lokal") {
                            Text(if (modelPath.isBlank()) "Kein lokales Modell importiert" else "Lokales Modell bereit: ${File(modelPath).name}", color = if (modelPath.isBlank()) Color(0xFFFFC078) else Color(0xFF93E6B0), fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { startDictation() },
                                modifier = Modifier.size(48.dp).background(SurfaceRaised, CircleShape)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Nachricht diktieren", tint = AccentBright)
                            }
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Schreib NOVA eine Nachricht …", color = TextMuted) },
                                shape = RoundedCornerShape(22.dp),
                                maxLines = 5,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { sendPrompt() }),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceDark,
                                    unfocusedContainerColor = SurfaceDark,
                                    focusedBorderColor = Accent,
                                    unfocusedBorderColor = Color(0xFF383149),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            IconButton(
                                onClick = { sendPrompt() },
                                enabled = input.isNotBlank() && !isGenerating,
                                modifier = Modifier.size(52.dp).background(if (input.isNotBlank() && !isGenerating) Accent else SurfaceRaised, CircleShape)
                            ) {
                                if (isGenerating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = TextPrimary)
                                else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden", tint = if (input.isNotBlank()) Color.White else TextMuted)
                            }
                        }
                        Text("NOVA kann Fehler machen. Prüfe wichtige Informationen.", color = TextMuted, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 7.dp))
                    }
                }
            }
        ) { padding ->
            if (showSettings) {
                SettingsScreen(
                    modifier = Modifier.padding(padding),
                    mode = mode,
                    provider = settingsProvider,
                    modelId = providerModelId,
                    onProviderSelected = { selected ->
                        settingsProvider = selected
                        apiKey = ApiKeyStore.load(context, selected)
                        providerModelId = prefs.getString("model_${selected.lowercase(Locale.ROOT)}", defaultProviderModel(selected)) ?: defaultProviderModel(selected)
                    },
                    onModelIdChanged = { providerModelId = it },
                    availableModels = availableModels,
                    isLoadingModels = isLoadingModels,
                    modelListError = modelListError,
                    onLoadModels = {
                        val providerToLoad = settingsProvider
                        val keyToUse = apiKey.trim()
                        isLoadingModels = true
                        modelListError = null
                        scope.launch {
                            runCatching {
                                when (providerToLoad) {
                                    "Gemini" -> GeminiClient.listModels(keyToUse)
                                    else -> CloudProviderClient.listModels(providerToLoad, keyToUse)
                                }
                            }.onSuccess { models ->
                                availableModels = models
                                if (models.isEmpty()) modelListError = "Der Anbieter hat keine verwendbaren Modelle für diesen Key zurückgegeben."
                            }.onFailure { error ->
                                modelListError = error.message ?: "Modellliste konnte nicht geladen werden."
                            }
                            isLoadingModels = false
                        }
                    },
                    accentKey = accentKey,
                    onAccentSelected = { accentKey = it },
                    apiKey = apiKey,
                    modelPath = modelPath,
                    onApiKeyChanged = { apiKey = it },
                    onSaveKey = {
                        runCatching {
                            ApiKeyStore.save(context, settingsProvider, apiKey)
                            prefs.edit()
                                .putString("model_${settingsProvider.lowercase(Locale.ROOT)}", providerModelId.trim().ifBlank { defaultProviderModel(settingsProvider) })
                                .putString("last_cloud_provider", settingsProvider)
                                .putString("ai_mode", settingsProvider)
                                .apply()
                            mode = settingsProvider
                        }.onSuccess { errorMessage = null }
                            .onFailure { errorMessage = "API-Key konnte nicht gespeichert werden: ${it.message}" }
                    },
                    onPickModel = { modelPicker.launch(arrayOf("*/*")) },
                    onClose = { showSettings = false }
                )
            } else {
                val currentMessages = activeChat?.messages.orEmpty()
                if (currentMessages.isEmpty()) {
                    HomeScreen(
                        modifier = Modifier.padding(padding),
                        mode = mode,
                        onNewChat = { newChat() },
                        onOpenSettings = { showSettings = true }
                    )
                } else {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                            reverseLayout = false,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item { Spacer(Modifier.height(8.dp)) }
                            items(currentMessages, key = { it.id }) { message -> MessageBubble(message, onSpeak) }
                            if (isGenerating) {
                                item {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentBright)
                                        Spacer(Modifier.width(10.dp))
                                        Text(if (mode == "Lokal") "Lokales Modell denkt …" else "NOVA schreibt …", color = TextMuted, fontSize = 13.sp)
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                        errorMessage?.let { error ->
                            Card(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF321A28))
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Das hat nicht geklappt", fontWeight = FontWeight.Bold, color = Color(0xFFFFB7C0))
                                    Text(error, color = TextPrimary, fontSize = 13.sp)
                                    if (mode == "Lokal" && modelPath.isBlank()) TextButton(onClick = { showSettings = true }) { Text("Lokales Modell einrichten") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renameChatId != null) {
        AlertDialog(
            onDismissRequest = { renameChatId = null },
            title = { Text("Chat umbenennen") },
            text = {
                OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = renameChatId
                    val safeTitle = renameValue.trim()
                    if (id != null && safeTitle.isNotEmpty()) persist(chats.map { if (it.id == id) it.copy(title = safeTitle, updatedAt = System.currentTimeMillis()) else it })
                    renameChatId = null
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { renameChatId = null }) { Text("Abbrechen") } }
        )
    }

    if (deleteChatId != null) {
        AlertDialog(
            onDismissRequest = { deleteChatId = null },
            title = { Text("Chat löschen?") },
            text = { Text("Der Chat und sein gespeicherter Verlauf werden von diesem Gerät gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = deleteChatId
                    persist(chats.filterNot { it.id == id })
                    if (activeChatId == id) activeChatId = null
                    deleteChatId = null
                    errorMessage = null
                    LocalLlmClient.close()
                }) { Text("Löschen", color = Color(0xFFFF8D9A)) }
            },
            dismissButton = { TextButton(onClick = { deleteChatId = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier, mode: String, onNewChat: () -> Unit, onOpenSettings: () -> Unit) {
    Box(modifier = modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Background, Color(0xFF17102C), Background)))) {
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(84.dp).background(Brush.linearGradient(listOf(Accent, Color(0xFF542EA1))), CircleShape), contentAlignment = Alignment.Center) {
                NovaMark(Modifier.size(68.dp), showOuter = false)
            }
            Spacer(Modifier.height(24.dp))
            Text("NOVA", fontSize = 54.sp, fontWeight = FontWeight.Black, letterSpacing = 7.sp, color = TextPrimary)
            Text("DEIN KI-ASSISTENT", fontSize = 11.sp, letterSpacing = 3.5.sp, color = AccentBright)
            Spacer(Modifier.height(20.dp))
            Text("Was möchtest du heute entdecken?", fontSize = 17.sp, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                when (mode) {
                    "Lokal" -> "Mit einem lokalen Modell chatten – auch ohne Internet."
                    else -> "Mit $mode online chatten – eigener API-Key erforderlich."
                },
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(26.dp))
            Button(onClick = onNewChat) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Chat starten") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("KI einrichten") }
        }
        Text("NOVA ORBIT · ANDROID BETA", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp), fontSize = 10.sp, color = TextMuted, letterSpacing = 1.4.sp)
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, onSpeak: (String) -> Unit) {
    val isUser = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) {
            NovaMark(Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
        }
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 0.92f),
            shape = RoundedCornerShape(if (isUser) 20.dp else 8.dp, 20.dp, 20.dp, 20.dp),
            colors = CardDefaults.cardColors(containerColor = if (isUser) Color(0xFF30234D) else SurfaceDark)
        ) {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                Text(if (isUser) "DU" else "NOVA", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = if (isUser) AccentBright else Color(0xFFB79AFF))
                Spacer(Modifier.height(5.dp))
                Text(message.text, color = TextPrimary, fontSize = 14.sp, lineHeight = 21.sp)
                if (!isUser && message.text.isNotBlank()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onSpeak(message.text) }) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentBright)
                            Spacer(Modifier.width(5.dp))
                            Text("Vorlesen", fontSize = 11.sp, color = AccentBright)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatDrawerItem(
    chat: ChatConversation,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember(chat.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
            .background(if (selected) Color(0xFF30234D) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(start = 12.dp, end = 4.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.SmartToy, contentDescription = null, tint = if (selected) AccentBright else TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(chat.title, maxLines = 1, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(chat.messages.lastOrNull()?.text?.take(34) ?: "Noch keine Nachrichten", maxLines = 1, fontSize = 10.sp, color = TextMuted)
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
                Text("⋮", color = TextMuted, fontSize = 21.sp)
            }
            androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("Umbenennen") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuOpen = false; onRename() })
                androidx.compose.material3.DropdownMenuItem(text = { Text("Löschen", color = Color(0xFFFF8D9A)) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF8D9A)) }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}

private fun defaultProviderModel(provider: String): String = when (provider.lowercase(Locale.ROOT)) {
    "gemini" -> "gemini-3.8-flash"
    "openai" -> "gpt-5.1"
    "claude", "anthropic" -> "claude-sonnet-5"
    "deepseek" -> "deepseek-flash"
    else -> "gemini-3.8-flash"
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    mode: String,
    provider: String,
    modelId: String,
    onProviderSelected: (String) -> Unit,
    onModelIdChanged: (String) -> Unit,
    availableModels: List<String>,
    isLoadingModels: Boolean,
    modelListError: String?,
    onLoadModels: () -> Unit,
    accentKey: String,
    onAccentSelected: (String) -> Unit,
    apiKey: String,
    modelPath: String,
    onApiKeyChanged: (String) -> Unit,
    onSaveKey: () -> Unit,
    onPickModel: () -> Unit,
    onClose: () -> Unit
) {
    var showModelPicker by remember { mutableStateOf(false) }
    var modelSearch by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NovaMark(Modifier.size(34.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("NOVA Orbit", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Dein persönlicher KI-Arbeitsplatz", fontSize = 11.sp, color = TextMuted)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Anpassung", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Akzentfarbe", color = TextMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilterChip(selected = accentKey == "purple", onClick = { onAccentSelected("purple") }, label = { Text("Cosmic Purple") })
                    FilterChip(selected = accentKey == "blue", onClick = { onAccentSelected("blue") }, label = { Text("Electric Blue") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = accentKey == "mint", onClick = { onAccentSelected("mint") }, label = { Text("Aurora Mint") })
                }
                Text("Die Farbe wird lokal gespeichert und auf NOVA angewendet.", color = TextMuted, fontSize = 11.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, null, tint = AccentBright, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Cloud-Modellmanager", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Einen Anbieter auswählen, Modell-ID und API-Key eintragen.", color = TextMuted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Gemini", "OpenAI", "Claude", "DeepSeek").forEach { item ->
                        FilterChip(selected = provider == item, onClick = { onProviderSelected(item) }, label = { Text(item) })
                    }
                }
                Spacer(Modifier.height(9.dp))
                Text("Modell-ID", color = TextMuted, fontSize = 12.sp)
                OutlinedTextField(
                    value = modelId,
                    onValueChange = onModelIdChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text(defaultProviderModel(provider), color = TextMuted) },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceRaised,
                        unfocusedContainerColor = SurfaceRaised,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color(0xFF383149),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                val modelSuggestions = when (provider) {
                    "Gemini" -> listOf("gemini-3.8-flash", "gemini-4-argon", "gemini-3.6-flash")
                    "OpenAI" -> listOf("gpt-5.1", "gpt-5", "gpt-5-mini", "gpt-5-nano", "gpt-4.1")
                    "Claude" -> listOf("claude-sonnet-5", "claude-opus-5", "claude-fable-5", "claude-haiku-4-5-20251001")
                    else -> listOf("deepseek-flash", "deepseek-v4-pro")
                }
                Spacer(Modifier.height(5.dp))
                Text("Schnellauswahl · frei änderbar", color = TextMuted, fontSize = 11.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    modelSuggestions.forEach { suggestion ->
                        FilterChip(selected = modelId == suggestion, onClick = { onModelIdChanged(suggestion) }, label = { Text(suggestion, fontSize = 11.sp) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onLoadModels, enabled = apiKey.isNotBlank() && !isLoadingModels, modifier = Modifier.weight(1f)) {
                        if (isLoadingModels) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(if (isLoadingModels) "Lade Modelle…" else "Modelle vom Anbieter laden")
                    }
                    if (availableModels.isNotEmpty()) {
                        OutlinedButton(onClick = { showModelPicker = true }) { Text("Liste (${availableModels.size})") }
                    }
                }
                modelListError?.let { Text(it, color = Color(0xFFFF9BA8), fontSize = 11.sp, lineHeight = 15.sp) }
                if (showModelPicker) {
                    AlertDialog(
                        onDismissRequest = { showModelPicker = false },
                        title = { Text("Verfügbare $provider-Modelle") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = modelSearch,
                                    onValueChange = { modelSearch = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Modell suchen") }
                                )
                                LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                                    items(availableModels.filter { it.contains(modelSearch, ignoreCase = true) }) { model ->
                                        TextButton(onClick = {
                                            onModelIdChanged(model)
                                            modelSearch = ""
                                            showModelPicker = false
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Text(model, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { showModelPicker = false }) { Text("Schließen") } }
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("$provider API-Key") },
                    placeholder = { Text("API-Key hier einfügen") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceRaised,
                        unfocusedContainerColor = SurfaceRaised,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color(0xFF383149),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onSaveKey, modifier = Modifier.fillMaxWidth()) { Text("Speichern und $provider verwenden") }
                Text("Keys werden mit Android Keystore verschlüsselt auf diesem Gerät gespeichert. Sie werden nie über NOVA synchronisiert. API-Nutzung kann Geld kosten.", color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, null, tint = AccentBright, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Lokales Modell", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text("Das Modell rechnet direkt auf deinem Handy. Die Modelldatei wird nicht mit der App geliefert.", color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                Text("Modellstatus", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(if (modelPath.isBlank()) "Noch kein Modell importiert" else File(modelPath).name, color = if (modelPath.isBlank()) Color(0xFFFFC078) else Color(0xFF93E6B0), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onPickModel, modifier = Modifier.fillMaxWidth()) { Text(".litertlm-Modell auswählen") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("Google-Konto & Chat-Sync", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Noch nicht verbunden. Die Synchronisierung benötigt ein Firebase-Projekt, Google OAuth-Konfiguration und sichere Firestore-Regeln. Bis diese Daten eingerichtet und getestet sind, bleiben Chats lokal.", color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Text("Aktiver Modus: $mode", color = AccentBright, fontSize = 12.sp)
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fertig") }
    }
}

@Composable
private fun NovaMark(modifier: Modifier = Modifier, showOuter: Boolean = true) {
    Box(modifier.background(if (showOuter) Color(0xFF28203E) else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize(if (showOuter) 0.7f else 1f).background(Brush.linearGradient(listOf(AccentBright, Accent, Color(0xFF4B2B88))), CircleShape), contentAlignment = Alignment.Center) {
            Text("N", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }
}
