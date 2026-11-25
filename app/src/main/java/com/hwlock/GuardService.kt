package com.hwlock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
    @Volatile private var lastLock = 0L
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
            loop = scope.launch {                 // периодическая проверка таймеров
                while (isActive) {
                    delay(3000)
                    safeCheck(current)
                }
            }
        } catch (t: Throwable) {
            Log.e(tag, "onServiceConnected", t)
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        try {
            if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            val pkg = e.packageName?.toString()
            if (pkg.isNullOrEmpty()) return
            if (pkg.contains("inputmethod") || pkg == "com.android.systemui") return
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
        if (hit) main.post { runCatching { performGlobalAction(GLOBAL_ACTION_HOME) } }
    }

    private suspend fun safeCheck(pkg: String) {
        try {
            if (pkg.isEmpty() || pkg == packageName) return
            val v = Policy.verdict(pkg)
            if (v == Verdict.ALLOW) return
            val now = System.currentTimeMillis()
            if (now - lastLock < 1500) return
            lastLock = now
            current = packageName
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
