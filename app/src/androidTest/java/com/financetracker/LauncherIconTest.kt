package com.financetracker

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.financetracker.launcher.LauncherIcon
import com.financetracker.launcher.LauncherIconManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {
    @Test fun switchingAllIconsKeepsExactlyOneLauncherAndMainActivityEnabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = LauncherIconManager(context)
        val previous = manager.selected()
        try {
            LauncherIcon.entries.forEach { icon ->
                manager.select(icon)
                assertEquals(icon, LauncherIconManager(context).selected())
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
                val entries = context.packageManager.queryIntentActivities(intent, 0)
                assertEquals(1, entries.size)
                assertEquals(manager.component(icon).className, entries.single().activityInfo.name)
                assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    context.packageManager.getComponentEnabledSetting(ComponentName(context, MainActivity::class.java)))
                assertNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
            }
        } finally { manager.select(previous) }
    }
}
