package com.hwlock

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.time.LocalDate

val CardShape = RoundedCornerShape(20.dp)

fun openLink(ctx: Context, raw: String) {
    val u = if ("://" in raw) raw else "https://$raw"
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
    }
}

/** Единая карточка: скругление 20dp, тонкая обводка и мягкая тень. */
@Composable
fun HwCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}

@Composable
fun AppIcon(pkg: String, size: Int = 40) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) {
        try { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() } catch (_: Throwable) { null }
    }
    if (bmp != null) Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(size.dp))
    else Spacer(Modifier.size(size.dp))
}

@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    scroll: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = 8.dp))
            actions()
        }
        val base = Modifier.widthIn(max = 640.dp).fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
        val scrollState = rememberScrollState()
        Column(if (scroll) base.verticalScroll(scrollState) else base, content = content)
    }
}

// ---------- Задачи ----------
@Composable
fun TaskCard(t: TaskEntity, vm: VM, editable: Boolean = true, onEdit: () -> Unit = {}) {
    val ctx = LocalContext.current
    val a by animateFloatAsState(if (t.done) 0.45f else 1f, label = "done")
    HwCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            if (editable) Checkbox(checked = t.done, onCheckedChange = { vm.toggleDone(t) })
            Column(Modifier.weight(1f).alpha(a)) {
                Text(
                    t.subject, style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null
                )
                if (t.description.isNotBlank()) {
                    Text(t.description, textDecoration = if (t.done) TextDecoration.LineThrough else null)
                }
                t.links.lines().filter { l -> l.isNotBlank() }.forEach { l ->
                    TextButton(onClick = { openLink(ctx, l.trim()) }, contentPadding = PaddingValues(0.dp)) {
                        Text(l.trim(), maxLines = 1, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (editable) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Изменить") }
                IconButton(onClick = { vm.delete(t) }) { Icon(Icons.Default.Delete, "Удалить") }
            }
        }
    }
}

@Composable
fun TaskDialog(initial: TaskEntity?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var subj by remember { mutableStateOf(initial?.subject ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var links by remember { mutableStateOf(initial?.links ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CardShape,
        title = { Text(if (initial == null) "Новое задание" else "Редактирование") },
        text = {
            Column(
                Modifier.imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = subj, onValueChange = { subj = it }, label = { Text("Предмет") },
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Задание") }, minLines = 2)
                OutlinedTextField(
                    value = links, onValueChange = { links = it },
                    label = { Text("Ссылки (каждая с новой строки)") }, minLines = 2,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (subj.isNotBlank()) { onSave(subj.trim(), desc.trim(), links.trim()); onDismiss() }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
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
    val done = tasks.count { t -> t.done }
    val progress by animateFloatAsState(if (tasks.isEmpty()) 1f else done.toFloat() / tasks.size, label = "p")
    val free = Policy.isFreeDay(set)
    val night = Policy.isNight()
    val locked = !free && tasks.any { t -> !t.done }
    val red = night || locked

    LaunchedEffect(apps) {
        while (true) { vm.refreshUsage(); delay(30_000) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        val raw = (maxWidth - 640.dp) / 2
        val side = if (raw < 16.dp) 16.dp else raw
        // Прокручиваемый список — корневой контейнер: свайп работает в любой точке экрана.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Привет, ${set.name.ifBlank { "друг" }}!",
                        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onArchive) { Icon(Icons.Default.DateRange, "Архив") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.SettingsIcon, "Настройки") }
                }
            }
            item {
                HwCard(
                    Modifier.fillMaxWidth(),
                    container = if (red) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            when {
                                night -> "Ночной режим до 06:00 — приложения заблокированы"
                                free -> "Выходной / каникулы — только таймеры"
                                locked -> "Приложения заблокированы"
                                else -> "Всё сделано — приложения открыты"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Выполнено $done из ${tasks.size}")
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(10.dp),
                            color = if (red) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            strokeCap = StrokeCap.Round
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Выходной / Каникулы", Modifier.weight(1f))
                            Switch(checked = set.vacation, onCheckedChange = { v -> vm.setVacation(v) })
                        }
                    }
                }
            }
            if (apps.isNotEmpty()) {
                item {
                    HwCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Приложения", style = MaterialTheme.typography.titleMedium)
                            apps.forEach { a ->
                                val usedMs = vm.used[a.pkg] ?: 0L
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(a.pkg, 32)
                                    Spacer(Modifier.width(12.dp))
                                    Text(a.label, Modifier.weight(1f), maxLines = 1)
                                    if (a.limitMinutes == 0) {
                                        Text(if (night || locked) "заблокировано" else "Без таймера")
                                    } else {
                                        val left = (a.limitMinutes - usedMs / 60000).coerceAtLeast(0)
                                        val frac = (left.toFloat() / a.limitMinutes).coerceIn(0f, 1f)
                                        Text(if (night || locked) "заблокировано" else "$left мин", Modifier.padding(end = 8.dp))
                                        CircularProgressIndicator(
                                            progress = { frac }, modifier = Modifier.size(28.dp),
                                            color = MaterialTheme.colorScheme.tertiary, strokeWidth = 4.dp
                                        )
                                    }
                                }
                            }
                            if (locked && set.emergencyDay != LocalDate.now().toString()) {
                                Text(
                                    "Экстренный доступ (5 мин, 1 раз в сутки) — на экране блокировки",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
            item { Text("Задания на сегодня", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium) }
            if (tasks.isEmpty()) item { Text("Заданий нет 🎉") }
            items(tasks, key = { t -> t.id }) { t ->
                Box(Modifier.animateItem()) { TaskCard(t, vm) { dialog = t } }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Добавить ДЗ") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        )
    }
    if (adding) TaskDialog(null, { adding = false }) { a, b, c -> vm.saveTask(0, a, b, c) }
    dialog?.let { d -> TaskDialog(d, { dialog = null }) { a, b, c -> vm.saveTask(d.id, a, b, c) } }
}

@Composable
fun ArchiveScreen(vm: VM, onBack: () -> Unit) {
    val list by vm.archive.collectAsState()
    Page("Архив (7 дней)", onBack) {
        if (list.isEmpty()) Text("Архив пуст")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.groupBy { t -> t.date }.forEach { (date, ts) ->
                item { Text(date, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium) }
                items(ts, key = { t -> t.id }) { t -> TaskCard(t, vm, editable = false) }
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
    Page("Настройки", onBack, scroll = true) {
        Text("Выходные дни", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DAYS.forEachIndexed { i, d ->
                val n = i + 1
                FilterChip(
                    selected = n in set.weekend,
                    onClick = { vm.setWeekend(if (n in set.weekend) set.weekend - n else set.weekend + n) },
                    label = { Text(d) }
                )
            }
        }
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Режим каникул", Modifier.weight(1f))
            Switch(checked = set.vacation, onCheckedChange = { v -> vm.setVacation(v) })
        }
        Button(onClick = onApps, modifier = Modifier.fillMaxWidth()) { Text("Приложения и таймеры") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onMesh, modifier = Modifier.fillMaxWidth()) {
            Text(if (set.meshToken.isEmpty()) "Подключить Дневник МЭШ" else "Войти в МЭШ заново")
        }
        if (set.meshToken.isNotEmpty()) {
            OutlinedButton(onClick = { vm.syncMesh() }, modifier = Modifier.fillMaxWidth()) { Text("Синхронизировать МЭШ") }
        }
        if (vm.meshMsg.isNotEmpty()) Text(vm.meshMsg, Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        PermissionsContent()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AppsSettings(vm: VM, onBack: () -> Unit) {
    Page("Приложения и таймеры", onBack) {
        Box(Modifier.weight(1f)) { AppPicker(vm) }
        Box(Modifier.weight(1f)) { Timers(vm) }
    }
}
