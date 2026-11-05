package id.masrul.kaca.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(ui: UiState) {
    val st = LocalLauncher.current
    val p = st.prefs
    val ctx = LocalContext.current
    val dark = LocalDark.current
    val fg = if (dark) Color.White else Color(0xFF15151A)
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val apps = st.apps
        .filter { it.key !in p.hidden }
        .let { list ->
            if (query.isBlank()) list
            else {
                val q = query.trim().lowercase()
                list.filter { st.label(it).lowercase().contains(q) || it.packageName.lowercase().contains(q) }
                    .sortedBy { if (st.label(it).lowercase().startsWith(q)) 0 else 1 }
            }
        }

    LaunchedEffect(Unit) {
        if (ui.drawerSearchFocus || p.drawerAutoKeyboard) {
            runCatching { focus.requestFocus(); keyboard?.show() }
        }
    }

    fun close() {
        focusManager.clearFocus(); keyboard?.hide(); ui.drawerOpen = false
    }

    // Tarik ke bawah saat list sudah di paling atas -> tutup laci
    var pull by remember { mutableFloatStateOf(0f) }
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0) {
                    pull += available.y
                    if (pull > 220f) { pull = 0f; close() }
                } else pull = 0f
                return Offset.Zero
            }
        }
    }

    GlassSurface(
        Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = p.glassCorner.dp + 8.dp, topEnd = p.glassCorner.dp + 8.dp),
        strength = 1.6f,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            // handle untuk swipe tutup
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dy -> if (dy > 12) close() }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(40.dp, 5.dp).background(fg.copy(alpha = 0.4f), RoundedCornerShape(50)))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassSurface(Modifier.weight(1f), shape = RoundedCornerShape(50), strength = 1.2f) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Cari aplikasi", color = fg.copy(alpha = 0.5f), fontSize = 16.sp)
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(color = fg, fontSize = 16.sp),
                                cursorBrush = SolidColor(fg),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = {
                                    apps.firstOrNull()?.let { st.launch(it, ctx); close() }
                                }),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                        }
                    }
                }
                IconButton(onClick = { close(); ui.settingsOpen = true }) {
                    Icon(Icons.Rounded.Settings, "Pengaturan", tint = fg)
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(p.drawerColumns),
                modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(nested),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
            ) {
                items(apps, key = { it.key }) { app ->
                    Box(
                        Modifier
                            .padding(vertical = 10.dp)
                            .combinedClickable(
                                onClick = { st.launch(app, ctx); close() },
                                onLongClick = { ui.menu = MenuTarget(app, MenuSource.DRAWER) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppTile(app, showLabel = true, labelColor = fg, shadow = dark)
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
