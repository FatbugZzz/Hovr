package com.fatbug.hovr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.SeekBar
import androidx.core.app.NotificationCompat
import com.fatbug.hovr.databinding.OverlayWindowBinding

class OverlayService : Service() {

    companion object {
        const val EXTRA_URI      = "extra_uri"
        const val EXTRA_URL      = "extra_url"
        const val ACTION_CLOSE_ALL = "com.fatbug.hovr.CLOSE_ALL"

        private const val CHANNEL_ID     = "hovr_overlay"
        private const val NOTIF_ID       = 1
        private const val NIGHT_CHECK_MS = 60_000L
        private const val MAX_OVERLAYS   = 3

        val SPEED_VALUES = floatArrayOf(0.25f, 0.5f, 1.0f, 2.0f, 4.0f)
        val SPEED_LABELS = arrayOf("0.25×", "0.5×", "1×", "2×", "4×")

        private const val MIN_W_DP  = 120
        private const val MAX_W_DP  = 420
        private const val BUBBLE_DP = 72

        // Posiciones de inicio escalonadas para que no se apilen una encima de la otra
        private val START_POSITIONS = listOf(
            Pair(40, 160),
            Pair(60, 220),
            Pair(80, 280)
        )
    }

    // ── Servicios compartidos ─────────────────────────────────────────────────
    private lateinit var wm:       WindowManager
    private lateinit var favMgr:   FavoritesManager
    private lateinit var nightMgr: NightModeManager
    private lateinit var histMgr:  HistoryManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Lista de overlays activos ─────────────────────────────────────────────
    private val overlays = mutableListOf<OverlayInstance>()

