package pl.footballaipro

import android.content.Context

/**
 * API key is stored locally on this device and is never committed to Git.
 * For a public production release this should be moved behind a backend proxy.
 */
object ApiFootballSettings {
    private const val PREFS = "football_ai_api_settings"
    private const val KEY_API = "api_football_key"

    fun getApiKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API, "")
            .orEmpty()

    fun saveApiKey(context: Context, apiKey: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API, apiKey.trim())
            .apply()
    }

    fun hasApiKey(context: Context): Boolean = getApiKey(context).isNotBlank()
}
