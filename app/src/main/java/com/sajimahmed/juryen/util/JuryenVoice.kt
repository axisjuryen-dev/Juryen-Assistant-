package com.sajimahmed.juryen.util

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

class JuryenVoice(context: Context) {
    private val appContext = context.applicationContext
    private var ready = false
    private lateinit var tts: TextToSpeech

    fun initialize(onReady: () -> Unit = {}) {
        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                configureSoftFemaleVoice()
                onReady()
            }
        }
    }

    private fun configureSoftFemaleVoice() {
        val preferredLocales = listOf(Locale.US, Locale.UK)
        val voices = tts.voices ?: emptySet()
        val candidate = voices
            .filter { it.locale.language == "en" && it.locale.country in setOf("US", "GB", "AU", "CA") }
            .sortedWith(compareByDescending<Voice> { femaleScore(it) }
                .thenByDescending { it.quality }
                .thenBy { it.latency })
            .firstOrNull()

        if (candidate != null) {
            tts.voice = candidate
        } else {
            tts.language = preferredLocales.first()
        }

        tts.setSpeechRate(0.90f)
        tts.setPitch(0.98f)
    }

    private fun femaleScore(voice: Voice): Int {
        val name = voice.name.lowercase(Locale.ROOT)
        var score = 0
        if ("female" in name) score += 100
        if ("woman" in name || "girl" in name) score += 25
        if ("neural" in name || "enhanced" in name || "premium" in name || "natural" in name) score += 30
        if (!voice.isNetworkConnectionRequired) score += 5
        return score
    }

    fun speak(text: String, flush: Boolean = true) {
        if (!ready) return
        val queue = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle()
        tts.speak(text, queue, params, "juryen-${System.currentTimeMillis()}")
    }

    fun rawTts(): TextToSpeech = tts

    fun shutdown() {
        if (::tts.isInitialized) tts.shutdown()
    }
}