    // ── Night mode global ─────────────────────────────────────────────────────
    private val nightRunner = object : Runnable {
        override fun run() {
            overlays.forEach { it.applyNightAlpha() }
            mainHandler.postDelayed(this, NIGHT_CHECK_MS)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm       = getSystemService(WINDOW_SERVICE) as WindowManager
        favMgr   = FavoritesManager(this)
        nightMgr = NightModeManager(this)
        histMgr  = HistoryManager(this)
        createChannel()
        startForeground(NOTIF_ID, buildNotif())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY

        // Acción para cerrar todos los overlays desde la notificación
        if (intent.action == ACTION_CLOSE_ALL) {
            closeAll()
            stopSelf()
            return START_NOT_STICKY
        }

        val uriStr = intent.getStringExtra(EXTRA_URI)
        val urlStr = intent.getStringExtra(EXTRA_URL)

        if (uriStr == null && urlStr == null) return START_NOT_STICKY

        // Límite máximo de overlays
        if (overlays.size >= MAX_OVERLAYS) {
            // Eliminar el más antiguo para hacer espacio
            overlays.removeFirstOrNull()?.destroy()
        }

        // Registrar en historial
        when {
            uriStr != null -> {
                val uri  = Uri.parse(uriStr)
                val name = uri.lastPathSegment ?: uriStr
                val gif  = isGifUri(uri)
                histMgr.push(uriStr, name, gif)
            }
            urlStr != null -> {
                val domain = runCatching { Uri.parse(urlStr).host ?: urlStr }.getOrDefault(urlStr)
                histMgr.push(urlStr, domain, urlStr.contains(".gif", true))
            }
        }

        // Crear nueva instancia con posición escalonada
        val position = START_POSITIONS.getOrElse(overlays.size) { Pair(60, 180) }
        val instance = OverlayInstance(
            uriStr = uriStr,
            urlStr = urlStr,
            startX = position.first,
            startY = position.second
        )
        overlays.add(instance)
        instance.create()

        updateNotif()

        // Iniciar/reiniciar night mode checker
        mainHandler.removeCallbacks(nightRunner)
        mainHandler.post(nightRunner)

        return START_STICKY
    }

    private fun closeAll() {
        overlays.forEach { it.destroy() }
        overlays.clear()
        mainHandler.removeCallbacks(nightRunner)
    }

    override fun onDestroy() {
        super.onDestroy()
        closeAll()
    }

    // ── Helpers globales ──────────────────────────────────────────────────────

    private fun isGifUri(uri: Uri): Boolean = try {
        val mime = contentResolver.getType(uri) ?: ""
        mime.contains("gif") || uri.toString().endsWith(".gif", true)
    } catch (_: Exception) { false }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    // ── Notificación ──────────────────────────────────────────────────────────

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Hovr Overlay", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Overlays activos" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun updateNotif() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotif())
    }

    private fun buildNotif(): Notification {
        val count = overlays.size
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).apply { action = ACTION_CLOSE_ALL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("✦ Hovr — $count overlay${if (count != 1) "s" else ""} activo${if (count != 1) "s" else ""}")
            .setContentText("Toca para abrir Hovr • Máximo $MAX_OVERLAYS simultáneos")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cerrar todos", stopPi)
            .setOngoing(true)
            .build()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OverlayInstance — encapsula el estado completo de UN overlay
    // ══════════════════════════════════════════════════════════════════════════

    inner class OverlayInstance(
        private val uriStr: String?,
        private val urlStr: String?,
        private val startX: Int,
        private val startY: Int
    ) {
        private lateinit var binding: OverlayWindowBinding
        private lateinit var params:  WindowManager.LayoutParams
        private var view: View? = null

        // State
        private val currentUri = uriStr?.let { Uri.parse(it) }
        private val currentUrl = urlStr ?: uriStr
        private var isFav      = favMgr.isFavorite(currentUrl ?: "")
        private var userAlpha  = 1.0f
        private var isBubble   = false
        private var isBarHidden = false  // barra superior oculta con long press
        private var isLocked   = false
        private var speedIndex = 2
        private var curW       = 0
        private var savedW     = 0
        private var rotationDeg = 0f   // 0, 90, 180, 270

        // GIF state
        private var gifRunning = false

        // Handler dedicado SOLO para long press — completamente separado del GIF
        private val longPressHandler = Handler(Looper.getMainLooper())
        private var longPressRunnable: Runnable? = null

        // Manual pinch state
        private var pinchStartDist = 0f
        private var pinchStartW    = 0
        private var pinchStartH    = 0
        private var isPinching     = false

        // ── Create ────────────────────────────────────────────────────────────

        fun create() {
            val themedCtx = android.view.ContextThemeWrapper(applicationContext, R.style.Theme_Hovr)
            binding = OverlayWindowBinding.inflate(LayoutInflater.from(themedCtx))
            view    = binding.root

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            curW   = dpToPx(280)
            savedW = curW

            params = WindowManager.LayoutParams(
                curW,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = startX; y = startY
                alpha = nightMgr.resolveAlpha(userAlpha)
            }

            wm.addView(view, params)
            setupDrag()
            setupButtons()
            loadImage()
            updateFavBtn()
            updateNightBtn()
            updateLockBtn()
        }

        // ── Drag + Pinch ──────────────────────────────────────────────────────

        private fun fingerDistance(e: MotionEvent): Float {
            val dx = e.getX(0) - e.getX(1)
            val dy = e.getY(0) - e.getY(1)
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }

        private fun setupDrag() {
            // ── Drag handle ───────────────────────────────────────────────────
            var sx = 0; var sy = 0; var ix = 0; var iy = 0
            binding.dragHandle.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = e.rawX.toInt(); sy = e.rawY.toInt()
                        ix = params.x;       iy = params.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isLocked) {
                            params.x = ix + (e.rawX - sx).toInt()
                            params.y = iy + (e.rawY - sy).toInt()
                            wm.updateViewLayout(view, params)
                        }
                    }
                }
                true
            }

            // ── ImageView: pinch + drag + long press ──────────────────────────
            var imgSx = 0f; var imgSy = 0f
            var imgIx = 0;  var imgIy = 0
            var isDragging  = false
            val longPressMs = 500L

            fun cancelLongPress() {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                longPressRunnable = null
            }

            fun scheduleLongPress(action: () -> Unit) {
                cancelLongPress()
                val r = Runnable { action() }
                longPressRunnable = r
                longPressHandler.postDelayed(r, longPressMs)
            }

            binding.imageView.setOnTouchListener { _, e ->
                when (e.actionMasked) {

                    MotionEvent.ACTION_DOWN -> {
                        imgSx = e.rawX; imgSy = e.rawY
                        imgIx = params.x; imgIy = params.y
                        isDragging = false; isPinching = false

                        if (isBubble) {
                            // Long press en burbuja → expandir
                            scheduleLongPress { expandFromBubble() }
                        } else {
                            // Long press en modo normal → ocultar/mostrar barra
                            scheduleLongPress { toggleBar() }
                        }
                    }

                    MotionEvent.ACTION_POINTER_DOWN -> {
                        cancelLongPress()
                        if (!isLocked && e.pointerCount == 2) {
                            isPinching     = true
                            isDragging     = false
                            pinchStartDist = fingerDistance(e).coerceAtLeast(1f)
                            pinchStartW    = params.width
                            pinchStartH    = binding.imageView.layoutParams.height
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (e.pointerCount >= 2 && isPinching && !isLocked) {
                            cancelLongPress()
                            val ratio = fingerDistance(e).coerceAtLeast(1f) / pinchStartDist
                            val newW  = (pinchStartW * ratio).toInt()
                                .coerceIn(dpToPx(MIN_W_DP), dpToPx(MAX_W_DP))
                            val newH  = (pinchStartH * ratio).toInt()
                                .coerceAtLeast(dpToPx(80))
                            params.width = newW; curW = newW; savedW = newW
                            binding.imageView.layoutParams.height = newH
                            wm.updateViewLayout(view, params)
                            binding.root.requestLayout()
                        } else if (e.pointerCount == 1 && !isPinching && !isLocked && !isBubble) {
                            val dx = (e.rawX - imgSx).toInt()
                            val dy = (e.rawY - imgSy).toInt()
                            if (!isDragging && (dx * dx + dy * dy) > 100) {
                                isDragging = true
                                cancelLongPress()   // movió el dedo → no es long press
                            }
                            if (isDragging) {
                                params.x = imgIx + dx; params.y = imgIy + dy
                                wm.updateViewLayout(view, params)
                            }
                        }
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        cancelLongPress()
                        isPinching = false; isDragging = false
                    }

                    MotionEvent.ACTION_POINTER_UP -> {
                        isPinching = false; isDragging = false
                    }
                }
                true
            }
        }

        // ── Buttons ───────────────────────────────────────────────────────────

        private fun setupButtons() {
            // Botón cerrar — solo cierra ESTE overlay
            binding.btnClose.setOnClickListener {
                destroy()
                overlays.remove(this)
                updateNotif()
                if (overlays.isEmpty()) stopSelf()
            }

            binding.btnFavorite.setOnClickListener { toggleFav() }
            binding.btnBubble.setOnClickListener   { toggleBubble() }
            binding.btnLock.setOnClickListener     { toggleLock() }
            binding.btnRotate.setOnClickListener   { rotateImage() }
            binding.btnNight.setOnClickListener    {
                nightMgr.isEnabled = !nightMgr.isEnabled
                overlays.forEach { it.applyNightAlpha() }
            }

            binding.btnExpand.setOnClickListener {
                val vis = binding.controlsPanel.visibility == View.VISIBLE
                binding.controlsPanel.visibility = if (vis) View.GONE else View.VISIBLE
                binding.btnExpand.text = if (vis) "⌄" else "⌃"
                setFocusable(!vis)
            }

            binding.seekBarSpeed.max      = SPEED_VALUES.size - 1
            binding.seekBarSpeed.progress = speedIndex
            binding.tvSpeedLabel.text     = SPEED_LABELS[speedIndex]
            binding.seekBarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                    speedIndex = p; binding.tvSpeedLabel.text = SPEED_LABELS[p]
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            binding.seekBarOpacity.progress = 100
            binding.tvOpacityLabel.text     = "100%"
            binding.seekBarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                    userAlpha = 0.2f + (p / 100f) * 0.8f
                    applyNightAlpha()
                    binding.tvOpacityLabel.text = "${(userAlpha * 100).toInt()}%"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            binding.controlsPanel.visibility = View.GONE
        }

        // ── Load image ────────────────────────────────────────────────────────

        private fun loadImage() {
            val uri = currentUri
            val url = currentUrl
            originalBitmap = null   // resetear bitmap original al cargar imagen nueva
            rotationDeg    = 0f     // resetear rotación
            binding.imageView.rotation = 0f
            Thread {
                try {
                    val bytes: ByteArray = when {
                        uri != null -> contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@Thread
                        url != null -> {
                            val conn = java.net.URL(url).openConnection()
                            conn.connectTimeout = 10_000; conn.readTimeout = 15_000
                            conn.getInputStream().use { it.readBytes() }
                        }
                        else -> return@Thread
                    }
                    val isGif = bytes.size >= 3 &&
                        bytes[0] == 'G'.code.toByte() &&
                        bytes[1] == 'I'.code.toByte() &&
                        bytes[2] == 'F'.code.toByte()

                    mainHandler.post {
                        if (isGif) decodeAndPlayGif(bytes)
                        else {
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                originalBitmap = bmp   // guardar para rotación limpia
                                binding.imageView.setImageBitmap(bmp)
                            }
                        }
                    }
                } catch (e: Exception) {
                    mainHandler.post { android.util.Log.e("Hovr", "loadImage: ${e.message}") }
                }
            }.start()
        }

        private fun decodeAndPlayGif(bytes: ByteArray) {
            stopGif()
            val movie = android.graphics.Movie.decodeByteArray(bytes, 0, bytes.size)
            if (movie != null && movie.duration() > 0) {
                playWithMovie(movie)
            } else {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) binding.imageView.setImageBitmap(bmp)
            }
        }

        private fun playWithMovie(movie: android.graphics.Movie) {
            val duration  = movie.duration().coerceAtLeast(1)
            val startTime = System.currentTimeMillis()
            val srcW      = movie.width().coerceAtLeast(1)
            val srcH      = movie.height().coerceAtLeast(1)

            // Bitmap temporal donde se dibuja cada frame sin rotar
            val frameBmp    = Bitmap.createBitmap(srcW, srcH, Bitmap.Config.ARGB_8888)
            val frameCanvas = android.graphics.Canvas(frameBmp)

            gifRunning = true

            fun tick() {
                if (!gifRunning) return

                // 1 — Dibujar el frame actual en el bitmap temporal (sin rotación)
                val elapsed = ((System.currentTimeMillis() - startTime) * SPEED_VALUES[speedIndex]).toLong()
                movie.setTime((elapsed % duration).toInt())
                frameCanvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
                movie.draw(frameCanvas, 0f, 0f)

                // 2 — Rotar el bitmap resultante con Matrix
                val outBmp = if (rotationDeg == 0f) {
                    frameBmp   // sin rotación: usar directo
                } else {
                    val matrix = android.graphics.Matrix().apply { postRotate(rotationDeg) }
                    Bitmap.createBitmap(frameBmp, 0, 0, srcW, srcH, matrix, true)
                }

                binding.imageView.setImageBitmap(outBmp)

                val delay = (16 / SPEED_VALUES[speedIndex]).toLong().coerceAtLeast(8L)
                mainHandler.postDelayed({ tick() }, delay)
            }
            tick()
        }

        private fun stopGif() { gifRunning = false }

        // ── Rotate ────────────────────────────────────────────────────────────

        private var originalBitmap: Bitmap? = null

        private fun rotateImage() {
            rotationDeg = (rotationDeg + 90f) % 360f

            if (originalBitmap != null) {
                // Imagen estática — rotar bitmap desde el original
                applyBitmapRotation()
            } else {
                // GIF — el loop tick() ya lee rotationDeg en cada frame
                // Solo necesitamos resetear la rotation visual del View a 0
                // y ajustar el tamaño del contenedor
                binding.imageView.rotation = 0f
                adjustContainerForRotation()
            }

            binding.btnRotate.text = when (rotationDeg) {
                0f   -> "↻"
                90f  -> "↺"
                180f -> "↻"
                else -> "↺"
            }
        }

        private fun applyBitmapRotation() {
            val src = originalBitmap ?: return
            val matrix = android.graphics.Matrix().apply { postRotate(rotationDeg) }
            val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            binding.imageView.rotation = 0f   // sin rotación visual — el bitmap ya está rotado
            binding.imageView.setImageBitmap(rotated)

            // Ajustar altura del ImageView para mantener proporción
            val isLandscape = rotationDeg == 90f || rotationDeg == 270f
            val baseW = params.width.toFloat()
            val aspect = src.height.toFloat() / src.width.toFloat()
            binding.imageView.layoutParams.height = if (isLandscape) {
                // imagen apaisada dentro del contenedor: alto = ancho * (w/h original)
                (baseW / aspect).toInt().coerceAtLeast(dpToPx(80))
            } else {
                // portrait o 180°: alto normal proporcional
                (baseW * aspect).toInt().coerceIn(dpToPx(80), dpToPx(400))
            }
            wm.updateViewLayout(view, params)
            binding.root.requestLayout()
        }

        private fun adjustContainerForRotation() {
            val isLandscape = rotationDeg == 90f || rotationDeg == 270f
            // Para GIFs en landscape: hacemos la ventana más estrecha y alta
            // para que la imagen rotada quepa sin recortarse
            val baseH = dpToPx(200)
            val baseW = curW
            if (isLandscape) {
                params.width = (baseH * 0.75f).toInt()
                    .coerceIn(dpToPx(MIN_W_DP), dpToPx(MAX_W_DP))
                binding.imageView.layoutParams.height = (baseW * 0.75f).toInt()
            } else {
                params.width = curW
                binding.imageView.layoutParams.height = baseH
            }
            wm.updateViewLayout(view, params)
            binding.root.requestLayout()
        }

        // ── Bubble ────────────────────────────────────────────────────────────

        /** Llamado desde long press en la imagen cuando estamos en modo burbuja */
        private fun expandFromBubble() {
            if (!isBubble) return
            isBubble = false
            params.width  = savedW
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            wm.updateViewLayout(view, params)
            binding.dragHandle.visibility =
                if (isBarHidden) View.GONE else View.VISIBLE
            binding.imageView.layoutParams.height = dpToPx(200)
            binding.imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            binding.root.setBackgroundResource(R.drawable.overlay_bg)
            binding.btnBubble.text = "⊡"
            binding.root.requestLayout()
        }

        /** Oculta o muestra la barra superior con long press en la imagen */
        private fun toggleBar() {
            isBarHidden = !isBarHidden
            binding.dragHandle.visibility    = if (isBarHidden) View.GONE else View.VISIBLE
            binding.controlsPanel.visibility = View.GONE
            if (isBarHidden) {
                // Quitar el fondo del contenedor — elimina bordes y márgenes visibles
                binding.root.setBackgroundResource(android.R.color.transparent)
                binding.imageView.setBackgroundResource(android.R.color.transparent)
                setFocusable(false)
                binding.btnExpand.text = "⌄"
            } else {
                // Restaurar fondo original al mostrar la barra
                binding.root.setBackgroundResource(R.drawable.overlay_bg)
            }
            wm.updateViewLayout(view, params)
            binding.root.requestLayout()
        }

        private fun toggleBubble() {
            isBubble = !isBubble
            if (isBubble) {
                val bPx = dpToPx(BUBBLE_DP)
                params.width = bPx; params.height = bPx
                wm.updateViewLayout(view, params)
                binding.dragHandle.visibility = View.GONE
                binding.controlsPanel.visibility = View.GONE
                binding.imageView.layoutParams.height = bPx
                binding.imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                binding.root.setBackgroundResource(R.drawable.bubble_bg)
                binding.btnBubble.text = "⊡"
            } else {
                params.width = savedW; params.height = WindowManager.LayoutParams.WRAP_CONTENT
                wm.updateViewLayout(view, params)
                // Respetar estado de barra oculta al salir de burbuja
                binding.dragHandle.visibility = if (isBarHidden) View.GONE else View.VISIBLE
                binding.imageView.layoutParams.height = dpToPx(200)
                binding.imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                binding.root.setBackgroundResource(R.drawable.overlay_bg)
                binding.btnBubble.text = "⊡"
            }
            binding.root.requestLayout()
        }

        // ── Lock ──────────────────────────────────────────────────────────────

        private fun toggleLock() {
            isLocked = !isLocked; updateLockBtn()
        }

        private fun updateLockBtn() {
            binding.btnLock.text      = if (isLocked) "🔒" else "🔓"
            binding.btnLock.alpha     = if (isLocked) 1f else 0.4f
            binding.dragHandle.alpha  = if (isLocked) 0.4f else 1f
            binding.tvGripIcon.setTextColor(
                if (isLocked) 0xFF222222.toInt() else 0xFF555555.toInt()
            )
        }

        // ── Night / Opacity ───────────────────────────────────────────────────

        fun applyNightAlpha() {
            params.alpha = nightMgr.resolveAlpha(userAlpha)
            try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
            updateNightBtn()
        }

        private fun updateNightBtn() {
            val active = nightMgr.isEnabled && nightMgr.isNightTimeNow()
            binding.btnNight.text  = if (active) "☀" else "🌙"
            binding.btnNight.alpha = if (active) 1f else 0.4f
        }

        // ── Favorites ─────────────────────────────────────────────────────────

        private fun toggleFav() {
            val key = currentUrl ?: return
            if (isFav) { favMgr.removeFavorite(key); isFav = false }
            else       { favMgr.addFavorite(key);    isFav = true  }
            updateFavBtn()
        }

        private fun updateFavBtn() {
            binding.btnFavorite.text = if (isFav) "★" else "☆"
        }

        // ── Focusable ─────────────────────────────────────────────────────────

        private fun setFocusable(on: Boolean) {
            params.flags = if (on)
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            else
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            wm.updateViewLayout(view, params)
        }

        // ── Destroy ───────────────────────────────────────────────────────────

        fun destroy() {
            stopGif()
            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
            view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            view = null
        }
    }
}
