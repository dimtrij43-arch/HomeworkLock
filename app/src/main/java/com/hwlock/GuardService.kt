package com.hwlock

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Перехватывает запуск приложений, показывает экран-блокировщик, защищает настройки. Никогда не должен падать. */
class GuardService : AccessibilityService() {
    private val tag = "GuardService"
    private val handler = CoroutineExceptionHandler { _, t -> Log.e(tag, "coroutine error", t) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var current = ""
    @Volatile private var lastLock = 0L          // только короткий кулдаун, пакет НЕ запоминается
    private var loop: Job? = null

    private val settingsPkgs = setOf(
        "com.android.settings", "com.samsung.android.settings", "com.miui.securitycenter",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller", "com.miui.packageinstaller"
    )
    private val sensitive = listOf(
        "Приложения", "Все приложения", "Apps", "Device admin", "Администратор",
        "Удалить", "Uninstall", "О приложении", "App info", "Homework Lock"
    )

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            loop?.cancel()
            // Непрерывный тикер: каждые 2 с, пока экран включён, проверяем активное приложение.
            loop = scope.launch {
                while (isActive) {
                    delay(2000)
                    try {
                        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                        if (!pm.isInteractive) continue
                        val p = foregroundPackage()
                        if (!p.isNullOrEmpty()) safeCheck(p)
                    } catch (t: Throwable) {
                        Log.e(tag, "ticker", t)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(tag, "onServiceConnected", t)
        }
    }

    private fun ignored(p: String) = p.contains("inputmethod") || p == "com.android.systemui"

    /** rootInActiveWindow → UsageStats за последние 5 секунд → последнее известное окно. */
    private fun foregroundPackage(): String? {
        val root = try { rootInActiveWindow?.packageName?.toString() } catch (t: Throwable) { null }
        if (!root.isNullOrEmpty() && !ignored(root)) return root
        val usage = recentUsagePackage()
        if (!usage.isNullOrEmpty() && !ignored(usage)) return usage
        return current.ifEmpty { null }
    }

    private fun recentUsagePackage(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val ev = usm.queryEvents(now - 5000, now)
            val e = UsageEvents.Event()
            var last: String? = null
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
            }
            last
        } catch (t: Throwable) {
            null
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        try {
            if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            val pkg = e.packageName?.toString()
            if (pkg.isNullOrEmpty() || ignored(pkg)) return
            current = pkg
            scope.launch {
                try {
                    if (pkg in settingsPkgs) guardSettings()
                    safeCheck(pkg)
                } catch (t: Throwable) {
                    Log.e(tag, "event handling", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(tag, "onAccessibilityEvent", t)
        }
    }

    private suspend fun guardSettings() {
        if (!Policy.homeworkLocked()) return
        val root = try { rootInActiveWindow } catch (t: Throwable) { null } ?: return
        val hit = sensitive.any {
            try { root.findAccessibilityNodeInfosByText(it).isNotEmpty() } catch (t: Throwable) { false }
        }
        if (hit) main.post { try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (_: Throwable) {} }
    }

    private suspend fun safeCheck(pkg: String) {
        try {
            if (pkg.isEmpty() || pkg == packageName) return
            val v = Policy.verdict(pkg)
            if (v == Verdict.ALLOW) return
            val now = System.currentTimeMillis()
            if (now - lastLock < 1500) return    // кулдаун 1.5 с вместо запоминания пакета
            lastLock = now
            main.post {
                try {
                    val i = Intent(applicationContext, LockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra("reason", v.name)
                        .putExtra("pkg", pkg)
                    applicationContext.startActivity(i)
                } catch (t: Throwable) {
                    Log.e(tag, "start LockActivity", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(tag, "check", t)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        try { scope.cancel() } catch (_: Throwable) {}
        super.onDestroy()
    }
}
