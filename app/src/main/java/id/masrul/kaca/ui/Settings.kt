package id.masrul.kaca.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import id.masrul.kaca.GestureAction
import id.masrul.kaca.IconPack
import id.masrul.kaca.IconShape
import id.masrul.kaca.SystemActions
import id.masrul.kaca.ThemeMode
import kotlin.math.roundToInt

private val ACCENTS = listOf(
    0xFF7C9CFF, 0xFF4FC3F7, 0xFF26C6A6, 0xFF9CCC65, 0xFFFFCA28,
    0xFFFF8A65, 0xFFFF5C8A, 0xFFB388FF, 0xFFECEFF1,
).map { Color(it) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(ui: UiState, onPickWallpaper: () -> Unit, onMakeDefault: () -> Unit) {
    val st = LocalLauncher.current
    val p = st.prefs
    val ctx = LocalContext.current
    val fg = fgColor()

    // refresh status aksesibilitas saat kembali ke layar ini
    var resumeTick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val accOn = remember(resumeTick) { SystemActions.isAccessibilityEnabled(ctx) }
    val packs = remember(resumeTick) { IconPack.installed(ctx) }

    GlassSurface(Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp), strength = 2.6f) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ui.settingsOpen = false }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", tint = fg)
                }
                Text("Pengaturan", color = fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Card("Launcher default") {
                        Text(
                            "Jadikan Kaca Launcher sebagai aplikasi Home supaya tombol Home membuka launcher ini.",
                            color = fg.copy(alpha = 0.75f), fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onMakeDefault) { Text("Jadikan default") }
                    }
                }
                item {
                    Card("Home") {
                        IntSlider("Kolom", p.homeColumns, 3..7) { p.homeColumns = it }
                        IntSlider("Baris", p.homeRows, 3..8) { p.homeRows = it }
                        IntSlider("Ukuran ikon", p.iconSizeDp, 36..80, suffix = " dp") { p.iconSizeDp = it }
                        SwitchRow("Tampilkan nama aplikasi", p.showLabels) { p.showLabels = it }
                        if (p.showLabels) IntSlider("Ukuran teks", p.labelSizeSp, 9..16, suffix = " sp") { p.labelSizeSp = it }
                        SwitchRow("Jam besar", p.showClock) { p.showClock = it }
                        SwitchRow("Format 24 jam", p.clock24h) { p.clock24h = it }
                        SwitchRow("Gelembung tanggal & baterai", p.showStatusWidget) { p.showStatusWidget = it }
                        SwitchRow("Titik indikator halaman", p.showPageDots) { p.showPageDots = it }
                    }
                }
                item {
                    Card("Dock") {
                        SwitchRow("Tampilkan dock kaca", p.showDock) { p.showDock = it }
                        if (p.showDock) IntSlider("Jumlah ikon", p.dockCount, 2..6) { p.dockCount = it }
                    }
                }
                item {
                    Card("Laci aplikasi") {
                        IntSlider("Kolom", p.drawerColumns, 3..7) { p.drawerColumns = it }
                        SwitchRow("Langsung buka keyboard", p.drawerAutoKeyboard) { p.drawerAutoKeyboard = it }
                        if (p.hidden.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Aplikasi tersembunyi", color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            p.hidden.toList().forEach { key ->
                                val app = st.appByKey(key)
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (app != null) AppIcon(app, 32.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(app?.let { st.label(it) } ?: key, color = fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { st.setHidden(key, false) }) { Text("Tampilkan") }
                                }
                            }
                        }
                    }
                }
                item {
                    Card("Ikon") {
                        Text("Icon pack", color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        RadioRow("Bawaan sistem", p.iconPack.isBlank()) { st.setIconPack("") }
                        packs.forEach { info ->
                            RadioRow(info.label, p.iconPack == info.packageName) { st.setIconPack(info.packageName) }
                        }
                        if (packs.isEmpty()) {
                            Text(
                                "Belum ada icon pack. Install icon pack apa saja yang mendukung Nova Launcher dari Play Store.",
                                color = fg.copy(alpha = 0.65f), fontSize = 12.sp,
                            )
                        }
                        if (st.iconPackLoading) Text("Memuat icon pack…", color = fg.copy(alpha = 0.65f), fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Bentuk ikon (untuk ikon bawaan)", color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconShape.entries.forEach { s ->
                                FilterChip(selected = p.iconShape == s, onClick = { p.iconShape = s }, label = { Text(s.label) })
                            }
                        }
                        Text(
                            "Ganti nama / ikon per aplikasi: tahan ikon → Edit nama & ikon.",
                            color = fg.copy(alpha = 0.65f), fontSize = 12.sp,
                        )
                    }
                }
                item {
                    Card("Tema & kaca") {
                        Text("Mode", color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { m ->
                                FilterChip(selected = p.themeMode == m, onClick = { p.themeMode = m }, label = { Text(m.label) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Wallpaper", color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GRADIENTS.forEachIndexed { i, g ->
                                FilterChip(
                                    selected = p.wallpaper == "gradient:$i",
                                    onClick = { p.wallpaper = "gradient:$i" },
                                    label = { Text(g.name) },
                                )
                            }
                            FilterChip(
                                selected = p.wallpaper == "photo",
                                onClick = onPickWallpaper,
                                label = { Text("Foto…") },
                            )
                            FilterChip(
                                selected = p.wallpaper == "system",
                                onClick = { p.wallpaper = "system" },
                                label = { Text("Wallpaper sistem") },
                            )
                        }
                        if (p.wallpaper == "system") {
                            Text(
                                "Dengan wallpaper sistem, efek blur kaca tidak bisa dipakai (Android tidak mengizinkan " +
                                    "launcher membaca wallpaper). Pilih Foto untuk kaca blur asli.",
                                color = fg.copy(alpha = 0.65f), fontSize = 12.sp,
                            )
                        }
                        FloatSlider("Gelapkan wallpaper", p.wallpaperDim, 0f..0.6f, percent = true) { p.wallpaperDim = it }
                        FloatSlider("Blur kaca", p.blurRadius, 0f..60f, suffix = " dp") { p.blurRadius = it }
                        FloatSlider("Ketebalan kaca", p.glassOpacity, 0f..0.6f, percent = true) { p.glassOpacity = it }
                        IntSlider("Sudut membulat", p.glassCorner, 8..44, suffix = " dp") { p.glassCorner = it }
                        Spacer(Modifier.height(6.dp))
                        Text("Warna aksen", color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ACCENTS.forEach { c ->
                                val sel = p.accent == c.toArgb()
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .background(c, CircleShape)
                                        .border(if (sel) 3.dp else 1.dp, fg.copy(alpha = if (sel) 0.9f else 0.3f), CircleShape)
                                        .clickable { p.accent = c.toArgb() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (sel) Icon(Icons.Rounded.Check, null, tint = Color.Black.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                item {
                    Card("Gestur") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(10.dp).background(if (accOn) Color(0xFF4CD964) else Color(0xFFFF9500), CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (accOn) "Layanan gestur aktif" else "Layanan gestur belum aktif (perlu untuk kunci layar)",
                                color = fg, fontSize = 13.sp, modifier = Modifier.weight(1f),
                            )
                        }
                        if (!accOn) {
                            OutlinedButton(onClick = { SystemActions.openAccessibilitySettings(ctx) }) {
                                Text("Aktifkan di Aksesibilitas")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        GestureRow("Swipe ke atas", p.swipeUp) { p.swipeUp = it }
                        GestureRow("Swipe ke bawah", p.swipeDown) { p.swipeDown = it }
                        GestureRow("Swipe bawah 2 jari", p.twoFingerDown) { p.twoFingerDown = it }
                        GestureRow("Ketuk dua kali", p.doubleTap) { p.doubleTap = it }
                        Text(
                            "Tahan area kosong di Home untuk menu cepat.",
                            color = fg.copy(alpha = 0.65f), fontSize = 12.sp,
                        )
                    }
                }
                item {
                    val ver = remember {
                        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
                    }
                    Text(
                        "Kaca Launcher v$ver",
                        color = fg.copy(alpha = 0.5f), fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    val fg = fgColor()
    GlassSurface(Modifier.fillMaxWidth(), strength = 0.8f) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun IntSlider(label: String, value: Int, range: IntRange, suffix: String = "", onChange: (Int) -> Unit) {
    val fg = fgColor()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("$value$suffix", color = fg.copy(alpha = 0.7f), fontSize = 14.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun FloatSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    suffix: String = "", percent: Boolean = false, onChange: (Float) -> Unit,
) {
    val fg = fgColor()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(
                if (percent) "${(value * 100).roundToInt()}%" else "${value.roundToInt()}$suffix",
                color = fg.copy(alpha = 0.7f), fontSize = 14.sp,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val fg = fgColor()
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val fg = fgColor()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, color = fg, fontSize = 14.sp)
    }
}

@Composable
private fun GestureRow(label: String, value: GestureAction, onChange: (GestureAction) -> Unit) {
    val fg = fgColor()
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(value.label, color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            GestureAction.entries.forEach { a ->
                DropdownMenuItem(text = { Text(a.label) }, onClick = { onChange(a); open = false })
            }
        }
    }
}
