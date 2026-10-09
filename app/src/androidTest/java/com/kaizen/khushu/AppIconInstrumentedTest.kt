package com.kaizen.khushu

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
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
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val aliases = mapOf("DYNAMIC" to "MainActivityAliasDynamic", "DARK" to "MainActivityAliasDark",
        "LIGHT" to "MainActivityAliasLight", "GREEN" to "MainActivityAliasGreen")
    private fun component(alias: String) = ComponentName(context.packageName, "com.kaizen.khushu.$alias")

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
