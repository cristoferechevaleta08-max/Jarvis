package com.jarvis.assistant

import android.content.Context
import android.os.BatteryManager
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun normalizeCommand(message: String): String =
    Normalizer.normalize(message.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[¿?¡!,.;]"), "")
        .trim()

internal fun isClearHistoryCommand(message: String): Boolean =
    normalizeCommand(message) in setOf(
        "borrar conversacion", "limpiar conversacion",
        "borrar chat", "limpiar chat", "eliminar historial"
    )

/**
 * Offline commands return a response. Null means the message needs the future AI service.
 */
internal fun processLocalCommand(context: Context, message: String): String? {
    return when (normalizeCommand(message)) {
        "hora", "que hora es", "dime la hora", "hora actual" -> {
            val time = SimpleDateFormat("HH:mm", Locale("es", "ES")).format(Date())
            "Son las $time."
        }
        "fecha", "que fecha es", "que dia es hoy", "fecha de hoy" -> {
            val date = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "ES")).format(Date())
            "Hoy es $date."
        }
        "bateria", "nivel de bateria", "cuanta bateria tengo" -> {
            val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (level in 0..100) "La batería está al $level por ciento."
            else "No pude consultar el nivel de batería en este dispositivo."
        }
        "ayuda", "que puedes hacer", "comandos", "ayuda jarvis" ->
            "Puedo decirte la hora, la fecha y el nivel de batería sin conexión. También puedes decir «borrar conversación» para eliminar el historial guardado. La inteligencia artificial remota todavía está pendiente."
        "quien eres", "que eres" ->
            "Soy JARVIS, un asistente Android en desarrollo. Tengo interfaz, voz local, reconocimiento de voz compatible, memoria local y comandos básicos sin conexión. La inteligencia artificial remota aún no está conectada."
        else -> null
    }
}
