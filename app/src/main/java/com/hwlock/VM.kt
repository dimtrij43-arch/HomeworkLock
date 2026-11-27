package com.hwlock

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class InstalledApp(val pkg: String, val label: String)

class VM(app: Application) : AndroidViewModel(app) {
    private val db = Graph.db
    val settings: StateFlow<Settings?> = Graph.prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val tasks = db.tasks().active().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val archive = db.tasks().archive().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val selected = db.apps().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var installed by mutableStateOf<List<InstalledApp>>(emptyList())
    var used by mutableStateOf<Map<String, Long>>(emptyMap())
    var meshMsg by mutableStateOf("")
    /** Метка времени для пересчёта статуса (23:00 / 06:00, смена дня) на главном экране. */
    var now by mutableStateOf(System.currentTimeMillis())

    fun refresh() {
        now = System.currentTimeMillis()
        refreshUsage()
    }

    init { viewModelScope.launch(Dispatchers.IO) { Rotation.run() } }  // догоняющая ротация

    fun loadInstalled() = viewModelScope.launch(Dispatchers.IO) {
        val pm = getApplication<Application>().packageManager
        val ctx = getApplication<Application>()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        installed = pm.queryIntentActivities(intent, 0).mapNotNull {
            val ai = it.activityInfo.applicationInfo
            val sys = ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
            val upd = ai.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            val pkg = it.activityInfo.packageName
            if ((sys && !upd) || Whitelist.isProtected(ctx, pkg)) null
            else InstalledApp(pkg, it.loadLabel(pm).toString())
        }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }

    fun refreshUsage() = viewModelScope.launch(Dispatchers.IO) {
        val c = getApplication<Application>()
        used = selected.value.associate { it.pkg to runCatching { Usage.usedToday(c, it.pkg) }.getOrDefault(0L) }
    }

    fun toggleApp(a: InstalledApp, on: Boolean) = Graph.scope.launch {
        if (on) db.apps().insert(AppEntity(a.pkg, a.label)) else db.apps().remove(a.pkg)
    }
    fun setLimit(pkg: String, m: Int) = Graph.scope.launch { db.apps().setLimit(pkg, m) }

    fun saveProfile(n: String, e: String) = viewModelScope.launch { Graph.prefs.setProfile(n, e) }
    fun finishOnboarding() = viewModelScope.launch { Graph.prefs.setOnboarded() }
    fun setWeekend(s: Set<Int>) = viewModelScope.launch { Graph.prefs.setWeekend(s) }
    fun setVacation(v: Boolean) = viewModelScope.launch { Graph.prefs.setVacation(v) }

    fun saveTask(id: Long, subject: String, desc: String, links: String) = viewModelScope.launch {
        val old = tasks.value.find { it.id == id }
        db.tasks().upsert((old ?: TaskEntity(subject = "", description = "", date = LocalDate.now().toString()))
            .copy(subject = subject, description = desc, links = links))
    }
    fun toggleDone(t: TaskEntity) = viewModelScope.launch { db.tasks().setDone(t.id, !t.done) }
    fun delete(t: TaskEntity) = viewModelScope.launch { db.tasks().delete(t) }

    fun connectMesh(token: String?) = viewModelScope.launch {
        if (token == null) { meshMsg = "Вход в МЭШ не выполнен — можно добавлять задания вручную"; return@launch }
        Graph.prefs.setToken(token)
        syncMesh()
    }
    fun syncMesh() = viewModelScope.launch {
        val t = Graph.prefs.flow.let { settings.value?.meshToken ?: "" }
        if (t.isEmpty()) { meshMsg = "МЭШ не подключён"; return@launch }
        meshMsg = Mesh.sync(t).fold({ "Импортировано заданий: $it" }, { "МЭШ недоступен (${it.message}). Добавляйте задания вручную" })
    }
}
