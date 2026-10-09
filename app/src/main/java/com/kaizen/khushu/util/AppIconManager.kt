package com.kaizen.khushu.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.kaizen.khushu.MainActivity

/**
 * Switches the active launcher alias to match the selected [logoStyle].
 * Alias classes use the code namespace, while components belong to the installed
 * application ID. Enable the replacement before disabling the old launcher entry.
 */
object AppIconManager {

    private val styleToAlias = mapOf(
        "DYNAMIC" to "MainActivityAliasDynamic",
        "DARK"    to "MainActivityAliasDark",
        "LIGHT"   to "MainActivityAliasLight",
        "GREEN"   to "MainActivityAliasGreen",
    )

    fun apply(context: Context, logoStyle: String) {
        val pm = context.packageManager
        require(logoStyle in styleToAlias) { "Unknown icon style" }
        val namespace = MainActivity::class.java.name.substringBeforeLast('.')
        val ordered = styleToAlias.entries.sortedBy { if (it.key == logoStyle) 0 else 1 }
        val changes = ordered.map { (style, alias) ->
            ComponentName(context.packageName, "$namespace.$alias") to
                if (style == logoStyle) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (Build.VERSION.SDK_INT >= 33) {
            pm.setComponentEnabledSettings(changes.map { (component, state) ->
                PackageManager.ComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            })
        } else {
            changes.forEach { (component, state) ->
                pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
