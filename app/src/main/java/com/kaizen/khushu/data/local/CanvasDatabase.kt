package com.kaizen.khushu.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kaizen.khushu.ui.screens.salah.SalahCanvasLayout
import com.kaizen.khushu.ui.screens.tasbeeh.TasbeehCanvasLayout
import com.kaizen.khushu.data.model.PresetEntity
import com.kaizen.khushu.data.model.TasbeehPresetEntity

@Database(
    entities = [SalahCanvasLayout::class, TasbeehCanvasLayout::class, PresetEntity::class, TasbeehPresetEntity::class],
    version = 5,
    exportSchema = true
)
@TypeConverters(CanvasWidgetListConverter::class, TasbeehCanvasWidgetListConverter::class)
abstract class CanvasDatabase : RoomDatabase() {
    abstract fun canvasDao(): CanvasDao

    companion object {
        // Upstream 926d3e6 used version 3 with the same Salah tables. 775762b
        // moved directly to version 5 by adding Tasbeeh tables; no data is removed.
        val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `tasbeeh_canvas_layouts` (`id` TEXT NOT NULL, `backgroundColorInt` INTEGER NOT NULL, `widgets` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `tasbeeh_canvas_presets` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `backgroundColor` INTEGER NOT NULL, `widgets` TEXT NOT NULL, `isDeletable` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        @Volatile
        private var INSTANCE: CanvasDatabase? = null

        fun getInstance(context: Context): CanvasDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    CanvasDatabase::class.java,
                    "canvas_db"
                )
                .addMigrations(MIGRATION_3_5)
                .build()
                .also { INSTANCE = it }
            }
    }
}
