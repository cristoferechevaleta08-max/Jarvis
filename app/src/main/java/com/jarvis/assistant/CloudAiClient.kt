package com.jarvis.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

internal object CloudAiClient {
    suspend fun transcribeAudio(
        endpoint: String,
        wav: ByteArray
    ): String = withContext(Dispatchers.IO) {
        val voiceEndpoint = endpoint.trimEnd('/').replace(Regex("/chat$"), "/voice")
        if (voiceEndpoint == endpoint.trimEnd('/')) {
            throw IllegalArgumentException("La URL del servidor debe terminar en /chat para habilitar la voz.")
        }
        val connection = (URL(voiceEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val payload = JSONObject()
                .put("audioBase64", Base64.encodeToString(wav, Base64.NO_WRAP))
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
                throw IllegalStateException(detail.ifBlank { "El servidor respondió con HTTP $code." })
            }
            val response = JSONObject(body)
            val transcript = response.optString("transcript").trim()
            if (transcript.isBlank()) throw IllegalStateException("No pude reconocer las palabras.")
            transcript
        } finally {
            connection.disconnect()
        }
    }

    suspend fun ask(
        apiKey: String,
        message: String,
        history: List<Pair<String, String>>
    ): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Configura tu clave de Anthropic en Ajustes." }
        val connection = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        try {
            val transcript = JSONArray()
            history.takeLast(12).forEach { (role, content) ->
                transcript.put(JSONObject().put("role", role).put("content", content))
            }
            transcript.put(JSONObject().put("role", "user").put("content", message))
            val payload = JSONObject()
                .put("model", "claude-sonnet-4-5-20250929")
                .put("max_tokens", 1200)
                .put("system", "Eres JARVIS, un asistente personal útil. Responde en español claro y natural.")
                .put("messages", transcript)
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
                throw IllegalStateException(detail.ifBlank { "El servidor respondió con HTTP $code." })
            }
            val response = JSONObject(body)
            val blocks = response.optJSONArray("content")
            val answer = buildString {
                if (blocks != null) for (i in 0 until blocks.length()) {
                    val block = blocks.optJSONObject(i) ?: continue
                    if (block.optString("type") == "text") append(block.optString("text"))
                }
            }.trim()
            if (answer.isBlank()) {
                val detail = response.optJSONObject("error")?.optString("message").orEmpty()
                throw IllegalStateException(detail.ifBlank { "Claude devolvió una respuesta vacía." })
            }
            answer
        } finally {
            connection.disconnect()
        }
    }
}
