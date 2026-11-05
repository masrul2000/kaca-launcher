package id.masrul.kaca.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import id.masrul.kaca.LauncherState
import kotlin.math.roundToInt

val LocalLauncher = staticCompositionLocalOf<LauncherState> { error("no state") }
val LocalScreenSize = compositionLocalOf { IntSize(1080, 2400) }
val LocalDark = compositionLocalOf { true }

data class GradientWallpaper(val name: String, val base: List<Color>, val blobs: List<Pair<Color, Offset>>)

val GRADIENTS = listOf(
    GradientWallpaper(
        "Aurora",
        listOf(Color(0xFF0B1026), Color(0xFF1B2A6B), Color(0xFF3A1C71)),
        listOf(Color(0xFF00E5A8) to Offset(0.15f, 0.25f), Color(0xFF7C4DFF) to Offset(0.85f, 0.45f), Color(0xFFFF4FA3) to Offset(0.3f, 0.85f)),
    ),
    GradientWallpaper(
        "Senja",
        listOf(Color(0xFF2B1055), Color(0xFF7597DE)),
        listOf(Color(0xFFFF7E5F) to Offset(0.2f, 0.7f), Color(0xFFFEB47B) to Offset(0.8f, 0.85f), Color(0xFFB06AB3) to Offset(0.7f, 0.2f)),
    ),
    GradientWallpaper(
        "Laut",
        listOf(Color(0xFF021B2E), Color(0xFF0A4D68)),
        listOf(Color(0xFF05BFDB) to Offset(0.8f, 0.2f), Color(0xFF00FFCA) to Offset(0.15f, 0.6f), Color(0xFF088395) to Offset(0.6f, 0.9f)),
    ),
    GradientWallpaper(
        "Mint",
        listOf(Color(0xFFE8F6EF), Color(0xFFC9E4DE)),
        listOf(Color(0xFF7FD8BE) to Offset(0.2f, 0.2f), Color(0xFFA1C6EA) to Offset(0.85f, 0.55f), Color(0xFFFFD6A5) to Offset(0.3f, 0.9f)),
    ),
    GradientWallpaper(
        "Grafit",
        listOf(Color(0xFF111111), Color(0xFF2A2A2E)),
        listOf(Color(0xFF4B5563) to Offset(0.25f, 0.3f), Color(0xFF6B7280) to Offset(0.8f, 0.7f), Color(0xFF374151) to Offset(0.4f, 0.95f)),
    ),
)

/** Menggambar wallpaper (foto/gradien). Untuk mode "system" tidak menggambar apa-apa. */
@Composable
fun WallpaperContent(modifier: Modifier = Modifier) {
    val st = LocalLauncher.current
    val wp = st.prefs.wallpaper
    val photo = st.wallpaperStore.bitmap
    when {
        wp == "photo" && photo != null ->
            Image(photo, null, modifier, contentScale = ContentScale.Crop)
        wp.startsWith("gradient:") -> {
            val g = GRADIENTS.getOrElse(wp.removePrefix("gradient:").toIntOrNull() ?: 0) { GRADIENTS[0] }
            Box(
                modifier.drawBehind {
                    drawRect(Brush.linearGradient(g.base, Offset.Zero, Offset(size.width, size.height)))
                    val r = size.maxDimension * 0.55f
                    g.blobs.forEach { (c, f) ->
                        val center = Offset(size.width * f.x, size.height * f.y)
                        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.75f), Color.Transparent), center, r), r, center)
                    }
                }
            )
        }
        else -> Box(modifier)
    }
}

fun LauncherState.drawsWallpaper(): Boolean =
    prefs.wallpaper.startsWith("gradient:") || (prefs.wallpaper == "photo" && wallpaperStore.bitmap != null)

/**
 * Panel kaca: wallpaper di belakang panel digambar ulang dengan blur (frosted glass asli),
 * lalu diberi lapisan warna tipis + garis tepi berkilau.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    strength: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val st = LocalLauncher.current
    val prefs = st.prefs
    val screen = LocalScreenSize.current
    val dark = LocalDark.current
    val shp = shape ?: RoundedCornerShape(prefs.glassCorner.dp)
    var pos by remember { mutableStateOf(IntOffset.Zero) }
    val tint = if (dark) Color(0xFF0E0E14).copy(alpha = (prefs.glassOpacity * 1.4f * strength).coerceIn(0f, 0.92f))
    else Color.White.copy(alpha = (prefs.glassOpacity * 1.8f * strength).coerceIn(0f, 0.95f))
    val edge = Brush.linearGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.35f else 0.8f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = if (dark) 0.18f else 0.5f))
    )

    Box(
        modifier
            .onGloballyPositioned {
                val p = it.positionInRoot()
                pos = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            }
            .clip(shp)
    ) {
        if (st.drawsWallpaper() && prefs.blurRadius > 0.5f) {
            WallpaperContent(
                Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(Constraints.fixed(screen.width, screen.height))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(-pos.x, -pos.y)
                        }
                    }
                    .blur(prefs.blurRadius.dp, BlurredEdgeTreatment.Rectangle)
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(tint)
                .border(1.dp, edge, shp)
        )
        content()
    }
}

@Composable
fun KacaTheme(dark: Boolean, accent: Color, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(primary = accent, secondary = accent)
    else lightColorScheme(primary = accent, secondary = accent)
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun FullWallpaper() {
    val st = LocalLauncher.current
    Box(Modifier.fillMaxSize()) {
        WallpaperContent(Modifier.fillMaxSize())
        if (st.prefs.wallpaperDim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = st.prefs.wallpaperDim)))
        }
    }
}
