package com.veacon.veilmusic.ui.theme

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AppearanceTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @After fun cleanUp() {
        context.getSharedPreferences("appearance", 0).edit().clear().commit()
        AppPalette.load(context)
    }

    @Test fun defaultsToLightAndPersistsBothChoices() {
        context.getSharedPreferences("appearance", 0).edit().clear().commit()
        AppPalette.load(context)
        assertEquals(Color.White, AppPalette.background)
        AppPalette.setDark(context, true)
        AppPalette.load(context)
        assertEquals(Color(0xFF101012), AppPalette.background)
        assertEquals(Color(0xFFF5F5F7), AppPalette.primary)
        AppPalette.setDark(context, false)
        AppPalette.load(context)
        assertEquals(Color.White, AppPalette.background)
    }

    @Test fun buttonTextUsesItsActualBackground() {
        AppPalette.setDark(context, true)
        assertEquals(Color(0xFF1D1D1F), AppPalette.contentOn(Color.White))
        assertEquals(Color.White, AppPalette.contentOn(Color.Black))
    }

    @Test
    @Config(qualifiers = "night")
    fun followSystemUsesTheDeviceNightMode() {
        context.getSharedPreferences("appearance", 0).edit().clear().commit()
        AppPalette.setMode(context, AppearanceMode.SYSTEM)
        assertEquals(AppearanceMode.SYSTEM, AppPalette.mode)
        assertEquals(Color(0xFF101012), AppPalette.background)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun followSystemUsesTheDeviceDayMode() {
        context.getSharedPreferences("appearance", 0).edit().clear().commit()
        AppPalette.setMode(context, AppearanceMode.SYSTEM)
        assertEquals(Color.White, AppPalette.background)
    }

    @Test fun explicitChoiceOverridesTheDeviceMode() {
        context.getSharedPreferences("appearance", 0).edit().clear().commit()
        AppPalette.setMode(context, AppearanceMode.SYSTEM)
        AppPalette.setMode(context, AppearanceMode.LIGHT)
        assertEquals(AppearanceMode.LIGHT, AppPalette.mode)
        assertEquals(Color.White, AppPalette.background)
    }

    @Test fun stepperStopsAtBothEnds() {
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.LIGHT.previous())
        assertEquals(AppearanceMode.DARK, AppearanceMode.LIGHT.next())
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.DARK.next())
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.SYSTEM.next())
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.SYSTEM.previous())
    }
}
