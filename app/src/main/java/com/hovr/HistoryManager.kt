package com.fatbug.hovr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps a rolling history of the last MAX_SIZE images/GIFs opened.
 * Each entry stores: uri/url string, display name, timestamp, isGif flag.
 * Persisted in SharedPreferences as a JSON array (newest first).
 */
class HistoryManager(private val context: Context) {

    companion object {
        private const val PREFS = "hovr_history"
        private const val KEY   = "entries"
        const val MAX_SIZE = 10
    }

    data class HistoryEntry(
        val key: String,        // content URI string or http URL
        val name: String,       // display name (filename or domain)
        val timestamp: Long,    // System.currentTimeMillis()
        val isGif: Boolean
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Read ──────────────────────────────────────────────────────────────────

    fun getAll(): List<HistoryEntry> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                HistoryEntry(
                    key       = obj.getString("key"),
                    name      = obj.getString("name"),
                    timestamp = obj.getLong("ts"),
                    isGif     = obj.optBoolean("gif", false)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Adds an entry to the front of the list.
     * If the same key already exists it is moved to the front (no duplicates).
     * Trims to MAX_SIZE.
     */
    fun push(key: String, name: String, isGif: Boolean) {
        val list = getAll().toMutableList()
        // Remove existing entry with same key
        list.removeAll { it.key == key }
        // Prepend new entry
        list.add(0, HistoryEntry(key, name, System.currentTimeMillis(), isGif))
        // Trim
        val trimmed = list.take(MAX_SIZE)
        save(trimmed)
    }

    fun remove(key: String) {
        val list = getAll().toMutableList()
        list.removeAll { it.key == key }
        save(list)
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun save(entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("key",  e.key)
                put("name", e.name)
                put("ts",   e.timestamp)
                put("gif",  e.isGif)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Human-readable relative time: "hace 2 min", "hace 3 h", "ayer" */
    fun relativeTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val mins  = diff / 60_000
        val hours = diff / 3_600_000
        val days  = diff / 86_400_000
        return when {
            mins  < 1  -> "ahora"
            mins  < 60 -> "hace ${mins}min"
            hours < 24 -> "hace ${hours}h"
            days  == 1L -> "ayer"
            else       -> "hace ${days}d"
        }
    }
}
