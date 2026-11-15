package com.jarvis.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object CloudAiClient {
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
