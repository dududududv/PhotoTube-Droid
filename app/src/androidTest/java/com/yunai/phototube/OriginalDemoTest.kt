package com.yunai.phototube

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Optional comparison against the APK shipped in the upstream repository. */
@RunWith(AndroidJUnit4::class)
class OriginalDemoTest {
    @Test fun captureOriginalCatalog() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val installed = device.executeShellCommand("pm path com.kyant.backdrop.catalog").contains("package:")
        assumeTrue("Install the upstream demo APK to run this comparison", installed)
        device.executeShellCommand("am force-stop com.kyant.backdrop.catalog")
        instrumentation.targetContext.startActivity(Intent().setClassName("com.kyant.backdrop.catalog", "com.kyant.backdrop.catalog.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        check(device.wait(Until.hasObject(By.text("Bottom tabs")), 5_000))
        device.findObject(By.text("Bottom tabs")).click()
        check(device.wait(Until.hasObject(By.text("Tab 3")), 5_000))
        device.waitForIdle()
        capture(device, "original-tabs")
        val firstTab = device.findObjects(By.text("Tab 1")).first()
        val lastTab = device.findObjects(By.text("Tab 3")).first()
        val start = firstTab.visibleCenter
        val end = lastTab.visibleCenter
        device.swipe(start.x, start.y, end.x, end.y, 90)
        device.waitForIdle()
        capture(device, "original-tabs-after-drag")
        device.pressBack()
        check(device.wait(Until.hasObject(By.text("Buttons")), 5_000))
        device.findObject(By.text("Buttons")).click()
        device.waitForIdle()
        capture(device, "original-buttons")
        device.pressBack()
        check(device.wait(Until.hasObject(By.text("Glass playground")), 5_000))
        device.findObject(By.text("Glass playground")).click()
        device.waitForIdle()
        capture(device, "original-playground")
    }
    private fun capture(device: UiDevice, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "$name.png")
        check(device.takeScreenshot(file))
        device.executeShellCommand("mkdir -p /sdcard/Download/phototube-original-demo")
        device.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/phototube-original-demo/$name.png")
    }
}
