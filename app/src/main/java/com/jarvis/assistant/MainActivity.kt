package com.jarvis.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var voiceStatus by remember { mutableStateOf("VOZ LOCAL · IA REMOTA PENDIENTE") }
    val messages = remember(context) {
        mutableStateListOf<ChatMessage>().apply {
            addAll(loadConversation(context))
            if (isEmpty()) {
                add(ChatMessage("Sistemas en línea. Soy JARVIS, tu asistente personal. Puedes escribirme o utilizar el botón del micrófono.", false))
            }
        }
    }

    fun respondTo(userText: String) {
        val text = userText.trim()
        if (text.isNotEmpty()) {
            messages.add(ChatMessage(text, true))
            val reply = "He recibido tu mensaje. Mi interfaz y voz local están operativas. Para responder con inteligencia artificial avanzada falta conectar un servidor seguro."
            messages.add(ChatMessage(reply, false))
            if (messages.size > MAX_SAVED_MESSAGES) {
                repeat(messages.size - MAX_SAVED_MESSAGES) { messages.removeAt(0) }
            }
            saveConversation(context, messages)
            onSpeak(reply)
        }
    }

    val speechResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (result.resultCode == android.app.Activity.RESULT_OK && !spokenText.isNullOrBlank()) {
            voiceStatus = "MENSAJE DE VOZ RECIBIDO"
            respondTo(spokenText)
        } else {
            voiceStatus = "NO SE RECIBIÓ AUDIO · INTENTA OTRA VEZ"
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con JARVIS")
            }
            runCatching { speechResultLauncher.launch(intent) }
                .onSuccess { voiceStatus = "ESCUCHANDO · HABLA AHORA" }
                .onFailure { voiceStatus = "NO HAY SERVICIO DE RECONOCIMIENTO DE VOZ INSTALADO" }
        } else {
            voiceStatus = "PERMISO DE MICRÓFONO DENEGADO"
        }
    }

    fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con JARVIS")
            }
            runCatching { speechResultLauncher.launch(intent) }
                .onSuccess { voiceStatus = "ESCUCHANDO · HABLA AHORA" }
                .onFailure { voiceStatus = "NO HAY SERVICIO DE RECONOCIMIENTO DE VOZ INSTALADO" }
        } else {
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
                    Text("ASISTENTE PERSONAL", color = Color(0xFF8EA6BC), fontSize = 10.sp, letterSpacing = 2.sp)
                }
                Surface(shape = CircleShape, color = Color(0xFF123B43)) {
                    Text("● LOCAL", color = Cyan, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 10.sp)
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
                    placeholder = { Text("Escribe a JARVIS…", color = Color(0xFF7890A8)) },
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF294056),
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White
                    ),
                    maxLines = 3
                )
                IconButton(onClick = { startVoiceInput() }) {
                    Icon(Icons.Default.Mic, contentDescription = "Hablar con JARVIS", tint = Cyan)
                }
                IconButton(onClick = {
                    respondTo(input)
                    input = ""
                }) {
                    Icon(Icons.Default.Send, contentDescription = "Enviar", tint = Cyan)
                }
            }
            Text(
                voiceStatus,
                color = Color(0xFF71899F), fontSize = 9.sp, letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )
        }
    }
}
