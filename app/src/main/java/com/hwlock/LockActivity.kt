package com.hwlock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class LockActivity : ComponentActivity() {
    private var reason by mutableStateOf("HOMEWORK")
    private var pkg by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        read(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = home()
        })
        setContent {
            HwTheme {
                LockUi(reason, onList = {
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish()
                }, onHome = ::home, onEmergency = {
                    val ok = Graph.prefs.useEmergency()
                    if (ok) {
                        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                        finish()
                    }
                })
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); read(intent) }
    private fun read(i: Intent) { reason = i.getStringExtra("reason") ?: "HOMEWORK"; pkg = i.getStringExtra("pkg") ?: "" }
    private fun home() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@Composable
fun LockUi(reason: String, onList: () -> Unit, onHome: () -> Unit, onEmergency: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { available = Graph.prefs.flow.first().emergencyDay != LocalDate.now().toString() }
    val progress = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) { progress.animateTo(1f, tween(10_000, easing = LinearEasing)); scope.launch { onEmergency() } }
        else progress.snapTo(0f)
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF141A33)).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 480.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(when (reason) { "LIMIT" -> "Лимит времени на сегодня исчерпан"; "NIGHT" -> "Ночной режим"; else -> "Сначала домашние задания" },
                color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(when (reason) { "LIMIT" -> "Приложение снова будет доступно завтра."; "NIGHT" -> "Приложения недоступны с 23:00 до 06:00. Пора отдыхать."; else -> "Выполни все задания — и приложения откроются." },
                color = Color(0xFFB0B8E0), textAlign = TextAlign.Center)
            if (reason == "HOMEWORK") Button(onList, Modifier.fillMaxWidth()) { Text("Перейти к списку ДЗ") }
            OutlinedButton(onHome, Modifier.fillMaxWidth()) { Text("На главный экран", color = Color.White) }
            if (reason == "HOMEWORK") {
                if (available) {
                    Text("Экстренный доступ на 5 минут (1 раз в сутки). Удерживайте кнопку 10 секунд.",
                        color = Color(0xFFB0B8E0), textAlign = TextAlign.Center)
                    Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF2B3354))
                        .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }) },
                        contentAlignment = Alignment.CenterStart) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(Color(0xFFFF8F00)))
                        Text("Удерживайте: экстренный доступ", Modifier.fillMaxWidth(), color = Color.White, textAlign = TextAlign.Center)
                    }
                } else Text("Экстренный доступ на сегодня уже использован", color = Color(0xFFB0B8E0))
            }
        }
    }
}
