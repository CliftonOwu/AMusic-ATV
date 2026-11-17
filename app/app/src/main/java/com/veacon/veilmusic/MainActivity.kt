package com.veacon.veilmusic

import com.veacon.veilmusic.i18n.tr

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.veacon.veilmusic.data.WindowChromePreferences
import com.veacon.veilmusic.ui.AppShell
import com.veacon.veilmusic.ui.viewmodel.MusicVideoViewModel
import com.veacon.veilmusic.ui.viewmodel.NavigationViewModel
import com.veacon.veilmusic.ui.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var windowChromePrefs: WindowChromePreferences

    private val navVm: NavigationViewModel by viewModels()
    private val playerVmLazy = viewModels<PlayerViewModel>()
    private val playerVm: PlayerViewModel by playerVmLazy
    private val mvVmLazy = viewModels<MusicVideoViewModel>()
    private val mvVm: MusicVideoViewModel by mvVmLazy
    private var appWindowAllowed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest launch theme skips the OS's fixed-size icon splash on API31+.
        // Switch decor styling before creation, then restore server-side opacity with
        // the public API; regular playback never remains a translucent activity.
        if (Build.VERSION.SDK_INT >= 31) setTheme(R.style.Theme_AppleMusicTV_Runtime)
        super.onCreate(savedInstanceState)
        // Ask for the fastest mode at the current resolution; Android may choose otherwise.
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val currentMode = display.mode
        val preferred = display.supportedModes
            .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
            .maxByOrNull { it.refreshRate }
        if (preferred != null) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = preferred.modeId }
        }
        // Load the saved appearance first: the bar colours below follow the theme.
        com.veacon.veilmusic.ui.theme.AppPalette.load(applicationContext)
        applyWindowChrome()
        if (Build.VERSION.SDK_INT >= 31) {
            val restored = setTranslucent(false)
            android.util.Log.i("BrandStartup", "Opaque runtime restored=$restored")
        }
        // The Appearance toggle writes the preference; the window belongs to the
        // activity, so re-style it here instead of asking the user to restart.
        lifecycleScope.launch {
            windowChromePrefs.showSystemBars.collect { applyWindowChrome() }
        }
        // Appearance edits (light / dark / follow system) recolour the bars at once.
        lifecycleScope.launch {
            androidx.compose.runtime.snapshotFlow { com.veacon.veilmusic.ui.theme.AppPalette.isDark }
                .collect { applyWindowChrome() }
        }
        setContent {
            com.veacon.veilmusic.ui.components.LargeScreenGate(onAccessChanged = ::updateWindowAccess) {
                com.veacon.veilmusic.ui.components.StartupHost { ready ->
                    com.veacon.veilmusic.ui.theme.ProvideTvFontWeights { AppShell(onStartupReady = ready) }
                }
            }
        }
    }

    private fun updateWindowAccess(allowed: Boolean) {
        if (appWindowAllowed == allowed) return
        appWindowAllowed = allowed
        val app = application as AppleMusicApp
        if (!allowed) {
            if (playerVmLazy.isInitialized()) playerVm.stopPlayback()
            if (mvVmLazy.isInitialized()) mvVm.pause()
            app.webServer.stop()
            return
        }
        app.startMainServices()
    }

    /**
     * Two window modes, chosen by the Appearance settings toggle.
     *
     * Immersive (default, the shipped television behaviour) hides both bars and
     * draws edge to edge. The alternative keeps the bars and insets the content,
     * which is what a phone or a touch panel needs: the notification shade stays
     * reachable and the gesture handle stops sitting on top of the first row.
     */
    private fun applyWindowChrome() {
        if (windowChromePrefs.isShowingSystemBars()) {
            // The theme sets android:windowFullscreen, so the flag has to go too —
            // the insets controller alone cannot bring the status bar back.
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
            val light = !com.veacon.veilmusic.ui.theme.AppPalette.isDark
            val barColor = if (light) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
            return
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyWindowChrome()
    }

    override fun onStop() {
        super.onStop()
        // Compose can already own these ViewModels while the Activity's lazy references are
        // untouched. Resolve the same Activity-scoped instances instead of treating an
        // uninitialized delegate as proof that no playback exists.
        if (appWindowAllowed && !playerVm.backgroundPlayEnabled) {
            playerVm.pause()
            mvVm.pause()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!appWindowAllowed) return super.dispatchKeyEvent(event)
        if (navVm.consumePlayerWakeKey(event)) return true
        if (event.action == KeyEvent.ACTION_DOWN && navVm.isOnNowPlaying) navVm.notePlayerInteraction()
        if (com.veacon.veilmusic.ui.navigation.dispatchRemoteMenu(event, { super.dispatchKeyEvent(it) }) {
            if (navVm.isOnMusicVideo && navVm.isOnNowPlaying) mvVm.toggleQueue()
            else if (navVm.isOnNowPlaying) navVm.toggleQueuePanel()
            else navVm.navigateToNowPlaying()
        }) return true
        // While a video is active, media keys drive the VIDEO — never the (paused) audio
        // player. D-pad keys still fall through so the fullscreen video screen (or the UI
        // behind an in-app PiP) can handle them.
        if (navVm.isOnMusicVideo) {
            if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { mvVm.next(); return true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> { mvVm.prev(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { mvVm.togglePlayPause(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { mvVm.resume(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { mvVm.pause(); return true }

            }
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { playerVm.togglePlayPause(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { playerVm.resume(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { playerVm.pause(); return true }
                KeyEvent.KEYCODE_MEDIA_NEXT     -> { playerVm.next(); return true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { playerVm.prev(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { playerVm.next(); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND       -> { playerVm.prev(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
