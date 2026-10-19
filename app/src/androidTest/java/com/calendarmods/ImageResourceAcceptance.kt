package com.calendarmods

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File

internal fun Instrumentation.verifyImageResources(checks: MutableList<String>) = runBlocking {
    val resources = targetContext.resources
    val root = File(targetContext.cacheDir, "image-resource-acceptance").apply { mkdirs() }
    ThemeImages.clear()
    try {
        val builtin = ThemeImageSource(resource = R.drawable.winter_bear)
        val requests = List(8) { async { ThemeImages.load(resources, listOf(builtin), 512) } }.awaitAll()
        val first = checkNotNull(requests.first())
        check(requests.all { it === first } && maxOf(first.width, first.height) == 512)
        check(ThemeImages.targetEdge(1080, 2400) == ThemeImages.targetEdge(2400, 1080))
        check(ThemeImages.targetEdge(4000, 3000) == 2048)
        checks += "concurrent image requests share one bounded bitmap; rotation shares target size PASS"

        val corrupt = File(root, "broken.png").apply { writeText("not an image") }
        val missing = ThemeImageSource(path = File(root, "missing.png").path)
        val fallback = ThemeImages.load(resources, listOf(missing, ThemeImageSource(path = corrupt.path), builtin), 512)
        check(fallback === first)
        checks += "missing/corrupt custom images fall back to bundled artwork PASS"

        val custom = File(root, "custom.png")
        val large = Bitmap.createBitmap(4096, 1024, Bitmap.Config.ARGB_8888)
        large.eraseColor(Color.RED)
        custom.outputStream().use { check(large.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        large.recycle()
        val source = ThemeImageSource(path = custom.path)
        val loaded = checkNotNull(ThemeImages.load(resources, listOf(source, builtin), 1024))
        check(loaded.width == 1024 && loaded.height == 256 && loaded.getPixel(0, 0) == Color.RED)
        ThemeImages.discard(custom.path)
        val reloaded = checkNotNull(ThemeImages.load(resources, listOf(source), 1024))
        check(reloaded !== loaded && !loaded.isRecycled)
        check(ThemeImages.bytes() <= ThemeImages.MAX_BYTES)
        checks += "large custom images downsample with aspect preserved; eviction leaves visible bitmaps valid PASS"

        val icon = targetContext.packageManager.getApplicationIcon(targetContext.packageName)
        check(icon is AdaptiveIconDrawable)
        val output = File(targetContext.getExternalFilesDir(null), "acceptance").apply { mkdirs() }
        fun save(name: String, drawable: android.graphics.drawable.Drawable, background: Int) {
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(background)
            drawable.setBounds(0, 0, 512, 512)
            drawable.draw(Canvas(bitmap))
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        save("launcher-icon", icon, Color.TRANSPARENT)
        if (Build.VERSION.SDK_INT >= 33) {
            val monochrome = checkNotNull(icon.monochrome).mutate()
            monochrome.setTint(Color.rgb(57, 76, 70))
            save("launcher-monochrome", AdaptiveIconDrawable(
                android.graphics.drawable.ColorDrawable(Color.rgb(214, 232, 222)), monochrome), Color.TRANSPARENT)
        }
        checks += "installed adaptive launcher icon and monochrome layer render successfully PASS"
    } finally {
        ThemeImages.clear()
        root.deleteRecursively()
    }
}
