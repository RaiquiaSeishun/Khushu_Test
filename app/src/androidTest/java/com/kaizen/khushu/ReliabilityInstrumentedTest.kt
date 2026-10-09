package com.kaizen.khushu

import android.database.sqlite.SQLiteDatabase
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaizen.khushu.data.local.CanvasDatabase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReliabilityInstrumentedTest {
    @org.junit.Before fun preparePermissions() = prepareActivityTestPermissions()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun updateProviderSharesOnlyUpdateFiles() {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(directory, "provider-test.apk").apply { writeText("test") }
        val privateFile = File(context.cacheDir, "private-test.txt").apply { writeText("private") }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.update_provider", apk)
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.update_provider", uri.authority)
            assertEquals("test", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.update_provider", privateFile)
                fail("Provider exposed unrelated cache content")
            } catch (_: IllegalArgumentException) { }
        } finally { apk.delete(); privateFile.delete() }
    }

    @Test fun versionThreeMigrationPreservesLayoutsAndCustomPresets() = runBlocking {
        val name = "canvas-migration-test"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        // Fixture follows the unchanged version-3 entities in upstream 926d3e6,
        // independently of the migration's two new CREATE TABLE statements.
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE salah_canvas_layouts (id TEXT NOT NULL PRIMARY KEY, backgroundColorInt INTEGER NOT NULL, widgets TEXT NOT NULL)")
            db.execSQL("CREATE TABLE canvas_presets (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, backgroundColor INTEGER NOT NULL, widgets TEXT NOT NULL, isDeletable INTEGER NOT NULL)")
            db.execSQL("INSERT INTO salah_canvas_layouts VALUES ('default', -16777216, '[]')")
            db.execSQL("INSERT INTO canvas_presets VALUES ('custom', 'My preserved preset', -16777216, '[]', 1)")
            db.version = 3
        }
        val database = Room.databaseBuilder(context, CanvasDatabase::class.java, name)
            .addMigrations(CanvasDatabase.MIGRATION_3_5).build()
        try {
            assertEquals("My preserved preset", database.canvasDao().getPresetById("custom").first()!!.name)
            assertEquals(-16777216, database.canvasDao().getDefault().first()!!.backgroundColorInt)
            assertEquals(0, database.canvasDao().getTasbeehPresetCount())
            assertEquals(5, database.openHelper.readableDatabase.version)
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun activityReachesResumedState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            }
        }
    }
}
