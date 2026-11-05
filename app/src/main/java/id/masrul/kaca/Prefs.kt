package id.masrul.kaca

import android.content.Context
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class GestureAction(val label: String) {
    NONE("Tidak ada"),
    DRAWER("Buka laci aplikasi"),
    SEARCH("Cari aplikasi"),
    NOTIFICATIONS("Buka notifikasi"),
    QUICK_SETTINGS("Quick settings"),
    LOCK("Kunci layar"),
    RECENTS("Aplikasi terbaru"),
    SETTINGS("Pengaturan launcher"),
}

enum class IconShape(val label: String) {
    SYSTEM("Bawaan"),
    CIRCLE("Lingkaran"),
    SQUIRCLE("Squircle"),
    ROUNDED("Kotak bulat"),
    SQUARE("Kotak"),
    TEARDROP("Tetes"),
}

enum class ThemeMode(val label: String) { AUTO("Otomatis"), LIGHT("Terang"), DARK("Gelap") }

/**
 * Semua pengaturan launcher. Setiap properti adalah Compose state sehingga UI langsung
 * berubah, dan otomatis disimpan ke SharedPreferences.
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("kaca", Context.MODE_PRIVATE)

    // ---- Tata letak
    var homeColumns by intPref("homeColumns", 4)
    var homeRows by intPref("homeRows", 5)
    var drawerColumns by intPref("drawerColumns", 4)
    var iconSizeDp by intPref("iconSizeDp", 56)
    var showLabels by boolPref("showLabels", true)
    var labelSizeSp by intPref("labelSizeSp", 12)
    var dockCount by intPref("dockCount", 4)
    var showDock by boolPref("showDock", true)
    var showClock by boolPref("showClock", true)
    var showStatusWidget by boolPref("showStatusWidget", true)
    var clock24h by boolPref("clock24h", true)
    var showPageDots by boolPref("showPageDots", true)
    var drawerAutoKeyboard by boolPref("drawerAutoKeyboard", false)

    // ---- Ikon
    var iconPack by stringPref("iconPack", "")
    var iconShape by enumPref("iconShape", IconShape.SYSTEM)

    // ---- Tema kaca
    var themeMode by enumPref("themeMode", ThemeMode.DARK)
    var blurRadius by floatPref("blurRadius", 28f)        // dp
    var glassOpacity by floatPref("glassOpacity", 0.22f)  // 0..0.8
    var glassCorner by intPref("glassCorner", 28)
    var accent by intPref("accent", 0xFF7C9CFF.toInt())
    /** "system", "gradient:N", atau "photo" */
    var wallpaper by stringPref("wallpaper", "gradient:0")
    var wallpaperDim by floatPref("wallpaperDim", 0.1f)

    // ---- Gestur
    var swipeUp by enumPref("swipeUp", GestureAction.DRAWER)
    var swipeDown by enumPref("swipeDown", GestureAction.NOTIFICATIONS)
    var doubleTap by enumPref("doubleTap", GestureAction.LOCK)
    var twoFingerDown by enumPref("twoFingerDown", GestureAction.QUICK_SETTINGS)

    // ---- Data aplikasi
    val home = mutableStateListOf<String>()
    val dock = mutableStateListOf<String>()
    val hidden = mutableStateListOf<String>()
    val customLabels = mutableStateMapOf<String, String>()
    /** key app -> "paketIconPack|namaDrawable" */
    val customIcons = mutableStateMapOf<String, String>()
    var initialized by boolPref("initialized", false)

    init {
        home.addAll(readList("home"))
        dock.addAll(readList("dock"))
        hidden.addAll(readList("hidden"))
        customLabels.putAll(readMap("customLabels"))
        customIcons.putAll(readMap("customIcons"))
    }

    fun saveLists() {
        sp.edit()
            .putString("home", JSONArray(home.toList()).toString())
            .putString("dock", JSONArray(dock.toList()).toString())
            .putString("hidden", JSONArray(hidden.toList()).toString())
            .putString("customLabels", JSONObject(customLabels.toMap()).toString())
            .putString("customIcons", JSONObject(customIcons.toMap()).toString())
            .apply()
    }

    private fun readList(key: String): List<String> = try {
        val arr = JSONArray(sp.getString(key, "[]"))
        List(arr.length()) { arr.getString(it) }
    } catch (e: Exception) { emptyList() }

    private fun readMap(key: String): Map<String, String> = try {
        val o = JSONObject(sp.getString(key, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    } catch (e: Exception) { emptyMap() }

    // ---------- delegates
    private fun intPref(key: String, def: Int) = object : ReadWriteProperty<Any?, Int> {
        val s = mutableIntStateOf(sp.getInt(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s.intValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            s.intValue = value; sp.edit().putInt(key, value).apply()
        }
    }

    private fun floatPref(key: String, def: Float) = object : ReadWriteProperty<Any?, Float> {
        val s = mutableFloatStateOf(sp.getFloat(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s.floatValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
            s.floatValue = value; sp.edit().putFloat(key, value).apply()
        }
    }

    private fun boolPref(key: String, def: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        val s = mutableStateOf(sp.getBoolean(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            s.value = value; sp.edit().putBoolean(key, value).apply()
        }
    }

    private fun stringPref(key: String, def: String) = object : ReadWriteProperty<Any?, String> {
        val s = mutableStateOf(sp.getString(key, def) ?: def)
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            s.value = value; sp.edit().putString(key, value).apply()
        }
    }

    private inline fun <reified E : Enum<E>> enumPref(key: String, def: E) =
        object : ReadWriteProperty<Any?, E> {
            val s = mutableStateOf(
                runCatching { enumValueOf<E>(sp.getString(key, def.name)!!) }.getOrDefault(def)
            )
            override fun getValue(thisRef: Any?, property: KProperty<*>) = s.value
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: E) {
                s.value = value; sp.edit().putString(key, value.name).apply()
            }
        }
}
