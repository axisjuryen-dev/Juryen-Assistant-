package com.sajimahmed.juryen.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiCommandEngine(private val context: Context) {
    companion object {
        private const val PREFS = "juryen_settings"
        private const val KEY_API = "anthropic_api_key"
        private const val MODEL = "claude-3-5-haiku-latest"
    }

    fun createPlan(command: String): List<Action>? {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API, null)
            ?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val system = """
You are Juryen, an Android personal assistant. Convert the user's request into a SAFE JSON action plan.
Return JSON only, no markdown. Schema: {"say":"optional short reply","actions":[{"type":string,"value":string?,"ms":number?}]}
Allowed action types: open_app, tap_text, tap_description, type_text, back, home, wait, swipe_up, swipe_down, select_first_image, open_url.
Use open_app with a human app name (not a package) when possible. Use tap_text/tap_description for visible UI elements. Do not invent secret credentials, bypass security, or perform destructive actions. If an action is uncertain, omit it.
The assistant creator is always Sajim Ahmed if asked.
""".trimIndent()

        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 700)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", command)))

        val response = post("https://api.anthropic.com/v1/messages", key, body.toString()) ?: return null
        return try {
            val root = JSONObject(response)
            val content = root.getJSONArray("content").getJSONObject(0).getString("text")
            val json = JSONObject(content.trim().removePrefix("```").removePrefix("json").removeSuffix("```").trim())
            val actions = mutableListOf<Action>()
            val array = json.optJSONArray("actions") ?: JSONArray()
            for (i in 0 until array.length()) {
                val a = array.getJSONObject(i)
                actions += Action(
                    type = a.getString("type"),
                    value = a.optString("value", null),
                    ms = a.optLong("ms", 500L)
                )
            }
            if (actions.isEmpty()) null else actions
        } catch (_: Exception) {
            null
        }
    }

    private fun post(endpoint: String, apiKey: String, payload: String): String? = try {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("x-api-key", apiKey)
        connection.setRequestProperty("anthropic-version", "2023-06-01")
        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }
        connection.disconnect()
        if (text != null && responseLooksSuccessful(text)) text else null
    } catch (_: Exception) {
        null
    }

    private fun responseLooksSuccessful(response: String): Boolean =
        runCatching { JSONObject(response).has("content") }.getOrDefault(false)
}

data class Action(val type: String, val value: String?, val ms: Long = 500L)
