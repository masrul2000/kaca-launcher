package id.masrul.kaca.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.masrul.kaca.AppInfo
import id.masrul.kaca.IconPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun fgColor(): Color = if (LocalDark.current) Color.White else Color(0xFF15151A)

/** Latar gelap + panel kaca di tengah / bawah. */
@Composable
fun Overlay(onDismiss: () -> Unit, bottom: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }
            .systemBarsPadding()
            .imePadding()
            .padding(16.dp),
        contentAlignment = if (bottom) Alignment.BottomCenter else Alignment.Center,
    ) {
        Box(
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            content = content,
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val fg = if (danger) Color(0xFFFF6B6B) else fgColor()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = fg, fontSize = 15.sp)
    }
}

@Composable
fun AppMenu(ui: UiState, target: MenuTarget) {
    val st = LocalLauncher.current
    val p = st.prefs
    val ctx = LocalContext.current
    val app = target.app
    val fg = fgColor()
    fun done() { ui.menu = null }

    Overlay(onDismiss = { done() }) {
        GlassSurface(Modifier.widthIn(max = 340.dp).fillMaxWidth(), strength = 2.2f) {
            Column(Modifier.padding(vertical = 10.dp)) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(st.label(app), color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(app.packageName, color = fg.copy(alpha = 0.55f), fontSize = 11.sp)
                    }
                }
                ActionRow(Icons.Rounded.Edit, "Edit nama & ikon") { done(); ui.editApp = app }

                val inHome = app.key in p.home
                val inDock = app.key in p.dock
                if (target.source == MenuSource.HOME) {
                    val idx = p.home.indexOf(app.key)
                    val per = p.homeColumns * p.homeRows
                    val page = if (per > 0) idx / per else 0
                    if (page > 0) ActionRow(Icons.AutoMirrored.Rounded.ArrowBack, "Pindah ke halaman sebelumnya") {
                        st.moveInList(p.home, idx, (page - 1) * per + per - 1); done()
                    }
                    ActionRow(Icons.AutoMirrored.Rounded.ArrowForward, "Pindah ke halaman berikutnya") {
                        // taruh di awal halaman berikut (bisa membuat halaman baru)
                        val target2 = ((page + 1) * per).coerceAtMost(p.home.size)
                        if (target2 >= p.home.size) {
                            // isi halaman ini sampai penuh tidak perlu; pindah ke akhir
                            st.moveInList(p.home, idx, p.home.lastIndex)
                        } else st.moveInList(p.home, idx, target2)
                        done()
                    }
                }
                if (!inHome) ActionRow(Icons.Rounded.Home, "Tambah ke Home") { st.addToHome(app.key); done() }
                else if (target.source == MenuSource.HOME) ActionRow(Icons.Rounded.RemoveCircleOutline, "Hapus dari Home") { st.removeFromHome(app.key); done() }

                if (!inDock) ActionRow(Icons.Rounded.Star, "Tambah ke dock") {
                    if (p.dock.size >= p.dockCount) p.dockCount = (p.dockCount + 1).coerceAtMost(6)
                    st.addToDock(app.key); done()
                } else ActionRow(Icons.Rounded.RemoveCircleOutline, "Hapus dari dock") { st.removeFromDock(app.key); done() }

                if (target.source == MenuSource.DRAWER) {
                    ActionRow(Icons.Rounded.VisibilityOff, "Sembunyikan dari laci") { st.setHidden(app.key, true); done() }
                }
                ActionRow(Icons.Rounded.Info, "Info aplikasi") { done(); st.openAppInfo(app, ctx) }
                ActionRow(Icons.Rounded.Delete, "Uninstall", danger = true) { done(); st.uninstall(app, ctx) }
            }
        }
    }
}

@Composable
fun EditAppDialog(ui: UiState, app: AppInfo) {
    val st = LocalLauncher.current
    val fg = fgColor()
    var name by remember(app.key) { mutableStateOf(st.prefs.customLabels[app.key] ?: app.originalLabel) }

    Overlay(onDismiss = { ui.editApp = null }) {
        GlassSurface(Modifier.widthIn(max = 360.dp).fillMaxWidth(), strength = 2.4f) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Edit aplikasi", color = fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.clickable { ui.pickIconFor = app }.padding(6.dp)
                ) { AppIcon(app, 72.dp) }
                Text("Ketuk ikon untuk mengganti", color = fg.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        st.rename(app.key, null); st.setCustomIcon(app.key, null); ui.editApp = null
                    }) { Text("Reset") }
                    Row {
                        TextButton(onClick = { ui.editApp = null }) { Text("Batal") }
                        TextButton(onClick = {
                            st.rename(app.key, if (name.trim() == app.originalLabel) null else name)
                            ui.editApp = null
                        }) { Text("Simpan") }
                    }
                }
            }
        }
    }
}

