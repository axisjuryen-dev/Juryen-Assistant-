package com.sajimahmed.juryen.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sajimahmed.juryen.R
import com.sajimahmed.juryen.util.CommandProcessor
import com.sajimahmed.juryen.util.JuryenVoice
import java.util.Locale

class JuryenListenerService : Service(), RecognitionListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var juryenVoice: JuryenVoice
    private lateinit var commandProcessor: CommandProcessor
    private var listening = false
    private val restartHandler = Handler(Looper.getMainLooper())
    private var consecutiveErrors = 0

    companion object {
        const val CHANNEL_ID = "juryen_listener_channel"
        const val NOTIFICATION_ID = 101
        const val WAKE_WORD = "juryen"
        private const val TAG = "JuryenListener"
        private const val NORMAL_RESTART_DELAY_MS = 400L
        private const val ERROR_BACKOFF_DELAY_MS = 3000L
        private const val MAX_CONSECUTIVE_ERRORS = 5
    }

    override fun onCreate() {
        super.onCreate()
        juryenVoice = JuryenVoice(this)
        juryenVoice.initialize()
        tts = juryenVoice.rawTts()
        commandProcessor = CommandProcessor(this, tts)

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "No speech recognition service available on this device.")
            juryenVoice.speak("Speech recognition is not available on this device.")
            stopSelf()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission missing at service start; stopping.")
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(), foregroundType())
        startListening()
        return START_STICKY
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        else 0

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Juryen Assistant", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Juryen is listening")
            .setContentText("Say \"Juryen\" to give a command")
            .setSmallIcon(R.drawable.ic_juryen_logo)
            .setOngoing(true)
            .build()
    }

    private fun startListening() {
        if (listening || !::speechRecognizer.isInitialized) return
        listening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer.startListening(intent)
    }

    private fun scheduleRestart(delayMs: Long) {
        restartHandler.postDelayed({ startListening() }, delayMs)
    }

    override fun onResults(results: Bundle?) {
        listening = false
        consecutiveErrors = 0
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val heard = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""
        handleHeardText(heard)
        scheduleRestart(NORMAL_RESTART_DELAY_MS)
    }

    override fun onError(error: Int) {
        listening = false
        consecutiveErrors++
        Log.w(TAG, "SpeechRecognizer error code=$error (consecutive=$consecutiveErrors)")

        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            Log.e(TAG, "Too many consecutive recognizer errors, backing off.")
            consecutiveErrors = 0
            scheduleRestart(ERROR_BACKOFF_DELAY_MS)
            return
        }
        scheduleRestart(NORMAL_RESTART_DELAY_MS)
    }

    private fun handleHeardText(heard: String) {
        if (heard.contains(WAKE_WORD)) {
            val afterWakeWord = heard.substringAfter(WAKE_WORD).trim()
            if (afterWakeWord.isNotEmpty()) {
                commandProcessor.process(afterWakeWord)
            } else {
                juryenVoice.speak("Yes?")
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onDestroy() {
        restartHandler.removeCallbacksAndMessages(null)
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
        juryenVoice.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
