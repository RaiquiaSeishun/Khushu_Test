package com.kaizen.khushu

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaizen.khushu.data.repository.SettingsRepository
import com.kaizen.khushu.util.AppIconManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIconInstrumentedTest {
    @org.junit.Before fun preparePermissions() = prepareActivityTestPermissions()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val aliases = mapOf("DYNAMIC" to "MainActivityAliasDynamic", "DARK" to "MainActivityAliasDark",
        "LIGHT" to "MainActivityAliasLight", "GREEN" to "MainActivityAliasGreen")
    private fun component(alias: String) = ComponentName(context.packageName, "com.kaizen.khushu.$alias")

    private fun render(drawable: Drawable): Bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888).also {
        drawable.setBounds(0, 0, 108, 108)
        drawable.draw(Canvas(it))
    }

    private fun assertNewMark(drawable: Drawable, singleColour: Boolean = false) {
        val bitmap = render(drawable)
        try {
            var opaque = 0
            val colours = mutableSetOf<Int>()
            for (y in 0 until 108) for (x in 0 until 108) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) >= 16) {
                    opaque++
                    val radiusSquared = (x + 0.5 - 54) * (x + 0.5 - 54) + (y + 0.5 - 54) * (y + 0.5 - 54)
                    assertTrue("Artwork exceeds the adaptive icon safe circle at $x,$y", radiusSquared <= 33 * 33)
                    if (Color.alpha(pixel) >= 128) colours += pixel or (0xff shl 24)
                }
            }
            assertTrue("Missing arch-and-crescent artwork", opaque > 200)
            // The old ring mark had an underline here; the supplied arch has an open foot.
            assertTrue("Old underline remains", Color.alpha(bitmap.getPixel(54, 77)) < 16)
            if (singleColour) {
                assertTrue("Monochrome layer is not white", colours.all {
                    Color.red(it) >= 250 && Color.green(it) >= 250 && Color.blue(it) >= 250
                })
            } else assertTrue("Teal artwork lost its gradients", colours.size > 8)
        } finally { bitmap.recycle() }
    }

    @Test fun everyStyleKeepsExactlyOneWorkingLauncherAndRestoresSavedSelection() = runBlocking {
        val repository = SettingsRepository(context)
        val original = repository.settingsFlow.first().logoStyle
        val icons = mutableListOf<Bitmap>()
        try {
            for ((style, alias) in aliases) {
                repository.updateLogoStyle(style)
                AppIconManager.apply(context, style)
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
                val activities = context.packageManager.queryIntentActivities(launcher, 0)
                assertEquals("Multiple or missing launcher entries for $style", 1, activities.size)
                val activity = activities.single().activityInfo
                assertEquals(component(alias).className, activity.name)
                val icon = activity.loadIcon(context.packageManager)
                assertTrue("Launcher icon is not adaptive", icon is AdaptiveIconDrawable)
                val adaptive = icon as AdaptiveIconDrawable
                assertNewMark(adaptive.foreground, singleColour = style == "GREEN")
                if (Build.VERSION.SDK_INT >= 33) {
                    assertNotNull("Missing themed-icon layer", adaptive.monochrome)
                    assertNewMark(adaptive.monochrome!!, singleColour = true)
                }
                val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
                icon.setBounds(0, 0, 108, 108); icon.draw(Canvas(bitmap)); icons += bitmap
                // Open the actual launcher alias, then recreate to exercise startup restoration.
                ActivityScenario.launch<MainActivity>(Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component(alias))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                    scenario.onActivity { assertFalse(it.isFinishing) }
                    scenario.recreate()
                    scenario.onActivity { assertFalse(it.isFinishing) }
                }
                assertEquals(style, repository.settingsFlow.first().logoStyle)
                assertEquals(component(alias).className, context.packageManager.queryIntentActivities(launcher, 0).single().activityInfo.name)
            }
            val identity = context.applicationInfo.loadIcon(context.packageManager) as AdaptiveIconDrawable
            assertNewMark(identity.foreground)
            for (i in icons.indices) for (j in 0 until i) {
                assertFalse("Two style previews have identical launcher images", icons[i].sameAs(icons[j]))
            }
        } finally {
            repository.updateLogoStyle(original)
            AppIconManager.apply(context, original)
            icons.forEach { it.recycle() }
        }
    }

    @Test fun invalidStyleCannotRemoveTheLauncher() {
        val states = aliases.values.associateWith { context.packageManager.getComponentEnabledSetting(component(it)) }
        try {
            AppIconManager.apply(context, "INVALID")
            fail("Invalid style accepted")
        } catch (_: IllegalArgumentException) { }
        assertEquals(states, aliases.values.associateWith { context.packageManager.getComponentEnabledSetting(component(it)) })
    }
}
