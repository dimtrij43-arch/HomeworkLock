package com.hwlock

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun Onboarding(vm: VM, onMesh: () -> Unit, onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val tasks by vm.tasks.collectAsState()
    val selected by vm.selected.collectAsState()

    val canNext = when (step) {
        0 -> name.isNotBlank() && email.contains("@")
        1 -> selected.isNotEmpty()
        else -> true
    }
    Page("Настройка ${step + 1} / 5") {
        LinearProgressIndicator({ (step + 1) / 5f }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
        AnimatedContent(step, Modifier.weight(1f), label = "step", transitionSpec = {
            if (targetState > initialState) (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
            else (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
        }) { st ->
            Column(Modifier.fillMaxSize()) {
                when (st) {
                    0 -> {
                        Text("Профиль", style = MaterialTheme.typography.headlineSmall)
                        Text("Данные хранятся только на устройстве.", Modifier.padding(bottom = 16.dp))
                        OutlinedTextField(name, { name = it }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                    }
                    1 -> { Text("Какие приложения блокировать?", style = MaterialTheme.typography.headlineSmall); AppPicker(vm) }
                    2 -> { Text("Таймеры", style = MaterialTheme.typography.headlineSmall); Timers(vm) }
                    3 -> { Text("Разрешения", style = MaterialTheme.typography.headlineSmall); PermissionsContent() }
                    else -> {
                        Text("Домашние задания", style = MaterialTheme.typography.headlineSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                            Button({ adding = true }) { Text("Добавить вручную") }
                            OutlinedButton(onMesh) { Text("Подключить Дневник МЭШ") }
                        }
                        if (vm.meshMsg.isNotEmpty()) Text(vm.meshMsg)
                        LazyColumn { items(tasks, key = { it.id }) { TaskCard(it, vm) {} } }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) OutlinedButton({ step-- }, Modifier.weight(1f)) { Text("Назад") }
            Button({
                if (step == 0) vm.saveProfile(name.trim(), email.trim())
                if (step < 4) step++ else onFinish()
            }, Modifier.weight(1f), enabled = canNext) { Text(if (step < 4) "Далее" else "Завершить настройку") }
        }
    }
    if (adding) TaskDialog(null, { adding = false }) { a, b, c -> vm.saveTask(0, a, b, c) }
}

// ---------- Окно 2: выбор приложений ----------
@Composable
fun AppPicker(vm: VM) {
    val selected by vm.selected.collectAsState()
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadInstalled() }
    Column {
        OutlinedTextField(q, { q = it }, label = { Text("Поиск") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
        Text("Системные приложения (телефон, SMS, камера, часы, контакты, лаунчер) защищены и не отображаются.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
        LazyColumn {
            items(vm.installed.filter { it.label.contains(q, true) }, key = { it.pkg }) { a ->
                val on = selected.any { it.pkg == a.pkg }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a.pkg); Spacer(Modifier.width(12.dp))
                    Text(a.label, Modifier.weight(1f))
                    Checkbox(on, { vm.toggleApp(a, it) })
                }
            }
        }
    }
}

// ---------- Окно 3: таймеры ----------
@Composable
fun Timers(vm: VM) {
    val apps by vm.selected.collectAsState()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(apps, key = { it.pkg }) { a ->
            Card(Modifier.fillMaxWidth(), shape = Card) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(a.pkg); Spacer(Modifier.width(12.dp))
                        Text(a.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Без ограничения по времени", Modifier.weight(1f))
                        Switch(a.limitMinutes == 0, { vm.setLimit(a.pkg, if (it) 0 else 30) })
                    }
                    AnimatedVisibility(a.limitMinutes > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            FilledTonalIconButton({ vm.setLimit(a.pkg, (a.limitMinutes - 5).coerceAtLeast(5)) }) { Icon(Icons.Default.KeyboardArrowDown, "-5") }
                            val h = a.limitMinutes / 60; val m = a.limitMinutes % 60
                            Text("  ${h} ч ${m} мин  ", style = MaterialTheme.typography.titleLarge)
                            FilledTonalIconButton({ vm.setLimit(a.pkg, (a.limitMinutes + 5).coerceAtMost(720)) }) { Icon(Icons.Default.KeyboardArrowUp, "+5") }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Окно 4: разрешения ----------
@Composable
fun PermissionsContent() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    data class P(val title: String, val desc: String, val ok: Boolean, val intent: Intent)
    val list = remember(tick) {
        listOf(
            P("Доступ к истории использования", "Учёт времени в приложениях", Perms.usage(ctx), Perms.usageIntent(ctx)),
            P("Поверх других окон", "Показ экрана-блокировщика", Perms.overlay(ctx), Perms.overlayIntent(ctx)),
            P("Служба специальных возможностей", "Мгновенный перехват запуска приложений", Perms.accessibility(ctx), Perms.accessibilityIntent(ctx)),
            P("Администратор устройства", "Защита от удаления", Perms.admin(ctx), Perms.adminIntent(ctx)),
            P("Без ограничений батареи", "Чтобы система не убивала защиту", Perms.battery(ctx), Perms.batteryIntent(ctx))
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { p ->
            Card(Modifier.fillMaxWidth(), shape = Card) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = MaterialTheme.typography.titleSmall)
                        Text(p.desc, style = MaterialTheme.typography.bodySmall)
                    }
                    if (p.ok) Icon(Icons.Default.CheckCircle, "OK", tint = MaterialTheme.colorScheme.secondary)
                    else Button({ runCatching { ctx.startActivity(p.intent) } }) { Text("Выдать") }
                }
            }
        }
    }
}
