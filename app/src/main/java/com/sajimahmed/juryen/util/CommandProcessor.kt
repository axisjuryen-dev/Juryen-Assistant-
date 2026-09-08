package com.sajimahmed.juryen.util

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import com.sajimahmed.juryen.service.JuryenAccessibilityService
import java.util.Locale

class CommandProcessor(
    private val context: Context,
    private val tts: TextToSpeech
) {
    private val ai = AiCommandEngine(context)

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "juryen")
    }

    fun process(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return

        if (isCreatorQuestion(text.lowercase(Locale.getDefault()))) {
            speak("I was created by Sajim Ahmed.")
            return
        }

        when {
            text.contains("open facebook", true) -> openApp("Facebook")
            text.contains("open whatsapp", true) -> openApp("WhatsApp")
            text.contains("open youtube", true) -> openApp("YouTube")
            text.contains("open chrome", true) -> openApp("Chrome")
            text.contains("open camera", true) -> openApp("Camera")
            text.contains("open gallery", true) || text.contains("open photos", true) -> openApp("Photos")
            text.contains("go home", true) -> JuryenAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                ?: speak("Accessibility access is not enabled.")
            else -> processWithAi(text)
        }
    }

    private fun processWithAi(text: String) {
        Thread {
            val plan = ai.createPlan(text)
            if (plan == null) {
                speak("I couldn't understand that command. Please add an AI API key in Juryen settings, or try a simpler command.")
                return@Thread
            }
            ActionExecutor(context, ::speak).execute(plan)
        }.start()
    }

    private fun openApp(label: String) {
        val pm = context.packageManager
        val packageName = AppResolver.findPackage(context, label)
        val launchIntent = packageName?.let { pm.getLaunchIntentForPackage(it) }
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            speak("Opening $label")
        } else {
            speak("$label is not installed on this device.")
        }
    }

    private fun isCreatorQuestion(text: String): Boolean {
        val triggers = listOf(
            "who made you", "who created you", "who is your creator",
            "who built you", "who is your developer", "tomar creator ke",
            "tomake ke baniyeche", "tomar nirmata ke", "tomar malik ke"
        )
        return triggers.any { text.contains(it) }
    }
}

object AppResolver {
    fun findPackage(context: Context, requested: String): String? {
        val normalized = requested.trim().lowercase(Locale.getDefault())
        val aliases = mapOf(
            "facebook" to listOf("com.facebook.katana"),
            "whatsapp" to listOf("com.whatsapp"),
            "youtube" to listOf("com.google.android.youtube"),
            "chrome" to listOf("com.android.chrome"),
            "photos" to listOf("com.google.android.apps.photos"),
            "gallery" to listOf("com.google.android.apps.photos"),
            "camera" to listOf("com.android.camera", "com.android.camera2")
        )
        aliases[normalized]?.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }?.let { return it }

        @Suppress("DEPRECATION")
        return context.packageManager.getInstalledApplications(0)
            .firstOrNull { context.packageManager.getApplicationLabel(it).toString().equals(requested, true) }
            ?.packageName
    }
}
