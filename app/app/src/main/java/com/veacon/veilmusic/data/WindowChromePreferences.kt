package com.veacon.veilmusic.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the status and navigation bars stay visible.
 *
 * The television build hides both because a TV has none to hide. On a phone or a
 * touch panel that removes the only way to reach the notification shade, and the
 * gesture bar overlaps the first row of content. Defaults to [DEFAULT_SHOW] so an
 * existing install keeps the shipped immersive look until the user asks for the
 * bars back.
 */
@Singleton
class WindowChromePreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("window_chrome_prefs", Context.MODE_PRIVATE)

    private val _showSystemBars = MutableStateFlow(prefs.getBoolean(KEY_SHOW_SYSTEM_BARS, DEFAULT_SHOW))

    /** Emits on every change so the activity can re-style the window without a restart. */
    val showSystemBars: StateFlow<Boolean> = _showSystemBars

    fun isShowingSystemBars(): Boolean = _showSystemBars.value

    fun setShowSystemBars(show: Boolean) {
        if (_showSystemBars.value == show) return
        prefs.edit { putBoolean(KEY_SHOW_SYSTEM_BARS, show) }
        _showSystemBars.value = show
    }

    companion object {
        private const val KEY_SHOW_SYSTEM_BARS = "show_system_bars"

        /** Immersive by default, matching every release that predates this option. */
        const val DEFAULT_SHOW = false
    }
}
