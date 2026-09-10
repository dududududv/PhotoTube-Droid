package com.yunai.phototube

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Renders the installed manifest icon with the device's real adaptive mask. */
@RunWith(AndroidJUnit4::class)
class AppIconTest {
    @Test fun renderLauncherIcon() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue(icon is AdaptiveIconDrawable)
        val adaptive = icon as AdaptiveIconDrawable
        assertNotNull(adaptive.monochrome)
        val bitmap = Bitmap.createBitmap(840, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(240, 243, 248))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(49, 58, 72); textSize = 24f; textAlign = Paint.Align.CENTER }
        adaptive.setBounds(66, 80, 322, 336)
        adaptive.draw(canvas)
        canvas.drawText("PhotoTube", 194f, 390f, paint)
        adaptive.setBounds(396, 172, 460, 236)
        adaptive.draw(canvas)
        paint.textSize = 19f
        canvas.drawText("64 px", 428f, 276f, paint)
        val mono = adaptive.monochrome!!.mutate().apply { setTint(Color.rgb(51, 78, 100)) }
        val themed = AdaptiveIconDrawable(android.graphics.drawable.ColorDrawable(Color.rgb(199, 221, 236)), mono)
        themed.setBounds(540, 80, 796, 336)
        themed.draw(canvas)
        paint.textSize = 24f
        canvas.drawText("Themed", 668f, 390f, paint)
        val output = File(context.getExternalFilesDir(null), "app-icon-preview.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        instrumentation.uiAutomation.executeShellCommand("cp ${output.absolutePath} /sdcard/Download/phototube-app-icon.png").close()
        bitmap.recycle()
    }
}
