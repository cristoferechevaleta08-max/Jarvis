package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.SpeechRecognizer.RESULTS_RECOGNITION
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Local wake-word engine. Audio is analyzed on-device by sherpa-onnx;
 * no microphone audio is sent to the cloud while waiting for "Jarvis".
 */
class JarvisVoiceService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var stopping = false
    @Volatile private var waitingForQuestion = false
    @Volatile private var workerRunning = false
    private var audioRecord: AudioRecord? = null
    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "ES")
                tts?.setPitch(0.88f)
                tts?.setSpeechRate(0.94f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "jarvis_reply") mainHandler.postDelayed({ resumeWakeWord() }, 450L)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == "jarvis_reply") mainHandler.post { resumeWakeWord() }
                    }
                })
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        stopping = false
        if (!workerRunning && !waitingForQuestion) startWakeWordLoop()
        return START_STICKY
    }

    private fun startWakeWordLoop() {
        if (stopping || waitingForQuestion || workerRunning) return
        workerRunning = true
        thread(name = "JarvisWakeWord", isDaemon = true) {
            var localSpotter: KeywordSpotter? = null
            var localStream: OnlineStream? = null
            var recorder: AudioRecord? = null
            try {
                val modelDir = "jarvis-kws"
                val modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$modelDir/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                        decoder = "$modelDir/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                        joiner = "$modelDir/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
                    ),
                    tokens = "$modelDir/tokens.txt",
                    numThreads = 2,
                    provider = "cpu",
                    modelType = "zipformer2",
                    modelingUnit = "bpe",
                    bpeVocab = "$modelDir/bpe.model"
                )
                localSpotter = KeywordSpotter(
                    assetManager = assets,
                    config = KeywordSpotterConfig(
                        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                        modelConfig = modelConfig,
                        keywordsFile = "$modelDir/keywords.txt",
                        keywordsScore = 1.5f,
                        keywordsThreshold = 0.25f
                    )
                )
                localStream = localSpotter.createStream()
                spotter = localSpotter
                stream = localStream

                val minBytes = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBytes <= 0) throw IllegalStateException("El micrófono no admite audio PCM de 16 kHz.")
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBytes * 2, SAMPLE_RATE / 5 * 2)
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("No se pudo inicializar el micrófono.")
                }
                audioRecord = recorder
                recorder.startRecording()
                broadcast(ACTION_STATUS, "JARVIS ESCUCHANDO · DETECCIÓN LOCAL")

                val shorts = ShortArray(SAMPLE_RATE / 10)
                while (!stopping && !waitingForQuestion) {
                    val count = recorder.read(shorts, 0, shorts.size)
                    if (count <= 0) continue
                    val samples = FloatArray(count) { shorts[it] / 32768.0f }
                    localStream.acceptWaveform(samples, SAMPLE_RATE)
                    while (localSpotter.isReady(localStream)) {
                        localSpotter.decode(localStream)
                        val detected = localSpotter.getResult(localStream).keyword
                        if (detected.isNotBlank()) {
                            localSpotter.reset(localStream)
                            waitingForQuestion = true
                            mainHandler.post { onWakeWordDetected() }
                            break
                        }
                    }
                }
            } catch (error: Exception) {
                broadcast(ACTION_STATUS, "MOTOR DE VOZ NO DISPONIBLE: ${error.message ?: "error al iniciar"}")
            } finally {
                runCatching { recorder?.stop() }
                recorder?.release()
                if (audioRecord === recorder) audioRecord = null
                localStream?.release()
                localSpotter?.release()
                if (stream === localStream) stream = null
                if (spotter === localSpotter) spotter = null
                workerRunning = false
                // Recover automatically from transient microphone/model errors.
                // Do not restart while a question is being handled or the service is stopping.
                if (!stopping && !waitingForQuestion) {
                    mainHandler.postDelayed({ startWakeWordLoop() }, 3000L)
                }
            }
        }
    }

    private fun onWakeWordDetected() {
        // Wait until AudioRecord and the local KWS stream have been released
        // before handing the microphone to Android's speech recognizer.
        if (workerRunning) {
            mainHandler.postDelayed({ onWakeWordDetected() }, 120L)
            return
        }
        broadcast(ACTION_STATUS, "JARVIS ACTIVADO · TE ESCUCHO")
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            broadcast(ACTION_STATUS, "PALABRA DETECTADA · NO HAY MOTOR DE TRANSCRIPCIÓN INSTALADO")
            speakAndResume("Te he escuchado, pero falta un servicio de reconocimiento de voz en este teléfono.")
            return
        }
        runCatching {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    broadcast(ACTION_STATUS, "JARVIS TE ESCUCHA · HAZ TU PREGUNTA")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { broadcast(ACTION_STATUS, "PROCESANDO TU PREGUNTA…") }
                override fun onError(error: Int) {
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                    speakAndResume("No he podido entenderte. Vuelve a decir Jarvis y tu pregunta.")
                }
                override fun onResults(results: android.os.Bundle?) {
                    val question = results?.getStringArrayList(RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                    if (question.isBlank()) speakAndResume("No he recibido tu pregunta. Inténtalo otra vez.")
                    else answerQuestion(question)
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-UY")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-UY")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            recognizer.startListening(request)
        }.onFailure {
            broadcast(ACTION_STATUS, "NO SE PUDO INICIAR LA TRANSCRIPCIÓN: ${it.message ?: "error"}")
            speakAndResume("No puedo iniciar el reconocimiento de voz en este dispositivo.")
        }
    }

    private fun answerQuestion(question: String) {
        broadcast(ACTION_USER, question)
        appendHistory(question, true)
        val local = processLocalCommand(this, question)
        if (local != null) {
            appendHistory(local, false)
            broadcast(ACTION_ASSISTANT, local)
            speakAndResume(local)
            return
        }
        val prefs = getSharedPreferences("jarvis_preferences", MODE_PRIVATE)
        val endpoint = prefs.getString("cloud_ai_endpoint", "").orEmpty().trim()
        if (endpoint.isBlank()) {
            val message = "Todavía falta conectar JARVIS Cloud. Configura la dirección del servidor de IA en los ajustes de la aplicación."
            appendHistory(message, false)
            broadcast(ACTION_ASSISTANT, message)
            speakAndResume(message)
            return
        }
        broadcast(ACTION_STATUS, "CONSULTANDO LA IA EN LA NUBE…")
        serviceScope.launch {
            try {
                val history = readHistory().takeLast(12).map {
                    (if (it.first) "user" else "assistant") to it.second
                }.dropLast(1)
                val answer = CloudAiClient.ask(endpoint, question, history)
                appendHistory(answer, false)
                broadcast(ACTION_ASSISTANT, answer)
                speakAndResume(answer)
            } catch (error: Exception) {
                val message = "No pude conectar con la IA: ${error.message ?: "revisa la conexión y el servidor"}"
                appendHistory(message, false)
                broadcast(ACTION_ASSISTANT, message)
                speakAndResume(message)
            }
        }
    }

    private fun appendHistory(text: String, fromUser: Boolean) {
        val prefs = getSharedPreferences("jarvis_preferences", MODE_PRIVATE)
        val json = runCatching { JSONArray(prefs.getString("conversation_history", "[]")) }.getOrDefault(JSONArray())
        json.put(JSONObject().put("text", text).put("fromUser", fromUser))
        while (json.length() > 100) json.remove(0)
        prefs.edit().putString("conversation_history", json.toString()).apply()
    }

    private fun readHistory(): List<Pair<Boolean, String>> {
        val raw = getSharedPreferences("jarvis_preferences", MODE_PRIVATE)
            .getString("conversation_history", "[]") ?: "[]"
        val json = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val value = item.optString("text")
            if (value.isBlank()) null else item.optBoolean("fromUser") to value
        }
    }

    private fun speakAndResume(message: String) {
        broadcast(ACTION_STATUS, "JARVIS RESPONDIENDO…")
        val engine = tts
        if (engine == null) {
            mainHandler.postDelayed({ resumeWakeWord() }, 1500L)
            return
        }
        engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
    }

    private fun resumeWakeWord() {
        if (stopping) return
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        waitingForQuestion = false
        startWakeWordLoop()
    }

    private fun broadcast(action: String, value: String) {
        sendBroadcast(Intent(action).setPackage(packageName).putExtra(EXTRA_TEXT, value))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Escucha de JARVIS", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Indica que JARVIS está esperando localmente la palabra de activación."
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS está escuchando")
            .setContentText("Detección local de la palabra «Jarvis»")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopping = true
        // stop() unblocks AudioRecord.read(); the worker thread owns and releases
        // AudioRecord, OnlineStream and KeywordSpotter in its finally block.
        // Releasing them here as well caused a possible double-release race.
        runCatching { audioRecord?.stop() }
        audioRecord = null
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STATUS = "com.jarvis.assistant.VOICE_STATUS"
        const val ACTION_USER = "com.jarvis.assistant.VOICE_USER"
        const val ACTION_ASSISTANT = "com.jarvis.assistant.VOICE_ASSISTANT"
        const val EXTRA_TEXT = "text"
        private const val CHANNEL_ID = "jarvis_voice_service"
        private const val NOTIFICATION_ID = 7102
        private const val SAMPLE_RATE = 16000
    }
}
