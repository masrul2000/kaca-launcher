package id.masrul.kaca.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private val ID = Locale("id", "ID")

@Composable
fun HomeScreen(ui: UiState, onGesture: (id.masrul.kaca.GestureAction) -> Unit) {
    val st = LocalLauncher.current
    val p = st.prefs
    val perPage = (p.homeColumns * p.homeRows).coerceAtLeast(1)
    val pageCount = maxOf(1, ceil(p.home.size / perPage.toFloat()).toInt())
    val pager = rememberPagerState { pageCount }

    LaunchedEffect(ui.homeResetTick) { if (pager.currentPage != 0) pager.animateScrollToPage(0) }

    Box(
        Modifier
            .fillMaxSize()
            .homeSwipes(
                onUp = { onGesture(p.swipeUp) },
                onDown = { onGesture(p.swipeDown) },
                onTwoDown = { onGesture(p.twoFingerDown) },
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onGesture(p.doubleTap) },
                    onLongPress = { if (!ui.iconPressed) ui.homeMenuOpen = true },
                )
            }
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            if (p.showClock || p.showStatusWidget) HomeHeader()
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                key = { it },
            ) { page ->
                HomeGrid(ui, page, perPage)
            }
            if (p.showPageDots && pageCount > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(pageCount) { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (i == pager.currentPage) 8.dp else 6.dp)
                                .background(Color.White.copy(alpha = if (i == pager.currentPage) 0.95f else 0.45f), CircleShape)
                        )
                    }
                }
            }
            if (p.showDock && p.dockCount > 0) Dock(ui)
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Swipe atas/bawah (1 atau 2 jari) di area kosong home. */
private fun Modifier.homeSwipes(onUp: () -> Unit, onDown: () -> Unit, onTwoDown: () -> Unit) =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var maxPointers = 1
            var total = Offset.Zero
            var decided = false
            var vertical = false
            val slop = viewConfiguration.touchSlop
            while (true) {
                val ev = awaitPointerEvent()
                maxPointers = maxOf(maxPointers, ev.changes.count { it.pressed })
                if (ev.changes.none { it.pressed }) break
                if (!decided && ev.changes.any { it.isConsumed }) return@awaitEachGesture
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.first()
                total += ch.positionChange()
                if (!decided && total.getDistance() > slop) {
                    decided = true
                    vertical = abs(total.y) > abs(total.x) * 1.2f
                    if (!vertical) return@awaitEachGesture
                }
                if (vertical) ev.changes.forEach { it.consume() }
            }
            if (vertical) {
                val th = 56.dp.toPx()
                if (total.y < -th) onUp()
                else if (total.y > th) { if (maxPointers >= 2) onTwoDown() else onDown() }
            }
        }
    }

