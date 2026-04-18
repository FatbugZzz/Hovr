package com.fatbug.hovr

import android.content.Context
import java.util.Calendar

/**
 * Gestiona el modo nocturno automático del overlay.
 *
 * Horario nocturno por defecto: 22:00 → 08:00
 * Opacidad nocturna: configurable (default 40%)
 * El usuario puede activar/desactivar y personalizar desde SharedPreferences.
 */
class NightModeManager(private val context: Context) {

    companion object {
        private const val PREFS = "hovr_nightmode"
        const val KEY_ENABLED   = "enabled"
        const val KEY_START_H   = "start_hour"    // hora inicio noche (0-23)
        const val KEY_END_H     = "end_hour"      // hora fin noche   (0-23)
        const val KEY_NIGHT_ALPHA = "night_alpha" // 0..100 (porcentaje)

        // Defaults
        const val DEFAULT_START = 22
        const val DEFAULT_END   = 8
        const val DEFAULT_NIGHT_ALPHA = 40  // 40% opacidad en modo nocturno
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    var startHour: Int
        get() = prefs.getInt(KEY_START_H, DEFAULT_START)
        set(v) = prefs.edit().putInt(KEY_START_H, v).apply()

    var endHour: Int
        get() = prefs.getInt(KEY_END_H, DEFAULT_END)
        set(v) = prefs.edit().putInt(KEY_END_H, v).apply()

    /** Opacidad nocturna como porcentaje (0-100) */
    var nightAlphaPercent: Int
        get() = prefs.getInt(KEY_NIGHT_ALPHA, DEFAULT_NIGHT_ALPHA)
        set(v) = prefs.edit().putInt(KEY_NIGHT_ALPHA, v.coerceIn(10, 90)).apply()

    /** Alpha nocturna como float (0f..1f) para WindowManager */
    val nightAlphaFloat: Float
        get() = nightAlphaPercent / 100f

    /**
     * Devuelve true si AHORA es horario nocturno y el modo está activado.
     * Soporta rangos que cruzan medianoche (ej: 22→08).
     */
    fun isNightTimeNow(): Boolean {
        if (!isEnabled) return false
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return isInNightRange(hour, startHour, endHour)
    }

    private fun isInNightRange(hour: Int, start: Int, end: Int): Boolean {
        return if (start > end) {
            // Cruza medianoche: ej. 22 → 08
            hour >= start || hour < end
        } else {
            // Mismo día: ej. 01 → 06
            hour in start until end
        }
    }

    /**
     * Calcula el alpha final combinando el alpha del usuario con el modo nocturno.
     * El modo nocturno solo puede REDUCIR el alpha, nunca aumentarlo.
     */
    fun resolveAlpha(userAlpha: Float): Float {
        return if (isNightTimeNow()) {
            minOf(userAlpha, nightAlphaFloat)
        } else {
            userAlpha
        }
    }

    /** Descripción legible del horario configurado */
    fun scheduleDescription(): String =
        "Modo nocturno: %02d:00 → %02d:00 (%d%% opacidad)"
            .format(startHour, endHour, nightAlphaPercent)
}
