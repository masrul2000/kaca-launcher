package id.masrul.kaca.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.rememberUpdatedState
import id.masrul.kaca.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MenuSource { HOME, DOCK, DRAWER }
data class MenuTarget(val app: AppInfo, val source: MenuSource)

/** State tampilan (overlay mana yang terbuka). */
class UiState {
    var drawerOpen by mutableStateOf(false)
    var drawerSearchFocus by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var homeMenuOpen by mutableStateOf(false)
    var menu by mutableStateOf<MenuTarget?>(null)
    var editApp by mutableStateOf<AppInfo?>(null)
    var pickIconFor by mutableStateOf<AppInfo?>(null)
    var homeResetTick by mutableStateOf(0)
    /** true saat jari sedang menekan ikon, supaya long-press latar tidak ikut terpicu */
    var iconPressed = false

    val anyOverlay get() = drawerOpen || settingsOpen || homeMenuOpen || menu != null || editApp != null || pickIconFor != null

    fun openDrawer(search: Boolean = false) { drawerSearchFocus = search; drawerOpen = true }

    fun back() {
        when {
            pickIconFor != null -> pickIconFor = null
            editApp != null -> editApp = null
            menu != null -> menu = null
            homeMenuOpen -> homeMenuOpen = false
            settingsOpen -> settingsOpen = false
            drawerOpen -> drawerOpen = false
        }
    }

    fun goHome() {
        pickIconFor = null; editApp = null; menu = null; homeMenuOpen = false
        settingsOpen = false; drawerOpen = false
        homeResetTick++
    }
}

@Composable
fun AppIcon(app: AppInfo, size: Dp, modifier: Modifier = Modifier) {
    val st = LocalLauncher.current
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(48, 288)
    val key = st.iconCache.keyFor(app, px)
    val bmp by produceState<ImageBitmap?>(st.iconCache.peek(key), key) {
        value = withContext(Dispatchers.IO) { runCatching { st.iconCache.load(app, px) }.getOrNull() }
    }
    Box(modifier.size(size)) {
        bmp?.let { Image(it, null, Modifier.size(size)) }
    }
}

val LabelShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1.5f), 6f)

@Composable
fun AppTile(
    app: AppInfo,
    modifier: Modifier = Modifier,
    showLabel: Boolean = LocalLauncher.current.prefs.showLabels,
    labelColor: Color = Color.White,
    shadow: Boolean = true,
) {
    val st = LocalLauncher.current
    val p = st.prefs
    Column(
        modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(app, p.iconSizeDp.dp)
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                st.label(app),
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(fontSize = p.labelSizeSp.sp, shadow = if (shadow) LabelShadow else null),
            )
        }
    }
}

/**
 * Gestur ikon: ketuk = buka, tahan = mode geser; dilepas tanpa digeser = menu.
 */
fun Modifier.appGestures(
    key: Any,
    ui: UiState,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Offset) -> Unit,
    onRelease: (total: Offset) -> Unit,
): Modifier = this.pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        ui.iconPressed = true
        try {
            var longPressed = false
            val up = try {
                withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
            } catch (e: PointerEventTimeoutCancellationException) {
                longPressed = true
                null
            }
            if (up != null) {
                up.consume()
                onTap()
                return@awaitEachGesture
            }
            if (!longPressed) return@awaitEachGesture
            onLongPress()
            var total = Offset.Zero
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) { ch.consume(); break }
                val d = ch.positionChange()
                ch.consume()
                total += d
                onDrag(d)
            }
            onRelease(total)
        } finally {
            ui.iconPressed = false
        }
    }
}

/** Versi composable yang selalu memakai callback terbaru. */
@Composable
fun Modifier.appGesturesUpdated(
    key: Any,
    ui: UiState,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Offset) -> Unit,
    onRelease: (Offset) -> Unit,
): Modifier {
    val t by rememberUpdatedState(onTap)
    val l by rememberUpdatedState(onLongPress)
    val d by rememberUpdatedState(onDrag)
    val r by rememberUpdatedState(onRelease)
    return appGestures(key, ui, { t() }, { l() }, { d(it) }, { r(it) })
}
