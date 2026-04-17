package com.hovr

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import java.io.InputStream

/**
 * Home screen widget for Hovr.
 *
 * Shows:
 *   - Thumbnail of the most recently added favorite
 *   - ✦ HOVR label + play button
 *   - Empty state when no favorites exist
 *
 * Tapping the widget or the ▶ button launches the last favorite
 * directly as an overlay — without opening the app.
 *
 * The widget refreshes automatically when:
 *   - The device boots (via BOOT_COMPLETED)
 *   - A favorite is added/removed (via ACTION_FAVORITES_CHANGED broadcast)
 *   - The user taps "refresh" (via ACTION_WIDGET_REFRESH)
 */
class HovrWidgetProvider : AppWidgetProvider() {

    companion object {
        /** Broadcast to force-refresh all Anima widgets */
        const val ACTION_FAVORITES_CHANGED = "com.hovr.ACTION_FAVORITES_CHANGED"
        const val ACTION_WIDGET_REFRESH    = "com.hovr.ACTION_WIDGET_REFRESH"
        const val ACTION_LAUNCH_OVERLAY    = "com.hovr.ACTION_LAUNCH_OVERLAY"
        const val EXTRA_WIDGET_URI         = "widget_uri"

        /**
         * Call this from FavoritesManager whenever favorites change
         * to trigger an immediate widget refresh.
         */
        fun notifyFavoritesChanged(context: Context) {
            val intent = Intent(context, HovrWidgetProvider::class.java).apply {
                action = ACTION_FAVORITES_CHANGED
            }
            context.sendBroadcast(intent)
        }
    }

    // ── onUpdate: called on first add and by the system periodically ──────────

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    // ── onReceive: handle custom broadcasts ───────────────────────────────────

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_FAVORITES_CHANGED,
            ACTION_WIDGET_REFRESH -> {
                // Refresh all widget instances
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(
                    android.content.ComponentName(context, HovrWidgetProvider::class.java)
                )
                ids.forEach { updateWidget(context, mgr, it) }
            }

            ACTION_LAUNCH_OVERLAY -> {
                // Direct overlay launch from widget tap — no MainActivity needed
                val uriString = intent.getStringExtra(EXTRA_WIDGET_URI) ?: return
                val serviceIntent = Intent(context, OverlayService::class.java).apply {
                    putExtra(OverlayService.EXTRA_URI, uriString)
                    // Flag needed when starting service from a non-Activity context
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }

    // ── Core update logic ─────────────────────────────────────────────────────

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_anima)
        val favManager = FavoritesManager(context)
        val favorites  = favManager.getFavorites()   // List<Uri>

        if (favorites.isEmpty()) {
            // Show empty state
            views.setViewVisibility(R.id.widgetEmptyState, View.VISIBLE)
            views.setViewVisibility(R.id.widgetImage,      View.INVISIBLE)
            views.setViewVisibility(R.id.widgetBtnPlay,    View.INVISIBLE)

            // Tapping empty widget opens the app
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val openPi = PendingIntent.getActivity(
                context, widgetId, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetEmptyState, openPi)

        } else {
            // Show last added favorite thumbnail
            val lastUri = favorites.last()
            val bitmap  = loadThumbnail(context, lastUri)

            views.setViewVisibility(R.id.widgetEmptyState, View.GONE)
            views.setViewVisibility(R.id.widgetImage,      View.VISIBLE)
            views.setViewVisibility(R.id.widgetBtnPlay,    View.VISIBLE)

            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widgetImage, bitmap)
            } else {
                views.setImageViewResource(R.id.widgetImage, android.R.drawable.ic_menu_gallery)
            }

            // Tapping anywhere on widget → launch overlay with last favorite
            val launchIntent = Intent(context, HovrWidgetProvider::class.java).apply {
                action = ACTION_LAUNCH_OVERLAY
                putExtra(EXTRA_WIDGET_URI, lastUri.toString())
            }
            val launchPi = PendingIntent.getBroadcast(
                context, widgetId, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetImage,   launchPi)
            views.setOnClickPendingIntent(R.id.widgetBtnPlay, launchPi)
        }

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    // ── Thumbnail loader ──────────────────────────────────────────────────────

    /**
     * Loads a downsampled bitmap from a content URI.
     * Targets ~200×200px to keep memory low in RemoteViews.
     */
    private fun loadThumbnail(context: Context, uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            var stream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(stream, null, options)
            stream?.close()

            val targetSize = 200
            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, targetSize)

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            stream = context.contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(stream, null, decodeOptions)
            stream?.close()
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, target: Int): Int {
        var size = 1
        val longest = maxOf(width, height)
        while (longest / (size * 2) >= target) size *= 2
        return size
    }
}
