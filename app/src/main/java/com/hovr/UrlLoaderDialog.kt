package com.fatbug.hovr

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.getSystemService

/**
 * Dialog flotante para cargar una imagen/GIF desde URL.
 * - Pega automáticamente desde el portapapeles si hay una URL de imagen
 * - Valida la URL antes de confirmar
 * - Callback onUrlConfirmed(url) cuando el usuario acepta
 */
class UrlLoaderDialog(
    private val context: Context,
    private val onUrlConfirmed: (String) -> Unit
) {

    fun show() {
        // ── Build layout programmatically (no XML needed) ──────────────────
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }

        val tvTitle = TextView(context).apply {
            text = "✦ Cargar desde URL"
            textSize = 15f
            setTextColor(Color.parseColor("#E8FF00"))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(12))
        }

        val tvHint = TextView(context).apply {
            text = "Pega un enlace directo a una imagen o GIF\n(termina en .jpg, .png, .gif, .webp…)"
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(10))
        }

        val etUrl = EditText(context).apply {
            hint = "https://ejemplo.com/imagen.gif"
            setHintTextColor(Color.parseColor("#333333"))
            setTextColor(Color.parseColor("#EEEEEE"))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        val tvStatus = TextView(context).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, dp(6), 0, 0)
            text = ""
        }

        root.addView(tvTitle)
        root.addView(tvHint)
        root.addView(etUrl)
        root.addView(tvStatus)

        // ── Auto-paste from clipboard ──────────────────────────────────────
        val clipboard = context.getSystemService<ClipboardManager>()
        val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        if (clip.isImageUrl()) {
            etUrl.setText(clip)
            etUrl.setSelection(clip.length)
            tvStatus.setTextColor(Color.parseColor("#00FFB2"))
            tvStatus.text = "✓ URL pegada desde el portapapeles"
        }

        // ── Live validation ────────────────────────────────────────────────
        etUrl.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val url = s.toString().trim()
                when {
                    url.isEmpty() -> { tvStatus.text = ""; }
                    !url.startsWith("http") -> {
                        tvStatus.setTextColor(Color.parseColor("#FF4444"))
                        tvStatus.text = "⚠ La URL debe empezar con http:// o https://"
                    }
                    else -> {
                        tvStatus.setTextColor(Color.parseColor("#00FFB2"))
                        tvStatus.text = if (url.isGifUrl()) "🎞 GIF detectado" else "🖼 Imagen detectada"
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // ── Dialog ────────────────────────────────────────────────────────
        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .setPositiveButton("Cargar overlay", null)   // override below to prevent auto-dismiss on error
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            val btn: Button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setTextColor(Color.parseColor("#E8FF00"))

            btn.setOnClickListener {
                val url = etUrl.text.toString().trim()
                if (url.isEmpty()) {
                    tvStatus.setTextColor(Color.parseColor("#FF4444"))
                    tvStatus.text = "⚠ Ingresa una URL"
                    return@setOnClickListener
                }
                if (!url.startsWith("http")) {
                    tvStatus.setTextColor(Color.parseColor("#FF4444"))
                    tvStatus.text = "⚠ URL inválida"
                    return@setOnClickListener
                }
                dialog.dismiss()
                onUrlConfirmed(url)
            }
        }

        dialog.show()
    }

    // ── Extensions ────────────────────────────────────────────────────────────

    private fun String.isGifUrl() =
        contains(".gif", ignoreCase = true) ||
        contains("giphy.com", ignoreCase = true) ||
        contains("tenor.com", ignoreCase = true)

    private fun String.isImageUrl() =
        (startsWith("http://") || startsWith("https://")) &&
        (contains(".gif") || contains(".jpg") || contains(".jpeg") ||
         contains(".png") || contains(".webp") ||
         contains("giphy.com") || contains("tenor.com"))

    private fun dp(value: Int) =
        (value * context.resources.displayMetrics.density).toInt()
}
