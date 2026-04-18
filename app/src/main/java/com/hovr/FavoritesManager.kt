package com.fatbug.hovr

import android.content.Context
import android.net.Uri

/**
 * Manages favorite image/GIF URIs in SharedPreferences.
 * Automatically notifies the home screen widget whenever the list changes.
 */
class FavoritesManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("hovr_favs", Context.MODE_PRIVATE)
    private val KEY   = "favorites"

    fun getFavorites(): List<Uri> =
        (prefs.getStringSet(KEY, emptySet()) ?: emptySet())
            .map { Uri.parse(it) }

    fun addFavorite(uriString: String) {
        val set = prefs.getStringSet(KEY, mutableSetOf())!!.toMutableSet()
        set.add(uriString)
        prefs.edit().putStringSet(KEY, set).apply()
        notifyWidget()
    }

    fun removeFavorite(uriString: String) {
        val set = prefs.getStringSet(KEY, mutableSetOf())!!.toMutableSet()
        set.remove(uriString)
        prefs.edit().putStringSet(KEY, set).apply()
        notifyWidget()
    }

    fun isFavorite(uriString: String): Boolean =
        prefs.getStringSet(KEY, emptySet())?.contains(uriString) == true

    /** Triggers a refresh of all Anima home screen widgets. */
    private fun notifyWidget() {
        HovrWidgetProvider.notifyFavoritesChanged(context)
    }
}
