package com.jarvis.assistant

import android.Manifest
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private val Night = Color(0xFF080D18)
private val Panel = Color(0xFF111C2D)
private val Cyan = Color(0xFF55D6E8)
private const val PREFS_NAME = "jarvis_preferences"
private const val CHAT_KEY = "conversation_history"
private const val SERVER_KEY = "cloud_ai_endpoint"
private const val MAX_SAVED_MESSAGES = 100

private data class ChatMessage(val text: String, val fromUser: Boolean)

private fun loadConversation(context: Context): List<ChatMessage> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(CHAT_KEY, null) ?: return emptyList()
    return runCatching {
        val json = JSONArray(raw)
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val text = item.optString("text")
            if (text.isBlank()) null else ChatMessage(text, item.optBoolean("fromUser"))
        }
    }.getOrDefault(emptyList())
}

private fun saveConversation(context: Context, messages: List<ChatMessage>) {
    val json = JSONArray()
    messages.takeLast(MAX_SAVED_MESSAGES).forEach { message ->
        json.put(JSONObject().put("text", message.text).put("fromUser", message.fromUser))
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(CHAT_KEY, json.toString()).apply()
}

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "ES")
                tts?.setPitch(0.88f)
                tts?.setSpeechRate(0.94f)
            }
        }
        setContent { JarvisScreen(onSpeak = { speak(it) }) }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()


    }
}