private val pickerCache = LruCache<String, ImageBitmap>(300)

@Composable
fun IconPickerDialog(ui: UiState, app: AppInfo) {
    val st = LocalLauncher.current
    val ctx = LocalContext.current
    val fg = fgColor()
    val packs = remember { IconPack.installed(ctx) }
    var selected by remember { mutableStateOf(st.iconPack?.packageName ?: packs.firstOrNull()?.packageName) }
    var query by remember { mutableStateOf("") }

    val pack by produceState<IconPack?>(null, selected) {
        value = null
        val sel = selected ?: return@produceState
        value = if (st.iconPack?.packageName == sel) st.iconPack
        else withContext(Dispatchers.IO) { runCatching { IconPack.load(ctx, sel) }.getOrNull() }
    }

    Overlay(onDismiss = { ui.pickIconFor = null }) {
        GlassSurface(Modifier.fillMaxWidth().heightIn(max = 620.dp), strength = 2.4f) {
            Column(Modifier.padding(16.dp)) {
                Text("Pilih ikon – ${st.label(app)}", color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                if (packs.isEmpty()) {
                    Text(
                        "Belum ada icon pack terpasang. Install icon pack apa saja dari Play Store " +
                            "(yang mendukung Nova/ADW), lalu buka lagi menu ini.",
                        color = fg.copy(alpha = 0.8f), fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { ui.pickIconFor = null }) { Text("Tutup") }
                    return@Column
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(packs, key = { it.packageName }) { info ->
                        FilterChip(
                            selected = selected == info.packageName,
                            onClick = { selected = info.packageName },
                            label = { Text(info.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("Cari ikon") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val pk = pack
                if (pk == null) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    // Saran: ikon yang cocok untuk aplikasi ini ditaruh paling depan
                    val suggested = pk.drawableNameFor(app.packageName, app.className)
                    val q = query.trim().lowercase()
                    val base = if (q.isEmpty()) pk.allDrawables else pk.allDrawables.filter { it.lowercase().contains(q) }
                    val names = (listOfNotNull(suggested?.takeIf { q.isEmpty() }) + base).distinct()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(64.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 420.dp),
                        contentPadding = PaddingValues(4.dp),
                    ) {
                        items(names, key = { it }) { name ->
                            val bmp by produceState<ImageBitmap?>(pickerCache.get("${pk.packageName}|$name"), pk.packageName, name) {
                                if (value == null) value = withContext(Dispatchers.IO) {
                                    pk.getDrawable(name)?.let { d ->
                                        val b = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
                                        d.setBounds(0, 0, 144, 144); d.draw(Canvas(b))
                                        b.asImageBitmap().also { pickerCache.put("${pk.packageName}|$name", it) }
                                    }
                                }
                            }
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .padding(6.dp)
                                    .clickable {
                                        st.setCustomIcon(app.key, "${pk.packageName}|$name")
                                        ui.pickIconFor = null
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                bmp?.let { Image(it, name, Modifier.fillMaxSize()) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeMenu(ui: UiState, onPickWallpaper: () -> Unit, onMakeDefault: () -> Unit) {
    val fg = fgColor()
    Overlay(onDismiss = { ui.homeMenuOpen = false }, bottom = true) {
        GlassSurface(Modifier.widthIn(max = 420.dp).fillMaxWidth(), strength = 2f) {
            Column(Modifier.padding(vertical = 10.dp)) {
                Text(
                    "Kaca Launcher", color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                ActionRow(Icons.Rounded.Add, "Tambah aplikasi ke Home") { ui.homeMenuOpen = false; ui.openDrawer() }
                ActionRow(Icons.Rounded.Image, "Ganti wallpaper (foto)") { ui.homeMenuOpen = false; onPickWallpaper() }
                ActionRow(Icons.Rounded.Settings, "Pengaturan launcher") { ui.homeMenuOpen = false; ui.settingsOpen = true }
                ActionRow(Icons.Rounded.Apps, "Jadikan launcher default") { ui.homeMenuOpen = false; onMakeDefault() }
            }
        }
    }
}
