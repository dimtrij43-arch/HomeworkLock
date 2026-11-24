package com.hwlock

import android.app.AppOpsManager
import android.app.Application
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings as AS
import androidx.room.Room
import androidx.work.*
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        val delay = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(1))
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
                .setConstraints(Constraints.NONE).build()
        )
    }
}

object Graph {
    lateinit var ctx: Context; private set
    lateinit var db: Db; private set
    lateinit var prefs: Prefs; private set
    fun init(c: Context) {
        ctx = c.applicationContext
        db = Room.databaseBuilder(ctx, Db::class.java, "hw.db").build()
        prefs = Prefs(ctx)
    }
}

// ---------- Ротация в полночь + очистка архива ----------
object Rotation {
    suspend fun run() {
        val dao = Graph.db.tasks()
        dao.archiveOld(LocalDate.now().toString(), System.currentTimeMillis())
        dao.purge(System.currentTimeMillis() - 7L * 24 * 3600_000)   // архив хранится 7 дней
        val token = Graph.prefs.flow.first().meshToken
        if (token.isNotEmpty()) Mesh.sync(token)
    }
}

class DailyWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result { Rotation.run(); return Result.success() }
}

// ---------- Белый список ----------
object Whitelist {
    private val keywords = listOf("dialer", "contacts", "deskclock", "clock", "mms", "messaging", "camera",
        "telecom", "incallui", "launcher", "systemui", "com.android.settings", "packageinstaller", "permissioncontroller")

    fun set(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val s = mutableSetOf(ctx.packageName)
        fun add(i: Intent) = pm.queryIntentActivities(i, 0).forEach { s += it.activityInfo.packageName }
        add(Intent(Intent.ACTION_DIAL))
        add(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
        add(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        add(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        add(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))
        add(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        return s
    }

    fun isProtected(ctx: Context, pkg: String): Boolean =
        pkg in set(ctx) || keywords.any { pkg.contains(it, true) }
}

// ---------- Учёт времени ----------
object Usage {
    fun usedToday(ctx: Context, pkg: String): Long {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val start = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val ev = usm.queryEvents(start, now)
        val e = UsageEvents.Event()
        var total = 0L; var from = 0L
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.packageName != pkg) continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> if (from == 0L) from = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED -> if (from != 0L) { total += e.timeStamp - from; from = 0 }
            }
        }
        if (from != 0L) total += now - from
        return total
    }
}

// ---------- Политика блокировки ----------
enum class Verdict { ALLOW, HOMEWORK, LIMIT }

object Policy {
    fun isFreeDay(s: Settings) = s.vacation || LocalDate.now().dayOfWeek.value in s.weekend

    suspend fun homeworkLocked(): Boolean {
        val s = Graph.prefs.flow.first()
        return !isFreeDay(s) && Graph.db.tasks().pending() > 0 && s.emergencyUntil < System.currentTimeMillis()
    }

    suspend fun verdict(pkg: String): Verdict {
        if (Whitelist.isProtected(Graph.ctx, pkg)) return Verdict.ALLOW
        val app = Graph.db.apps().get(pkg) ?: return Verdict.ALLOW
        val s = Graph.prefs.flow.first()
        if (s.emergencyUntil > System.currentTimeMillis()) return Verdict.ALLOW
        if (!isFreeDay(s) && Graph.db.tasks().pending() > 0) return Verdict.HOMEWORK
        if (app.limitMinutes > 0 && Usage.usedToday(Graph.ctx, pkg) >= app.limitMinutes * 60_000L) return Verdict.LIMIT
        return Verdict.ALLOW
    }
}

// ---------- Разрешения ----------
object Perms {
    fun usage(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
    }
    fun overlay(c: Context) = AS.canDrawOverlays(c)
    fun accessibility(c: Context): Boolean {
        val v = AS.Secure.getString(c.contentResolver, AS.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return v.contains("${c.packageName}/${GuardService::class.java.name}")
    }
    fun admin(c: Context) = (c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
        .isAdminActive(ComponentName(c, AdminReceiver::class.java))
    fun battery(c: Context) = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)

    fun usageIntent(c: Context) = Intent(AS.ACTION_USAGE_ACCESS_SETTINGS)
    fun overlayIntent(c: Context) = Intent(AS.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}"))
    fun accessibilityIntent(c: Context) = Intent(AS.ACTION_ACCESSIBILITY_SETTINGS)
    fun adminIntent(c: Context) = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(c, AdminReceiver::class.java))
    @Suppress("BatteryLife")
    fun batteryIntent(c: Context) = Intent(AS.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}"))
}

class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Отключение защиты недоступно, пока не выполнены домашние задания."
}
