package com.jarvis.assistant

import android.content.Context
import android.os.BatteryManager
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small offline command set. Returns null when the message should be sent
 * to the future remote AI service instead.
 */
internal fun processLocalCommand(context: Context, message: String): String? {
    val normalized = Normalizer.normalize(message.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[¿?¡!,.;]"), "")
        .trim()

    return when {
        normalized in setOf("hora", "que hora es", "dime la hora", "hora actual") -> {
            val time = SimpleDateFormat("HH:mm", Locale("es", "ES")).format(Date())
            "Son las $time."
        }
        normalized in setOf("fecha", "que fecha es", "que dia es hoy", "fecha de hoy") -> {
            val date = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "ES")).format(Date())
            "Hoy es $date."
        }
        normalized in setOf("bateria", "nivel de bateria", "cuanta bateria tengo") -> {
            val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (level in 0..100) "La batería está al $level por ciento."
            else "No pude consultar el nivel de batería en este dispositivo."
        }
        normalized in setOf("ayuda", "que puedes hacer", "comandos", "ayuda jarvis") -> {
            "Por ahora puedo decirte la hora, la fecha y el nivel de batería sin conexión. La conexión con la inteligencia artificial remota todavía está pendiente."
        }
        normalized in setOf("quien eres", "que eres") -> {
            "Soy JARVIS, un asistente Android en desarrollo. Ya tengo interfaz, voz local, reconocimiento de voz compatible y memoria local de conversación. La inteligencia artificial remota aún no está conectada."
        }
        else -> null
    }
}
