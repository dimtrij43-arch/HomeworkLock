package com.hwlock

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private fun AnimatedContentTransitionScope<Int>.stepTransition(): ContentTransform {
    val forward = targetState > initialState
    return if (forward) {
        (slideInHorizontally { w -> w } + fadeIn()) togetherWith (slideOutHorizontally { w -> -w } + fadeOut())
    } else {
        (slideInHorizontally { w -> -w } + fadeIn()) togetherWith (slideOutHorizontally { w -> w } + fadeOut())
    }
}

@Composable
fun Onboarding(vm: VM, onMesh: () -> Unit, onFinish: () -> Unit) {
    var step: Int by rememberSaveable { mutableStateOf(0) }
    var name: String by rememberSaveable { mutableStateOf("") }
    var email: String by rememberSaveable { mutableStateOf("") }
    var adding: Boolean by remember { mutableStateOf(false) }
    val tasks by vm.tasks.collectAsState()
    val selected by vm.selected.collectAsState()

    val canNext: Boolean = when (step) {
        0 -> name.isNotBlank() && email.contains("@")
        1 -> selected.isNotEmpty()
        else -> true
    }

    Page("Настройка ${step + 1} / 5") {
        LinearProgressIndicator(
            progress = { (step + 1) / 5f },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = { stepTransition() },
            label = "step"
        ) { st: Int ->
            StepContent(st, vm, name, { name = it }, email, { email = it }, onMesh) { adding = true }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { step -= 1 }, modifier = Modifier.weight(1f)) { Text("Назад") }
            }
            Button(
                onClick = {
                    if (step == 0) vm.saveProfile(name.trim(), email.trim())
                    if (step < 4) step += 1 else onFinish()
                },
                modifier = Modifier.weight(1f),
                enabled = canNext
            ) { Text(if (step < 4) "Далее" else "Завершить настройку") }
        }
    }

    if (adding) {
        TaskDialog(null, { adding = false }) { a, b, c -> vm.saveTask(0, a, b, c) }
    }
}

@Composable
private fun StepContent(
    st: Int,
    vm: VM,
    name: String,
    onName: (String) -> Unit,
    email: String,
    onEmail: (String) -> Unit,
    onMesh: () -> Unit,
    onAdd: () -> Unit
) {
    val tasks by vm.tasks.collectAsState()
    Column(Modifier.fillMaxSize()) {
        when (st) {
            0 -> {
                Text("Профиль", style = MaterialTheme.typography.headlineSmall)
                Text("Данные хранятся только на устройстве.", Modifier.padding(bottom = 16.dp))
                OutlinedTextField(
                    value = name, onValueChange = onName, label = { Text("Имя") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email, onValueChange = onEmail, label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            1 -> {
                Text("Какие приложения блокировать?", style = MaterialTheme.typography.headlineSmall)
                AppPicker(vm)
            }
            2 -> {
                Text("Таймеры", style = MaterialTheme.typography.headlineSmall)
                Timers(vm)
            }
            3 -> {
                Text("Разрешения", style = MaterialTheme.typography.headlineSmall)
                PermissionsContent()
            }
            else -> {
                Text("Домашние задания", style = MaterialTheme.typography.headlineSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Button(onClick = onAdd) { Text("Добавить вручную") }
                    OutlinedButton(onClick = onMesh) { Text("Подключить Дневник МЭШ") }
                }
                if (vm.meshMsg.isNotEmpty()) Text(vm.meshMsg)
                LazyColumn {
                    items(tasks, key = { t -> t.id }) { t -> TaskCard(t, vm) {} }
                }
            }
        }
    }
}

// ---------- Окно 2: выбор приложений ----------
@Composable
fun AppPicker(vm: VM) {
    val selected by vm.selected.collectAsState()
    var q: String by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadInstalled() }
    Column {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, label = { Text("Поиск") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Системные приложения (телефон, SMS, камера, часы, контакты, лаунчер) защищены и не отображаются.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        LazyColumn {
            items(vm.installed.filter { a -> a.label.contains(q, true) }, key = { a -> a.pkg }) { a ->
                val on = selected.any { s -> s.pkg == a.pkg }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(a.pkg)
                    Spacer(Modifier.width(12.dp))
                    Text(a.label, Modifier.weight(1f))
                    Checkbox(checked = on, onCheckedChange = { c -> vm.toggleApp(a, c) })
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
        items(apps, key = { a -> a.pkg }) { a ->
            androidx.compose.material3.Card(Modifier.fillMaxWidth(), shape = Card) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(a.pkg)
                        Spacer(Modifier.width(12.dp))
                        Text(a.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Без ограничения по времени", Modifier.weight(1f))
                        Switch(
                            checked = a.limitMinutes == 0,
                            onCheckedChange = { on -> vm.setLimit(a.pkg, if (on) 0 else 30) }
                        )
                    }
                    AnimatedVisibility(visible = a.limitMinutes > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalIconButton(onClick = { vm.setLimit(a.pkg, (a.limitMinutes - 5).coerceAtLeast(5)) }) {
                                Icon(Icons.Default.KeyboardArrowDown, "-5")
                            }
                            val h = a.limitMinutes / 60
                            val m = a.limitMinutes % 60
                            Text("  $h ч $m мин  ", style = MaterialTheme.typography.titleLarge)
                            FilledTonalIconButton(onClick = { vm.setLimit(a.pkg, (a.limitMinutes + 5).coerceAtMost(720)) }) {
                                Icon(Icons.Default.KeyboardArrowUp, "+5")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Окно 4: разрешения ----------
private data class PermItem(val title: String, val desc: String, val ok: Boolean, val intent: Intent)

@Composable
fun PermissionsContent() {
    val ctx = LocalContext.current
    var tick: Int by remember { mutableStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick += 1 }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val list = remember(tick) {
        listOf(
            PermItem("Доступ к истории использования", "Учёт времени в приложениях", Perms.usage(ctx), Perms.usageIntent(ctx)),
            PermItem("Поверх других окон", "Показ экрана-блокировщика", Perms.overlay(ctx), Perms.overlayIntent(ctx)),
            PermItem("Служба специальных возможностей", "Мгновенный перехват запуска приложений", Perms.accessibility(ctx), Perms.accessibilityIntent(ctx)),
            PermItem("Администратор устройства", "Защита от удаления", Perms.admin(ctx), Perms.adminIntent(ctx)),
            PermItem("Без ограничений батареи", "Чтобы система не убивала защиту", Perms.battery(ctx), Perms.batteryIntent(ctx))
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { p ->
            androidx.compose.material3.Card(Modifier.fillMaxWidth(), shape = Card) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = MaterialTheme.typography.titleSmall)
                        Text(p.desc, style = MaterialTheme.typography.bodySmall)
                    }
                    if (p.ok) {
                        Icon(Icons.Default.CheckCircle, "OK", tint = MaterialTheme.colorScheme.secondary)
                    } else {
                        Button(onClick = { runCatching { ctx.startActivity(p.intent) } }) { Text("Выдать") }
                    }
                }
            }
        }
    }
}
