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
        wav: ByteArray,
        history: List<Pair<String, String>>
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
            val messages = JSONArray()
            history.takeLast(12).forEach { (role, content) ->
                messages.put(JSONObject().put("role", role).put("content", content))
            }
            val payload = JSONObject()
                .put("audioBase64", Base64.encodeToString(wav, Base64.NO_WRAP))
                .put("messages", messages)
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
        endpoint: String,
        message: String,
        history: List<Pair<String, String>>
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val transcript = JSONArray()
            history.takeLast(12).forEach { (role, content) ->
                transcript.put(JSONObject().put("role", role).put("content", content))
            }
            transcript.put(JSONObject().put("role", "user").put("content", message))
            val payload = JSONObject().put("messages", transcript)
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
                throw IllegalStateException(detail.ifBlank { "El servidor respondió con HTTP $code." })
            }
            val answer = JSONObject(body).optString("answer").trim()
            if (answer.isBlank()) throw IllegalStateException("La IA devolvió una respuesta vacía.")
            answer
        } finally {
            connection.disconnect()
        }
    }
}
