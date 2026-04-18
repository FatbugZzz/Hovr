package com.fatbug.hovr

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Manages app language — persists the user's choice in SharedPreferences
 * and wraps the Context with the correct Locale so all string resources
 * resolve in the right language.
 *
 * Supported: "es" (Spanish, default) and "en" (English).
 */
object LocaleManager {

    private const val PREFS = "hovr_locale"
    private const val KEY   = "language"
    const val LANG_ES = "es"
    const val LANG_EN = "en"

    /** Returns the currently saved language code ("es" or "en"). */
    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, LANG_ES) ?: LANG_ES
    }

    /** Saves the chosen language code. */
    fun setLanguage(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, lang).apply()
    }

    /** Toggles between "es" and "en", saves the new value, and returns it. */
    fun toggle(context: Context): String {
        val next = if (getLanguage(context) == LANG_ES) LANG_EN else LANG_ES
        setLanguage(context, next)
        return next
    }

    /**
     * Wraps a Context with the saved Locale so all getString() calls
     * on the wrapped context resolve correctly.
     *
     * Call this in Activity.attachBaseContext():
     *   override fun attachBaseContext(base: Context) {
     *       super.attachBaseContext(LocaleManager.wrap(base))
     *   }
     */
    fun wrap(context: Context): Context {
        val lang   = getLanguage(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
