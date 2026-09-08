package com.sajimahmed.juryen.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.sajimahmed.juryen.service.JuryenAccessibilityService

class ActionExecutor(
    private val context: Context,
    private val speak: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())

    fun execute(actions: List<Action>) {
        runAt(0, actions)
    }

    private fun runAt(index: Int, actions: List<Action>) {
        if (index >= actions.size) return
        val action = actions[index]
        main.post {
            val ok = executeOne(action)
            if (!ok && action.type != "wait") {
                speak("I couldn't complete the step: ${action.value ?: action.type}.")
                return@post
            }
            main.postDelayed({ runAt(index + 1, actions) }, action.ms.coerceIn(100L, 5000L))
        }
    }

    private fun executeOne(action: Action): Boolean {
        return when (action.type) {
            "open_app" -> {
                val name = action.value ?: return false
                val pkg = AppResolver.findPackage(context, name) ?: return false
                val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }
            "tap_text" -> JuryenAccessibilityService.instance?.tapByText(action.value ?: "") == true
            "tap_description" -> JuryenAccessibilityService.instance?.tapByDescription(action.value ?: "") == true
            "type_text" -> JuryenAccessibilityService.instance?.typeText(action.value ?: "") == true
            "back" -> JuryenAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) == true
            "home" -> JuryenAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) == true
            "wait" -> true
            "swipe_up" -> JuryenAccessibilityService.instance?.swipeVertical(true) == true
            "swipe_down" -> JuryenAccessibilityService.instance?.swipeVertical(false) == true
            "select_first_image" -> JuryenAccessibilityService.instance?.selectFirstImage() == true
            "open_url" -> {
                val url = action.value ?: return false
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
            else -> false
        }
    }
}
