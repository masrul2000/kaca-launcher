package id.masrul.kaca

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/** Service aksesibilitas khusus untuk gestur (kunci layar, notifikasi, recents). */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onUnbind(intent: Intent?): Boolean { if (instance === this) instance = null; return super.onUnbind(intent) }

    companion object {
        var instance: LockService? = null
            private set
    }
}

object SystemActions {
    fun isAccessibilityEnabled(ctx: Context): Boolean {
        if (LockService.instance != null) return true
        val flat = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(ctx, LockService::class.java).flattenToString()
        return flat.split(':').any { it.equals(me, ignoreCase = true) }
    }

    fun openAccessibilitySettings(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun global(ctx: Context, action: Int): Boolean {
        val s = LockService.instance
        if (s == null) {
            Toast.makeText(ctx, "Aktifkan layanan aksesibilitas \"Kaca Launcher – Gestur\" dulu", Toast.LENGTH_LONG).show()
            openAccessibilitySettings(ctx)
            return false
        }
        return s.performGlobalAction(action)
    }

    fun lock(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            global(ctx, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            Toast.makeText(ctx, "Kunci layar butuh Android 9+", Toast.LENGTH_SHORT).show()
        }
    }

    fun recents(ctx: Context) { global(ctx, AccessibilityService.GLOBAL_ACTION_RECENTS) }

    fun quickSettings(ctx: Context) {
        val s = LockService.instance
        if (s != null) s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        else if (!reflectStatusBar(ctx, "expandSettingsPanel")) global(ctx, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }

    fun notifications(ctx: Context) {
        val s = LockService.instance
        if (s != null) s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        else if (!reflectStatusBar(ctx, "expandNotificationsPanel")) global(ctx, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun reflectStatusBar(ctx: Context, method: String): Boolean = try {
        val sbs = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod(method).invoke(sbs)
        true
    } catch (e: Throwable) { false }
}