@Composable
private fun JarvisScreen(onSpeak: (String) -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var voiceStatus by remember { mutableStateOf("DI «JARVIS» Y TU PREGUNTA") }
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var endpoint by remember { mutableStateOf(preferences.getString(SERVER_KEY, "").orEmpty()) }
    var endpointDraft by remember { mutableStateOf(endpoint) }
    var showSettings by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var handsFree by remember { mutableStateOf(false) }
    var waitingForQuestion by remember { mutableStateOf(false) }
    var pendingHandsFreePermission by remember { mutableStateOf(false) }
    val messages = remember(context) {
        mutableStateListOf<ChatMessage>().apply {
            addAll(loadConversation(context))
            if (isEmpty()) {
                add(ChatMessage("Sistemas en línea. Soy JARVIS, tu asistente personal. La escucha automática se inicia al abrir la aplicación. Di «Jarvis» seguido de tu pregunta. Prueba: hora, fecha, batería o ayuda.", false))
            }
        }
    }

    fun respondTo(userText: String, fromVoice: Boolean = false) {
        val raw = userText.trim()
        if (raw.isEmpty() || isThinking) return
        val wakePattern = Regex("(?i)^jarvis[\\s,:;.!¿?¡-]*")
        if (fromVoice && !wakePattern.containsMatchIn(raw)) {
            voiceStatus = "DI «JARVIS» ANTES DE TU PREGUNTA"
            return
        }
        val text = raw.replace(wakePattern, "").trim()
        if (text.isBlank()) {
            voiceStatus = "DI «JARVIS» Y DESPUÉS TU PREGUNTA"
            return
        }
        if (isClearHistoryCommand(text)) {
            messages.clear()
            val reply = "He borrado el historial de conversación guardado en este dispositivo."
            messages.add(ChatMessage(reply, false))
            saveConversation(context, messages)
            onSpeak(reply)
            return
        }
        messages.add(ChatMessage(text, true))
        val localReply = processLocalCommand(context, text)
        if (localReply != null) {
            messages.add(ChatMessage(localReply, false))
            saveConversation(context, messages)
            onSpeak(localReply)
            return
        }
        val configuredEndpoint = endpoint.trim()
        if (configuredEndpoint.isBlank()) {
            val reply = "Aún falta conectar JARVIS Cloud. Abre Ajustes y configura la dirección HTTPS del servidor de IA."
            messages.add(ChatMessage(reply, false))
            saveConversation(context, messages)
            onSpeak(reply)
            voiceStatus = "FALTA CONFIGURAR EL SERVIDOR DE IA"
            return
        }
        saveConversation(context, messages)
        isThinking = true
        voiceStatus = "CONECTANDO CON LA IA EN LA NUBE…"
        scope.launch {
            try {
                val history = messages.dropLast(1).takeLast(12).map {
                    (if (it.fromUser) "user" else "assistant") to it.text
                }
                val answer = CloudAiClient.ask(configuredEndpoint, text, history)
                messages.add(ChatMessage(answer, false))
                if (messages.size > MAX_SAVED_MESSAGES) {
                    repeat(messages.size - MAX_SAVED_MESSAGES) { messages.removeAt(0) }
                }
                saveConversation(context, messages)
                onSpeak(answer)
                voiceStatus = "IA EN LA NUBE · CONECTADO"
            } catch (error: Exception) {
                val reply = "No pude conectar con la IA: ${error.message ?: "revisa internet y la URL del servidor"}"
                messages.add(ChatMessage(reply, false))
                saveConversation(context, messages)
                onSpeak(reply)
                voiceStatus = "ERROR DE CONEXIÓN · REVISA EL SERVIDOR"
            } finally {
                isThinking = false
            }
        }
    }

    val respondToLatest by rememberUpdatedState(newValue = { phrase: String, voice: Boolean -> respondTo(phrase, fromVoice = voice) })
    val handsFreeLatest by rememberUpdatedState(handsFree)
    val waitingLatest by rememberUpdatedState(waitingForQuestion)
    val thinkingLatest by rememberUpdatedState(isThinking)
    val statusLatest by rememberUpdatedState<(String) -> Unit>({ voiceStatus = it })

    LaunchedEffect(handsFree) {
        if (handsFree) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                voiceStatus = "INICIANDO MOTOR LOCAL DE «JARVIS»…"
                val serviceIntent = Intent(context, JarvisVoiceService::class.java)
                runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
                    .onFailure { voiceStatus = "NO SE PUDO INICIAR EL SERVICIO DE VOZ: ${it.message}" }
            } else {
                handsFree = false
                voiceStatus = "SE NECESITA PERMISO DE MICRÓFONO"
            }
        } else {
            context.stopService(Intent(context, JarvisVoiceService::class.java))
            voiceStatus = "ESCUCHA DE JARVIS DETENIDA"
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(JarvisVoiceService.ACTION_STATUS)
            addAction(JarvisVoiceService.ACTION_USER)
            addAction(JarvisVoiceService.ACTION_ASSISTANT)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val value = intent?.getStringExtra(JarvisVoiceService.EXTRA_TEXT).orEmpty()
                when (intent?.action) {
                    JarvisVoiceService.ACTION_STATUS -> voiceStatus = value
                    JarvisVoiceService.ACTION_USER -> {
                        messages.add(ChatMessage(value, true))
                        saveConversation(context, messages)
                    }
                    JarvisVoiceService.ACTION_ASSISTANT -> {
                        messages.add(ChatMessage(value, false))
                        while (messages.size > MAX_SAVED_MESSAGES) messages.removeAt(0)
                        saveConversation(context, messages)
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingHandsFreePermission = false
        if (granted) {
            handsFree = true
            voiceStatus = "PERMISO CONCEDIDO · INICIANDO JARVIS"
        } else {
            voiceStatus = "PERMISO DE MICRÓFONO DENEGADO"
        }
    }

    // Solicita el permiso una sola vez. Después el servicio local arranca automáticamente.
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            handsFree = true
            voiceStatus = "INICIANDO ESCUCHA AUTOMÁTICA"
        } else {
            pendingHandsFreePermission = true
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Night, surface = Panel, primary = Cyan,
        onBackground = Color(0xFFEAF4FF), onSurface = Color(0xFFEAF4FF)
    )) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF101E32), Night)))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("J.A.R.V.I.S.", color = Cyan, fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("ASISTENTE PERSONAL CON IA", color = Color(0xFF8EA6BC), fontSize = 10.sp, letterSpacing = 2.sp)
                }
                Surface(shape = CircleShape, color = if (endpoint.isNotBlank()) Color(0xFF123B43) else Color(0xFF49351B)) {
                    Text(if (endpoint.isNotBlank()) "● CLOUD" else "● CONFIG", color = if (endpoint.isNotBlank()) Cyan else Color(0xFFFFC66D), modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), fontSize = 10.sp)
                }
                IconButton(onClick = { endpointDraft = endpoint; showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Cyan)
                }
            }
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(174.dp),
                    shape = CircleShape,
                    color = Color(0xFF10283A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = .65f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(shape = CircleShape, color = Color(0xFF173D50), modifier = Modifier.size(124.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("J", color = Cyan, fontSize = 76.sp, fontWeight = FontWeight.Light)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("CONVERSACIÓN", color = Cyan, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                        Surface(
                            color = if (message.fromUser) Color(0xFF123747) else Panel,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (message.fromUser) Cyan.copy(alpha = .35f) else Color.White.copy(alpha = .08f)),
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) {
                            Text(message.text, modifier = Modifier.padding(14.dp), color = Color(0xFFEAF4FF), fontSize = 14.sp)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Jarvis, pregúntame algo…", color = Color(0xFF7890A8)) },
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF294056),
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White
                    ),
                    maxLines = 3
                )
                IconButton(onClick = {
                    respondTo(input)
                    input = ""
                }, enabled = !isThinking) {
                    Icon(Icons.Default.Send, contentDescription = "Enviar", tint = Cyan)
                }
            }
            Text(
                if (isThinking) "PROCESANDO EN LA NUBE…" else voiceStatus,
                color = Color(0xFF71899F), fontSize = 9.sp, letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("Conectar JARVIS Cloud") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pega la URL HTTPS de tu Cloudflare Worker terminada en /chat. La clave de OpenAI permanece en el servidor, nunca dentro del APK.")
                        OutlinedTextField(
                            value = endpointDraft,
                            onValueChange = { endpointDraft = it },
                            singleLine = true,
                            placeholder = { Text("https://tu-worker.workers.dev/chat") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val value = endpointDraft.trim()
                        if (value.isBlank() || value.startsWith("https://")) {
                            endpoint = value
                            preferences.edit().putString(SERVER_KEY, value).apply()
                            showSettings = false
                            voiceStatus = if (value.isBlank()) "SERVIDOR SIN CONFIGURAR" else "URL DEL SERVIDOR GUARDADA"
                        } else voiceStatus = "LA URL DEBE COMENZAR CON HTTPS://"
                    }) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = { showSettings = false }) { Text("Cancelar") }
                }
            )
        }

    }
}
