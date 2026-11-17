package com.veacon.veilmusic.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Light, dark, or whatever the device itself is set to. */
enum class AppearanceMode {
    LIGHT, DARK, SYSTEM;

    /** Stepper helpers; the control only moves one step at a time. */
    fun next(): AppearanceMode = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]
    fun previous(): AppearanceMode = entries[(ordinal - 1).coerceAtLeast(0)]
}

/** Browsing surfaces follow the selected appearance; artwork and player retain their own colors. */
object AppPalette {
    /** The user's choice, including [AppearanceMode.SYSTEM]. */
    var mode by mutableStateOf(AppearanceMode.LIGHT)
        private set

    /** Effective value every surface reads; SYSTEM is resolved against the device. */
    var isDark by mutableStateOf(false)
        private set

    fun load(context: Context) {
        mode = readMode(context)
        isDark = resolve(context, mode)
    }

    fun setMode(context: Context, newMode: AppearanceMode) {
        mode = newMode
        isDark = resolve(context, newMode)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, newMode.name)
            // Keep the legacy boolean in step so an older build still opens sanely.
            .putBoolean(KEY_DARK, isDark)
            .apply()
    }

    /** Kept for the two-state callers that predate [AppearanceMode]. */
    fun setDark(context: Context, enabled: Boolean) =
        setMode(context, if (enabled) AppearanceMode.DARK else AppearanceMode.LIGHT)

    /** Re-resolve after a device theme change while SYSTEM is active. */
    fun refreshSystem(context: Context) {
        if (mode == AppearanceMode.SYSTEM) isDark = systemIsDark(context)
    }

    fun adaptive(light: Color, dark: Color): Color = if (isDark) dark else light

    val background get() = adaptive(Color.White, Color(0xFF101012))
    val primary get() = adaptive(Color(0xFF1D1D1F), Color(0xFFF5F5F7))
    val secondary get() = adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8))
    val surface get() = adaptive(Color(0xFFF5F5F7), Color(0xFF222225))
    val selected get() = adaptive(Color(0xFFE8E8ED), Color(0xFF38383D))
    val accent = Color(0xFFFA233B)
    fun contentOn(background: Color): Color = if (background.luminance() < 0.4f) Color.White else Color(0xFF1D1D1F)

    private fun readMode(context: Context): AppearanceMode {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_MODE, null)
        if (stored != null) {
            return runCatching { AppearanceMode.valueOf(stored) }.getOrDefault(AppearanceMode.LIGHT)
        }
        // Migration: older builds only stored the boolean.
        return if (prefs.getBoolean(KEY_DARK, false)) AppearanceMode.DARK else AppearanceMode.LIGHT
    }

    private fun resolve(context: Context, value: AppearanceMode): Boolean = when (value) {
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
        AppearanceMode.SYSTEM -> systemIsDark(context)
    }

    private fun systemIsDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private const val PREFS = "appearance"
    private const val KEY_MODE = "mode"
    private const val KEY_DARK = "dark"
}
