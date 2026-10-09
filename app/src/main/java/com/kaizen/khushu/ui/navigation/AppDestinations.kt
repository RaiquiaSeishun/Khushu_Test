package com.kaizen.khushu.ui.navigation

import com.kaizen.khushu.R

const val LEARN_DETAIL_ROUTE = "learn_detail/{sectionTitle}"
const val SETTINGS_ROUTE = "settings"
const val SETTINGS_COUNTER_ROUTE = "settings/counter"
const val SETTINGS_APPEARANCE_ROUTE = "settings/appearance"
const val SETTINGS_PRAYER_ROUTE = "settings/prayer"
const val SETTINGS_WIDGET_ROUTE = "settings/widget"
const val SETTINGS_ABOUT_ROUTE = "settings/about"
const val CUSTOMIZE_ROUTE = "customize"
const val ONBOARDING_ROUTE = "onboarding"
const val CUSTOMIZE_PALETTE_ROUTE = "customize/palette"
const val CUSTOMIZE_TASBEEH_ROUTE = "customize/tasbeeh"

// Immersive & Editor Routes
const val TASBEEH_IMMERSIVE_ROUTE = "tasbeeh/immersive/{collectionId}"
const val TASBEEH_CANVAS_ROUTE = "tasbeeh/canvas"


enum class AppDestinations(val label: String, val icon: Int, val route: String) {
    HOME("Home", R.drawable.ic_home, "home"),
    TASBEEH("Tasbih", R.drawable.ic_tasbeeh, "tasbeeh"),
    LEARN("Study", R.drawable.ic_learn, "learn"),
    ;
    companion object {
        fun fromRoute(route: String?) = entries.find { it.route == route }
    }
}
