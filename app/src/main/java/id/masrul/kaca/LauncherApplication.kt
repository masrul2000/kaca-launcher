package id.masrul.kaca

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

data class AppInfo(
    val key: String,          // "paket/kelas"
    val packageName: String,
    val className: String,
    val originalLabel: String,
)

class LauncherApplication : Application() {
    lateinit var state: LauncherState
        private set

    override fun onCreate() {
        super.onCreate()
        state = LauncherState(this)
    }
}

val Context.launcherState: LauncherState
    get() = (applicationContext as LauncherApplication).state

/** Pusat state launcher: daftar aplikasi, pengaturan, icon pack. */
class LauncherState(val context: Context) {
    val prefs = Prefs(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val iconCache = IconCache(context, this)
    val wallpaperStore = WallpaperStore(context)

    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set
    var iconPack by mutableStateOf<IconPack?>(null)
        private set
    var iconPackLoading by mutableStateOf(false)
        private set

    private val pm: PackageManager = context.packageManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    init {
        reloadApps()
        loadIconPack(prefs.iconPack)
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reloadApps()
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = reloadApps()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) {
                iconCache.clear(); reloadApps()
                if (packageName == prefs.iconPack) loadIconPack(packageName)
            }
            override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = reloadApps()
            override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = reloadApps()
        }, Handler(Looper.getMainLooper()))
    }

    fun label(app: AppInfo): String = prefs.customLabels[app.key] ?: app.originalLabel

    fun appByKey(key: String): AppInfo? = apps.firstOrNull { it.key == key }

    fun reloadApps() {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val collator = Collator.getInstance()
                pm.queryIntentActivities(intent, 0)
                    .filter { it.activityInfo.packageName != context.packageName }
                    .map {
                        val ai = it.activityInfo
                        AppInfo(
                            key = "${ai.packageName}/${ai.name}",
                            packageName = ai.packageName,
                            className = ai.name,
                            originalLabel = it.loadLabel(pm).toString(),
                        )
                    }
                    .distinctBy { it.key }
                    .sortedWith { a, b -> collator.compare(a.originalLabel, b.originalLabel) }
            }
            apps = list
            if (!prefs.initialized && list.isNotEmpty()) setupDefaults(list)
            // Buang aplikasi yang sudah di-uninstall dari home/dock
            val keys = list.map { it.key }.toSet()
            if (prefs.home.removeAll { it !in keys } or prefs.dock.removeAll { it !in keys }) {
                prefs.saveLists()
            }
        }
    }

    private fun setupDefaults(list: List<AppInfo>) {
        fun forIntent(i: Intent): AppInfo? {
            val pkg = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                ?: return null
            return list.firstOrNull { it.packageName == pkg }
        }
        val dockApps = listOfNotNull(
            forIntent(Intent(Intent.ACTION_DIAL)),
            forIntent(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            forIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))),
            forIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
        ).distinctBy { it.key }
        prefs.dock.clear(); prefs.dock.addAll(dockApps.map { it.key })

        val preferred = listOf(
            "com.whatsapp", "com.google.android.youtube", "com.google.android.gm",
            "com.google.android.apps.maps", "com.google.android.apps.photos",
            "com.android.vending", "com.instagram.android", "org.telegram.messenger",
            "com.google.android.calendar", "com.android.settings", "com.google.android.deskclock",
            "com.spotify.music",
        )
        val homeApps = preferred.mapNotNull { p -> list.firstOrNull { it.packageName == p } }
            .filter { it.key !in prefs.dock }
            .ifEmpty { list.take(8) }
        prefs.home.clear(); prefs.home.addAll(homeApps.map { it.key })
        prefs.saveLists()
        prefs.initialized = true
    }

    fun loadIconPack(pkg: String) {
        if (pkg.isBlank()) { iconPack = null; iconCache.clear(); return }
        iconPackLoading = true
        scope.launch {
            val pack = withContext(Dispatchers.IO) { runCatching { IconPack.load(context, pkg) }.getOrNull() }
            iconPack = pack
            iconPackLoading = false
            iconCache.clear()
        }
    }

    fun setIconPack(pkg: String) {
        prefs.iconPack = pkg
        loadIconPack(pkg)
    }

    // ---------------- aksi aplikasi
    fun launch(app: AppInfo, ctx: Context = context) {
        try {
            val i = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(app.packageName, app.className))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ctx.startActivity(i)
        } catch (e: Exception) {
            reloadApps()
        }
    }

    fun openAppInfo(app: AppInfo, ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    @Suppress("DEPRECATION")
    fun uninstall(app: AppInfo, ctx: Context) {
        ctx.startActivity(
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ---------------- edit home/dock
    fun addToHome(key: String) {
        if (key !in prefs.home) { prefs.home.add(key); prefs.saveLists() }
    }
    fun removeFromHome(key: String) { prefs.home.remove(key); prefs.saveLists() }
    fun addToDock(key: String) {
        if (key in prefs.dock) return
        prefs.dock.add(key)
        prefs.saveLists()
    }
    fun removeFromDock(key: String) { prefs.dock.remove(key); prefs.saveLists() }
    fun moveInList(list: MutableList<String>, from: Int, to: Int) {
        if (from !in list.indices) return
        val t = to.coerceIn(0, list.lastIndex)
        if (from == t) return
        val item = list.removeAt(from)
        list.add(t, item)
        prefs.saveLists()
    }
    fun setHidden(key: String, hide: Boolean) {
        if (hide) { if (key !in prefs.hidden) prefs.hidden.add(key) } else prefs.hidden.remove(key)
        prefs.saveLists()
    }
    fun rename(key: String, label: String?) {
        if (label.isNullOrBlank()) prefs.customLabels.remove(key) else prefs.customLabels[key] = label.trim()
        prefs.saveLists()
    }
    fun setCustomIcon(key: String, value: String?) {
        if (value == null) prefs.customIcons.remove(key) else prefs.customIcons[key] = value
        prefs.saveLists()
        iconCache.clear()
    }
}
