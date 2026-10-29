package com.financetracker.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import com.financetracker.R

/** Aliases remain declared permanently so a selected launcher entry survives future updates. */
enum class LauncherIcon(val label: String, val alias: String, @DrawableRes val preview: Int) {
    DEFAULT("默认图标", "LauncherDefault", R.drawable.ic_launcher_foreground),
    NAMI_CUTE("娜美 · Q版", "LauncherNamiCute", R.drawable.ic_launcher_nami_cute_art),
    NAMI_MATURE("娜美 · 轻熟版", "LauncherNamiMature", R.drawable.ic_launcher_nami_mature_art),
}

class LauncherIconManager(context: Context) {
    private val context = context.applicationContext
    private val manager get() = context.packageManager
    fun component(icon: LauncherIcon) = ComponentName(context.packageName, "${context.packageName}.${icon.alias}")
    fun selected(): LauncherIcon = LauncherIcon.entries.firstOrNull { icon ->
        when (manager.getComponentEnabledSetting(component(icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == LauncherIcon.DEFAULT
            else -> false
        }
    } ?: LauncherIcon.DEFAULT

    fun select(icon: LauncherIcon) {
        val previous = LauncherIcon.entries.associateWith { manager.getComponentEnabledSetting(component(it)) }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                manager.setComponentEnabledSettings(LauncherIcon.entries.map { option ->
                    PackageManager.ComponentEnabledSetting(component(option), state(option == icon), PackageManager.DONT_KILL_APP)
                })
            } else {
                // Enable the new entry first: the app must never be left without a launcher entry.
                manager.setComponentEnabledSetting(component(icon), state(true), PackageManager.DONT_KILL_APP)
                LauncherIcon.entries.filter { it != icon }.forEach { manager.setComponentEnabledSetting(component(it), state(false), PackageManager.DONT_KILL_APP) }
            }
        } catch (e: Exception) {
            // Keep a working launcher even if an older system rejects a component change midway.
            previous.filter { (option, setting) -> setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && option == LauncherIcon.DEFAULT) }
                .forEach { (option, setting) -> runCatching { manager.setComponentEnabledSetting(component(option), setting, PackageManager.DONT_KILL_APP) } }
            previous.forEach { (option, setting) -> runCatching { manager.setComponentEnabledSetting(component(option), setting, PackageManager.DONT_KILL_APP) } }
            throw e
        }
    }
    private fun state(enabled: Boolean) = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
}
