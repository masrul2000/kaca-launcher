package id.masrul.kaca

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

class IconCache(private val context: Context, private val state: LauncherState) {
    private val cache = LruCache<String, ImageBitmap>(500)
    private val pm = context.packageManager

    fun keyFor(app: AppInfo, px: Int): String {
        val p = state.prefs
        return "${app.key}|$px|${state.iconPack?.packageName}|${p.iconShape}|${p.customIcons[app.key]}"
    }

    fun peek(key: String): ImageBitmap? = cache.get(key)

    fun clear() = cache.evictAll()

    /** Dipanggil di background thread. */
    fun load(app: AppInfo, px: Int): ImageBitmap {
        val key = keyFor(app, px)
        cache.get(key)?.let { return it }
        val bmp = render(app, px).asImageBitmap()
        cache.put(key, bmp)
        return bmp
    }

    @SuppressLint("DiscouragedApi")
    fun packDrawable(pkg: String, name: String): Drawable? {
        val current = state.iconPack
        if (current != null && current.packageName == pkg) return current.getDrawable(name)
        return try {
            val res = pm.getResourcesForApplication(pkg)
            val id = res.getIdentifier(name, "drawable", pkg)
            if (id != 0) res.getDrawable(id, null) else null
        } catch (e: Exception) { null }
    }

    private fun render(app: AppInfo, px: Int): Bitmap {
        var drawable: Drawable? = null
        var fromPack = false

        val override = state.prefs.customIcons[app.key]
        if (override != null) {
            val parts = override.split('|')
            if (parts.size == 2) {
                drawable = packDrawable(parts[0], parts[1])
                fromPack = drawable != null
            }
        }
        if (drawable == null) {
            val pack = state.iconPack
            val name = pack?.drawableNameFor(app.packageName, app.className)
            if (pack != null && name != null) {
                drawable = pack.getDrawable(name)
                fromPack = drawable != null
            }
        }
        if (drawable == null) {
            drawable = try {
                pm.getActivityIcon(ComponentName(app.packageName, app.className))
            } catch (e: Exception) {
                pm.defaultActivityIcon
            }
        }
        val d = drawable!!

        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val shape = state.prefs.iconShape
        if (fromPack || shape == IconShape.SYSTEM) {
            d.setBounds(0, 0, px, px)
            d.draw(c)
        } else {
            c.clipPath(shapePath(shape, px.toFloat()))
            if (d is AdaptiveIconDrawable) {
                // Layer adaptive 108dp, area terlihat 72dp -> perbesar 1.5x supaya penuh
                val ext = px / 4
                d.background?.let { it.setBounds(-ext, -ext, px + ext, px + ext); it.draw(c) }
                d.foreground?.let { it.setBounds(-ext, -ext, px + ext, px + ext); it.draw(c) }
            } else {
                c.drawColor(Color.WHITE)
                val inset = (px * 0.16f).toInt()
                d.setBounds(inset, inset, px - inset, px - inset)
                d.draw(c)
            }
        }
        return bmp
    }

    companion object {
        fun shapePath(shape: IconShape, s: Float): Path {
            val p = Path()
            val r = RectF(0f, 0f, s, s)
            when (shape) {
                IconShape.CIRCLE, IconShape.SYSTEM -> p.addCircle(s / 2, s / 2, s / 2, Path.Direction.CW)
                IconShape.ROUNDED -> p.addRoundRect(r, s * 0.24f, s * 0.24f, Path.Direction.CW)
                IconShape.SQUARE -> p.addRoundRect(r, s * 0.06f, s * 0.06f, Path.Direction.CW)
                IconShape.TEARDROP -> {
                    val big = s / 2; val small = s * 0.14f
                    p.addRoundRect(
                        r,
                        floatArrayOf(big, big, big, big, small, small, big, big),
                        Path.Direction.CW,
                    )
                }
                IconShape.SQUIRCLE -> {
                    val n = 4.0
                    val half = s / 2
                    for (i in 0..120) {
                        val t = Math.toRadians(i * 3.0)
                        val ct = cos(t); val st = sin(t)
                        val x = half + half * (sign(ct) * abs(ct).pow(2.0 / n)).toFloat()
                        val y = half + half * (sign(st) * abs(st).pow(2.0 / n)).toFloat()
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    p.close()
                }
            }
            return p
        }
    }
}

/** Wallpaper foto pilihan user, disimpan di storage internal aplikasi. */
class WallpaperStore(private val context: Context) {
    var bitmap by mutableStateOf<ImageBitmap?>(null)
        private set
    private val file get() = File(context.filesDir, "wallpaper.jpg")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        scope.launch {
            bitmap = withContext(Dispatchers.IO) { decode(file) }
        }
    }

    fun setFromUri(uri: Uri, onDone: (Boolean) -> Unit) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                    true
                } catch (e: Exception) { false }
            }
            if (ok) bitmap = withContext(Dispatchers.IO) { decode(file) }
            onDone(ok && bitmap != null)
        }
    }

    private fun decode(f: File): ImageBitmap? {
        if (!f.exists()) return null
        return try {
            val dm = context.resources.displayMetrics
            val target = maxOf(dm.widthPixels, dm.heightPixels)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = BitmapFactory.decodeFile(f.absolutePath, opts) ?: return null
            val deg = when (
                android.media.ExifInterface(f.absolutePath).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val out = if (deg == 0f) raw else {
                val m = android.graphics.Matrix().apply { postRotate(deg) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            }
            out.asImageBitmap()
        } catch (e: Exception) { null }
    }
}
