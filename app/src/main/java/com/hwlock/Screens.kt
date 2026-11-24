package com.hwlock

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

val Card = RoundedCornerShape(24.dp)

fun openLink(ctx: Context, raw: String) {
    val u = if ("://" in raw) raw else "https://$raw"
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun AppIcon(pkg: String, size: Int = 40) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) { runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    if (bmp != null) Image(bmp, null, Modifier.size(size.dp)) else Spacer(Modifier.size(size.dp))
}

@Composable
fun Page(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = 8.dp))
            actions()
        }
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().weight(1f).padding(horizontal = 16.dp), content = content)
    }
}

// ---------- Задачи ----------
@Composable
fun TaskCard(t: TaskEntity, vm: VM, editable: Boolean = true, onEdit: () -> Unit = {}) {
    val ctx = LocalContext.current
    val a by animateFloatAsState(if (t.done) 0.45f else 1f, label = "done")
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).animateContentSize(), shape = Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            if (editable) Checkbox(t.done, { vm.toggleDone(t) })
            Column(Modifier.weight(1f).alpha(a)) {
                Text(t.subject, style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null)
                if (t.description.isNotBlank()) Text(t.description,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null)
                t.links.lines().filter { it.isNotBlank() }.forEach {
                    TextButton({ openLink(ctx, it.trim()) }, contentPadding = PaddingValues(0.dp)) {
                        Text(it.trim(), maxLines = 1, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (editable) {
                IconButton(onEdit) { Icon(Icons.Default.Edit, "Изменить") }
                IconButton({ vm.delete(t) }) { Icon(Icons.Default.Delete, "Удалить") }
            }
        }
    }
}

@Composable
fun TaskDialog(initial: TaskEntity?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var subj by remember { mutableStateOf(initial?.subject ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var links by remember { mutableStateOf(initial?.links ?: "") }
    AlertDialog(onDismissRequest = onDismiss, shape = Card,
        title = { Text(if (initial == null) "Новое задание" else "Редактирование") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(subj, { subj = it }, label = { Text("Предмет") }, singleLine = true)
                OutlinedTextField(desc, { desc = it }, label = { Text("Задание") }, minLines = 2)
                OutlinedTextField(links, { links = it }, label = { Text("Ссылки (каждая с новой строки)") }, minLines = 2,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            }
        },
        confirmButton = { TextButton({ if (subj.isNotBlank()) { onSave(subj.trim(), desc.trim(), links.trim()); onDismiss() } }) { Text("Сохранить") } },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } })
}

// ---------- Главное меню ----------
@Composable
fun MainScreen(vm: VM, onArchive: () -> Unit, onSettings: () -> Unit) {
    val s by vm.settings.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val apps by vm.selected.collectAsState()
    val set = s ?: return
    var dialog by remember { mutableStateOf<TaskEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val done = tasks.count { it.done }
    val progress by animateFloatAsState(if (tasks.isEmpty()) 1f else done.toFloat() / tasks.size, label = "p")
    val free = Policy.isFreeDay(set)
    val locked = !free && tasks.any { !it.done }

    LaunchedEffect(apps) { while (true) { vm.refreshUsage(); kotlinx.coroutines.delay(30_000) } }

    Page("Привет, ${set.name.ifBlank { "друг" }}!", actions = {
        IconButton(onArchive) { Icon(Icons.Default.DateRange, "Архив") }
        IconButton(onSettings) { Icon(Icons.Default.Settings, "Настройки") }
    }) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), shape = Card, colors = CardDefaults.cardColors(
                    containerColor = if (locked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(if (free) "Выходной / каникулы — только таймеры" else if (locked) "Приложения заблокированы" else "Всё сделано — приложения открыты",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Выполнено $done из ${tasks.size}")
                        LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().padding(top = 8.dp).height(10.dp),
                            color = MaterialTheme.colorScheme.secondary, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Выходной / Каникулы", Modifier.weight(1f))
                            Switch(set.vacation, { vm.setVacation(it) })
                        }
                    }
                }
            }
            if (apps.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp), shape = Card) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Приложения", style = MaterialTheme.typography.titleMedium)
                        apps.forEach { a ->
                            val usedMs = vm.used[a.pkg] ?: 0L
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(a.pkg, 32); Spacer(Modifier.width(12.dp))
                                Text(a.label, Modifier.weight(1f), maxLines = 1)
                                if (a.limitMinutes == 0) Text("Без таймера")
                                else {
                                    val left = (a.limitMinutes - usedMs / 60000).coerceAtLeast(0)
                                    val frac = (left.toFloat() / a.limitMinutes).coerceIn(0f, 1f)
                                    Text(if (locked) "заблокировано" else "$left мин", Modifier.padding(end = 8.dp))
                                    CircularProgressIndicator({ frac }, Modifier.size(28.dp), color = MaterialTheme.colorScheme.tertiary, strokeWidth = 4.dp)
                                }
                            }
                        }
                        if (locked && set.emergencyDay != java.time.LocalDate.now().toString())
                            Text("Экстренный доступ (5 мин, 1 раз в сутки) — на экране блокировки", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Text("Задания на сегодня", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium) }
            if (tasks.isEmpty()) item { Text("Заданий нет 🎉") }
            items(tasks, key = { it.id }) { t ->
                Box(Modifier.animateItem()) { TaskCard(t, vm) { dialog = t } }
            }
        }
    }
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), contentAlignment = Alignment.BottomEnd) {
        ExtendedFloatingActionButton({ adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Добавить ДЗ") })
    }
    if (adding) TaskDialog(null, { adding = false }) { a, b, c -> vm.saveTask(0, a, b, c) }
    dialog?.let { d -> TaskDialog(d, { dialog = null }) { a, b, c -> vm.saveTask(d.id, a, b, c) } }
}

@Composable
fun ArchiveScreen(vm: VM, onBack: () -> Unit) {
    val list by vm.archive.collectAsState()
    Page("Архив (7 дней)", onBack) {
        if (list.isEmpty()) Text("Архив пуст")
        LazyColumn {
            list.groupBy { it.date }.forEach { (date, ts) ->
                item { Text(date, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium) }
                items(ts, key = { it.id }) { TaskCard(it, vm, editable = false) }
            }
        }
    }
}

// ---------- Настройки ----------
val DAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: VM, onBack: () -> Unit, onMesh: () -> Unit, onApps: () -> Unit) {
    val s by vm.settings.collectAsState()
    val set = s ?: return
    Page("Настройки", onBack) {
        Text("Выходные дни", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DAYS.forEachIndexed { i, d ->
                val n = i + 1
                FilterChip(n in set.weekend, { vm.setWeekend(if (n in set.weekend) set.weekend - n else set.weekend + n) }, { Text(d) })
            }
        }
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Режим каникул", Modifier.weight(1f)); Switch(set.vacation, { vm.setVacation(it) })
        }
        Button(onApps, Modifier.fillMaxWidth()) { Text("Приложения и таймеры") }
        Spacer(Modifier.height(8.dp))
        Button(onMesh, Modifier.fillMaxWidth()) { Text(if (set.meshToken.isEmpty()) "Подключить Дневник МЭШ" else "Войти в МЭШ заново") }
        if (set.meshToken.isNotEmpty()) OutlinedButton({ vm.syncMesh() }, Modifier.fillMaxWidth()) { Text("Синхронизировать МЭШ") }
        if (vm.meshMsg.isNotEmpty()) Text(vm.meshMsg, Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        PermissionsContent()
    }
}

@Composable
fun AppsSettings(vm: VM, onBack: () -> Unit) {
    Page("Приложения и таймеры", onBack) {
        Box(Modifier.weight(1f)) { AppPicker(vm) }
        Box(Modifier.weight(1f)) { Timers(vm) }
    }
}