@Composable
private fun HomeHeader() {
    val st = LocalLauncher.current
    val p = st.prefs
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000 + 50)
        }
    }
    val battery = rememberBattery()
    val time = SimpleDateFormat(if (p.clock24h) "HH:mm" else "h:mm", ID).format(Date(now))
    val date = SimpleDateFormat("EEEE, d MMMM", ID).format(Date(now))

    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (p.showClock) {
            Text(
                time,
                color = Color.White,
                style = TextStyle(fontSize = 84.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp, shadow = LabelShadow),
            )
        }
        if (p.showStatusWidget) {
            Spacer(Modifier.height(6.dp))
            GlassSurface(shape = RoundedCornerShape(50)) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val fg = if (LocalDark.current) Color.White else Color(0xFF15151A)
                    Text(date.replaceFirstChar { it.uppercase() }, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.size(4.dp).background(fg.copy(alpha = 0.5f), CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        if (battery.second) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                        null, tint = fg, modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${battery.first}%", color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun rememberBattery(): Pair<Int, Boolean> {
    val ctx = LocalContext.current
    var level by remember { mutableIntStateOf(0) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i == null) return
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                level = l * 100 / s
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    return level to charging
}

@Composable
private fun HomeGrid(ui: UiState, page: Int, perPage: Int) {
    val st = LocalLauncher.current
    val p = st.prefs
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val cols = p.homeColumns
    val rows = p.homeRows
    val start = page * perPage
    val items = p.home.drop(start).take(perPage)

    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        val density = LocalDensity.current
        val cellWpx = constraints.maxWidth / cols.toFloat()
        val cellHpx = constraints.maxHeight / rows.toFloat()
        val cellW = with(density) { cellWpx.toDp() }
        val cellH = with(density) { cellHpx.toDp() }
        val tapSlop = with(density) { 12.dp.toPx() }

        items.forEachIndexed { i, key ->
            val app = st.appByKey(key) ?: return@forEachIndexed
            val col = i % cols
            val row = i / cols
            val baseX = col * cellWpx
            val baseY = row * cellHpx
            val dragging = dragKey == key
            androidx.compose.runtime.key(key) {
            val scale by animateFloatAsState(if (dragging) 1.12f else 1f, label = "drag")
            Box(
                Modifier
                    .offset {
                        val o = if (dragging) dragOffset else Offset.Zero
                        IntOffset((baseX + o.x).roundToInt(), (baseY + o.y).roundToInt())
                    }
                    .size(cellW, cellH)
                    .zIndex(if (dragging) 10f else 0f)
                    .scale(scale)
                    .appGesturesUpdated(
                        key = key,
                        ui = ui,
                        onTap = { st.launch(app, ctx) },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            dragKey = key; dragOffset = Offset.Zero
                        },
                        onDrag = { dragOffset += it },
                        onRelease = { total ->
                            dragKey = null
                            if (total.getDistance() < tapSlop) {
                                ui.menu = MenuTarget(app, MenuSource.HOME)
                            } else {
                                val cx = baseX + total.x + cellWpx / 2
                                val cy = baseY + total.y + cellHpx / 2
                                val tc = (cx / cellWpx).toInt().coerceIn(0, cols - 1)
                                val tr = (cy / cellHpx).toInt().coerceIn(0, rows - 1)
                                val from = start + i
                                val to = (start + tr * cols + tc).coerceAtMost(p.home.lastIndex)
                                st.moveInList(p.home, from, to)
                            }
                            dragOffset = Offset.Zero
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AppTile(app)
            }
            }
        }
    }
}

@Composable
private fun Dock(ui: UiState) {
    val st = LocalLauncher.current
    val p = st.prefs
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val apps = p.dock.take(p.dockCount)
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    GlassSurface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(p.iconSizeDp.dp + 28.dp)
        ) {
            val density = LocalDensity.current
            val slots = p.dockCount.coerceAtLeast(1)
            val cellWpx = constraints.maxWidth / slots.toFloat()
            val cellW = with(density) { cellWpx.toDp() }
            val tapSlop = with(density) { 12.dp.toPx() }
            if (apps.isEmpty()) {
                Text(
                    "Tahan ikon aplikasi → \"Tambah ke dock\"",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            apps.forEachIndexed { i, key ->
                val app = st.appByKey(key) ?: return@forEachIndexed
                val baseX = i * cellWpx
                val dragging = dragKey == key
                androidx.compose.runtime.key(key) {
                Box(
                    Modifier
                        .offset {
                            val o = if (dragging) dragOffset else Offset.Zero
                            IntOffset((baseX + o.x).roundToInt(), o.y.roundToInt())
                        }
                        .width(cellW)
                        .fillMaxHeight()
                        .zIndex(if (dragging) 10f else 0f)
                        .scale(if (dragging) 1.12f else 1f)
                        .appGesturesUpdated(
                            key = key,
                            ui = ui,
                            onTap = { st.launch(app, ctx) },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragKey = key; dragOffset = Offset.Zero
                            },
                            onDrag = { dragOffset += it },
                            onRelease = { total ->
                                dragKey = null
                                if (total.getDistance() < tapSlop) {
                                    ui.menu = MenuTarget(app, MenuSource.DOCK)
                                } else {
                                    val to = ((baseX + total.x + cellWpx / 2) / cellWpx).toInt()
                                    st.moveInList(p.dock, i, to.coerceAtMost(apps.lastIndex))
                                }
                                dragOffset = Offset.Zero
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AppTile(app, showLabel = false)
                }
                }
            }
        }
    }
}

