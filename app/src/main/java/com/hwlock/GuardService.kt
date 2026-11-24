package com.hwlock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

/** Перехватывает запуск приложений, показывает экран-блокировщик, защищает настройки. */
class GuardService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var current = ""
    private var lastLock = 0L

    private val settingsPkgs = setOf("com.android.settings", "com.samsung.android.settings", "com.miui.securitycenter",
        "com.android.packageinstaller", "com.google.android.packageinstaller", "com.google.android.permissioncontroller")
    private val sensitive = listOf("Приложения", "Все приложения", "Apps", "Device admin", "Администратор",
        "Удалить", "Uninstall", "О приложении", "App info", "Homework Lock")

    override fun onServiceConnected() {
        scope.launch {                       // таймеры: периодическая проверка текущего приложения
            while (isActive) { delay(3000); check(current) }
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg.contains("inputmethod") || pkg == "com.android.systemui") return
        current = pkg
        scope.launch {
            if (pkg in settingsPkgs) guardSettings()
            check(pkg)
        }
    }

    private suspend fun guardSettings() {
        if (!Policy.homeworkLocked()) return
        val root = rootInActiveWindow ?: return
        if (sensitive.any { root.findAccessibilityNodeInfosByText(it).isNotEmpty() }) {
            main.post { performGlobalAction(GLOBAL_ACTION_HOME) }
        }
    }

    private suspend fun check(pkg: String) {
        if (pkg.isEmpty() || pkg == packageName) return
        val v = Policy.verdict(pkg)
        if (v == Verdict.ALLOW) return
        val now = System.currentTimeMillis()
        if (now - lastLock < 1500) return
        lastLock = now
        main.post {
            startActivity(Intent(this, LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("reason", v.name).putExtra("pkg", pkg))
        }
        current = packageName
    }

    override fun onInterrupt() {}
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
