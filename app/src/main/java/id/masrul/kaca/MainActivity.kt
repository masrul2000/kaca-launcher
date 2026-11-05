package id.masrul.kaca

import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import id.masrul.kaca.ui.AppDrawer
import id.masrul.kaca.ui.AppMenu
import id.masrul.kaca.ui.EditAppDialog
import id.masrul.kaca.ui.FullWallpaper
import id.masrul.kaca.ui.HomeMenu
import id.masrul.kaca.ui.HomeScreen
import id.masrul.kaca.ui.IconPickerDialog
import id.masrul.kaca.ui.KacaTheme
import id.masrul.kaca.ui.LocalDark
import id.masrul.kaca.ui.LocalLauncher
import id.masrul.kaca.ui.LocalScreenSize
import id.masrul.kaca.ui.SettingsScreen
import id.masrul.kaca.ui.UiState

class MainActivity : ComponentActivity() {
    private val ui = UiState()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val state = launcherState
        setContent {
            val p = state.prefs
            val dark = when (p.themeMode) {
                ThemeMode.AUTO -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CompositionLocalProvider(LocalLauncher provides state, LocalDark provides dark) {
                KacaTheme(dark, Color(p.accent)) {
                    LauncherRoot(ui)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Tombol Home ditekan saat sudah di launcher -> kembali ke halaman utama
        if (intent.action == Intent.ACTION_MAIN) ui.goHome()
    }

    override fun onResume() {
        super.onResume()
        launcherState.reloadApps()
    }
}

@Composable
private fun LauncherRoot(ui: UiState) {
    val st = LocalLauncher.current
    val p = st.prefs
    val ctx = LocalContext.current

    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            st.wallpaperStore.setFromUri(uri) { ok ->
                if (ok) p.wallpaper = "photo"
                else Toast.makeText(ctx, "Gagal memuat foto", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    val onPickWallpaper: () -> Unit = {
        pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val onMakeDefault: () -> Unit = {
        var done = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                runCatching { roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)); done = true }
            } else if (rm != null && rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                Toast.makeText(ctx, "Kaca Launcher sudah jadi launcher default", Toast.LENGTH_SHORT).show()
                done = true
            }
        }
        if (!done) {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    val onGesture: (GestureAction) -> Unit = { a ->
        when (a) {
            GestureAction.NONE -> {}
            GestureAction.DRAWER -> ui.openDrawer()
            GestureAction.SEARCH -> ui.openDrawer(search = true)
            GestureAction.NOTIFICATIONS -> SystemActions.notifications(ctx)
            GestureAction.QUICK_SETTINGS -> SystemActions.quickSettings(ctx)
            GestureAction.LOCK -> SystemActions.lock(ctx)
            GestureAction.RECENTS -> SystemActions.recents(ctx)
            GestureAction.SETTINGS -> { ui.settingsOpen = true }
        }
    }

    // Launcher tidak boleh "keluar" dengan tombol back
    BackHandler(enabled = true) { }
    BackHandler(enabled = ui.anyOverlay) { ui.back() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = IntSize(constraints.maxWidth, constraints.maxHeight)
        CompositionLocalProvider(LocalScreenSize provides screen) {
            Box(Modifier.fillMaxSize()) {
                FullWallpaper()
                HomeScreen(ui, onGesture)

                AnimatedVisibility(
                    visible = ui.drawerOpen,
                    enter = slideInVertically { it / 3 } + fadeIn(),
                    exit = slideOutVertically { it / 3 } + fadeOut(),
                ) { AppDrawer(ui) }

                AnimatedVisibility(
                    visible = ui.settingsOpen,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut(),
                ) { SettingsScreen(ui, onPickWallpaper, onMakeDefault) }

                AnimatedVisibility(visible = ui.homeMenuOpen, enter = fadeIn(), exit = fadeOut()) {
                    HomeMenu(ui, onPickWallpaper, onMakeDefault)
                }
                ui.menu?.let { AppMenu(ui, it) }
                ui.editApp?.let { EditAppDialog(ui, it) }
                ui.pickIconFor?.let { IconPickerDialog(ui, it) }
            }
        }
    }
}
