package com.jarvis.assistant

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val Night = Color(0xFF080D18)
private val Panel = Color(0xFF111C2D)
private val Cyan = Color(0xFF55D6E8)
private data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale("es", "ES")
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
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    val messages = remember {
        mutableStateListOf(
            ChatMessage("Sistemas en línea. Buenas noches. Soy JARVIS, tu asistente personal. ¿En qué puedo ayudarte?", false)
        )
    }

    fun send() {
        val text = input.trim()
        if (text.isNotEmpty()) {
            messages.add(ChatMessage(text, true))
            val reply = "He recibido tu mensaje. La conexión con el motor de inteligencia artificial todavía debe configurarse en un servidor seguro. Por ahora, la interfaz y la voz local están preparadas."
            messages.add(ChatMessage(reply, false))
            onSpeak(reply)
            input = ""
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
                    Text("● ONLINE", color = Cyan, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 10.sp)
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
                IconButton(onClick = { listening = !listening }) {
                    Icon(Icons.Default.Mic, contentDescription = "Voz", tint = if (listening) Cyan else Color.White)
                }
                IconButton(onClick = { send() }) {
                    Icon(Icons.Default.Send, contentDescription = "Enviar", tint = Cyan)
                }
            }
            Text(
                if (listening) "MODO DE VOZ SELECCIONADO" else "VOZ LOCAL · MOTOR IA PENDIENTE DE CONEXIÓN",
                color = Color(0xFF71899F), fontSize = 9.sp, letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )
        }
    }
}
