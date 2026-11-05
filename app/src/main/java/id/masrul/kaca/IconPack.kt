package id.masrul.kaca

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

data class IconPackInfo(val packageName: String, val label: String)

/**
 * Membaca icon pack format ADW/Nova (appfilter.xml) yang dipakai hampir semua icon pack
 * di Play Store.
 */
class IconPack private constructor(
    val packageName: String,
    private val res: Resources,
    /** "paket/kelas" -> nama drawable */
    private val map: Map<String, String>,
    /** paket -> nama drawable (fallback kalau kelas tidak cocok) */
    private val byPackage: Map<String, String>,
    /** Semua nama drawable untuk picker ikon */
    val allDrawables: List<String>,
) {
    fun drawableNameFor(pkg: String, cls: String): String? = map["$pkg/$cls"] ?: byPackage[pkg]

    @SuppressLint("DiscouragedApi")
    fun getDrawable(name: String): Drawable? = try {
        val id = res.getIdentifier(name, "drawable", packageName)
        if (id != 0) res.getDrawable(id, null) else null
    } catch (e: Exception) { null }

    companion object {
        private val ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
        )

        fun installed(context: Context): List<IconPackInfo> {
            val pm = context.packageManager
            return ACTIONS.flatMap { pm.queryIntentActivities(Intent(it), PackageManager.GET_META_DATA) }
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .map { IconPackInfo(it.first, it.second) }
                .sortedBy { it.label.lowercase() }
        }

        @SuppressLint("DiscouragedApi")
        fun load(context: Context, pkg: String): IconPack {
            val res = context.packageManager.getResourcesForApplication(pkg)
            val map = HashMap<String, String>()
            val byPkg = HashMap<String, String>()
            val all = LinkedHashSet<String>()

            openXml(res, pkg, "appfilter")?.let { p ->
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && p.name == "item") {
                        val comp = p.getAttributeValue(null, "component")
                        val draw = p.getAttributeValue(null, "drawable")
                        if (comp != null && !draw.isNullOrBlank()) {
                            parseComponent(comp)?.let { (pk, cl) ->
                                map.putIfAbsent("$pk/$cl", draw)
                                byPkg.putIfAbsent(pk, draw)
                            }
                            all.add(draw)
                        }
                    }
                    ev = p.next()
                }
            }

            // drawable.xml berisi daftar lengkap ikon (opsional)
            val listed = ArrayList<String>()
            openXml(res, pkg, "drawable")?.let { p ->
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && p.name == "item") {
                        p.getAttributeValue(null, "drawable")?.let { if (it.isNotBlank()) listed.add(it) }
                    }
                    ev = p.next()
                }
            }
            val drawables = (listed + all).distinct().sorted()
            return IconPack(pkg, res, map, byPkg, drawables)
        }

        @SuppressLint("DiscouragedApi")
        private fun openXml(res: Resources, pkg: String, name: String): XmlPullParser? {
            try {
                val id = res.getIdentifier(name, "xml", pkg)
                if (id != 0) return res.getXml(id)
            } catch (_: Exception) { }
            return try {
                val stream = res.assets.open("$name.xml")
                Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(stream, "utf-8")
                }
            } catch (_: Exception) { null }
        }

        /** "ComponentInfo{com.foo/com.foo.Main}" -> (com.foo, com.foo.Main) */
        private fun parseComponent(raw: String): Pair<String, String>? {
            val s = raw.removePrefix("ComponentInfo{").removeSuffix("}").trim()
            val slash = s.indexOf('/')
            if (slash <= 0 || slash == s.lastIndex) return null
            val pkg = s.substring(0, slash)
            var cls = s.substring(slash + 1)
            if (cls.startsWith(".")) cls = pkg + cls
            return pkg to cls
        }
    }
}
