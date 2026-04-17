package com.hovr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.hovr.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding:          ActivityMainBinding
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var nightModeManager: NightModeManager
    private lateinit var historyManager:   HistoryManager

    private var showingHistory = false

    // Gallery picker
    private val pickMedia = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { launchOverlayUri(it) }
    }

    private val requestStoragePerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pickMedia.launch("image/*")
            else Toast.makeText(this, getString(R.string.toast_storage_perm_denied), Toast.LENGTH_SHORT).show()
        }

    private val overlayPermResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) pickMedia.launch("image/*")
            else Toast.makeText(this, getString(R.string.toast_night_perm_denied), Toast.LENGTH_SHORT).show()
        }

    private val overlayPermForUrl =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) showUrlDialog()
            else Toast.makeText(this, getString(R.string.toast_night_perm_denied), Toast.LENGTH_SHORT).show()
        }

    // ── Locale ───────────────────────────────────────────────────────────────

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        favoritesManager = FavoritesManager(this)
        nightModeManager = NightModeManager(this)
        historyManager   = HistoryManager(this)

        setupUI()
        showFavorites()
    }

    override fun onResume() {
        super.onResume()
        refreshCurrentTab()
        updateNightModeStatus()
    }

    // ── UI setup ─────────────────────────────────────────────────────────────

    private fun setupUI() {
        binding.btnOpenGallery.setOnClickListener { checkOverlayThenStorage() }
        binding.btnOpenUrl.setOnClickListener     { checkOverlayThenUrl() }

        binding.btnStopOverlay.setOnClickListener {
            startService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_CLOSE_ALL
            })
            Toast.makeText(this, getString(R.string.toast_overlays_closed), Toast.LENGTH_SHORT).show()
        }

        binding.btnNightSettings.setOnClickListener { showNightModeDialog() }

        // Language toggle — switches locale and recreates the Activity
        binding.btnLanguage.setOnClickListener {
            LocaleManager.toggle(this)
            recreate()
        }

        binding.btnTabFavorites.setOnClickListener {
            showingHistory = false; updateTabUI(); showFavorites()
        }
        binding.btnTabHistory.setOnClickListener {
            showingHistory = true; updateTabUI(); showHistory()
        }

        binding.btnClearHistory.setOnClickListener {
            val count = historyManager.getAll().size
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_clear_history_title))
                .setMessage(getString(R.string.dialog_clear_history_msg, count))
                .setPositiveButton(getString(R.string.dialog_clear_history_confirm)) { _, _ ->
                    historyManager.clear(); showHistory()
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }

        updateTabUI()
    }

    private fun updateTabUI() {
        val activeColor   = 0xFFF0A050.toInt()
        val inactiveColor = 0xFF3A3228.toInt()

        binding.btnTabFavorites.setTextColor(if (!showingHistory) activeColor else inactiveColor)
        binding.btnTabHistory.setTextColor(if (showingHistory) activeColor else inactiveColor)
        binding.tabIndicatorFav.visibility  = if (!showingHistory) android.view.View.VISIBLE else android.view.View.INVISIBLE
        binding.tabIndicatorHist.visibility = if (showingHistory)  android.view.View.VISIBLE else android.view.View.INVISIBLE
        binding.btnClearHistory.visibility  = if (showingHistory)  android.view.View.VISIBLE else android.view.View.GONE
    }

    // ── Tabs ─────────────────────────────────────────────────────────────────

    private fun refreshCurrentTab() { if (showingHistory) showHistory() else showFavorites() }

    private fun showFavorites() {
        val favs = favoritesManager.getFavorites()
        binding.tvEmptyLabel.visibility = if (favs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvEmptyText.text = getString(R.string.empty_favorites)

        binding.rvList.layoutManager = GridLayoutManager(this, 3)
        binding.rvList.adapter = FavoritesAdapter(
            items        = favs,
            onItemClick  = { uri -> launchOverlayUri(uri) },
            onItemDelete = { uri -> favoritesManager.removeFavorite(uri.toString()); showFavorites() }
        )
    }

    private fun showHistory() {
        val entries = historyManager.getAll()
        binding.tvEmptyLabel.visibility = if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvEmptyText.text = getString(R.string.empty_history)

        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = HistoryAdapter(
            items          = entries,
            historyManager = historyManager,
            onItemClick    = { key -> launchOverlayKey(key) },
            onItemDelete   = { key -> historyManager.remove(key); showHistory() }
        )
    }

    // ── Permission flows ─────────────────────────────────────────────────────

    private fun checkOverlayThenStorage() {
        if (!Settings.canDrawOverlays(this))
            showOverlayPermDialog { overlayPermResult.launch(overlayPermIntent()) }
        else checkStorageAndPick()
    }

    private fun checkOverlayThenUrl() {
        if (!Settings.canDrawOverlays(this))
            showOverlayPermDialog { overlayPermForUrl.launch(overlayPermIntent()) }
        else showUrlDialog()
    }

    private fun checkStorageAndPick() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED)
            pickMedia.launch("image/*")
        else requestStoragePerm.launch(perm)
    }

    private fun showOverlayPermDialog(onOk: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_overlay_perm_title))
            .setMessage(getString(R.string.dialog_overlay_perm_msg))
            .setPositiveButton(getString(R.string.dialog_go_to_settings)) { _, _ -> onOk() }
            .setNegativeButton(getString(R.string.btn_cancel), null).show()
    }

    private fun overlayPermIntent() = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))

    // ── URL dialog ────────────────────────────────────────────────────────────

    private fun showUrlDialog() {
        UrlLoaderDialog(this) { url -> launchOverlayUrl(url) }.show()
    }

    // ── Launch overlay ────────────────────────────────────────────────────────

    private fun launchOverlayUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_URI, uri.toString())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startOverlayService(intent)
    }

    private fun launchOverlayUrl(url: String) {
        startOverlayService(Intent(this, OverlayService::class.java)
            .putExtra(OverlayService.EXTRA_URL, url))
    }

    private fun launchOverlayKey(key: String) {
        val intent = Intent(this, OverlayService::class.java)
        if (key.startsWith("http://") || key.startsWith("https://"))
            intent.putExtra(OverlayService.EXTRA_URL, key)
        else
            intent.putExtra(OverlayService.EXTRA_URI, key)
        startOverlayService(intent)
    }

    private fun startOverlayService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(intent) else startService(intent)
        Toast.makeText(this, getString(R.string.toast_overlay_launched), Toast.LENGTH_SHORT).show()
    }

    // ── Night mode ────────────────────────────────────────────────────────────

    private fun showNightModeDialog() {
        val nm   = nightModeManager
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        val switchRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(14))
        }
        val tvSwitch = android.widget.TextView(this).apply {
            text = getString(R.string.night_toggle_label); textSize = 14f
            layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f)
        }
        val toggle = android.widget.Switch(this).apply { isChecked = nm.isEnabled }
        switchRow.addView(tvSwitch); switchRow.addView(toggle)

        val tvAlphaTitle = android.widget.TextView(this).apply {
            text = getString(R.string.night_opacity_label); textSize = 13f
            setPadding(0, 0, 0, dp(4))
        }
        val tvAlphaVal = android.widget.TextView(this).apply {
            text = "${nm.nightAlphaPercent}%"; textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#C8855A"))
        }
        val sbAlpha = SeekBar(this).apply {
            max = 80; progress = nm.nightAlphaPercent - 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) { tvAlphaVal.text = "${p + 10}%" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        val alphaRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
        }
        alphaRow.addView(sbAlpha.apply { layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f) })
        alphaRow.addView(tvAlphaVal.apply { setPadding(dp(10), 0, 0, 0) })

        val tvSchedule = android.widget.TextView(this).apply {
            text = nm.scheduleDescription(); textSize = 11f
            setTextColor(android.graphics.Color.parseColor("#4A3A2A"))
            setPadding(0, dp(14), 0, 0)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        root.addView(switchRow); root.addView(tvAlphaTitle)
        root.addView(alphaRow);  root.addView(tvSchedule)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.night_dialog_title))
            .setView(root)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                nm.isEnabled         = toggle.isChecked
                nm.nightAlphaPercent = sbAlpha.progress + 10
                updateNightModeStatus()
                Toast.makeText(this, nm.scheduleDescription(), Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun updateNightModeStatus() {
        val nm = nightModeManager
        val (text, color) = when {
            !nm.isEnabled       -> Pair(getString(R.string.night_off), 0xFF3A3228.toInt())
            nm.isNightTimeNow() -> Pair(getString(R.string.night_active, nm.nightAlphaPercent), 0xFFF0A050.toInt())
            else                -> Pair(getString(R.string.night_scheduled, nm.startHour), 0xFF5A4A38.toInt())
        }
        binding.tvNightStatus.text = text
        binding.tvNightStatus.setTextColor(color)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
