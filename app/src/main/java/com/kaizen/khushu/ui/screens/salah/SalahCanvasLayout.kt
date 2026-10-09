package com.kaizen.khushu.ui.screens.salah

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.kaizen.khushu.data.local.CanvasWidgetListConverter

// Legacy table retained for non-destructive updates of the shared CanvasDatabase.
@Entity(tableName = "salah_canvas_layouts")
@TypeConverters(CanvasWidgetListConverter::class)
data class SalahCanvasLayout(
    @PrimaryKey val id: String = "default",  // single layout for now, extend later
    val backgroundColorInt: Int = 0xFF000000.toInt(),
    val widgets: List<CanvasWidget> = emptyList(),
)
